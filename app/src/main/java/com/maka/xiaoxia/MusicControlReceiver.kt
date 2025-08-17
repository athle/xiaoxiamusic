package com.maka.xiaoxia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MusicControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MusicControl", "收到广播: ${intent.action}")
        
        // UPDATE_WIDGET广播不应该由MusicControlReceiver处理，直接忽略
        
        // 始终启动后台音乐服务来处理操作
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = intent.action
        }
        
        try {
            context.startService(serviceIntent)
            Log.d("MusicControl", "启动后台音乐服务")
        } catch (e: Exception) {
            Log.e("MusicControl", "启动服务失败: ${e.message}")
            
            // 如果服务启动失败，回退到启动Activity
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = intent.action
            }
            
            try {
                context.startActivity(activityIntent)
                Log.d("MusicControl", "启动MainActivity处理广播")
            } catch (e2: Exception) {
                Log.e("MusicControl", "启动MainActivity失败: ${e2.message}")
            }
        }
    }
}