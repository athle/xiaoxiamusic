package com.maka.xiaoxia

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    const val REQUEST_CODE_PERMISSIONS = 1001
    
    // 获取当前需要的权限列表
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> emptyArray()
        }
    }
    
    // 检查是否所有权限都已授予
    fun hasAllPermissions(context: Context): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // 请求权限
    fun requestPermissions(activity: Activity) {
        val permissions = getRequiredPermissions()
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_PERMISSIONS)
        }
    }
    
    // 检查是否需要显示权限说明
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    
    // 获取权限描述文本
    fun getPermissionDescription(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                "需要音频和媒体访问权限来播放音乐"
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                "需要存储权限来访问音乐文件"
            }
            else -> ""
        }
    }
}