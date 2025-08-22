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
            "com.maka.xiaoxia.UPDATE_ALL_COMPONENTS" -> {
                // 统一广播处理 - 优化后的单一更新
                android.util.Log.d("CarWidget", "收到统一广播，准备更新车机小组件")
                
                // 处理广播数据并同步到SharedPreferences
                val title = intent.getStringExtra("current_title") ?: "未知歌曲"
                val artist = intent.getStringExtra("current_artist") ?: "未知艺术家"
                
                // 同步更新SharedPreferences，确保数据一致性
                val sharedPref = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("current_song_title", title)
                    putString("current_song_artist", artist)
                    putLong("last_widget_update_time", System.currentTimeMillis())
                    apply()
                }
                
                android.util.Log.d("CarWidget", "车机小组件广播数据: $title - $artist")
                
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, CarWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
            ACTION_UPDATE_CAR_WIDGET, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // 兼容旧版本广播，但减少更新频率
                val currentTime = System.currentTimeMillis()
                val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
                val lastUpdateTime = sharedPref.getLong("last_widget_update_time", 0)
                
                // 500ms内避免重复更新
                if (currentTime - lastUpdateTime > 500) {
                    android.util.Log.d("CarWidget", "收到系统广播，强制更新车机小组件")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, CarWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } else {
                    android.util.Log.d("CarWidget", "跳过重复系统广播更新")
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
        
        // 强制刷新SharedPreferences缓存，确保获取最新数据
        val sharedPref = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply()
        
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
        val title = sharedPref.getString("current_song_title", "未知歌曲") ?: "未知歌曲"
        val artist = sharedPref.getString("current_song_artist", "未知艺术家") ?: "未知艺术家"
        
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
        
        android.util.Log.d("CarWidget", "车机小组件更新: $title - $artist")
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // 低内存版不再需要复杂的尺寸适配，使用固定布局
}