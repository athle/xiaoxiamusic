package com.maka.xiaoxia

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent

/**
 * 已废弃：媒体按钮服务
 * 功能已整合到CarMediaButtonReceiver.kt和UnifiedMediaSessionManager.kt中
 * 保留此类以确保向后兼容性
 */
@Deprecated("功能已整合到CarMediaButtonReceiver和UnifiedMediaSessionManager")
class MediaButtonService : Service() {
    
    companion object {
        private const val TAG = "MediaButtonService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "媒体按钮服务已废弃，使用CarMediaButtonReceiver替代")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "媒体按钮服务收到事件，转发到CarMediaButtonReceiver处理")
        
        // 直接转发到CarMediaButtonReceiver处理
        intent?.let {
            val receiver = CarMediaButtonReceiver()
            receiver.onReceive(this, it)
        }
        
        stopSelf()
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}