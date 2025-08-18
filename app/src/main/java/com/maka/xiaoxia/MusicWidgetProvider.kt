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
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                // 直接处理小组件点击事件，避免广播路由问题
                android.util.Log.d("MusicWidget", "直接处理${intent.action}操作")
                
                // 创建明确的服务意图
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = when (intent.action) {
                        ACTION_PLAY_PAUSE -> "com.maka.xiaoxia.action.PLAY_PAUSE"
                        ACTION_NEXT -> "com.maka.xiaoxia.action.NEXT"
                        ACTION_PREVIOUS -> "com.maka.xiaoxia.action.PREVIOUS"
                        else -> intent.action
                    }
                    component = ComponentName(context, MusicService::class.java)
                    setPackage(context.packageName)
                }
                
                // 安卓8.0+需要使用startForegroundService
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    android.util.Log.d("MusicWidget", "成功启动音乐服务处理${intent.action}")
                } catch (e: Exception) {
                    android.util.Log.e("MusicWidget", "启动服务失败: ${e.message}")
                    
                    // 回退方案：启动Activity
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        action = when (intent.action) {
                            ACTION_PLAY_PAUSE -> "com.maka.xiaoxia.action.PLAY_PAUSE"
                            ACTION_NEXT -> "com.maka.xiaoxia.action.NEXT"
                            ACTION_PREVIOUS -> "com.maka.xiaoxia.action.PREVIOUS"
                            else -> intent.action
                        }
                    }
                    
                    try {
                        context.startActivity(activityIntent)
                        android.util.Log.d("MusicWidget", "成功启动MainActivity处理${intent.action}")
                    } catch (e2: Exception) {
                        android.util.Log.e("MusicWidget", "启动Activity失败: ${e2.message}")
                    }
                }
            }
            ACTION_UPDATE_WIDGET -> {
                android.util.Log.d("MusicWidget", "收到UPDATE_WIDGET广播，立即更新UI")
                
                // 无论是否有直接数据，都强制使用广播中的最新数据
                updateWidgetWithDirectData(context, intent)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                android.util.Log.d("MusicWidget", "收到系统APPWIDGET_UPDATE广播")
                
                // 系统广播触发时，强制从SharedPreferences获取最新数据
                forceUpdateWidget(context)
            }
        }
    }
    
    private fun forceUpdateWidget(context: Context) {
        // 强制刷新SharedPreferences缓存 - 使用同步方式确保获取最新数据
        val sharedPref = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        
        // 添加延迟确保SharedPreferences已同步
        Handler().postDelayed({
            // 获取所有小组件实例并更新
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            android.util.Log.d("MusicWidget", "强制更新所有小组件，数量: ${appWidgetIds.size}")
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }, 100) // 100ms延迟确保SharedPreferences已更新
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
            
            // 设置点击事件 - 安卓4.4+兼容性修复
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // 使用明确的ComponentName确保广播正确路由
            val playIntent = Intent(ACTION_PLAY_PAUSE).apply {
                component = ComponentName(context, MusicWidgetProvider::class.java)
                setPackage(context.packageName)
            }
            val nextIntent = Intent(ACTION_NEXT).apply {
                component = ComponentName(context, MusicWidgetProvider::class.java)
                setPackage(context.packageName)
            }
            val prevIntent = Intent(ACTION_PREVIOUS).apply {
                component = ComponentName(context, MusicWidgetProvider::class.java)
                setPackage(context.packageName)
            }
            
            val playPendingIntent = PendingIntent.getBroadcast(
                context, 1001, playIntent, flags
            )
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 1002, nextIntent, flags
            )
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 1003, prevIntent, flags
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
                    val bitmap = getAlbumArt(context, coverPath)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    } else {
                        // 如果文件没有嵌入式封面，再尝试通过专辑ID获取
                        val albumBitmap = getAlbumArtById(context, coverAlbumId)
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
        
        // 设置点击事件 - 安卓4.4+兼容性修复
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // 使用明确的ComponentName确保广播正确路由
        val playIntent = Intent(ACTION_PLAY_PAUSE).apply {
            component = ComponentName(context, MusicWidgetProvider::class.java)
            setPackage(context.packageName)
        }
        val nextIntent = Intent(ACTION_NEXT).apply {
            component = ComponentName(context, MusicWidgetProvider::class.java)
            setPackage(context.packageName)
        }
        val prevIntent = Intent(ACTION_PREVIOUS).apply {
            component = ComponentName(context, MusicWidgetProvider::class.java)
            setPackage(context.packageName)
        }
        
        val playPendingIntent = PendingIntent.getBroadcast(
            context, 1001, playIntent, flags
        )
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 1002, nextIntent, flags
        )
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 1003, prevIntent, flags
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
        val albumArtPath = sharedPref.getString("current_song_path", "")
        val albumId = sharedPref.getLong("current_album_id", 0L)
        
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        
        // 设置播放/暂停图标
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        views.setImageViewResource(R.id.widget_play_pause, playIcon)
        
        android.util.Log.d("MusicWidget", "=== 更新小组件内容开始 ===")
        android.util.Log.d("MusicWidget", "歌曲: $title - $artist")
        android.util.Log.d("MusicWidget", "文件路径: $albumArtPath")
        android.util.Log.d("MusicWidget", "专辑ID: $albumId")
        android.util.Log.d("MusicWidget", "播放状态: $isPlaying")
        
        // 设置专辑封面
        if (!albumArtPath.isNullOrEmpty()) {
            android.util.Log.d("MusicWidget", "尝试从文件路径获取封面: $albumArtPath")
            try {
                // 优先从文件直接读取嵌入式封面
                val bitmap = getAlbumArt(context, albumArtPath)
                if (bitmap != null) {
                    android.util.Log.d("MusicWidget", "成功从文件获取封面")
                    views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                } else {
                    android.util.Log.d("MusicWidget", "文件无嵌入式封面，尝试专辑ID: $albumId")
                    // 如果文件没有嵌入式封面，再尝试通过专辑ID获取
                    val albumBitmap = getAlbumArtById(context, albumId)
                    if (albumBitmap != null) {
                        android.util.Log.d("MusicWidget", "成功从专辑ID获取封面")
                        views.setImageViewBitmap(R.id.widget_album_art, albumBitmap)
                    } else {
                        android.util.Log.d("MusicWidget", "无法获取封面，使用默认")
                        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicWidget", "获取封面异常: ${e.message}")
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
            }
        } else {
            android.util.Log.d("MusicWidget", "无文件路径，使用默认封面")
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_default)
        }
        
        android.util.Log.d("MusicWidget", "=== 更新小组件内容结束 ===")
    }

    private fun getAlbumArtById(context: Context, albumId: Long): Bitmap? {
        try {
            if (albumId <= 0) return null
            
            // 安卓15/16权限检查 - 使用context参数
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.w("MusicWidget", "缺少READ_MEDIA_IMAGES权限，无法通过专辑ID获取封面")
                    return null
                }
            }
            
            // 使用新的媒体存储URI格式
            val albumUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Uri.parse("content://media/external/audio/albumart")
            } else {
                Uri.parse("content://media/external/audio/albumart")
            }
            
            val uri = Uri.withAppendedPath(albumUri, albumId.toString())
            
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                        // 缩放图片以适应小组件大小
                        val maxSize = 256
                        val scale = maxSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                        return if (scale < 1.0f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                bitmap, 
                                (bitmap.width * scale).toInt(), 
                                (bitmap.height * scale).toInt(), 
                                true
                            )
                        } else {
                            bitmap
                        }
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("MusicWidget", "权限拒绝访问专辑封面: ${e.message}")
                return null
            }
            
            return null
        } catch (e: Exception) {
            android.util.Log.w("MusicWidget", "通过专辑ID获取封面失败: ${e.message}")
            return null
        }
    }

    private fun getAlbumArt(context: Context, songPath: String): Bitmap? {
        try {
            // 安卓15/16权限检查 - 使用context参数
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // 检查是否有读取音频文件的权限
                if (context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.w("MusicWidget", "缺少READ_MEDIA_AUDIO权限，无法读取专辑封面")
                    return null
                }
            }
            
            val retriever = MediaMetadataRetriever()
            try {
                // 使用FileDescriptor方式读取，兼容性更好
                val file = java.io.File(songPath)
                if (!file.exists() || !file.canRead()) {
                    android.util.Log.w("MusicWidget", "文件不存在或无法读取: $songPath")
                    return null
                }
                
                // 根据安卓版本选择合适的设置数据源方式
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        val fileDescriptor = java.io.FileInputStream(file).fd
                        retriever.setDataSource(fileDescriptor)
                    } catch (e: Exception) {
                        // 回退到路径方式
                        retriever.setDataSource(songPath)
                    }
                } else {
                    retriever.setDataSource(songPath)
                }
                
                val art = retriever.embeddedPicture
                
                return if (art != null && art.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                        // 缩放图片以适应小组件大小
                        val maxSize = 256 // 小组件最大尺寸
                        val scale = maxSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                        if (scale < 1.0f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                bitmap, 
                                (bitmap.width * scale).toInt(), 
                                (bitmap.height * scale).toInt(), 
                                true
                            )
                        } else {
                            bitmap
                        }
                    } else {
                        null
                    }
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
        } catch (e: SecurityException) {
            android.util.Log.e("MusicWidget", "权限不足，无法读取文件封面: ${e.message}")
            return null
        } catch (e: Exception) {
            android.util.Log.e("MusicWidget", "读取文件封面失败: ${e.message}")
            return null
        }
    }


}