package com.maka.xiaoxia

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.RemoteViews
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.maka.xiaoxia.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.maka.xiaoxia.action.NEXT"
        const val ACTION_PREVIOUS = "com.maka.xiaoxia.action.PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.maka.xiaoxia.action.UPDATE_WIDGET"
    }

    private fun isAppInForeground(context: Context): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses ?: return false
            
            for (processInfo in runningAppProcesses) {
                if (processInfo.processName == context.packageName) {
                    return processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
            }
            return false
        } catch (e: Exception) {
            android.util.Log.w("MusicWidget", "检测前台状态失败: ${e.message}")
            return false
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 系统触发更新（配置已改为24小时一次）
        // 主要依赖MusicService的UPDATE_WIDGET广播进行主动更新
        android.util.Log.d("MusicWidget", "系统触发onUpdate，更新所有小组件")
        
        // 检查是否有最近更新的数据，避免使用旧SharedPreferences数据
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        val lastUpdateTime = sharedPref.getLong("last_widget_update_time", 0)
        val currentTime = System.currentTimeMillis()
        
        // 如果5秒内有主动更新，跳过系统更新
        if (currentTime - lastUpdateTime > 5000) {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        } else {
            android.util.Log.d("MusicWidget", "跳过系统onUpdate，因为有最近的主动更新")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        android.util.Log.d("MusicWidget", "Widget收到广播: ${intent.action}")
        
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                android.util.Log.d("MusicWidget", "发送播放/暂停广播给MusicControlReceiver")
                val playIntent = Intent("com.maka.xiaoxia.action.PLAY_PAUSE")
                playIntent.setComponent(ComponentName(context, MusicControlReceiver::class.java))
                context.sendBroadcast(playIntent)
            }
            ACTION_NEXT -> {
                android.util.Log.d("MusicWidget", "发送下一首广播给MusicControlReceiver")
                val nextIntent = Intent("com.maka.xiaoxia.action.NEXT")
                nextIntent.setComponent(ComponentName(context, MusicControlReceiver::class.java))
                context.sendBroadcast(nextIntent)
            }
            ACTION_PREVIOUS -> {
                android.util.Log.d("MusicWidget", "发送上一首广播给MusicControlReceiver")
                val prevIntent = Intent("com.maka.xiaoxia.action.PREVIOUS")
                prevIntent.setComponent(ComponentName(context, MusicControlReceiver::class.java))
                context.sendBroadcast(prevIntent)
            }
            ACTION_UPDATE_WIDGET -> {
                android.util.Log.d("MusicWidget", "收到UPDATE_WIDGET广播，立即更新UI")
                
                // 检查是否有直接传递的封面数据
                val coverPath = intent.getStringExtra("cover_path")
                val coverAlbumId = intent.getLongExtra("cover_album_id", 0L)
                
                if (coverPath != null) {
                    // 使用广播直接传递的数据，避免SharedPreferences延迟
                    updateWidgetWithDirectData(context, intent)
                } else {
                    // 回退到从SharedPreferences读取
                    forceUpdateWidget(context)
                }
            }
        }
    }
    
    private fun forceUpdateWidget(context: Context) {
        // 强制刷新SharedPreferences缓存
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply()
        
        // 获取所有小组件实例并更新
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        
        android.util.Log.d("MusicWidget", "强制更新所有小组件，数量: ${appWidgetIds.size}")
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidgetWithDirectData(context: Context, intent: Intent) {
        // 使用广播直接传递的数据更新小组件，避免SharedPreferences延迟
        val title = intent.getStringExtra("current_title") ?: "未知歌曲"
        val artist = intent.getStringExtra("current_artist") ?: "未知艺术家"
        val isPlaying = intent.getBooleanExtra("is_playing", false)
        val coverPath = intent.getStringExtra("cover_path") ?: ""
        val coverAlbumId = intent.getLongExtra("cover_album_id", 0L)
        
        // 记录更新时间戳，防止系统onUpdate覆盖
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putLong("last_widget_update_time", System.currentTimeMillis()).apply()
        
        android.util.Log.d("MusicWidget", "使用直接数据更新小组件: $title - $artist")
        
        // 获取所有小组件实例并更新
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)
            
            // 设置点击事件 - 兼容安卓4.4
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val playPendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_PLAY_PAUSE), flags
            )
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_NEXT), flags
            )
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_PREVIOUS), flags
            )
            
            views.setOnClickPendingIntent(R.id.widget_play_pause, playPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_previous, prevPendingIntent)
            
            // 更新小组件内容
            views.setTextViewText(R.id.widget_song_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            
            // 设置播放/暂停图标
            val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            views.setImageViewResource(R.id.widget_play_pause, playIcon)
            
            // 直接设置专辑封面，使用广播传递的数据
            if (coverPath.isNotEmpty()) {
                try {
                    // 优先从文件直接读取嵌入式封面
                    val bitmap = getAlbumArt(coverPath)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    } else {
                        // 如果文件没有嵌入式封面，再尝试通过专辑ID获取
                        val albumBitmap = getAlbumArtById(context, coverAlbumId, coverPath)
                        if (albumBitmap != null) {
                            views.setImageViewBitmap(R.id.widget_album_art, albumBitmap)
                        } else {
                            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                        }
                    }
                } catch (e: Exception) {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                }
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        android.util.Log.d("MusicWidget", "使用直接数据更新完成，小组件数量: ${appWidgetIds.size}")
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_music)
        
        // 设置点击事件 - 兼容安卓4.4
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val playPendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_PLAY_PAUSE), flags
        )
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_NEXT), flags
        )
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_PREVIOUS), flags
        )
        
        views.setOnClickPendingIntent(R.id.widget_play_pause, playPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_previous, prevPendingIntent)
        
        // 更新小组件内容
        updateWidgetContent(context, views)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateWidgetContent(context: Context, views: RemoteViews) {
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        
        // 强制刷新SharedPreferences
        sharedPref.edit().apply()
        
        val title = sharedPref.getString("current_title", "未知歌曲")
        val artist = sharedPref.getString("current_artist", "未知艺术家")
        val isPlaying = sharedPref.getBoolean("is_playing", false)
        val albumArtPath = sharedPref.getString("current_path", "")
        val albumId = sharedPref.getLong("current_album_id", 0L)
        
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        
        // 设置播放/暂停图标
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        views.setImageViewResource(R.id.widget_play_pause, playIcon)
        
        // 设置专辑封面
        if (!albumArtPath.isNullOrEmpty()) {
            try {
                // 优先从文件直接读取嵌入式封面
                val bitmap = getAlbumArt(albumArtPath)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                } else {
                    // 如果文件没有嵌入式封面，再尝试通过专辑ID获取
                    val albumBitmap = getAlbumArtById(context, albumId, albumArtPath)
                    if (albumBitmap != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, albumBitmap)
                    } else {
                        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                    }
                }
            } catch (e: Exception) {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
            }
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
        }
        
        android.util.Log.d("MusicWidget", "更新小组件内容: $title - $artist, 播放状态: $isPlaying")
    }

    private fun getAlbumArtById(context: Context, albumId: Long, songPath: String): Bitmap? {
        try {
            if (albumId > 0) {
                val albumUri = Uri.parse("content://media/external/audio/albumart")
                val uri = Uri.withAppendedPath(albumUri, albumId.toString())
                val inputStream = context.contentResolver.openInputStream(uri)
                return BitmapFactory.decodeStream(inputStream)
            }
            return null
        } catch (e: Exception) {
            // 如果通过专辑ID获取失败，回退到从文件获取
            return getAlbumArt(songPath)
        }
    }

    private fun getAlbumArt(songPath: String): Bitmap? {
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(songPath)
                val art = retriever.embeddedPicture
                
                return if (art != null) {
                    BitmapFactory.decodeByteArray(art, 0, art.size)
                } else {
                    null
                }
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    android.util.Log.w("MusicWidget", "释放MediaMetadataRetriever失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicWidget", "读取文件封面失败: ${e.message}")
            return null
        }
    }


}