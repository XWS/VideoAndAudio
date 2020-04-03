package com.test.zh.videoandaudio.codec.extractorImp

import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * 音视频数据提取器封装类(主要利用安卓自带MediaExtractor Api进行音视频的数据提取功能)
 */
class MMExtractor(path: String) {

    //音视频数据提取器
    private val mExtractor: MediaExtractor = MediaExtractor()

    //音频通道索引
    var mAudioTrack = -1

    //视频通道索引
    var mVideoTract = -1

    //当前帧时间戳
    private var mCurSampleTime: Long = 0

    //开始解码时间点
    var mStartPos: Long = 0

    init {
        mExtractor.setDataSource(path)
    }

    //获取视频格式参数
    fun getVideoFormat(): MediaFormat? {
        for (i in 0 until mExtractor.trackCount) {
            val mediaFormat = mExtractor.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("video/")) {
                mVideoTract = i
                break
            }
        }
        return if (mVideoTract >= 0) mExtractor.getTrackFormat(mVideoTract) else null
    }

    //获取音频格式参数
    fun getAudioFormat(): MediaFormat? {
        for (i in 0 until mExtractor.trackCount) {
            val mediaFormat = mExtractor.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                mAudioTrack = i
                break
            }
        }
        return if (mAudioTrack >= 0) mExtractor.getTrackFormat(mAudioTrack) else null
    }

    //读取音视频数据
    fun readBuffer(byteBuffer: ByteBuffer): Int {
        byteBuffer.clear()
        selectSourceTract()
        val readSampleCount = mExtractor.readSampleData(byteBuffer, 0)
        if (readSampleCount < 0) return -1
        mCurSampleTime = mExtractor.sampleTime
        mExtractor.advance()
        return readSampleCount
    }

    //选择通道（是音频还是视频）
    private fun selectSourceTract() {
        if (mVideoTract >= 0) {
            mExtractor.selectTrack(mVideoTract)
        } else if (mAudioTrack >= 0) {
            mExtractor.selectTrack(mAudioTrack)
        }
    }

    //Seek到指定位置，并返回实际帧的时间戳
    fun seek(pos: Long): Long {
        mExtractor.seekTo(pos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        return mExtractor.sampleTime
    }

    //停止读取数据
    fun stop() {
        mExtractor.release()
    }

    fun getCurTimestamp(): Long {
        return mCurSampleTime
    }
}