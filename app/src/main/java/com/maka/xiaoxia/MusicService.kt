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
    
    companion object {
        const val ACTION_PLAY_PAUSE = "com.maka.xiaoxia.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.maka.xiaoxia.action.NEXT"
        const val ACTION_PREVIOUS = "com.maka.xiaoxia.action.PREVIOUS"
        const val ACTION_STOP = "com.maka.xiaoxia.action.STOP"
        const val ACTION_SEEK_TO = "com.maka.xiaoxia.action.SEEK_TO"
        const val ACTION_UPDATE_WIDGET = "com.maka.xiaoxia.action.UPDATE_WIDGET"
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
        
        // 延迟加载歌曲列表，避免阻塞启动
        Thread {
            loadSongList()
            // 延迟恢复播放状态，减少启动时的CPU负载
            restorePlaybackState()
        }.start()
        
        // 注册耳机和媒体按钮接收器
        registerMediaButtonReceiver()
        
        // 延迟初始化媒体会话管理器
        Thread {
            // 初始化车机媒体会话管理器
            carMediaSessionManager = CarMediaSessionManager(this)
            carMediaSessionManager.createMediaSession()
            
            // 初始化ColorOS 15媒体会话管理器（用于系统控制中心）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                colorOS15MediaSessionManager = ColorOS15MediaSessionManager(this)
                colorOS15MediaSessionManager?.createMediaSession()
            }
        }.start()
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
        
        Log.d("MusicService", "加载了 ${songList.size} 首歌曲")
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
        
        currentSongIndex = (currentSongIndex + 1) % songList.size
        playMusicInternal(currentSongIndex)
    }

    private fun playPreviousInternal() {
        if (songList.isEmpty()) return
        
        currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
        playMusicInternal(currentSongIndex)
    }

    private fun stopPlaybackInternal() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            isPlaying = false
            currentSongPath = null
            
            Log.d("MusicService", "停止播放")
            
            // 更新通知
            stopForeground(true)
            
            // 更新小组件
            updateWidget()
            
            // 保存状态
            savePlaybackState()
            
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
        }
    }
    
    private fun updateWidget() {
        // 先保存播放状态到SharedPreferences
        savePlaybackState()
        
        val currentSong = songList.getOrNull(currentSongIndex)
        
        // 创建广播数据 - 使用直接数据传递，避免SharedPreferences延迟
        val broadcastData = Intent(ACTION_UPDATE_WIDGET).apply {
            putExtra("is_playing", isPlaying)
            putExtra("current_title", currentSong?.title ?: "未知歌曲")
            putExtra("current_artist", currentSong?.artist ?: "未知艺术家")
            putExtra("current_path", currentSong?.path ?: "")
            putExtra("current_album_id", currentSong?.albumId ?: 0L)
            putExtra("current_album", currentSong?.album ?: "")
            putExtra("current_lyrics", currentSong?.lyrics ?: "")
            putExtra("current_song_index", currentSongIndex)
            
            // 直接传递封面数据，避免从SharedPreferences读取的延迟
            putExtra("cover_path", currentSong?.path ?: "")
            putExtra("cover_album_id", currentSong?.albumId ?: 0L)
            
            // 添加时间戳确保广播的唯一性
            putExtra("update_timestamp", System.currentTimeMillis())
        }
        
        // 立即发送广播给所有接收者（包括MainActivity和小组件）
        sendBroadcast(broadcastData)
        Log.d("MusicService", "已发送UPDATE_WIDGET广播，当前歌曲: ${currentSong?.title}, 索引: $currentSongIndex")
        
        // 立即发送系统广播，确保小组件及时更新
        val widgetUpdateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(this@MusicService, MusicWidgetProvider::class.java)
        }
        sendBroadcast(widgetUpdateIntent)
        Log.d("MusicService", "立即发送系统小组件更新广播")
        
        // 车机专用小组件更新
        val carWidgetUpdateIntent = Intent("com.maka.xiaoxia.action.UPDATE_CAR_WIDGET")
        sendBroadcast(carWidgetUpdateIntent)
        Log.d("MusicService", "已发送车机专用小组件更新广播")
        
        // 发送一个专门的UI更新广播给MainActivity
        val uiUpdateIntent = Intent("com.maka.xiaoxia.UPDATE_UI").apply {
            putExtra("current_song_index", currentSongIndex)
            putExtra("is_playing", isPlaying)
            putExtra("current_title", currentSong?.title ?: "未知歌曲")
            putExtra("current_artist", currentSong?.artist ?: "未知艺术家")
            putExtra("current_album", currentSong?.album ?: "未知专辑")
            putExtra("current_path", currentSong?.path ?: "")
            putExtra("current_album_id", currentSong?.albumId ?: 0L)
            putExtra("current_duration", currentSong?.duration ?: 0L)
            putExtra("song_count", songList.size)
        }
        sendBroadcast(uiUpdateIntent)
        Log.d("MusicService", "已发送UPDATE_UI广播给MainActivity")
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
}