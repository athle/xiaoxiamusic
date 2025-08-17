package com.maka.xiaoxia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val lyricsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 14f * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 16f * context.resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    
    private var lyrics: List<LyricLine> = emptyList()
    private var currentLineIndex: Int = -1
    private var currentProgress: Long = 0
    private var showNoLyricsMessage: Boolean = false
    
    fun setLyrics(lyrics: List<LyricLine>) {
        this.lyrics = lyrics
        currentLineIndex = -1
        showNoLyricsMessage = false
        invalidate()
    }
    
    fun setNoLyricsMessage() {
        this.lyrics = emptyList()
        this.showNoLyricsMessage = true
        invalidate()
    }
    
    fun updateProgress(progress: Long) {
        if (lyrics.isEmpty()) return
        
        currentProgress = progress
        val newIndex = findCurrentLineIndex(progress)
        
        if (newIndex != currentLineIndex) {
            currentLineIndex = newIndex
            invalidate()
        }
    }
    
    private fun findCurrentLineIndex(progress: Long): Int {
        if (lyrics.isEmpty()) return -1
        
        for (i in lyrics.indices.reversed()) {
            if (progress >= lyrics[i].time) {
                return i
            }
        }
        return -1
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (showNoLyricsMessage) {
            val centerY = height / 2f
            canvas.drawText("当前歌曲没有匹配的歌词", width / 2f, centerY, lyricsPaint)
            return
        }
        
        if (lyrics.isEmpty()) {
            val centerY = height / 2f
            canvas.drawText("加载歌词中...", width / 2f, centerY, lyricsPaint)
            return
        }
        
        val centerY = height / 2f
        val lineHeight = 24f * context.resources.displayMetrics.density
        
        // 显示当前行和上下各2行
        val startLine = maxOf(0, currentLineIndex - 2)
        val endLine = minOf(lyrics.size - 1, currentLineIndex + 2)
        
        for (i in startLine..endLine) {
            val text = lyrics[i].text
            val y = centerY + (i - currentLineIndex) * lineHeight
            
            val paint = if (i == currentLineIndex) highlightPaint else lyricsPaint
            canvas.drawText(text, width / 2f, y, paint)
        }
    }
}