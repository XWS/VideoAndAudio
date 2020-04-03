package com.test.zh.videoandaudio.codec.decoderImp

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.test.zh.videoandaudio.codec.BaseDecoder
import com.test.zh.videoandaudio.codec.IExtractor
import com.test.zh.videoandaudio.codec.extractorImp.VideoExtractor
import java.nio.ByteBuffer

class VideoDecoder(
    path: String,
    sfv: SurfaceView?,
    surface: Surface?
) : BaseDecoder(path) {

    private val mSurfaceView = sfv
    private var mSurface = surface

    override fun check(): Boolean {
        if (mSurfaceView == null && mSurface == null) {
            mStateListener?.decoderError(this, "显示器为空")
            return false
        }
        return true
    }

    override fun initRender(): Boolean = true

    override fun configCodec(codec: MediaCodec, format: MediaFormat): Boolean {
        if (mSurface != null) {
            codec.configure(format, mSurface, null, 0)
            notifyDecode()
        } else {
            mSurfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceChanged(
                    holder: SurfaceHolder?,
                    format: Int,
                    width: Int,
                    height: Int
                ) {

                }

                override fun surfaceDestroyed(holder: SurfaceHolder?) {

                }

                override fun surfaceCreated(holder: SurfaceHolder?) {
                    mSurface = holder!!.surface
                    configCodec(codec, format)
                }
            })
            return false
        }
        return true
    }

    override fun initExtractor(path: String): IExtractor = VideoExtractor(path)

    override fun initSpecParams(format: MediaFormat) {

    }

    override fun doneDecode() {

    }

    override fun render(outputBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {

    }
}