package com.maka.xiaoxia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * Vivo MIUI系统控制中心媒体播放集成管理器
 * 专门用于适配vivo MIUI系统的媒体控制中心
 */
class VivoMIUIMediaSessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VivoMIUIMediaSession"
    }
    
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    
    /**
     * 创建并配置MediaSession - 适配vivo MIUI
     */
    fun createMediaSession() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            mediaSession = MediaSessionCompat(context, "VivoMIUIMediaSession").apply {
                // 设置支持的标志 - vivo MIUI需要更多标志
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                    MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
                )
                
                // 设置回调
                setCallback(VivoMIUIMediaSessionCallback())
                
                // 设置初始播放状态 - vivo MIUI需要明确的暂停状态
                val playbackState = PlaybackStateCompat.Builder()
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
                
                setPlaybackState(playbackState)
                
                // 强制激活会话
                isActive = true
                
                Log.d(TAG, "Vivo MIUI MediaSession已创建并激活")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "创建Vivo MIUI MediaSession失败: ${e.message}")
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
                Log.d(TAG, "Vivo MIUI MediaSession已释放")
            }
            mediaSession = null
        } catch (e: Exception) {
            Log.e(TAG, "释放Vivo MIUI MediaSession失败: ${e.message}")
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
                
                // 确保会话始终激活
                if (!session.isActive) {
                    session.isActive = true
                }
                
                Log.d(TAG, "Vivo MIUI播放状态更新: ${if (isPlaying) "播放" else "暂停"}")
                
            } catch (e: Exception) {
                Log.e(TAG, "更新Vivo MIUI播放状态失败: ${e.message}")
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
                val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
                
                albumArt?.let { bitmap ->
                    metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                }
                
                session.setMetadata(metadataBuilder.build())
                
                // 确保会话始终激活
                if (!session.isActive) {
                    session.isActive = true
                }
                
                Log.d(TAG, "Vivo MIUI元数据更新: $title - $artist")
                
            } catch (e: Exception) {
                Log.e(TAG, "更新Vivo MIUI元数据失败: ${e.message}")
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
     * 检查是否为vivo MIUI系统
     */
    fun isVivoMIUISystem(): Boolean {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            val brand = android.os.Build.BRAND.lowercase()
            val fingerprint = android.os.Build.FINGERPRINT.lowercase()
            
            // 检查是否是vivo设备
            val isVivo = manufacturer.contains("vivo") || brand.contains("vivo")
            
            // 检查MIUI特征
            val isMIUI = fingerprint.contains("miui")
            
            isVivo || isMIUI
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Vivo MIUI媒体会话回调
     */
    private inner class VivoMIUIMediaSessionCallback : MediaSessionCompat.Callback() {
        
        override fun onPlay() {
            super.onPlay()
            Log.d(TAG, "Vivo MIUI控制中心: 播放命令")
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            context.startService(intent)
        }
        
        override fun onPause() {
            super.onPause()
            Log.d(TAG, "Vivo MIUI控制中心: 暂停命令")
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            context.startService(intent)
        }
        
        override fun onSkipToNext() {
            super.onSkipToNext()
            Log.d(TAG, "Vivo MIUI控制中心: 下一首命令")
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            context.startService(intent)
        }
        
        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            Log.d(TAG, "Vivo MIUI控制中心: 上一首命令")
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            context.startService(intent)
        }
        
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "Vivo MIUI控制中心: 停止命令")
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            context.startService(intent)
        }
        
        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            Log.d(TAG, "Vivo MIUI控制中心: 跳转命令 - $pos")
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_SEEK_TO
                putExtra("position", pos)
            }
            context.startService(intent)
        }
    }
}