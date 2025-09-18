package com.example.rtspviewer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var handler: Handler
    private lateinit var httpClient: OkHttpClient

    private val RTSP_URL = "rtsp://192.168.0.1:554/livestream/12"
    private val AUTH_URL = "http://192.168.0.1/cgi-bin/client.cgi?&-operation=register&-ip=192.168.0.22"
    private val AUTH_INTERVAL = 1000 // 1秒

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置全屏显示
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        setContentView(R.layout.activity_main)

        videoLayout = findViewById(R.id.videoLayout)

        handler = Handler(Looper.getMainLooper())
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val options = ArrayList<String>().apply {
            add("--aout=opensles")
            add("--avcodec-codec=h264")
            add("--file-logging")
            add("--logfile=vlc-log.txt")
        }
        libVLC = LibVLC(this, options)

        // 初始化媒体播放器并开始播放
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        // 发送授权请求并等待完成
        Thread {
            try {
                val request = Request.Builder()
                    .url(AUTH_URL)
                    .build()
                val response = httpClient.newCall(request).execute()
                Log.d("Auth", "Initial auth response: ${response.code()}")
                response.close()
                
                // 授权完成后在主线程开始拉流
                handler.postDelayed({
                    mediaPlayer = MediaPlayer(libVLC).apply {
                        setVideoTrackEnabled(true)
                        setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                        attachViews(videoLayout, null, false, false)
                    }

                    val media = Media(libVLC, android.net.Uri.parse(RTSP_URL)).apply {
                        setHWDecoderEnabled(true, false)
                        addOption(":network-caching=300")
                    }
                    mediaPlayer.media = media
                    mediaPlayer.play()
                    
                    // 开始定时授权轮询
                    startAuthPolling()
                }, 3000)
            } catch (e: Exception) {
                Log.e("Auth", "Initial auth failed: ${e.message}")
            }
        }.start()
    }

    private fun startAuthPolling() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                sendAuthRequest()
                handler.postDelayed(this, AUTH_INTERVAL)
            }
        }, AUTH_INTERVAL)
    }

    private fun sendAuthRequest() {
        Thread {
            try {
                val request = Request.Builder()
                    .url(AUTH_URL)
                    .build()
                val response = httpClient.newCall(request).execute()
                Log.d("Auth", "Response: ${response.code()}")
                response.close()
            } catch (e: Exception) {
                Log.e("Auth", "Error: ${e.message}")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
        handler.removeCallbacksAndMessages(null)
    }
} 