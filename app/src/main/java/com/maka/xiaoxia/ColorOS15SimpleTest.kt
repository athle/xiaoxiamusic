package com.maka.xiaoxia

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 简化的ColorOS 15媒体会话测试工具
 */
object ColorOS15SimpleTest {
    
    private const val TAG = "ColorOS15SimpleTest"
    
    /**
     * 检查是否为ColorOS 15系统
     */
    fun isColorOS15(): Boolean {
        return try {
            // 检查系统属性
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            val colorOSVersion = getMethod.invoke(null, "ro.build.version.oplusrom") as String?
            
            colorOSVersion?.startsWith("15") ?: false
        } catch (e: Exception) {
            // 备用检查：根据厂商判断
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Realme", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true)
        }
    }
    
    /**
     * 获取集成状态
     */
    fun getIntegrationStatus(context: Context): String {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            "Android版本过低，不支持MediaSession"
        } else if (isColorOS15()) {
            "ColorOS 15系统检测到，媒体会话已集成"
        } else {
            "标准Android系统，使用标准媒体会话"
        }
    }
    
    /**
     * 快速测试媒体会话
     */
    fun quickTest(context: Context): String {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            "测试跳过：Android版本过低"
        } else {
            try {
                val sessionManager = ColorOS15MediaSessionManager(context)
                sessionManager.createMediaSession()
                val isActive = sessionManager.isSessionActive()
                sessionManager.releaseMediaSession()
                
                "MediaSession测试: ${if (isActive) "通过" else "失败"}"
            } catch (e: Exception) {
                "测试异常: ${e.message}"
            }
        }
    }
}