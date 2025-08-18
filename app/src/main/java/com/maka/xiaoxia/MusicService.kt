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
import android.os.IBinder
import android.util.Log
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import java.io.IOException

class MusicService : Service() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongPath: String? = null
    private var isPlaying = false
    private var songList: MutableList<Song> = mutableListOf()
    private var currentSongIndex = 0
    private lateinit var sharedPref: SharedPreferences
    
    companion object {
        const val ACTION_PLAY_PAUSE = "com.maka.xiaoxia.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.maka.xiaoxia.action.NEXT"
        const val ACTION_PREVIOUS = "com.maka.xiaoxia.action.PREVIOUS"
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
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 初始化MediaPlayer
        mediaPlayer = MediaPlayer()
        
        // 加载歌曲列表
        loadSongList()
        
        // 恢复播放状态
        restorePlaybackState()
        
        // 广播接收器已移除，通过onStartCommand处理所有操作
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "服务启动: ${intent?.action}")
        
        // 启动前台服务
        startForegroundNotification()
        
        // 处理启动意图中的动作
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> togglePlayPauseInternal()
                ACTION_NEXT -> playNextInternal()
                ACTION_PREVIOUS -> playPreviousInternal()
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MusicService", "服务销毁")
        savePlaybackState()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun registerBroadcastReceiver() {
        // 服务内部广播接收器已移除，通过onStartCommand处理所有操作
    }

    private fun loadSongList() {
        songList.clear()
        
        // 从SharedPreferences加载歌曲列表
        val songCount = sharedPref.getInt("song_count", 0)
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
        
        // 恢复歌曲列表
        val songCount = sharedPref.getInt("song_count", 0)
        if (songCount > 0) {
            songList.clear()
            for (i in 0 until songCount) {
                val id = sharedPref.getLong("song_${i}_id", 0)
                val title = sharedPref.getString("song_${i}_title", "未知歌曲") ?: "未知歌曲"
                val artist = sharedPref.getString("song_${i}_artist", "未知艺术家") ?: "未知艺术家"
                val album = sharedPref.getString("song_${i}_album", "未知专辑") ?: "未知专辑"
                val duration = sharedPref.getLong("song_${i}_duration", 0)
                val path = sharedPref.getString("song_${i}_path", "") ?: ""
                val albumId = sharedPref.getLong("song_${i}_albumId", 0)
                val lyrics = sharedPref.getString("song_${i}_lyrics", "") ?: ""
                
                if (path.isNotEmpty()) {
                    songList.add(Song(id, title, artist, album, duration, path, albumId, lyrics))
                }
            }
        }
        
        // 如果有保存的歌曲列表，设置当前歌曲
        if (songList.isNotEmpty() && currentSongIndex < songList.size) {
            val song = songList[currentSongIndex]
            currentSongPath = song.path
            
            // 尝试加载并seek到保存的位置
            try {
                mediaPlayer?.reset()
                mediaPlayer?.setDataSource(song.path)
                mediaPlayer?.prepare()
                if (savedPosition > 0) {
                    mediaPlayer?.seekTo(savedPosition)
                }
                
                if (isPlaying) {
                    mediaPlayer?.start()
                    Log.d("MusicService", "恢复播放: ${song.title} 从位置: ${formatTime(savedPosition)}")
                } else {
                    Log.d("MusicService", "准备播放: ${song.title} 从位置: ${formatTime(savedPosition)}")
                }
                
                // 更新通知和小部件
                updateNotification()
                updateWidget()
                
            } catch (e: Exception) {
                Log.e("MusicService", "恢复播放状态失败: ${e.message}")
                isPlaying = false
            }
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
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    
    // 添加进度控制方法
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
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


    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "音乐播放"
            val descriptionText = "音乐播放控制"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val prevIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getService(
            this, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val songTitle = if (songList.isNotEmpty() && currentSongIndex < songList.size) {
            songList[currentSongIndex].title
        } else {
            "未选择歌曲"
        }
        
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
            .setContentTitle("正在播放")
            .setContentText(songTitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_play, "播放/暂停", playPausePendingIntent)
            .addAction(R.drawable.ic_next, "下一首", nextPendingIntent)
            .addAction(R.drawable.ic_previous, "上一首", prevPendingIntent)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun updateNotification() {
        if (songList.isNotEmpty() && currentSongIndex < songList.size) {
            val song = songList[currentSongIndex]
            
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val playPauseIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getService(
                this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val nextIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getService(
                this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val prevIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_PREVIOUS
            }
            val prevPendingIntent = PendingIntent.getService(
                this, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_play, "播放/暂停", playPausePendingIntent)
                .addAction(R.drawable.ic_next, "下一首", nextPendingIntent)
                .addAction(R.drawable.ic_previous, "上一首", prevPendingIntent)
                .build()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
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
        if (index in 0 until songList.size) {
            currentSongIndex = index
            playMusicInternal(index)
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
    
    fun setSongList(newList: List<Song>) {
        songList.clear()
        songList.addAll(newList)
        savePlaybackState()
    }
    
    fun getCurrentSongIndex(): Int = currentSongIndex
    
    fun setCurrentSongIndex(index: Int) {
        if (index in 0 until songList.size) {
            currentSongIndex = index
        }
    }
}