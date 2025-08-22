package com.maka.xiaoxia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * 统一媒体会话管理器 - 适配所有Android系统版本
 * 基于标准MediaSessionCompat实现，无需为定制系统做特殊适配
 */
class UnifiedMediaSessionManager(private val context: Context) {
    
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    
    companion object {
        private const val TAG = "UnifiedMediaSession"
    }
    
    /**
     * 创建统一的MediaSession
     */
    fun createMediaSession() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            mediaSession = MediaSessionCompat(context, "UnifiedMediaSession").apply {
                // 设置标准标志 - 兼容所有系统
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                
                // 设置回调
                setCallback(UnifiedMediaSessionCallback())
                
                // 设置标准播放状态 - 支持所有必要操作
                val playbackState = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                    .build()
                
                setPlaybackState(playbackState)
                
                // 激活会话
                isActive = true
            }
            
            Log.d(TAG, "统一媒体会话创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建统一媒体会话失败: ${e.message}")
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
            Log.d(TAG, "统一媒体会话已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放统一媒体会话失败: ${e.message}")
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
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
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
            } catch (e: Exception) {
                Log.e(TAG, "更新播放状态失败: ${e.message}")
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
            } catch (e: Exception) {
                Log.e(TAG, "更新媒体元数据失败: ${e.message}")
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
     * 统一媒体会话回调
     */
    private inner class UnifiedMediaSessionCallback : MediaSessionCompat.Callback() {
        
        override fun onPlay() {
            super.onPlay()
            sendActionToService(MusicService.ACTION_PLAY_PAUSE)
        }
        
        override fun onPause() {
            super.onPause()
            sendActionToService(MusicService.ACTION_PLAY_PAUSE)
        }
        
        override fun onSkipToNext() {
            super.onSkipToNext()
            sendActionToService(MusicService.ACTION_NEXT)
        }
        
        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            sendActionToService(MusicService.ACTION_PREVIOUS)
        }
        
        override fun onStop() {
            super.onStop()
            sendActionToService(MusicService.ACTION_STOP)
        }
        
        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_SEEK_TO
                putExtra("position", pos)
            }
            context.startService(intent)
        }
        
        private fun sendActionToService(action: String) {
            val intent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }
    }
}