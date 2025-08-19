package com.maka.xiaoxia

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log

/**
 * 安卓4.4及以下版本的兼容媒体通知管理器
 * 使用传统通知API，避免使用NotificationChannel和MediaStyle
 */
class LegacyMediaNotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LegacyMediaNotification"
        private const val NOTIFICATION_ID = 1001
    }
    
    private var notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    
    /**
     * 创建传统媒体通知 - 兼容安卓4.4
     */
    fun createLegacyMediaNotification(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        albumArt: Bitmap? = null
    ): Notification {
        
        // 创建主界面Intent
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val mainPendingIntent = PendingIntent.getActivity(context, 0, mainIntent, pendingIntentFlags)
        
        // 创建控制Intent
        val playPauseIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(context, 1, playPauseIntent, pendingIntentFlags)
        
        val nextIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(context, 2, nextIntent, pendingIntentFlags)
        
        val prevIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getService(context, 3, prevIntent, pendingIntentFlags)
        
        val stopIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(context, 4, stopIntent, pendingIntentFlags)
        
        // 获取专辑封面并进行尺寸适配
        val originalArt = albumArt ?: getDefaultAlbumArt()
        val largeIcon = scaleAlbumArtForNotification(originalArt)
        
        // 构建传统通知
        val builder = NotificationCompat.Builder(context)
            .setSmallIcon(R.drawable.ic_music_default)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setLargeIcon(largeIcon)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setAutoCancel(false)
            
        // 添加操作按钮
        builder.addAction(
            R.drawable.ic_prev,
            "上一首",
            prevPendingIntent
        )
        
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (isPlaying) "暂停" else "播放"
        builder.addAction(
            playPauseIcon,
            playPauseTitle,
            playPausePendingIntent
        )
        
        builder.addAction(
            R.drawable.ic_next,
            "下一首",
            nextPendingIntent
        )
        
        // 添加停止按钮
        builder.addAction(
            R.drawable.ic_stop,
            "停止",
            stopPendingIntent
        )
        
        return builder.build()
    }
    
    /**
     * 获取默认专辑封面 - 修复空指针问题
     */
    private fun getDefaultAlbumArt(): Bitmap {
        return try {
            val resources = context.resources
            
            // 尝试多个备用资源
            val resourceIds = listOf(
                R.drawable.ic_music_default,
                R.drawable.ic_launcher_foreground,
                R.mipmap.ic_launcher
            )
            
            for (resId in resourceIds) {
                try {
                    if (resId != 0) {
                        val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            context.getDrawable(resId)
                        } else {
                            context.resources.getDrawable(resId)
                        }
                        
                        drawable?.let {
                            val bitmap = Bitmap.createBitmap(
                                it.intrinsicWidth.coerceAtLeast(200),
                                it.intrinsicHeight.coerceAtLeast(200),
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(bitmap)
                            it.setBounds(0, 0, canvas.width, canvas.height)
                            it.draw(canvas)
                            return bitmap
                        }
                    }
                } catch (e: Exception) {
                    // 继续尝试下一个资源
                }
            }
            
            // 如果所有资源都失败，创建纯色后备封面
            createFallbackAlbumArt()
            
        } catch (e: Exception) {
            Log.e(TAG, "获取默认专辑封面失败: ${e.message}")
            createFallbackAlbumArt()
        }
    }
    
    /**
     * 缩放专辑封面以适应通知栏显示
     * 安卓4.4系统需要固定尺寸以避免显示异常
     */
    private fun scaleAlbumArtForNotification(originalBitmap: Bitmap): Bitmap {
        return try {
            // 安卓4.4通知栏大图标推荐尺寸：64x64 dp
            // 转换为像素：64 * density
            val targetSizeDp = 64
            val density = context.resources.displayMetrics.density
            val targetSizePx = (targetSizeDp * density).toInt()
            
            // 如果图片已经符合要求尺寸，直接返回
            if (originalBitmap.width == targetSizePx && originalBitmap.height == targetSizePx) {
                return originalBitmap
            }
            
            // 计算缩放比例，保持宽高比
            val scaleFactor = targetSizePx.toFloat() / Math.max(originalBitmap.width, originalBitmap.height)
            val scaledWidth = (originalBitmap.width * scaleFactor).toInt()
            val scaledHeight = (originalBitmap.height * scaleFactor).toInt()
            
            // 创建缩放后的Bitmap
            Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "专辑封面缩放失败: ${e.message}")
            originalBitmap // 如果缩放失败，返回原始图片
        }
    }

    /**
     * 创建纯色后备专辑封面
     */
    private fun createFallbackAlbumArt(): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 蓝色背景
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1976D2")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        
        // 白色音符图标
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 80f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("♪", size / 2f, size / 2f + 25f, textPaint)
        
        return bitmap
    }
    
    /**
     * 显示传统通知
     */
    fun showLegacyNotification(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        albumArt: Bitmap? = null
    ) {
        try {
            val notification = createLegacyMediaNotification(title, artist, album, isPlaying, albumArt)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "显示传统通知失败: ${e.message}")
        }
    }
    
    /**
     * 取消通知
     */
    fun cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "取消通知失败: ${e.message}")
        }
    }
}