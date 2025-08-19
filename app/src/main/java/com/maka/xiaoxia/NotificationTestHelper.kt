package com.maka.xiaoxia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationTestHelper {
    
    private const val TEST_CHANNEL_ID = "test_notification_channel"
    
    /**
     * 测试通知权限和显示
     */
    fun testNotificationDisplay(context: Context): String {
        val result = StringBuilder()
        
        try {
            // 检查通知权限
            val notificationManager = NotificationManagerCompat.from(context)
            val hasPermission = notificationManager.areNotificationsEnabled()
            result.append("通知权限: ${if (hasPermission) "已开启" else "已关闭"}\n")
            
            // 检查通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                val channels = listOf(
                    "xiaoxia_music_channel",
                    "xiaoxia_music_channel_oppo",
                    "xiaoxia_music_channel_coloros15"
                )
                
                channels.forEach { channelId ->
                    val channel = systemNotificationManager.getNotificationChannel(channelId)
                    if (channel != null) {
                        result.append("通知渠道 $channelId: ${getImportanceString(channel.importance)}\n")
                    } else {
                        result.append("通知渠道 $channelId: 不存在\n")
                    }
                }
            }
            
            // 创建测试通知
            createTestNotification(context)
            result.append("测试通知创建: 成功\n")
            
            // 检查系统通知设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                result.append("系统通知设置: ${systemNotificationManager.areNotificationsEnabled()}\n")
            }
            
        } catch (e: Exception) {
            result.append("测试异常: ${e.message}\n")
            Log.e("NotificationTest", "通知测试失败", e)
        }
        
        return result.toString()
    }
    
    /**
     * 创建测试通知
     */
    private fun createTestNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                TEST_CHANNEL_ID,
                "测试通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "测试通知渠道"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_default)
            .setContentTitle("测试通知")
            .setContentText("这是一个测试通知")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(9999, notification)
    }
    
    /**
     * 获取重要性描述
     */
    private fun getImportanceString(importance: Int): String {
        return when (importance) {
            NotificationManager.IMPORTANCE_NONE -> "已禁用"
            NotificationManager.IMPORTANCE_MIN -> "最小"
            NotificationManager.IMPORTANCE_LOW -> "低"
            NotificationManager.IMPORTANCE_DEFAULT -> "默认"
            NotificationManager.IMPORTANCE_HIGH -> "高"
            else -> "未知($importance)"
        }
    }
    
    /**
     * 检查ColorOS 15特殊设置
     */
    fun checkColorOS15Settings(context: Context): String {
        val result = StringBuilder()
        
        try {
            result.append("系统版本: Android ${Build.VERSION.SDK_INT}\n")
            result.append("ColorOS 15: ${ColorOSHelper.isColorOS15(context)}\n")
            
            // 检查通知权限
            val notificationManager = NotificationManagerCompat.from(context)
            result.append("通知权限: ${notificationManager.areNotificationsEnabled()}\n")
            
            // 检查具体通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                val channelIds = listOf(
                    "xiaoxia_music_channel",
                    "xiaoxia_music_channel_oppo",
                    "xiaoxia_music_channel_coloros15"
                )
                
                channelIds.forEach { channelId ->
                    val channel = systemNotificationManager.getNotificationChannel(channelId)
                    if (channel != null) {
                        result.append("渠道 $channelId:\n")
                        result.append("  重要性: ${getImportanceString(channel.importance)}\n")
                        result.append("  名称: ${channel.name}\n")
                        result.append("  描述: ${channel.description}\n")
                        result.append("  是否启用: ${channel.importance != NotificationManager.IMPORTANCE_NONE}\n")
                    }
                }
            }
            
        } catch (e: Exception) {
            result.append("检查异常: ${e.message}\n")
        }
        
        return result.toString()
    }
}