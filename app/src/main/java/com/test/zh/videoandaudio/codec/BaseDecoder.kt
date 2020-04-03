package com.test.zh.videoandaudio.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer

//解码器基类，封装了解码的基本通用功能和流程
abstract class BaseDecoder(private var mFilePath: String = "") : IDecoder {

    //编码器是否正在工作
    private var mIsRunning = true

    //线程等待锁
    private val mLock = Object()

    //是否可以进入解码流程
    private var mIsReadyForDeCode = false

    //音视频解码器
    private var mCodec: MediaCodec? = null

    private var mExtractor: IExtractor? = null

    //数据流输入缓冲区
    private var mInputBuffers: Array<ByteBuffer>? = null

    //数据流输出缓冲区
    private var mOutputBuffers: Array<ByteBuffer>? = null

    //解码数据信息
    private var mBufferInfo = MediaCodec.BufferInfo()

    //当前解码状态
    private var mState = DecodeState.STOP

    //解码状态监听
    var mStateListener: IDecoderStateListener? = null

    /**
     * 流数据是否结束(EOS:End Of Stream)
     */
    private var mIsEOS = false

    //视频画面宽度
    protected var mVideoWidth = 0

    //视频画面高度
    protected var mVideoHeight = 0

    private var mDuration: Long = 0

    private var mStartPos: Long = 0

    private var mEndPos: Long = 0

    // 是否需要音视频渲染同步
    private var mSyncRender = true

    private val TAG = "BaseDecoder"

    /**
     * 开始解码时间，用于音视频同步
     */
    private var mStartTimeForSync = -1L

    override fun run() {
        if (mState == DecodeState.STOP) {
            mState = DecodeState.START
        }
        mStateListener?.decoderPrepare(this)

        //【解码步骤：1. 初始化，并启动解码器】
        if (!init()) return

        Log.i(TAG, "开始解码")
        try {
            while (mIsRunning) {
                if (mState != DecodeState.START
                    && mState != DecodeState.DECODING
                    && mState != DecodeState.SEEKING
                ) {
                    Log.i(TAG, "进入等待：$mState")
                    waitDecode()

                    // ---------【同步时间矫正】-------------
                    //恢复同步的起始时间，即去除等待流失的时间
                    mStartTimeForSync = System.currentTimeMillis() - getCurTimeStamp()
                }
                if (mStartTimeForSync == -1L) {
                    mStartTimeForSync = System.currentTimeMillis()
                }
                if (!mIsRunning || mState == DecodeState.STOP) {
                    mIsRunning = false
                    break
                }
                if (!mIsEOS) {
                    //【解码步骤：2. 将数据压入解码器输入缓冲】
                    mIsEOS = pushBufferToDecoder()
                }

                //【解码步骤：3. 将解码好的数据从缓冲区拉取出来】
                val index = pullBufferFromDecoder()
                if (index >= 0) {
                    // ---------【音视频同步】-------------
                    if (mSyncRender && mState == DecodeState.DECODING) sleepRender()

                    //【解码步骤：4. 渲染】(非必要步骤，如果只是用于编码合成新视频，无需渲染)
                    if (mSyncRender) {
                        render(mOutputBuffers!![index], mBufferInfo)
                    }

                    //将解码数据传递出去
                    val frame = Frame().apply {
                        this.buffer = mOutputBuffers!![index]
                        this.setBufferInfo(mBufferInfo)
                    }
                    mStateListener?.decodeOneFrame(this, frame)

                    //【解码步骤：5. 释放输出缓冲】
                    mCodec!!.releaseOutputBuffer(index, true)
                    if (mState == DecodeState.START) mState = DecodeState.PAUSE
                }

                //【解码步骤：6. 判断解码是否完成】
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    mState = DecodeState.FINISH
                    mStateListener?.decoderFinish(this)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            //【解码步骤：7. 释放解码器】
            doneDecode()
            release()
        }
    }

    override fun pause() {
        mState = DecodeState.DECODING
    }

    override fun goOn() {
        mState = DecodeState.DECODING
        notifyDecode()
    }

    override fun stop() {
        mState = DecodeState.STOP
        mIsRunning = false
        notifyDecode()
    }

    override fun getDuration(): Long {
        return mDuration
    }

    //【解码步骤：1. 初始化，并启动解码器】
    private fun init(): Boolean {

        //1，验证路径
        if (mFilePath.isEmpty() || !File(mFilePath).exists()) {
            Log.w("TAG", "文件路径为空")
            mStateListener?.decoderError(this, "文件路径为空")
            return false
        }

        //调用虚函数，检查子类参数是否完整(路径是否有效等)
        if (!check()) return false

        //2，初始化数据提取器(初始化Extractor)
        mExtractor = initExtractor(mFilePath)
        if (mExtractor == null || mExtractor!!.getMediaFormat() == null) return false

        //3.初始化参数(提取一些必须的参数：duration，width，height等)
        if (!initParams()) return false

        //4.初始化渲染器(视频不需要，音频为AudioTracker)
        if (!initRender()) return false

        //5.初始化解码器(初始化MediaCodec)
        if (!initCodec()) return false
        return true
    }

    //【解码步骤：2. 将数据压入解码器输入缓冲】
    private fun pushBufferToDecoder(): Boolean {
        try {
            val inputBufferIndex = mCodec!!.dequeueInputBuffer(1000)
            var isEndOfStream = false
            if (inputBufferIndex >= 0) {
                val inputBuffer = mInputBuffers!![inputBufferIndex]
                val sampleSize = mExtractor!!.readBuffer(inputBuffer)
                if (sampleSize < 0) {
                    //如果数据已经取完，压入数据结束标志：MediaCodec.BUFFER_FLAG_END_OF_STREAM\
                    mCodec!!.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    isEndOfStream = true
                } else {
                    mCodec!!.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        sampleSize,
                        mExtractor!!.getCurrentTimestamp(),
                        0
                    )
                }
            }
            return isEndOfStream
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun pullBufferFromDecoder(): Int {
        when (val outputStreamIndex = mCodec!!.dequeueOutputBuffer(mBufferInfo, 1000)) {
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {//输出格式改变了
            }
            MediaCodec.INFO_TRY_AGAIN_LATER -> {//输入缓冲改变了
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {//没有可用数据，等会再来
                mOutputBuffers = mCodec!!.outputBuffers
            }
            else -> return outputStreamIndex
        }
        return -1
    }

    /**
     * 初始化参数
     */
    private fun initParams(): Boolean {
        try {
            val mediaFormat = mExtractor!!.getMediaFormat()
            val durationTime = mediaFormat!!.getLong(MediaFormat.KEY_DURATION) / 1000
            if (mEndPos == 0L) mEndPos = durationTime
            initSpecParams(mediaFormat)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * 初始化解码器
     */
    private fun initCodec(): Boolean {
        try {

            //1.根据音视频编码格式初始化解码器
            val type = mExtractor!!.getMediaFormat()!!.getString(MediaFormat.KEY_MIME)
            mCodec = MediaCodec.createDecoderByType(type!!)

            //2.配置解码器
            if (!configCodec(mCodec!!, mExtractor!!.getMediaFormat()!!)) waitDecode()

            //3.启动解码器
            mCodec!!.start()

            //4.获取解码器缓冲区
            mInputBuffers = mCodec!!.inputBuffers
            mOutputBuffers = mCodec!!.outputBuffers
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * 解码线程进入等待状态
     */
    private fun waitDecode() {
        try {
            if (mState == DecodeState.PAUSE) {
                mStateListener?.decoderPause(this)
            }
            synchronized(mLock) {
                mLock.wait()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 唤醒解码线程
     */
    fun notifyDecode() {
        synchronized(mLock) {
            mLock.notifyAll()
        }
        if (mState == DecodeState.DECODING) {
            mStateListener?.decoderRunning(this)
        }
    }

    private fun sleepRender() {
        val passTime = System.currentTimeMillis() - mStartTimeForSync
        val curTime = getCurTimeStamp()
        if (curTime > passTime) Thread.sleep(curTime - passTime)
    }

    /**
     * 解码结束
     */
    private fun release() {
        Log.i(TAG, "解码停止，释放解码器")
        mState = DecodeState.STOP
        mIsEOS = false
        mExtractor?.stop()
        mCodec?.stop()
        mCodec?.release()
        mStateListener?.decoderDestroy(this)
    }

    /**
     * mBufferInfo.presentationTimeUs方法获取到的时间单位是微秒
     */
    override fun getCurTimeStamp(): Long = mBufferInfo.presentationTimeUs / 1000

    override fun getWidth(): Int = mVideoWidth

    override fun getHeight(): Int = mVideoHeight

    override fun seekTo(pos: Long): Long = 0

    override fun seekAndPlay(pos: Long): Long = 0

    override fun isDecoding(): Boolean = mState == DecodeState.DECODING

    override fun isStop(): Boolean = mState == DecodeState.STOP

    override fun isSeeking(): Boolean = mState == DecodeState.SEEKING

    override fun setStateListener(l: IDecoderStateListener?) {
        mStateListener = l
    }

    override fun getRotationAngle(): Int = 0

    override fun getMediaFormat(): MediaFormat? = mExtractor?.getMediaFormat()

    override fun getTrack(): Int = 0

    override fun getFilePath(): String = mFilePath

    override fun withoutSync(): IDecoder {
        mSyncRender = false
        return this
    }

    /**
     * 检查子类参数
     */
    abstract fun check(): Boolean

    /**
     * 初始化渲染器
     */
    abstract fun initRender(): Boolean

    /**
     * 渲染
     */
    abstract fun render(
        outputBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    )

    /**
     * 配置解码器
     */
    abstract fun configCodec(codec: MediaCodec, format: MediaFormat): Boolean

    /**
     * 初始化数据提取器
     */
    abstract fun initExtractor(path: String): IExtractor

    /**
     * 初始化子类自己特有的参数
     */
    abstract fun initSpecParams(format: MediaFormat)

    /**
     * 结束解码
     */
    abstract fun doneDecode()
}