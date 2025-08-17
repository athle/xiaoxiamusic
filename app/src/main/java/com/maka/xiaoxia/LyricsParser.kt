package com.maka.xiaoxia

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val time: Long,
    val text: String
)

class LyricsParser {
    
    fun extractLyricsFromTags(songPath: String): String? {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(songPath)
            
            // 尝试从标准标签读取歌词
            val lyrics = retriever.extractMetadata(21) // MediaMetadataRetriever.METADATA_KEY_LYRICS
            retriever.release()
            
            return lyrics
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    fun findLyricsFile(songFile: File): String? {
        val songDir = songFile.parentFile ?: return null
        val songName = songFile.nameWithoutExtension
        
        // 支持的歌词文件扩展名
        val extensions = listOf(".lrc", ".txt", ".lyric")
        
        // 查找匹配的歌词文件
        for (ext in extensions) {
            val lyricsFile = File(songDir, "$songName$ext")
            if (lyricsFile.exists()) {
                return lyricsFile.readText(Charsets.UTF_8)
            }
        }
        
        // 查找包含歌曲名的歌词文件
        val files = songDir.listFiles { file ->
            extensions.any { ext -> file.name.endsWith(ext, true) }
        }
        
        files?.forEach { file ->
            if (file.name.contains(songName, true)) {
                return file.readText(Charsets.UTF_8)
            }
        }
        
        return null
    }
    
    fun parseLrcLyrics(lyricsText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lyricsText.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                val minutes = matcher.group(1)?.toIntOrNull() ?: 0
                val seconds = matcher.group(2)?.toIntOrNull() ?: 0
                val milliseconds = matcher.group(3)?.toIntOrNull() ?: 0
                val text = matcher.group(4)?.trim() ?: ""
                
                if (text.isNotEmpty()) {
                    val time = minutes * 60 * 1000L + seconds * 1000L + milliseconds
                    lines.add(LyricLine(time, text))
                }
            }
        }
        
        return lines.sortedBy { it.time }
    }
    
    fun getLyricsForSong(song: Song): Pair<String?, List<LyricLine>> {
        android.util.Log.d("LyricsParser", "开始查找歌词: ${song.title}")
        android.util.Log.d("LyricsParser", "歌曲路径: ${song.path}")
        
        // 1. 从标签中读取歌词
        var lyrics = extractLyricsFromTags(song.path)
        android.util.Log.d("LyricsParser", "ID3标签歌词: ${if (lyrics.isNullOrEmpty()) "未找到" else "找到"}")
        
        // 2. 如果标签中没有，从文件查找
        if (lyrics.isNullOrEmpty()) {
            val songFile = File(song.path)
            val songDir = songFile.parentFile
            android.util.Log.d("LyricsParser", "搜索目录: ${songDir?.absolutePath}")
            android.util.Log.d("LyricsParser", "歌曲文件名: ${songFile.nameWithoutExtension}")
            
            lyrics = findLyricsFile(songFile)
            android.util.Log.d("LyricsParser", "文件搜索歌词: ${if (lyrics.isNullOrEmpty()) "未找到" else "找到"}")
        }
        
        // 3. 解析歌词
        val lyricLines = if (!lyrics.isNullOrEmpty()) {
            parseLrcLyrics(lyrics)
        } else {
            emptyList()
        }
        
        android.util.Log.d("LyricsParser", "最终解析结果: ${lyricLines.size} 行歌词")
        return Pair(lyrics, lyricLines)
    }
}