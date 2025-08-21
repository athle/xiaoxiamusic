package com.maka.xiaoxia

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

object MusicScanner {
    
    data class ScanOptions(
        val minDuration: Long = 30_000, // 最小时长30秒
        val maxDuration: Long = 10 * 60 * 60 * 1000, // 最大时长10小时
        val includePath: List<String> = emptyList(), // 包含路径
        val excludePath: List<String> = emptyList(), // 排除路径
        val supportedFormats: List<String> = listOf(
            "mp3", "m4a", "wav", "flac", "aac", "ogg", "wma"
        )
    )
    
    fun scanMusic(context: Context, options: ScanOptions = ScanOptions()): List<Song> {
        val songs = mutableListOf<Song>()
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.SIZE
            )

            // 基础筛选：是音乐文件
            var selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
            var selectionArgs = arrayOf<String>()

            // 添加时长筛选
            if (options.minDuration > 0 || options.maxDuration < Long.MAX_VALUE) {
                selection += " AND " + MediaStore.Audio.Media.DURATION + " BETWEEN ? AND ?"
                selectionArgs = arrayOf(
                    options.minDuration.toString(),
                    options.maxDuration.toString()
                )
            }

            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                MediaStore.Audio.Media.TITLE + " ASC"
            )

            cursor?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    val file = File(path)
                    
                    // 检查文件是否存在
                    if (!file.exists()) continue
                    
                    // 检查文件格式
                    val extension = path.substringAfterLast('.', "").lowercase()
                    if (extension !in options.supportedFormats) continue
                    
                    // 检查路径包含/排除规则
                    if (options.includePath.isNotEmpty()) {
                        val included = options.includePath.any { path.contains(it, ignoreCase = true) }
                        if (!included) continue
                    }
                    
                    if (options.excludePath.isNotEmpty()) {
                        val excluded = options.excludePath.any { path.contains(it, ignoreCase = true) }
                        if (excluded) continue
                    }
                    
                    // 检查文件大小（至少100KB）
                    val size = cursor.getLong(sizeColumn)
                    if (size < 100 * 1024) continue

                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "未知歌曲"
                    val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                    val album = cursor.getString(albumColumn) ?: "未知专辑"
                    val duration = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)

                    songs.add(Song(id, title, artist, album, duration, path, albumId))
                }
            }
            

            
        } catch (e: Exception) {
            Log.e("MusicScanner", "扫描音乐失败: ${e.message}")
        }

        return songs
    }
    
    fun getDefaultScanOptions(): ScanOptions {
        return ScanOptions(
            minDuration = 30_000, // 30秒
            maxDuration = 60 * 60 * 1000, // 1小时
            excludePath = listOf(
                "/Alarms/",
                "/Notifications/",
                "/Ringtones/",
                "/WhatsApp/",
                "/Telegram/",
                "/recordings/",
                "/voice/"
            )
        )
    }
    
    fun getQuickScanOptions(): ScanOptions {
        return ScanOptions(
            minDuration = 60_000, // 1分钟
            maxDuration = 30 * 60 * 1000, // 30分钟
            excludePath = listOf(
                "/Alarms/",
                "/Notifications/",
                "/Ringtones/"
            )
        )
    }
}