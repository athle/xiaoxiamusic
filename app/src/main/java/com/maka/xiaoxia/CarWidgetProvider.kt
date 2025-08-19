package com.maka.xiaoxia

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.widget.RemoteViews

/**
 * 车机专用小组件 - 领克02 CS11兼容性
 * 针对车机系统的特殊优化版本
 */
class CarWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_UPDATE_CAR_WIDGET = "com.maka.xiaoxia.action.UPDATE_CAR_WIDGET"
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_UPDATE_CAR_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, CarWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_music)
        
        // 车机优化的点击事件 - 低内存版，点击任意位置打开应用
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = android.app.PendingIntent.getActivity(
            context, 0, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // 设置点击事件 - 低内存版简化处理
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_song_title, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent)
        
        // 车机优化的显示内容 - 低内存版
        val sharedPref = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val title = sharedPref.getString("current_song_title", "未知歌曲") ?: "未知歌曲"
        val artist = sharedPref.getString("current_song_artist", "未知艺术家") ?: "未知艺术家"
        
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // 低内存版不再需要复杂的尺寸适配，使用固定布局
}