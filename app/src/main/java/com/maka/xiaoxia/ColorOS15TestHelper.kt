package com.maka.xiaoxia

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * ColorOS 15媒体播放通知测试工具
 * 用于验证ColorOS 15的"媒体播放"通知栏控件是否正确接入
 */
object ColorOS15TestHelper {
    
    /**
     * 测试ColorOS 15媒体播放通知
     */
    fun testColorOS15MediaNotification(context: Context): String {
        val builder = StringBuilder()
        builder.append("=== ColorOS 15媒体播放通知测试 ===\n")
        
        // 1. 系统检测
        val isColorOS15 = ColorOSHelper.isColorOS15(context)
        builder.append("ColorOS 15检测: ${if (isColorOS15) "✅通过" else "❌失败"}\n")
        
        // 2. 通知权限检查
        val hasPermission = checkNotificationPermission(context)
        builder.append("通知权限: ${if (hasPermission) "✅已开启" else "❌未开启"}\n")
        
        // 3. 通知渠道检查
        val channelStatus = checkNotificationChannels(context)
        builder.append("通知渠道状态:\n").append(channelStatus)
        
        // 4. 系统设置检查
        val systemSettings = checkSystemSettings(context)
        builder.append("系统设置:\n").append(systemSettings)
        
        return builder.toString()
    }
    
    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission(context: Context): Boolean {
        return try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.areNotificationsEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查通知渠道
     */
    private fun checkNotificationChannels(context: Context): String {
        val builder = StringBuilder()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                val channels = listOf(
                    "music_service_channel",
                    "music_service_channel_coloros",
                    "music_service_channel_coloros15",
                    "coloros15_media_playback"
                )
                
                channels.forEach { channelId ->
                    val channel = notificationManager.getNotificationChannel(channelId)
                    if (channel != null) {
                        builder.append("  - $channelId: ${getImportanceText(channel.importance)}\n")
                    } else {
                        builder.append("  - $channelId: ❌未创建\n")
                    }
                }
            } catch (e: Exception) {
                builder.append("  检查失败: ${e.message}\n")
            }
        } else {
            builder.append("  Android 8.0以下无需检查通知渠道\n")
        }
        
        return builder.toString()
    }
    
    /**
     * 检查系统设置
     */
    private fun checkSystemSettings(context: Context): String {
        val builder = StringBuilder()
        
        try {
            // 检查系统属性
            val properties = listOf(
                "ro.build.version.oplusrom" to "ColorOS版本",
                "ro.build.version.coloros" to "ColorOS构建版本",
                "ro.build.version.opporom" to "OPPO ROM版本",
                "ro.build.version.release" to "Android版本"
            )
            
            properties.forEach { (prop, desc) ->
                val value = getSystemProperty(prop)
                builder.append("  $desc: ${value ?: "未知"}\n")
            }
            
            // 检查Android版本
            builder.append("  API级别: ${Build.VERSION.SDK_INT}\n")
            
        } catch (e: Exception) {
            builder.append("  系统检查失败: ${e.message}\n")
        }
        
        return builder.toString()
    }
    
    /**
     * 获取重要性文本描述
     */
    private fun getImportanceText(importance: Int): String {
        return when (importance) {
            NotificationManager.IMPORTANCE_HIGH -> "✅高"
            NotificationManager.IMPORTANCE_DEFAULT -> "⚠️默认"
            NotificationManager.IMPORTANCE_LOW -> "⚠️低"
            NotificationManager.IMPORTANCE_MIN -> "❌最小"
            NotificationManager.IMPORTANCE_NONE -> "❌已禁用"
            else -> "未知($importance)"
        }
    }
    
    /**
     * 获取系统属性
     */
    private fun getSystemProperty(propName: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, propName) as String?
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取ColorOS 15设置建议
     */
    fun getColorOS15SettingsGuide(context: Context): String {
        return """
            === ColorOS 15媒体播放设置指南 ===
            
            1. 打开设置 → 通知与状态栏
            2. 找到"音乐播放器"应用
            3. 确保"媒体播放"权限已开启
            4. 设置通知重要性为"高"
            5. 允许通知在状态栏显示
            6. 确保"媒体播放"类别已启用
            
            如仍无法显示，请尝试：
            - 重启手机
            - 清除应用缓存
            - 重新安装应用
            
            技术检查清单：
            - 通知权限: ${checkNotificationPermission(context)}
            - ColorOS版本: ${getSystemProperty("ro.build.version.oplusrom") ?: "未知"}
            - Android版本: ${Build.VERSION.RELEASE}
            - API级别: ${Build.VERSION.SDK_INT}
        """.trimIndent()
    }
}