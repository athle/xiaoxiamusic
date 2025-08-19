package com.maka.xiaoxia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.session.MediaSession
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.util.Log

/**
 * 增强型媒体通知管理器
 * 支持安卓15及小米、OPPO、vivo定制系统的通知栏音乐控件
 * 兼容安卓4.4至安卓15全版本
 */
class EnhancedMediaNotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedMediaNotification"
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1001
        
        // 自定义ROM常量
        private const val MIUI_PACKAGE = "com.miui.player"
        private const val OPPO_PACKAGE = "com.oppo.music"
        private const val VIVO_PACKAGE = "com.android.bbkmusic"
    }
    
    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannel()
        createMediaSession()
    }
    
    /**
     * 创建通知渠道 - 适配安卓8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = "音乐播放"
                val descriptionText = "音乐播放控制通知"
                val importance = NotificationManager.IMPORTANCE_HIGH

                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setAllowBubbles(false)
                        setBypassDnd(false)
                    }
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)

                // ColorOS特殊处理：确保通知渠道可见
                if (isOppoRom()) {
                    val channel2 = NotificationChannel("${CHANNEL_ID}_oppo", "音乐播放(ColorOS)", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "ColorOS音乐播放控制"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    notificationManager.createNotificationChannel(channel2)
                    
                    // ColorOS 15特殊处理
                    if (isColorOS15()) {
                        val colorOS15Channel = NotificationChannel("${CHANNEL_ID}_coloros15", "音乐播放(ColorOS 15)", NotificationManager.IMPORTANCE_HIGH).apply {
                            description = "ColorOS 15音乐播放控制"
                            setShowBadge(false)
                            enableLights(false)
                            enableVibration(false)
                            setSound(null, null)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setAllowBubbles(false)
                                setBypassDnd(false)
                            }
                        }
                        notificationManager.createNotificationChannel(colorOS15Channel)
                    }
                }
            } catch (e: NoClassDefFoundError) {
                // 安卓4.4及以下版本，NotificationChannel不存在，静默处理
                Log.d(TAG, "NotificationChannel not supported on this Android version")
            } catch (e: Exception) {
                Log.e(TAG, "创建通知渠道失败: ${e.message}")
            }
        }
    }
    
    /**
     * 创建媒体会话 - 支持系统级媒体控制
     */
    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(context, "XiaoxiaMusic").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                     MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            // 设置播放状态
            setPlaybackState(PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or
                           PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                           PlaybackStateCompat.ACTION_STOP)
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build())
        }
    }
    
    /**
     * 创建增强型媒体通知
     */
    fun createMediaNotification(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        albumArt: Bitmap? = null,
        duration: Long = 0,
        position: Long = 0
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
        
        // 获取专辑封面
        val largeIcon = albumArt ?: getDefaultAlbumArt()
        
        // 构建通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_default)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setLargeIcon(largeIcon)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 改为高优先级
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT) // 添加传输类别
            
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
        
        // 添加停止按钮（部分ROM需要）
        builder.addAction(
            R.drawable.ic_stop,
            "停止",
            stopPendingIntent
        )
        
        // 设置媒体样式 - 安卓5.0+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val mediaStyle = MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2) // 显示前3个操作
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopPendingIntent)
                builder.setStyle(mediaStyle)
            }
        } catch (e: Exception) {
            // 安卓4.4及以下版本，MediaStyle不存在，使用普通通知样式
            Log.d(TAG, "MediaStyle not supported on this Android version")
        }
        
        // 添加进度条（安卓4.4+兼容）
        if (duration > 0) {
            builder.setProgress(duration.toInt(), position.toInt(), false)
        }
        
        // 添加自定义ROM适配
        addCustomRomFeatures(builder, title, artist, isPlaying)
        
        return builder.build()
    }
    
    /**
     * 添加小米、OPPO、vivo定制ROM的特殊功能
     */
    private fun addCustomRomFeatures(
        builder: NotificationCompat.Builder,
        title: String,
        artist: String,
        isPlaying: Boolean
    ) {
        try {
            // 小米MIUI特殊处理
            if (isMiuiRom()) {
                val colorRes = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.getColor(R.color.miui_accent_color)
                    } else {
                        context.resources.getColor(R.color.miui_accent_color)
                    }
                } catch (e: Exception) {
                    0 // 使用默认颜色
                }
                if (colorRes != 0) {
                    builder.setColor(colorRes)
                }
                builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            }
            
            // OPPO ColorOS特殊处理
            if (isOppoRom()) {
                val colorRes = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.getColor(R.color.oppo_accent_color)
                    } else {
                        context.resources.getColor(R.color.oppo_accent_color)
                    }
                } catch (e: Exception) {
                    0 // 使用默认颜色
                }
                if (colorRes != 0) {
                    builder.setColor(colorRes)
                }
                builder.setGroup("oppo_music_group")
                
                // ColorOS系统特殊标志
                builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                
                // 安卓16及以上需要额外设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            
            // VIVO FuntouchOS特殊处理
            if (isVivoRom()) {
                val colorRes = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.getColor(R.color.vivo_accent_color)
                    } else {
                        context.resources.getColor(R.color.vivo_accent_color)
                    }
                } catch (e: Exception) {
                    0 // 使用默认颜色
                }
                if (colorRes != 0) {
                    builder.setColor(colorRes)
                }
                builder.setGroup("vivo_music_group")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "添加定制ROM功能失败: ${e.message}")
        }
    }
    
    /**
     * 检测是否为小米MIUI系统
     */
    private fun isMiuiRom(): Boolean {
        return try {
            val prop = getSystemProperty("ro.miui.ui.version.name")
            !prop.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测是否为OPPO ColorOS系统
     */
    private fun isOppoRom(): Boolean {
        return try {
            val prop = getSystemProperty("ro.build.version.opporom")
            val prop2 = getSystemProperty("ro.oppo.version")
            val prop3 = getSystemProperty("ro.coloros.version")
            val prop4 = getSystemProperty("ro.build.version.oplusrom") // ColorOS 15+
            !prop.isNullOrEmpty() || !prop2.isNullOrEmpty() || !prop3.isNullOrEmpty() || !prop4.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测是否为ColorOS 15及以上版本
     */
    private fun isColorOS15(): Boolean {
        return try {
            val version = getSystemProperty("ro.build.version.oplusrom")
            !version.isNullOrEmpty() && version.startsWith("15")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测是否为安卓16及以上
     */
    private fun isAndroid16OrAbove(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
    
    /**
     * 检测是否为VIVO FuntouchOS系统
     */
    private fun isVivoRom(): Boolean {
        return try {
            val prop = getSystemProperty("ro.vivo.os.version")
            !prop.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取系统属性
     */
    private fun getSystemProperty(propName: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, propName) as String?
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取默认专辑封面
     */
    private fun getDefaultAlbumArt(): Bitmap {
        return try {
            // 尝试多个备用图标资源
            val fallbackIds = listOf(
                R.drawable.ic_music_default,
                R.drawable.ic_launcher_foreground,
                R.mipmap.ic_launcher,
                android.R.drawable.ic_media_play
            )
            
            for (resId in fallbackIds) {
                try {
                    if (resId != 0) {
                        val drawable = context.resources.getDrawable(resId, null)
                        if (drawable != null) {
                            val bitmap = Bitmap.createBitmap(
                                200, 200, Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(bitmap)
                            drawable.setBounds(0, 0, 200, 200)
                            drawable.draw(canvas)
                            return bitmap
                        }
                    }
                } catch (e: Exception) {
                    // 忽略单个资源的错误，继续尝试下一个
                }
            }
            
            // 所有资源都失败，创建纯色bitmap
            createFallbackAlbumArt()
            
        } catch (e: Exception) {
            Log.e(TAG, "获取默认专辑封面失败: ${e.message}")
            createFallbackAlbumArt()
        }
    }
    
    /**
     * 创建后备专辑封面
     */
    private fun createFallbackAlbumArt(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = 0xFF3F51B5.toInt() // 默认蓝色
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 200f, 200f, paint)
        
        // 添加一个简单的音符图标
        val notePaint = android.graphics.Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = android.graphics.Paint.Style.FILL
            textSize = 80f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("♪", 100f, 120f, notePaint)
        
        return bitmap
    }
    
    /**
     * 更新播放状态
     */
    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        mediaSession?.let { session ->
            val state = if (isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }
            
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or
                           PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                           PlaybackStateCompat.ACTION_STOP)
                .setState(state, position, 1.0f)
                .build()
            
            session.setPlaybackState(playbackState)
        }
    }
    
    /**
     * 更新媒体元数据
     */
    fun updateMediaMetadata(
        title: String,
        artist: String,
        album: String,
        duration: Long,
        albumArt: Bitmap? = null
    ) {
        mediaSession?.let { session ->
            val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
            
            session.setMetadata(metadata)
        }
    }
    
    /**
     * 显示通知
     */
    fun showNotification(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        albumArt: Bitmap? = null,
        duration: Long = 0,
        position: Long = 0
    ) {
        val notification = createMediaNotification(
            title, artist, album, isPlaying, albumArt, duration, position
        )
        
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "显示通知失败: ${e.message}")
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
    
    /**
     * 释放资源
     */
    fun release() {
        cancelNotification()
        mediaSession?.release()
        mediaSession = null
    }
}