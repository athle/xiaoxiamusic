package com.maka.xiaoxia

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.media.RemoteControlClient
import android.content.ComponentName

/**
 * 领克02 CS11车机媒体会话管理器 - 安卓4.4兼容版
 * 使用RemoteControlClient处理车机实体按键和媒体控制
 */
class CarMediaSessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CarMediaSessionManager"
    }
    
    private var remoteControlClient: RemoteControlClient? = null
    private var audioManager: AudioManager? = null
    private var isRegistered = false
    
    fun createMediaSession() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 创建媒体按钮接收器组件
            val mediaButtonReceiver = ComponentName(context, CarMediaButtonReceiver::class.java)
            
            // 注册媒体按钮事件接收器
            audioManager?.registerMediaButtonEventReceiver(mediaButtonReceiver)
            
            // 创建RemoteControlClient
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            mediaButtonIntent.component = mediaButtonReceiver
            
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 0, mediaButtonIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            remoteControlClient = RemoteControlClient(pendingIntent).apply {
                // 设置支持的传输控制
                setTransportControlFlags(
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY or
                    RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or
                    RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                    RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS or
                    RemoteControlClient.FLAG_KEY_MEDIA_STOP
                )
            }
            
            isRegistered = true
            Log.d(TAG, "媒体会话已创建并注册")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建媒体会话失败: ${e.message}")
        }
    }
    
    fun releaseMediaSession() {
        try {
            if (isRegistered) {
                val mediaButtonReceiver = ComponentName(context, CarMediaButtonReceiver::class.java)
                audioManager?.unregisterMediaButtonEventReceiver(mediaButtonReceiver)
                
                remoteControlClient?.let {
                    // 清理资源
                    it.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED)
                }
                
                remoteControlClient = null
                isRegistered = false
                Log.d(TAG, "媒体会话已释放")
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放媒体会话失败: ${e.message}")
        }
    }
    
    fun updatePlaybackState(isPlaying: Boolean, title: String, artist: String, position: Long, duration: Long) {
        remoteControlClient?.let { client ->
            try {
                val playbackState = if (isPlaying) {
                    RemoteControlClient.PLAYSTATE_PLAYING
                } else {
                    RemoteControlClient.PLAYSTATE_PAUSED
                }
                
                // 更新播放状态
                client.setPlaybackState(playbackState, position, 1.0f)
                
                // 更新媒体信息
                client.editMetadata(true).apply {
                    putString(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                    putString(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST, artist)
                    putLong(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
                    apply()
                }
                
                Log.d(TAG, "播放状态更新: ${if (isPlaying) "播放" else "暂停"}, 歌曲: $title - $artist")
                
            } catch (e: Exception) {
                Log.e(TAG, "更新播放状态失败: ${e.message}")
            }
        }
    }
}