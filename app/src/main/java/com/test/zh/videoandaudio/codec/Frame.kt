package com.test.zh.videoandaudio.codec

import android.media.MediaCodec
import java.nio.ByteBuffer

/**
 * 单帧数据类型（封装类，用以数据处理方便）
 */
class Frame {
    var buffer: ByteBuffer? = null

    var bufferInfo = MediaCodec.BufferInfo()
        private set

    fun setBufferInfo(info: MediaCodec.BufferInfo) {
        bufferInfo.set(info.offset, info.size, info.presentationTimeUs, info.flags)
    }
}