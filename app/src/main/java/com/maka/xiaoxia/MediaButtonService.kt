package com.maka.xiaoxia

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent

class MediaButtonService : Service() {
    
    companion object {
        private const val TAG = "MediaButtonService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "媒体按钮服务创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "媒体按钮服务启动")
        
        intent?.let {
            handleMediaButtonIntent(it)
        }
        
        return START_NOT_STICKY
    }
    
    private fun handleMediaButtonIntent(intent: Intent) {
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    Log.d(TAG, "车机播放/暂停按钮")
                    sendActionToService(MusicService.ACTION_PLAY_PAUSE)
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    Log.d(TAG, "车机下一首按钮")
                    sendActionToService(MusicService.ACTION_NEXT)
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    Log.d(TAG, "车机上一首按钮")
                    sendActionToService(MusicService.ACTION_PREVIOUS)
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    Log.d(TAG, "车机停止按钮")
                    sendActionToService(MusicService.ACTION_STOP)
                }
            }
        }
    }
    
    private fun sendActionToService(action: String) {
        try {
            val serviceIntent = Intent(this, MusicService::class.java).apply {
                this.action = action
            }
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "启动音乐服务失败: ${e.message}")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}