package com.test.zh.videoandaudio.codec.decoderImp

import android.media.*
import android.os.Build
import com.test.zh.videoandaudio.codec.BaseDecoder
import com.test.zh.videoandaudio.codec.IExtractor
import com.test.zh.videoandaudio.codec.extractorImp.AudioExtractor
import java.nio.ByteBuffer

class AudioDecoder(path: String) : BaseDecoder(path) {

    //采样率
    private var mSimpleRate = -1

    //声音通道数量
    private var mChannels = 1

    //PCM采样位数(默认16bit)
    private var mPCMEncodeBit = AudioFormat.ENCODING_PCM_16BIT

    //音频渲染器（AudioTrack和MediaPlayer都是安卓系统自带的两个渲染音频的Api，MediaPlayer其实也是在framework层创建了一个AudioTrack，并将解码以后的PCM数据传递给AudioTrack）
    private var mAudioTrack: AudioTrack? = null

    private var mAudioOutTempBuf: ShortArray? = null

    override fun check(): Boolean = true

    override fun configCodec(codec: MediaCodec, format: MediaFormat): Boolean {
        codec.configure(format, null, null, 0)
        return true
    }

    override fun initExtractor(path: String): IExtractor = AudioExtractor(path)

    override fun initSpecParams(format: MediaFormat) {
        try {
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            mSimpleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            mPCMEncodeBit = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun initRender(): Boolean {
        val channel = if (mChannels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }

        //获取最小缓冲区
        val minBufferSize = AudioTrack.getMinBufferSize(mSimpleRate, channel, mPCMEncodeBit)
        mAudioOutTempBuf =
            ShortArray(minBufferSize / 2)//这里byteBuffer转换为shortArray的时候容量需要减半，因为byte是8位1字节，而short是16位2字节
        mAudioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            mSimpleRate,
            channel,
            mPCMEncodeBit,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )
        mAudioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            mSimpleRate,
            channel,
            mPCMEncodeBit,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )
//        mAudioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            AudioTrack.Builder()
//                .setAudioAttributes(
//                    AudioAttributes.Builder()
//                        .setUsage(AudioAttributes.USAGE_MEDIA)
//                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                        .build()
//                )
//                .setAudioFormat(
//                    AudioFormat.Builder()
//                        .setChannelMask(mChannels)
//                        .setEncoding(mPCMEncodeBit)
//                        .setSampleRate(mSimpleRate)
//                        .build()
//                )
//                .setTransferMode(AudioTrack.MODE_STREAM)
//                .setBufferSizeInBytes(minBufferSize)
//                .build()
//        } else {
//            AudioTrack(
//                AudioManager.STREAM_MUSIC,
//                mSimpleRate,
//                channel,
//                mPCMEncodeBit,
//                minBufferSize,
//                AudioTrack.MODE_STREAM
//            )
//        }
        mAudioTrack?.play()
        return true
    }

    override fun render(outputBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (mAudioOutTempBuf!!.size < bufferInfo.size / 2) {
            mAudioOutTempBuf = ShortArray(bufferInfo.size / 2)
        }
        outputBuffer.position(0)
        outputBuffer.asShortBuffer().get(mAudioOutTempBuf, 0, bufferInfo.size / 2)
        mAudioTrack?.write(mAudioOutTempBuf!!, 0, bufferInfo.size / 2)
    }

    override fun doneDecode() {
        mAudioTrack?.stop()
        mAudioTrack?.release()
    }
}