package com.maka.xiaoxia

import android.app.Service
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
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "服务创建")
        
        sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
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
        sharedPref.edit().apply {
            putBoolean("is_playing", isPlaying)
            putInt("current_song_index", currentSongIndex)
            putString("current_song_path", currentSongPath)
            putInt("song_count", songList.size)
            
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
            apply()
        }
    }

    private fun restorePlaybackState() {
        isPlaying = sharedPref.getBoolean("is_playing", false)
        currentSongIndex = sharedPref.getInt("current_song_index", 0)
        currentSongPath = sharedPref.getString("current_song_path", null)
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
            
            // 通知主界面更新
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
    }

    private fun resumeMusic() {
        mediaPlayer?.start()
        isPlaying = true
        Log.d("MusicService", "继续播放")
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

    private fun updateWidget() {
        // 先更新SharedPreferences，确保小组件能获取最新信息
        savePlaybackState()
        
        // 记录更新时间戳，防止系统onUpdate覆盖
        sharedPref.edit().putLong("last_widget_update_time", System.currentTimeMillis()).commit()
        
        // 强制确保SharedPreferences已写入完成
        sharedPref.edit().commit()
        
        val currentSong = songList.getOrNull(currentSongIndex)
        
        // 创建广播数据
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
        }
        
        // 发送广播给所有接收者（包括MainActivity和小组件）
        sendBroadcast(broadcastData)
        Log.d("MusicService", "已发送UPDATE_WIDGET广播，当前歌曲: ${currentSong?.title}, 索引: $currentSongIndex")
        
        // 延迟发送系统广播，确保数据已完全写入，但避免与系统onUpdate冲突
        android.os.Handler().postDelayed({
            val widgetUpdateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(this@MusicService, MusicWidgetProvider::class.java)
                putExtra("skip_if_recent_update", true)  // 标记这是延迟更新
            }
            sendBroadcast(widgetUpdateIntent)
            Log.d("MusicService", "延迟发送系统小组件更新广播")
        }, 200)
        
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