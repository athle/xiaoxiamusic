package com.maka.xiaoxia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * ColorOS 15控制中心媒体播放集成管理器
 * 专门用于接入ColorOS 15的系统级媒体播放控件（控制中心）
 */
class ColorOS15MediaSessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ColorOS15MediaSession"
    }
    
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var isActive = false
    
    /**
     * 创建并配置MediaSession
     */
    fun createMediaSession() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 创建MediaSessionCompat
            mediaSession = MediaSessionCompat(context, "ColorOS15MediaSession").apply {
                // 设置支持的标志 - 适配Android 15/16和ColorOS 15
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                    MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
                )
                
                // 设置会话令牌，确保系统能够发现
                setSessionActivity(null)
                
                // 确保会话优先级最高
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setSessionActivity(null)
                }
                
                // 设置回调
                setCallback(ColorOS15MediaSessionCallback())
                
                // 设置初始播放状态
                val playbackState = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .build()
                
                setPlaybackState(playbackState)
                
                // 激活会话 - 强制激活确保系统控制中心可见
                isActive = true
                
                // 立即设置播放状态为暂停，避免系统控制中心显示异常
                val initialState = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                    .build()
                setPlaybackState(initialState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建ColorOS 15 MediaSession失败: ${e.message}")
        }
    }
    
    /**
     * 释放MediaSession
     */
    fun releaseMediaSession() {
        try {
            mediaSession?.let { session ->
                session.isActive = false
                session.release()
            }
            mediaSession = null
            isActive = false
        } catch (e: Exception) {
            Log.e(TAG, "释放ColorOS 15 MediaSession失败: ${e.message}")
        }
    }
    
    /**
     * 更新播放状态
     */
    fun updatePlaybackState(
        isPlaying: Boolean,
        position: Long,
        duration: Long
    ) {
        mediaSession?.let { session ->
            try {
                val playbackState = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(
                        if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        position,
                        1.0f
                    )
                    .build()
                
                session.setPlaybackState(playbackState)
                
                if (!session.isActive) {
                    session.isActive = true
                } else {
                    // 会话已经激活，无需额外操作
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新ColorOS 15播放状态失败: ${e.message}")
            }
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
            try {
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1)
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
                
                albumArt?.let { bitmap ->
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                }
                
                session.setMetadata(metadataBuilder.build())
                
                if (!session.isActive) {
                    session.isActive = true
                } else {
                    // 会话已经激活，无需额外操作
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新ColorOS 15元数据失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取MediaSession令牌
     */
    fun getSessionToken(): MediaSessionCompat.Token? {
        return mediaSession?.sessionToken
    }
    
    /**
     * 检查MediaSession是否激活
     */
    fun isSessionActive(): Boolean {
        return mediaSession?.isActive ?: false
    }
    
    /**
     * ColorOS 15媒体会话回调
     */
    private inner class ColorOS15MediaSessionCallback : MediaSessionCompat.Callback() {
        
        override fun onPlay() {
            super.onPlay()

            // 通知MusicService处理播放
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            context.startService(intent)
        }
        
        override fun onPause() {
            super.onPause()

            // 通知MusicService处理暂停
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            context.startService(intent)
        }
        
        override fun onSkipToNext() {
            super.onSkipToNext()

            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            context.startService(intent)
        }
        
        override fun onSkipToPrevious() {
            super.onSkipToPrevious()

            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            context.startService(intent)
        }
        
        override fun onStop() {
            super.onStop()

            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            context.startService(intent)
        }
        
        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)

            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_SEEK_TO
                putExtra("position", pos)
            }
            context.startService(intent)
        }
    }
}