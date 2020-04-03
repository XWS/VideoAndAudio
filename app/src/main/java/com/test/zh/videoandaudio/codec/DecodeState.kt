package com.test.zh.videoandaudio.codec

enum class DecodeState {
    //开始状态
    START,

    //解码中
    DECODING,

    //暂停
    PAUSE,

    //快进中
    SEEKING,

    //解码完成
    FINISH,

    //解码停止，需释放
    STOP
}