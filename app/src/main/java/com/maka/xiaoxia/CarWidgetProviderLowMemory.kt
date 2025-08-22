package com.maka.xiaoxia

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

/**
 * 车机专用低内存小组件 - 领克02 CS11兼容性优化
 * 极简设计：仅显示专辑封面、歌曲标题、艺术家
 * 低内存占用：无控制按钮，点击任意位置打开应用
 * 参考小侠音乐小组件实现方式，确保系统正确接入
 */
class CarWidgetProviderLowMemory : AppWidgetProvider() {
    
    companion object {
        const val ACTION_UPDATE_CAR_WIDGET_LOW_MEMORY = "com.maka.xiaoxia.action.UPDATE_CAR_WIDGET_LOW_MEMORY"
        const val ACTION_UPDATE_WIDGET = "com.maka.xiaoxia.action.UPDATE_WIDGET"
        private const val TAG = "CarWidgetProviderLowMemory"
    }
    
    private fun getAlbumArt(context: Context, path: String): android.graphics.Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            retriever.release()
            
            if (art != null) {
                android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "获取专辑封面失败: ${e.message}")
            null
        }
    }
    
    private fun getAlbumArtById(context: Context, albumId: Long): android.graphics.Bitmap? {
        if (albumId <= 0) return null
        
        return try {
            val uri = Uri.parse("content://media/external/audio/albumart")
            val albumArtUri = Uri.withAppendedPath(uri, albumId.toString())
            
            val inputStream = context.contentResolver.openInputStream(albumArtUri)
            if (inputStream != null) {
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                // 车机优化：限制图片大小，避免内存问题
                val maxSize = 256 // 最大256px，低内存版更小
                val width = bitmap.width
                val height = bitmap.height
                
                if (width > maxSize || height > maxSize) {
                    val scaleFactor = maxSize.toFloat() / Math.max(width, height)
                    val newWidth = (width * scaleFactor).toInt()
                    val newHeight = (height * scaleFactor).toInt()
                    
                    android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                } else {
                    bitmap
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "通过专辑ID获取封面失败: ${e.message}")
            null
        }
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
                android.util.Log.d("CarWidgetLowMemory", "收到统一广播，准备更新车机低内存小组件")
                updateWidgetWithDirectData(context, intent)
            }
            ACTION_UPDATE_CAR_WIDGET_LOW_MEMORY, ACTION_UPDATE_WIDGET, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // 兼容旧版本广播，但减少更新频率
                val currentTime = System.currentTimeMillis()
                val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
                val lastUpdateTime = sharedPref.getLong("last_widget_update_time", 0)
                
                // 500ms内避免重复更新
                if (currentTime - lastUpdateTime > 500) {
                    android.util.Log.d("CarWidgetLowMemory", "收到系统广播，强制更新车机低内存小组件")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, CarWidgetProviderLowMemory::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                    sharedPref.edit().putLong("last_widget_update_time", currentTime).apply()
                } else {
                    android.util.Log.d("CarWidgetLowMemory", "跳过重复系统广播更新")
                }
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_music_low_memory)
        
        // 车机优化的点击事件 - 低内存版，点击任意位置打开应用
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, flags
        )
        
        // 设置点击事件 - 低内存版简化处理，整个小组件区域可点击
        views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_song_title, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent)
        
        // 车机优化的显示内容 - 低内存版
        updateWidgetContent(context, views)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun updateWidgetWithDirectData(context: Context, intent: Intent) {
        // 使用广播直接传递的数据更新小组件，避免SharedPreferences延迟
        val title = intent.getStringExtra("current_title") ?: "未知歌曲"
        val artist = intent.getStringExtra("current_artist") ?: "未知艺术家"
        val isPlaying = intent.getBooleanExtra("is_playing", false)
        val coverPath = intent.getStringExtra("cover_path") ?: ""
        val coverAlbumId = intent.getLongExtra("cover_album_id", 0L)
        val currentSongIndex = intent.getIntExtra("current_song_index", 0)
        val songCount = intent.getIntExtra("song_count", 0)
        
        // 记录更新时间戳并同步更新SharedPreferences
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putLong("last_widget_update_time", System.currentTimeMillis())
            // 同时更新SharedPreferences，确保后续获取的数据一致
            putString("current_title", title)
            putString("current_artist", artist)
            putBoolean("is_playing", isPlaying)
            putString("current_song_path", coverPath)
            putLong("current_album_id", coverAlbumId)
            putInt("current_song_index", currentSongIndex)
            apply()
        }
        
        android.util.Log.d("CarWidgetLowMemory", "车机低内存小组件更新: $title - $artist, 索引: $currentSongIndex/$songCount")
        
        // 获取所有小组件实例并更新
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, CarWidgetProviderLowMemory::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        
        android.util.Log.d("CarWidgetLowMemory", "需要更新的车机低内存小组件数量: ${appWidgetIds.size}")
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_low_memory)
            
            // 车机优化的点击事件 - 低内存版
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, flags
            )
            
            // 设置点击事件
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_song_title, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent)
            
            // 更新小组件内容
            views.setTextViewText(R.id.widget_song_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            
            // 设置专辑封面
            if (coverPath.isNotEmpty()) {
                try {
                    android.util.Log.d("CarWidgetLowMemory", "尝试加载封面: $coverPath")
                    // 优先从文件直接读取嵌入式封面
                    val bitmap = getAlbumArt(context, coverPath)
                    if (bitmap != null) {
                        android.util.Log.d("CarWidgetLowMemory", "成功加载封面")
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    } else {
                        android.util.Log.d("CarWidgetLowMemory", "文件无嵌入式封面，尝试专辑ID: $coverAlbumId")
                        // 如果文件没有嵌入式封面，再尝试通过专辑ID获取
                        val albumBitmap = getAlbumArtById(context, coverAlbumId)
                        if (albumBitmap != null) {
                            android.util.Log.d("CarWidgetLowMemory", "成功从专辑ID获取封面")
                            views.setImageViewBitmap(R.id.widget_album_art, albumBitmap)
                        } else {
                            android.util.Log.d("CarWidgetLowMemory", "使用默认封面")
                            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CarWidgetLowMemory", "获取封面异常: ${e.message}")
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                }
            } else {
                android.util.Log.d("CarWidgetLowMemory", "无封面路径，使用默认")
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        android.util.Log.d("CarWidgetLowMemory", "车机低内存小组件更新完成，数量: ${appWidgetIds.size}")
    }
    
    private fun updateWidgetContent(context: Context, views: RemoteViews) {
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        
        // 强制刷新SharedPreferences
        sharedPref.edit().apply()
        
        val title = sharedPref.getString("current_title", "未知歌曲")
        val artist = sharedPref.getString("current_artist", "未知艺术家")
        
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        
        android.util.Log.d("CarWidgetLowMemory", "=== 更新车机低内存小组件内容 ===")
        android.util.Log.d("CarWidgetLowMemory", "歌曲: $title - $artist")
        
        // 设置专辑封面
        val albumArtPath = sharedPref.getString("current_song_path", "")
        val albumId = sharedPref.getLong("current_album_id", 0L)
        
        if (!albumArtPath.isNullOrEmpty()) {
            android.util.Log.d("CarWidgetLowMemory", "尝试从文件路径获取封面: $albumArtPath")
            try {
                // 优先从文件直接读取嵌入式封面
                val bitmap = getAlbumArt(context, albumArtPath)
                if (bitmap != null) {
                    android.util.Log.d("CarWidgetLowMemory", "成功从文件获取封面")
                    views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                } else {
                    android.util.Log.d("CarWidgetLowMemory", "文件无嵌入式封面，尝试专辑ID: $albumId")
                    // 如果文件没有嵌入式封面，再尝试通过专辑ID获取
                    val albumBitmap = getAlbumArtById(context, albumId)
                    if (albumBitmap != null) {
                        android.util.Log.d("CarWidgetLowMemory", "成功从专辑ID获取封面")
                        views.setImageViewBitmap(R.id.widget_album_art, albumBitmap)
                    } else {
                        android.util.Log.d("CarWidgetLowMemory", "无法获取封面，使用默认")
                        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("CarWidgetLowMemory", "获取封面异常: ${e.message}")
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
            }
        } else {
            android.util.Log.d("CarWidgetLowMemory", "无文件路径，使用默认封面")
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
        }
        
        android.util.Log.d("CarWidgetLowMemory", "=== 更新车机低内存小组件内容结束 ===")
    }
}