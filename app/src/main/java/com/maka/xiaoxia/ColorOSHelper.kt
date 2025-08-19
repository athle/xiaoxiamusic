package com.maka.xiaoxia

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object ColorOSHelper {
    
    /**
     * 检查ColorOS 15的通知权限
     */
    fun checkColorOS15NotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = NotificationManagerCompat.from(context)
            val hasPermission = notificationManager.areNotificationsEnabled()
            
            // 检查ColorOS 15的特殊设置
            if (!hasPermission && isColorOS15()) {
                // 检查是否有ColorOS 15的特殊通知设置
                checkColorOSSpecificSettings(context)
            } else {
                hasPermission
            }
        } else {
            true // 低版本系统默认允许
        }
    }
    
    /**
     * 检查是否为ColorOS 15 (基于Android 15)
     */
    fun isColorOS15(context: Context): Boolean {
        return try {
            val version = getSystemProperty("ro.build.version.oplusrom")
            val androidVersion = Build.VERSION.SDK_INT
            val isOppo = isOppoRom()
            
            // ColorOS 15的严格检测
            val isColorOS15Version = !version.isNullOrEmpty() && version.startsWith("15")
            val isAndroid15 = androidVersion >= Build.VERSION_CODES.VANILLA_ICE_CREAM
            
            // 检查ColorOS 15特有的系统属性
            val colorOSBuildVersion = getSystemProperty("ro.build.version.coloros")
            val isColorOS15Build = !colorOSBuildVersion.isNullOrEmpty() && colorOSBuildVersion.startsWith("15")
            
            isColorOS15Version || isColorOS15Build || (isAndroid15 && isOppo)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测是否为ColorOS 15 (基于Android 15)
     */
    private fun isColorOS15(): Boolean {
        return try {
            val version = getSystemProperty("ro.build.version.oplusrom")
            val androidVersion = Build.VERSION.SDK_INT
            (!version.isNullOrEmpty() && version.startsWith("15")) || 
            (androidVersion >= Build.VERSION_CODES.VANILLA_ICE_CREAM && isOppoRom())
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测是否为OPPO ROM
     */
    private fun isOppoRom(): Boolean {
        return try {
            val oppoVersion = getSystemProperty("ro.build.version.opporom")
            val colorOSVersion = getSystemProperty("ro.build.version.coloros")
            val oplusVersion = getSystemProperty("ro.build.version.oplusrom")
            
            !oppoVersion.isNullOrEmpty() || 
            !colorOSVersion.isNullOrEmpty() || 
            !oplusVersion.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查ColorOS特殊设置
     */
    private fun checkColorOSSpecificSettings(context: Context): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 检查主要通知渠道
            val channel = notificationManager.getNotificationChannel("xiaoxia_music_channel")
            val colorOSChannel = notificationManager.getNotificationChannel("xiaoxia_music_channel_oppo")
            val colorOS15Channel = notificationManager.getNotificationChannel("xiaoxia_music_channel_coloros15")
            
            // 检查通知权限和渠道状态
            val hasMainChannel = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
            val hasColorOSChannel = colorOSChannel != null && colorOSChannel.importance != NotificationManager.IMPORTANCE_NONE
            val hasColorOS15Channel = colorOS15Channel != null && colorOS15Channel.importance != NotificationManager.IMPORTANCE_NONE
            
            hasMainChannel || hasColorOSChannel || hasColorOS15Channel
        } catch (e: Exception) {
            true // 默认允许
        }
    }
    
    /**
     * 打开ColorOS 15的通知设置页面
     */
    fun openColorOS15NotificationSettings(context: Context) {
        try {
            val intent = Intent()
            
            if (isColorOS15()) {
                // ColorOS 15的特殊设置页面
                try {
                    intent.setClassName("com.coloros.notificationmanager", "com.coloros.notificationmanager.AppDetailActivity")
                    intent.putExtra("packageName", context.packageName)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // 备用方案：打开应用通知设置
                    openAppNotificationSettings(context)
                }
            } else {
                // 标准通知设置
                openAppNotificationSettings(context)
            }
        } catch (e: Exception) {
            // 最后备用方案
            openAppNotificationSettings(context)
        }
    }
    
    /**
     * 打开应用通知设置
     */
    private fun openAppNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, "xiaoxia_music_channel")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 通用设置页面
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
    
    /**
     * 获取系统属性
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            getMethod.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检查通知权限状态
     */
    fun getNotificationPermissionStatus(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            val notificationManager = NotificationManagerCompat.from(context)
            val enabled = notificationManager.areNotificationsEnabled()
            
            "Permission: ${permission == android.content.pm.PackageManager.PERMISSION_GRANTED}, Enabled: $enabled"
        } else {
            "Legacy system"
        }
    }
}