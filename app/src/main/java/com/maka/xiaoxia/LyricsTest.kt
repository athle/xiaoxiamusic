package com.maka.xiaoxia

import android.util.Log

object LyricsTest {
    fun testLyricsParsing() {
        val testLrc = """
            [00:00.00]这是第一行歌词
            [00:05.50]这是第二行歌词
            [00:10.25]这是第三行歌词
            [00:15.00]这是第四行歌词
        """.trimIndent()
        
        val parser = LyricsParser()
        val lines = parser.parseLrcLyrics(testLrc)
        
        Log.d("LyricsTest", "解析到的歌词行数: ${lines.size}")
        lines.forEach { line ->
            Log.d("LyricsTest", "时间: ${line.time}ms, 文本: ${line.text}")
        }
    }
}