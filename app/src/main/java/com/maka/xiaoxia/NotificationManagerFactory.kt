package com.maka.xiaoxia

import android.content.Context
import android.graphics.Bitmap
import android.os.Build

/**
 * 通知管理器工厂
 * 根据Android版本选择合适的通知管理器，避免功能重复
 */
object NotificationManagerFactory {
    
    /**
     * 获取合适的通知管理器
     * 安卓5.0+使用增强型，安卓4.4及以下使用传统型
     */
    fun getNotificationManager(context: Context): INotificationManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            EnhancedMediaNotificationManager(context)
        } else {
            LegacyMediaNotificationManager(context)
        }
    }
    
    /**
     * 通知管理器接口
     * 统一通知管理器的接口定义
     */
    interface INotificationManager {
        fun showNotification(
            title: String,
            artist: String,
            album: String,
            isPlaying: Boolean,
            albumArt: Bitmap? = null,
            duration: Long,
            position: Long
        )
        
        fun cancelNotification()
    }
}