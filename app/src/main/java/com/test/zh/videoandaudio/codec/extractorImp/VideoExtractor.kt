package com.test.zh.videoandaudio.codec.extractorImp

import android.media.MediaFormat
import com.test.zh.videoandaudio.codec.IExtractor
import java.nio.ByteBuffer

//视频提取器
class VideoExtractor(path: String) :
    IExtractor {

    private val mMediaExtractor =
        MMExtractor(path)

    override fun getMediaFormat(): MediaFormat? = mMediaExtractor.getVideoFormat()

    override fun readBuffer(buffer: ByteBuffer): Int = mMediaExtractor.readBuffer(buffer)

    override fun getCurrentTimestamp(): Long = mMediaExtractor.getCurTimestamp()

    override fun seek(pos: Long): Long = mMediaExtractor.seek(pos)

    override fun setStartPos(pos: Long) {
        mMediaExtractor.mStartPos = pos
    }

    override fun stop() = mMediaExtractor.stop()
}