package com.maka.xiaoxia

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 小组件更新管理器 - 优化广播合并和节流
 * 
 * 功能：
 * 1. 合并多次更新请求，避免重复广播
 * 2. 500ms节流机制，减少更新频率
 * 3. 统一处理所有小组件类型
 */
object WidgetUpdateManager {
    
    private const val UPDATE_DELAY_MS = 500L
    private var pendingUpdate = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L
    
    /**
     * 请求更新所有小组件
     * 使用节流机制避免频繁更新
     */
    fun requestUpdate(context: Context, forceUpdate: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        
        // 如果强制更新或超过节流时间，立即更新
        if (forceUpdate || currentTime - lastUpdateTime > UPDATE_DELAY_MS) {
            performUpdate(context)
            return
        }
        
        // 如果已有待更新，跳过
        if (pendingUpdate) {
            Log.d("WidgetUpdateManager", "已有待更新请求，跳过")
            return
        }
        
        // 延迟更新，合并多次请求
        pendingUpdate = true
        handler.postDelayed({
            performUpdate(context)
            pendingUpdate = false
        }, UPDATE_DELAY_MS)
    }
    
    /**
     * 执行实际更新操作
     */
    private fun performUpdate(context: Context) {
        val currentTime = System.currentTimeMillis()
        lastUpdateTime = currentTime
        
        // 创建统一广播
        val unifiedIntent = Intent("com.maka.xiaoxia.UPDATE_ALL_COMPONENTS").apply {
            putExtra("update_timestamp", currentTime)
            putExtra("source", "WidgetUpdateManager")
        }
        
        context.sendBroadcast(unifiedIntent)
        Log.d("WidgetUpdateManager", "已发送统一更新广播")
    }
    
    /**
     * 立即更新，忽略节流
     */
    fun forceUpdate(context: Context) {
        performUpdate(context)
    }
    
    /**
     * 取消所有待更新
     */
    fun cancelPendingUpdates() {
        handler.removeCallbacksAndMessages(null)
        pendingUpdate = false
        Log.d("WidgetUpdateManager", "已取消所有待更新")
    }
}