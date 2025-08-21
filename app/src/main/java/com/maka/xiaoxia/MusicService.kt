package com.maka.xiaoxia

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.media.AudioManager
import android.view.KeyEvent
import java.io.IOException
import com.maka.xiaoxia.CarMediaSessionManager
import com.maka.xiaoxia.ColorOS15MediaSessionManager

class MusicService : Service() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongPath: String? = null
    private var isPlaying = false
    private var songList: MutableList<Song> = mutableListOf()
    private var currentSongIndex = 0
    private lateinit var sharedPref: SharedPreferences
    private lateinit var audioManager: AudioManager
    private var headsetReceiver: BroadcastReceiver? = null
    private lateinit var carMediaSessionManager: CarMediaSessionManager
    private var colorOS15MediaSessionManager: ColorOS15MediaSessionManager? = null
    private var vivoMIUIMediaSessionManager: VivoMIUIMediaSessionManager? = null
    private var playMode = 2 // 0: REPEAT_ONE, 1: PLAY_ORDER, 2: REPEAT_ALL, 3: SHUFFLE
    private var originalSongList: MutableList<Song> = mutableListOf()
    
    companion object {
        const val ACTION_PLAY_PAUSE = "com.maka.xiaoxia.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.maka.xiaoxia.action.NEXT"
        const val ACTION_PREVIOUS = "com.maka.xiaoxia.action.PREVIOUS"
        const val ACTION_STOP = "com.maka.xiaoxia.action.STOP"
        const val ACTION_SEEK_TO = "com.maka.xiaoxia.action.SEEK_TO"
        const val ACTION_UPDATE_WIDGET = "com.maka.xiaoxia.action.UPDATE_WIDGET"
        const val ACTION_SET_PLAY_MODE = "com.maka.xiaoxia.action.SET_PLAY_MODE"
        const val PREF_NAME = "music_service_prefs"
        
        private const val NOTIFICATION_CHANNEL_ID = "music_service_channel"
        private const val NOTIFICATION_ID = 1
    }

    // 格式化时间显示
    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "服务创建")
        
        sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 初始化MediaPlayer
        mediaPlayer = MediaPlayer()
        
        // 设置播放完成监听器
        mediaPlayer?.setOnCompletionListener {
            onSongCompleted()
        }
        
        // 延迟加载歌曲列表，避免阻塞启动
        Thread {
            loadSongList()
            // 延迟恢复播放状态，减少启动时的CPU负载
            restorePlaybackState()
        }.start()
        
        // 注册耳机和媒体按钮接收器
        registerMediaButtonReceiver()
        
        // 立即初始化媒体会话管理器 - 修复Android 15/16系统控制中心
        try {
            // 初始化车机媒体会话管理器
            carMediaSessionManager = CarMediaSessionManager(this)
            carMediaSessionManager.createMediaSession()
            
            // 初始化ColorOS 15媒体会话管理器（用于系统控制中心）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                colorOS15MediaSessionManager = ColorOS15MediaSessionManager(this)
                colorOS15MediaSessionManager?.createMediaSession()
            }
            
            // 初始化vivo MIUI媒体会话管理器
            vivoMIUIMediaSessionManager = VivoMIUIMediaSessionManager(this)
            if (vivoMIUIMediaSessionManager?.isVivoMIUISystem() == true) {
                vivoMIUIMediaSessionManager?.createMediaSession()
            }
            
            // 初始化增强型通知管理器并创建媒体会话
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enhancedMediaNotificationManager = EnhancedMediaNotificationManager(this)
                enhancedMediaNotificationManager.createMediaSession()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0-7.1也使用增强型通知
                enhancedMediaNotificationManager = EnhancedMediaNotificationManager(this)
                enhancedMediaNotificationManager.createMediaSession()
            }
            
            Log.d("MusicService", "所有媒体会话已初始化完成")
        } catch (e: Exception) {
            Log.e("MusicService", "媒体会话初始化失败: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "服务启动: ${intent?.action}")
        
        // 立即创建默认通知，避免前台服务超时
        createDefaultForegroundNotification()
        
        // 处理启动意图中的动作
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> togglePlayPauseInternal()
                ACTION_NEXT -> playNextInternal()
                ACTION_PREVIOUS -> playPreviousInternal()
                ACTION_STOP -> stopPlaybackInternal()
                ACTION_SEEK_TO -> {
                    val position = intent.getLongExtra("position", 0)
                    seekToInternal(position.toInt())
                }
                ACTION_SET_PLAY_MODE -> {
                    val mode = intent.getIntExtra("play_mode", 2)
                    setPlayModeInternal(mode)
                }
            }
        }
        
        // 延迟更新为实际内容通知
        updateNotificationWithDelay()
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MusicService", "服务销毁")
        savePlaybackState()
        
        // 注销耳机接收器
        headsetReceiver?.let {
            unregisterReceiver(it)
            headsetReceiver = null
        }
        
        // 释放增强型媒体通知管理器
        if (::enhancedMediaNotificationManager.isInitialized) {
            enhancedMediaNotificationManager.release()
        }
        
        // 释放传统通知管理器
        if (::legacyMediaNotificationManager.isInitialized) {
            legacyMediaNotificationManager.cancelNotification()
        }
        
        // 释放车机媒体会话
        if (::carMediaSessionManager.isInitialized) {
            carMediaSessionManager.releaseMediaSession()
        }
        
        // 释放ColorOS 15媒体会话
        colorOS15MediaSessionManager?.releaseMediaSession()
        
        // 释放vivo MIUI媒体会话
        vivoMIUIMediaSessionManager?.releaseMediaSession()
        
        mediaPlayer?.release()
        mediaPlayer = null
        
        // 取消通知
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun registerMediaButtonReceiver() {
        // 注册耳机插拔和蓝牙媒体按钮接收器
        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 0) {
                            // 耳机拔出，暂停播放
                            if (isPlaying) {
                                togglePlayPauseInternal()
                            }
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        // 音频即将变得嘈杂（如蓝牙断开），暂停播放
                        if (isPlaying) {
                            togglePlayPauseInternal()
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        
        try {
            registerReceiver(headsetReceiver, filter)
            Log.d("MusicService", "已注册媒体按钮接收器")
        } catch (e: Exception) {
            Log.e("MusicService", "注册媒体按钮接收器失败: ${e.message}")
        }
    }

    private fun loadSongList() {
        songList.clear()
        
        // 从SharedPreferences批量加载歌曲列表
        val songCount = sharedPref.getInt("song_count", 0)
        if (songCount == 0) {
            Log.d("MusicService", "没有歌曲需要加载")
            return
        }
        
        for (i in 0 until songCount) {
            val id = sharedPref.getLong("song_${i}_id", 0)
            val title = sharedPref.getString("song_${i}_title", "") ?: ""
            val artist = sharedPref.getString("song_${i}_artist", "") ?: ""
            val album = sharedPref.getString("song_${i}_album", "") ?: ""
            val duration = sharedPref.getLong("song_${i}_duration", 0)
            val path = sharedPref.getString("song_${i}_path", "") ?: ""
            val albumId = sharedPref.getLong("song_${i}_albumId", 0)
            val lyrics = sharedPref.getString("song_${i}_lyrics", null)
            
            if (title.isNotEmpty() && path.isNotEmpty()) {
                songList.add(Song(id, title, artist, album, duration, path, albumId, lyrics))
            }
        }
        
        originalSongList.clear()
        originalSongList.addAll(songList)
        
        Log.d("MusicService", "加载了 ${songList.size} 首歌曲")
        
        // 加载播放模式
        playMode = sharedPref.getInt("play_mode", 2)
        applyPlayMode()
    }

    private fun savePlaybackState() {
        // 使用同步写入确保数据立即生效
        sharedPref.edit().apply {
            putBoolean("is_playing", isPlaying)
            putInt("current_song_index", currentSongIndex)
            putString("current_song_path", currentSongPath)
            putInt("song_count", songList.size)
            putInt("current_position", getCurrentPosition())
            putLong("last_play_time", System.currentTimeMillis())
            putInt("play_mode", playMode)

            // 保存完整的歌曲列表信息
            songList.forEachIndexed { index, song ->
                putLong("song_${index}_id", song.id)
                putString("song_${index}_title", song.title)
                putString("song_${index}_artist", song.artist)
                putString("song_${index}_album", song.album)
                putLong("song_${index}_duration", song.duration)
                putString("song_${index}_path", song.path)
                putLong("song_${index}_albumId", song.albumId)
                putString("song_${index}_lyrics", song.lyrics ?: "")
            }

            // 保存当前歌曲的完整信息
            if (songList.isNotEmpty() && currentSongIndex < songList.size) {
                val song = songList[currentSongIndex]
                putString("current_title", song.title)
                putString("current_artist", song.artist)
                putString("current_album", song.album)
                putLong("current_album_id", song.albumId)
                putString("current_lyrics", song.lyrics ?: "")
            }
            // 使用commit()同步写入，确保数据立即生效
            commit()
        }
    }

    private fun restorePlaybackState() {
        isPlaying = sharedPref.getBoolean("is_playing", false)
        currentSongIndex = sharedPref.getInt("current_song_index", 0)
        currentSongPath = sharedPref.getString("current_song_path", null)
        val savedPosition = sharedPref.getInt("current_position", 0)
        
        // 延迟恢复播放状态，避免启动时阻塞
        if (songList.isNotEmpty() && currentSongIndex < songList.size) {
            val song = songList[currentSongIndex]
            currentSongPath = song.path
            
            // 延迟执行MediaPlayer初始化
            Thread {
                try {
                    mediaPlayer?.reset()
                    mediaPlayer?.setDataSource(song.path)
                    mediaPlayer?.prepare()
                    if (savedPosition > 0) {
                        mediaPlayer?.seekTo(savedPosition)
                    }
                    
                    if (isPlaying) {
                        mediaPlayer?.start()
                        Log.d("MusicService", "延迟恢复播放: ${song.title} 从位置: ${formatTime(savedPosition)}")
                    } else {
                        Log.d("MusicService", "延迟准备播放: ${song.title} 从位置: ${formatTime(savedPosition)}")
                    }
                    
                    // 延迟更新通知和小部件
                    updateNotification()
                    updateWidget()
                    
                } catch (e: Exception) {
                    Log.e("MusicService", "延迟恢复播放状态失败: ${e.message}")
                    isPlaying = false
                }
            }.start()
        }
    }

    private fun togglePlayPauseInternal() {
        if (songList.isEmpty()) {
            Log.d("MusicService", "歌曲列表为空")
            return
        }

        if (isPlaying) {
            pauseMusic()
        } else {
            if (currentSongPath == null || currentSongIndex >= songList.size) {
                playMusicInternal(0)
            } else {
                resumeMusic()
            }
        }
        
        // 确保状态更新广播被发送
        updateWidget()
    }

    // 添加服务控制接口
    fun getCurrentPosition(): Int = if (isMediaPlayerReady()) mediaPlayer?.currentPosition ?: 0 else 0
    fun getDuration(): Int = if (isMediaPlayerReady()) mediaPlayer?.duration ?: 0 else 0
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    
    // 检查MediaPlayer是否已准备就绪
    fun isMediaPlayerReady(): Boolean = mediaPlayer != null && currentSongPath != null
    
    // 添加进度控制方法
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }
    
    private fun seekToInternal(position: Int) {
        mediaPlayer?.seekTo(position)
        updateNotification()
        updateWidget()
    }
    
    // 修改播放控制方法，确保状态同步
    private fun playMusicInternal(index: Int) {
        if (index < 0 || index >= songList.size) return
        
        try {
            // 先停止当前播放
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            
            val song = songList[index]
            currentSongPath = song.path
            currentSongIndex = index
            
            mediaPlayer?.setDataSource(song.path)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            isPlaying = true
            
            Log.d("MusicService", "开始播放: ${song.title}")
            
            // 更新通知
            updateNotification()
            
            // 更新小组件
            updateWidget()
            
        } catch (e: IOException) {
            Log.e("MusicService", "播放失败: ${e.message}")
            isPlaying = false
        }
    }

    private fun pauseMusic() {
        mediaPlayer?.pause()
        isPlaying = false
        Log.d("MusicService", "暂停播放")
        
        // 更新通知
        updateNotification()
        
        // 更新小组件
        updateWidget()
    }

    private fun resumeMusic() {
        mediaPlayer?.start()
        isPlaying = true
        Log.d("MusicService", "继续播放")
        
        // 更新通知
        updateNotification()
        
        // 更新小组件
        updateWidget()
    }

    private fun playNextInternal() {
        if (songList.isEmpty()) return
        
        when (playMode) {
            0 -> { // REPEAT_ONE: 循环播放当前1首歌曲
                // 保持在当前歌曲
                playMusicInternal(currentSongIndex)
            }
            1 -> { // PLAY_ORDER: 顺序播放完整个播放列表不循环
                if (currentSongIndex < songList.size - 1) {
                    currentSongIndex++
                    playMusicInternal(currentSongIndex)
                } else {
                    // 到达列表末尾，停止播放
                    stopPlaybackInternal()
                }
            }
            2 -> { // REPEAT_ALL: 按列表顺序循环播放整个列表
                currentSongIndex = (currentSongIndex + 1) % songList.size
                playMusicInternal(currentSongIndex)
            }
            3 -> { // SHUFFLE: 乱序播放整个播放列表
                if (songList.size > 1) {
                    val oldIndex = currentSongIndex
                    do {
                        currentSongIndex = (0 until songList.size).random()
                    } while (currentSongIndex == oldIndex)
                }
                playMusicInternal(currentSongIndex)
            }
        }
    }

    private fun playPreviousInternal() {
        if (songList.isEmpty()) return
        
        when (playMode) {
            0 -> { // REPEAT_ONE: 循环播放当前1首歌曲
                // 保持在当前歌曲
                playMusicInternal(currentSongIndex)
            }
            1 -> { // PLAY_ORDER: 顺序播放完整个播放列表不循环
                if (currentSongIndex > 0) {
                    currentSongIndex--
                    playMusicInternal(currentSongIndex)
                } else {
                    // 到达列表开头，保持在第一首
                    currentSongIndex = 0
                    playMusicInternal(currentSongIndex)
                }
            }
            2 -> { // REPEAT_ALL: 按列表顺序循环播放整个列表
                currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
                playMusicInternal(currentSongIndex)
            }
            3 -> { // SHUFFLE: 乱序播放整个播放列表
                if (songList.size > 1) {
                    val oldIndex = currentSongIndex
                    do {
                        currentSongIndex = (0 until songList.size).random()
                    } while (currentSongIndex == oldIndex)
                }
                playMusicInternal(currentSongIndex)
            }
        }
    }

    private fun stopPlaybackInternal() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                }
                mp.stop()
                mp.reset()
                isPlaying = false
                currentSongPath = null
                
                Log.d("MusicService", "停止播放")
                
                // 更新通知
                stopForeground(true)
                
                // 更新小组件
                updateWidget()
                
                // 保存状态
                savePlaybackState()
            }
        } catch (e: Exception) {
            Log.e("MusicService", "停止播放失败: ${e.message}")
        }
    }


    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "音乐播放服务"
            val descriptionText = "音乐播放前台服务通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                
                // 安卓8.0及以上需要额外设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAllowBubbles(false)
                    setBypassDnd(false)
                }
                
                // 安卓14及以上需要额外设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setAllowBubbles(false)
                }
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            // ColorOS特殊处理
            try {
                // ColorOS 15+ 特殊处理
                val colorOsChannel = NotificationChannel("${NOTIFICATION_CHANNEL_ID}_coloros", 
                    "音乐播放(ColorOS)", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "ColorOS音乐播放服务"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setAllowBubbles(false)
                        setBypassDnd(false)
                    }
                }
                notificationManager.createNotificationChannel(colorOsChannel)
                
                // ColorOS 15需要额外的通知渠道
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val colorOs15Channel = NotificationChannel("${NOTIFICATION_CHANNEL_ID}_coloros15", 
                        "音乐播放(ColorOS 15)", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "ColorOS 15音乐播放服务"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setAllowBubbles(false)
                            setBypassDnd(false)
                        }
                    }
                    notificationManager.createNotificationChannel(colorOs15Channel)
                }
            } catch (e: Exception) {
                Log.d("MusicService", "ColorOS通知渠道创建失败: ${e.message}")
            }
        }
    }
    
    private fun getAlbumArt(songPath: String): Bitmap? {
        try {
            val retriever = MediaMetadataRetriever()
            try {
                val file = java.io.File(songPath)
                if (!file.exists() || !file.canRead()) {
                    return null
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val fileDescriptor = java.io.FileInputStream(file).fd
                        retriever.setDataSource(fileDescriptor)
                    } catch (e: Exception) {
                        retriever.setDataSource(songPath)
                    }
                } else {
                    retriever.setDataSource(songPath)
                }
                
                val art = retriever.embeddedPicture
                return if (art != null && art.isNotEmpty()) {
                    BitmapFactory.decodeByteArray(art, 0, art.size)
                } else {
                    null
                }
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // 忽略释放异常
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    // 新增增强型媒体通知管理器
    private lateinit var enhancedMediaNotificationManager: EnhancedMediaNotificationManager
    private lateinit var legacyMediaNotificationManager: LegacyMediaNotificationManager
    
    private fun createDefaultForegroundNotification() {
        // 立即创建默认通知，确保前台服务启动成功
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!::enhancedMediaNotificationManager.isInitialized) {
                enhancedMediaNotificationManager = EnhancedMediaNotificationManager(this)
            }
            enhancedMediaNotificationManager.createMediaNotification(
                "音乐播放器", "准备中...", "", false, null, 0, 0
            )
        } else {
            if (!::legacyMediaNotificationManager.isInitialized) {
                legacyMediaNotificationManager = LegacyMediaNotificationManager(this)
            }
            legacyMediaNotificationManager.createLegacyMediaNotification(
                "音乐播放器", "准备中...", "", false, null
            )
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationWithDelay() {
        // 延迟100ms后更新为实际内容，确保前台服务已启动
        android.os.Handler(mainLooper).postDelayed({
            startForegroundNotification()
        }, 100)
    }

    private fun startForegroundNotification() {
        // 检测是否为ColorOS 15
        val isColorOS15 = ColorOSHelper.isColorOS15(this)
        
        if (isColorOS15 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // ColorOS 15系统：使用标准增强型通知，但保留控制中心媒体会话
            if (!::enhancedMediaNotificationManager.isInitialized) {
                enhancedMediaNotificationManager = EnhancedMediaNotificationManager(this)
            }
            
            if (songList.isEmpty() || currentSongIndex >= songList.size) {
                // 即使没有歌曲，也要创建基本通知
                val basicNotification = enhancedMediaNotificationManager.createMediaNotification(
                    "音乐播放器", "暂无歌曲", "", isPlaying, null, 0, 0
                )
                startForeground(NOTIFICATION_ID, basicNotification)
                return
            }
            
            val song = songList[currentSongIndex]
            val albumArt = getAlbumArt(song.path)
            
            enhancedMediaNotificationManager.updatePlaybackState(isPlaying, getCurrentPosition().toLong(), getDuration().toLong())
            enhancedMediaNotificationManager.updateMediaMetadata(
                song.title,
                song.artist,
                song.album ?: "未知专辑",
                getDuration().toLong(),
                albumArt
            )
            
            // 更新ColorOS 15系统控制中心媒体会话（保留此功能）
            colorOS15MediaSessionManager?.updatePlaybackState(
                isPlaying,
                getCurrentPosition().toLong(),
                getDuration().toLong()
            )
            colorOS15MediaSessionManager?.updateMediaMetadata(
                song.title,
                song.artist,
                song.album ?: "未知专辑",
                getDuration().toLong(),
                albumArt
            )
            
            val notification = enhancedMediaNotificationManager.createMediaNotification(
                song.title, song.artist, song.album ?: "未知专辑", isPlaying, albumArt, getDuration().toLong(), getCurrentPosition().toLong()
            )
            startForeground(NOTIFICATION_ID, notification)
            
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 标准增强型通知
            if (!::enhancedMediaNotificationManager.isInitialized) {
                enhancedMediaNotificationManager = EnhancedMediaNotificationManager(this)
            }
            
            if (songList.isEmpty() || currentSongIndex >= songList.size) {
                // 即使没有歌曲，也要创建基本通知
                val basicNotification = enhancedMediaNotificationManager.createMediaNotification(
                    "音乐播放器", "暂无歌曲", "", isPlaying, null, 0, 0
                )
                startForeground(NOTIFICATION_ID, basicNotification)
                return
            }
            
            val song = songList[currentSongIndex]
            val albumArt = getAlbumArt(song.path)
            
            enhancedMediaNotificationManager.updatePlaybackState(isPlaying, getCurrentPosition().toLong(), getDuration().toLong())
            enhancedMediaNotificationManager.updateMediaMetadata(
                song.title,
                song.artist,
                song.album ?: "未知专辑",
                getDuration().toLong(),
                albumArt
            )
            
            if (::carMediaSessionManager.isInitialized && songList.isNotEmpty()) {
                carMediaSessionManager.updatePlaybackState(isPlaying, song.title, song.artist, getCurrentPosition().toLong(), getDuration().toLong())
            }
            
            val notification = enhancedMediaNotificationManager.createMediaNotification(
                song.title, song.artist, song.album ?: "未知专辑", isPlaying, albumArt, getDuration().toLong(), getCurrentPosition().toLong()
            )
            startForeground(NOTIFICATION_ID, notification)
        } else {
            // 传统通知（Android 4.4及以下）
            if (!::legacyMediaNotificationManager.isInitialized) {
                legacyMediaNotificationManager = LegacyMediaNotificationManager(this)
            }
            
            if (songList.isEmpty() || currentSongIndex >= songList.size) {
                // 即使没有歌曲，也要创建基本通知
                val basicNotification = legacyMediaNotificationManager.createLegacyMediaNotification(
                    "音乐播放器", "暂无歌曲", "", isPlaying, null
                )
                startForeground(NOTIFICATION_ID, basicNotification)
                return
            }
            
            val song = songList[currentSongIndex]
            val albumArt = getAlbumArt(song.path)
            
            val notification = legacyMediaNotificationManager.createLegacyMediaNotification(
                song.title,
                song.artist,
                song.album ?: "未知专辑",
                isPlaying,
                albumArt
            )
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun updateNotification() {
        if (songList.isNotEmpty() && currentSongIndex < songList.size) {
            val song = songList[currentSongIndex]
            val albumArt = getAlbumArt(song.path)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 使用增强型通知管理器更新通知
                enhancedMediaNotificationManager.updatePlaybackState(isPlaying, getCurrentPosition().toLong(), getDuration().toLong())
                enhancedMediaNotificationManager.updateMediaMetadata(
                    song.title,
                    song.artist,
                    song.album ?: "未知专辑",
                    getDuration().toLong(),
                    albumArt
                )
            } else {
                // 使用传统通知管理器更新通知
                if (::legacyMediaNotificationManager.isInitialized) {
                    val notification = legacyMediaNotificationManager.createLegacyMediaNotification(
                        song.title,
                        song.artist,
                        song.album ?: "未知专辑",
                        isPlaying,
                        albumArt
                    )
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

            // 更新车机媒体会话状态
            if (::carMediaSessionManager.isInitialized && songList.isNotEmpty()) {
                val song = songList[currentSongIndex]
                carMediaSessionManager.updatePlaybackState(isPlaying, song.title, song.artist, getCurrentPosition().toLong(), getDuration().toLong())
            }
            
            // 更新ColorOS 15系统控制中心媒体会话
            colorOS15MediaSessionManager?.updatePlaybackState(
                isPlaying,
                getCurrentPosition().toLong(),
                getDuration().toLong()
            )
            colorOS15MediaSessionManager?.updateMediaMetadata(
                song.title,
                song.artist,
                song.album ?: "未知专辑",
                getDuration().toLong(),
                albumArt
            )
            
            // 更新vivo MIUI系统控制中心媒体会话
            vivoMIUIMediaSessionManager?.updatePlaybackState(
                isPlaying,
                getCurrentPosition().toLong(),
                getDuration().toLong()
            )
            vivoMIUIMediaSessionManager?.updateMediaMetadata(
                song.title,
                song.artist,
                song.album ?: "未知专辑",
                getDuration().toLong(),
                albumArt
            )
        }
    }
    
    private fun updateWidget() {
        // 先保存播放状态到SharedPreferences
        savePlaybackState()
        
        val currentSong = songList.getOrNull(currentSongIndex)
        
        // 创建统一的广播数据 - 合并所有组件更新
        val unifiedBroadcastData = Intent("com.maka.xiaoxia.UPDATE_ALL_COMPONENTS").apply {
            // 核心播放信息
            putExtra("is_playing", isPlaying)
            putExtra("current_song_index", currentSongIndex)
            putExtra("song_count", songList.size)
            
            // 歌曲详细信息
            putExtra("current_title", currentSong?.title ?: "未知歌曲")
            putExtra("current_artist", currentSong?.artist ?: "未知艺术家")
            putExtra("current_album", currentSong?.album ?: "")
            putExtra("current_path", currentSong?.path ?: "")
            putExtra("current_album_id", currentSong?.albumId ?: 0L)
            putExtra("current_duration", currentSong?.duration ?: 0L)
            putExtra("current_lyrics", currentSong?.lyrics ?: "")
            
            // 封面信息
            putExtra("cover_path", currentSong?.path ?: "")
            putExtra("cover_album_id", currentSong?.albumId ?: 0L)
            
            // 广播标识和时间戳
            putExtra("update_timestamp", System.currentTimeMillis())
            putExtra("broadcast_type", "unified_update")
        }
        
        // 发送统一广播给所有组件
        sendBroadcast(unifiedBroadcastData)
        Log.d("MusicService", "已发送统一广播，歌曲: ${currentSong?.title}, 索引: $currentSongIndex")
        
        // 发送系统小组件更新广播（兼容旧版本）
        val widgetUpdateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(this@MusicService, MusicWidgetProvider::class.java)
        }
        sendBroadcast(widgetUpdateIntent)
        
        // 发送车机专用广播（兼容旧版本）
        val carWidgetUpdateIntent = Intent("com.maka.xiaoxia.action.UPDATE_CAR_WIDGET")
        sendBroadcast(carWidgetUpdateIntent)
        
        // 发送车机低内存版广播（兼容旧版本）
        val carWidgetLowMemoryUpdateIntent = Intent("com.maka.xiaoxia.action.UPDATE_CAR_WIDGET_LOW_MEMORY")
        sendBroadcast(carWidgetLowMemoryUpdateIntent)
    }
    
    // 添加服务绑定功能
    private val binder = MusicBinder()
    
    inner class MusicBinder : android.os.Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    // 公共接口供MainActivity调用
    fun playSong(index: Int) {
        if (songList.isEmpty()) {
            Log.e("MusicService", "无法播放：歌曲列表为空")
            return
        }
        
        if (index < 0 || index >= songList.size) {
            Log.e("MusicService", "播放歌曲索引无效: $index, 列表大小: ${songList.size}")
            return
        }
        
        currentSongIndex = index
        playMusicInternal(index)
    }
    
    // 添加设置歌曲列表的方法
    fun setSongList(newSongList: List<Song>) {
        songList.clear()
        songList.addAll(newSongList)
        Log.d("MusicService", "更新歌曲列表，共 ${songList.size} 首歌曲")
        
        // 如果当前索引超出范围，重置为0
        if (currentSongIndex >= songList.size) {
            currentSongIndex = 0
        }
    }
    
    fun togglePlayPause() {
        if (isPlaying) {
            pauseMusic()
        } else {
            if (currentSongPath == null || currentSongIndex >= songList.size) {
                playMusicInternal(0)
            } else {
                resumeMusic()
            }
        }
    }
    
    fun playNext() {
        if (songList.isNotEmpty()) {
            currentSongIndex = (currentSongIndex + 1) % songList.size
            playMusicInternal(currentSongIndex)
        }
    }
    
    fun playPrevious() {
        if (songList.isNotEmpty()) {
            currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
            playMusicInternal(currentSongIndex)
        }
    }
    
    fun stopService() {
        mediaPlayer?.stop()
        isPlaying = false
        updateWidget()
    }
    
    fun getCurrentSong(): Song? = 
        if (songList.isNotEmpty() && currentSongIndex < songList.size) songList[currentSongIndex] else null
    
    fun getSongList(): List<Song> = songList
    

    
    fun getCurrentSongIndex(): Int = currentSongIndex
    
    fun setCurrentSongIndex(index: Int) {
        if (index in 0 until songList.size) {
            currentSongIndex = index
        }
    }
    
    fun setPlayMode(mode: Int) {
        setPlayModeInternal(mode)
    }
    
    private fun setPlayModeInternal(mode: Int) {
        playMode = mode.coerceIn(0, 3)
        sharedPref.edit().putInt("play_mode", playMode).apply()
        
        // 如果是乱序模式，重新打乱列表
        if (playMode == 3) { // SHUFFLE
            applyShuffleMode()
        } else {
            // 恢复原始顺序
            restoreOriginalOrder()
        }
    }
    
    private fun applyPlayMode() {
        when (playMode) {
            3 -> applyShuffleMode() // SHUFFLE
            else -> restoreOriginalOrder()
        }
    }
    
    private fun applyShuffleMode() {
        if (originalSongList.isNotEmpty()) {
            val currentSong = if (currentSongIndex < originalSongList.size) {
                originalSongList[currentSongIndex]
            } else null
            
            songList.clear()
            songList.addAll(originalSongList.shuffled())
            
            // 保持当前歌曲在相同位置
            currentSong?.let { song ->
                val newIndex = songList.indexOfFirst { it.path == song.path }
                if (newIndex != -1) {
                    currentSongIndex = newIndex
                }
            }
        }
    }
    
    private fun restoreOriginalOrder() {
        if (originalSongList.isNotEmpty()) {
            val currentSong = if (currentSongIndex < songList.size) {
                songList[currentSongIndex]
            } else null
            
            songList.clear()
            songList.addAll(originalSongList)
            
            // 恢复当前歌曲索引
            currentSong?.let { song ->
                val newIndex = songList.indexOfFirst { it.path == song.path }
                if (newIndex != -1) {
                    currentSongIndex = newIndex
                }
            }
        }
    }
    
    private fun onSongCompleted() {
        Log.d("MusicService", "歌曲播放完成，当前播放模式: $playMode")
        
        // 根据播放模式处理下一首歌曲
        when (playMode) {
            0 -> { // REPEAT_ONE: 单曲循环 - 重新播放当前歌曲
                Log.d("MusicService", "单曲循环，重新播放当前歌曲")
                playMusicInternal(currentSongIndex)
            }
            1 -> { // PLAY_ORDER: 顺序播放 - 播放下一首，如果到达末尾则停止
                if (currentSongIndex < songList.size - 1) {
                    currentSongIndex++
                    playMusicInternal(currentSongIndex)
                } else {
                    Log.d("MusicService", "到达播放列表末尾，停止播放")
                    isPlaying = false
                    updateNotification()
                    updateWidget()
                    savePlaybackState()
                }
            }
            2 -> { // REPEAT_ALL: 列表循环 - 播放下一首，循环到开头
                currentSongIndex = (currentSongIndex + 1) % songList.size
                playMusicInternal(currentSongIndex)
            }
            3 -> { // SHUFFLE: 随机播放 - 随机选择下一首歌曲
                if (songList.size > 1) {
                    val oldIndex = currentSongIndex
                    do {
                        currentSongIndex = (0 until songList.size).random()
                    } while (currentSongIndex == oldIndex)
                }
                playMusicInternal(currentSongIndex)
            }
        }
    }
}