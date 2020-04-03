package com.test.zh.videoandaudio.codec

import android.media.MediaFormat
import java.nio.ByteBuffer

//音视频读取器接口
interface IExtractor {

    //获取音视频格式数据
    fun getMediaFormat(): MediaFormat?

    //读取音视频数据
    fun readBuffer(buffer: ByteBuffer): Int

    //获取当前帧时间
    fun getCurrentTimestamp(): Long

    //Seek到指定位置，并返回实际帧时间戳
    fun seek(pos: Long): Long

    fun setStartPos(pos: Long)

    //停止读取数据
    fun stop()
}