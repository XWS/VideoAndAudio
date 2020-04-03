package com.test.zh.videoandaudio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import com.test.zh.videoandaudio.codec.decoderImp.AudioDecoder
import com.test.zh.videoandaudio.codec.decoderImp.VideoDecoder
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    lateinit var videoDecoder:VideoDecoder

    lateinit var audioDecoder:AudioDecoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initPlayer()
    }

    private fun initPlayer() {
        val path = "${Environment.getExternalStorageDirectory().absoluteFile}/mvtest_2.mp4"
        val newFixedThreadPool = Executors.newFixedThreadPool(10)

        videoDecoder = VideoDecoder(path, sfv, null)
        newFixedThreadPool.execute(videoDecoder)

        audioDecoder = AudioDecoder(path)
        newFixedThreadPool.execute(audioDecoder)

        videoDecoder.goOn()
        audioDecoder.goOn()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoDecoder.stop()
        audioDecoder.stop()
    }
}
