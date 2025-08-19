package com.maka.xiaoxia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

/**
 * 车机实体按键接收器 - 领克02 CS11专用
 * 处理车机方向盘或中控台的实体按键事件 - 安卓4.4兼容版
 */
class CarMediaButtonReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CarMediaButtonReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到车机按键事件: ${intent.action}")
        
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            keyEvent?.let { event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyEvent(context, event.keyCode)
                }
            }
        }
    }
    
    private fun handleKeyEvent(context: Context, keyCode: Int) {
        Log.d(TAG, "处理按键码: $keyCode")
        
        val action = when (keyCode) {
            // 标准Android媒体按键
            KeyEvent.KEYCODE_MEDIA_PLAY -> MusicService.ACTION_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PAUSE -> MusicService.ACTION_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> MusicService.ACTION_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_NEXT -> MusicService.ACTION_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MusicService.ACTION_PREVIOUS
            KeyEvent.KEYCODE_MEDIA_STOP -> MusicService.ACTION_STOP
            
            // CS11车机专用按键码（Linux键值直接映射）
            163 -> { // Linux KEY_NEXT (下一首)
                Log.d(TAG, "CS11车机下一首按键")
                MusicService.ACTION_NEXT
            }
            164 -> { // Linux KEY_PLAYPAUSE (播放/暂停)
                Log.d(TAG, "CS11车机播放/暂停按键")
                MusicService.ACTION_PLAY_PAUSE
            }
            165 -> { // Linux KEY_PREVIOUS (上一首)
                Log.d(TAG, "CS11车机上一首按键")
                MusicService.ACTION_PREVIOUS
            }
            
            else -> {
                Log.d(TAG, "未识别的按键码: $keyCode (CS11车机)")
                return
            }
        }
        
        try {
            // 优先尝试直接启动MusicService
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            
            // 安卓4.4直接使用startService
            context.startService(serviceIntent)
            
            Log.d(TAG, "已发送服务操作: $action")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动服务失败，尝试广播方式: ${e.message}")
            
            // 回退方案：使用广播
            val broadcastIntent = Intent(action).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(broadcastIntent)
        }
    }
}