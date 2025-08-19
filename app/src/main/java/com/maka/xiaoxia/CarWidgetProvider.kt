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
        val views = RemoteViews(context.packageName, R.layout.widget_music_car)
        
        // 获取小组件尺寸信息
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
        
        // 根据尺寸调整布局
        adjustLayoutForSize(views, minWidth, minHeight)
        
        // 车机优化的点击事件
        val playIntent = Intent(context, MusicService::class.java).apply {
            action = "com.maka.xiaoxia.action.PLAY_PAUSE"
        }
        val playPendingIntent = android.app.PendingIntent.getService(
            context, 0, playIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val nextIntent = Intent(context, MusicService::class.java).apply {
            action = "com.maka.xiaoxia.action.NEXT"
        }
        val nextPendingIntent = android.app.PendingIntent.getService(
            context, 1, nextIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val prevIntent = Intent(context, MusicService::class.java).apply {
            action = "com.maka.xiaoxia.action.PREVIOUS"
        }
        val prevPendingIntent = android.app.PendingIntent.getService(
            context, 2, prevIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = android.app.PendingIntent.getActivity(
            context, 3, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // 设置点击事件
        views.setOnClickPendingIntent(R.id.widget_play_pause, playPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_previous, prevPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        
        // 车机优化的显示内容
        val sharedPref = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val title = sharedPref.getString("current_song_title", "未知歌曲") ?: "未知歌曲"
        val artist = sharedPref.getString("current_song_artist", "未知艺术家") ?: "未知艺术家"
        val isPlaying = sharedPref.getBoolean("is_playing", false)
        
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        views.setImageViewResource(R.id.widget_play_pause, playIcon)
        
        // 车机系统可能需要更明显的默认封面
        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun adjustLayoutForSize(views: RemoteViews, width: Int, height: Int) {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val widthDp = (width / density).toInt()
        val heightDp = (height / density).toInt()
        
        // 根据尺寸调整显示内容
        when {
            // 2x1 或 4x1 (小高度)
            heightDp <= 80 -> {
                views.setViewVisibility(R.id.album_cover_container, 8) // View.GONE
                views.setViewVisibility(R.id.control_buttons_container, 0) // View.VISIBLE
                views.setViewVisibility(R.id.widget_open_app_button, 8) // View.GONE
            }
            // 2x2 或 4x2 (中等尺寸)
            heightDp <= 120 -> {
                views.setViewVisibility(R.id.album_cover_container, 0) // View.VISIBLE
                views.setViewVisibility(R.id.control_buttons_container, 0) // View.VISIBLE
                views.setViewVisibility(R.id.widget_open_app_button, 8) // View.GONE
            }
            // 2x4 或 4x4 (大尺寸)
            else -> {
                views.setViewVisibility(R.id.album_cover_container, 0) // View.VISIBLE
                views.setViewVisibility(R.id.control_buttons_container, 0) // View.VISIBLE
                views.setViewVisibility(R.id.widget_open_app_button, 0) // View.VISIBLE
            }
        }
    }
}