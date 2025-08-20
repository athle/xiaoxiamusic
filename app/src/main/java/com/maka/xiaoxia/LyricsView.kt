package com.maka.xiaoxia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 13f * context.resources.displayMetrics.density
        textAlign = Paint.Align.LEFT
    }
    
    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 15f * context.resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    
    private var lyrics: List<LyricLine> = emptyList()
    private var currentLineIndex: Int = -1
    private var currentProgress: Long = 0
    private var showNoLyricsMessage: Boolean = false
    private var lyricLayouts: MutableList<StaticLayout?> = mutableListOf()
    
    fun setLyrics(lyrics: List<LyricLine>) {
        this.lyrics = lyrics
        currentLineIndex = -1
        showNoLyricsMessage = false
        updateLyricLayouts()
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
    
    private fun updateLyricLayouts() {
        lyricLayouts.clear()
        val availableWidth = width - (paddingLeft + paddingRight + 32f * context.resources.displayMetrics.density).toInt()
        
        for (line in lyrics) {
            if (line.text.isNotEmpty()) {
                val layout = StaticLayout(
                    line.text,
                    lyricsPaint,
                    availableWidth,
                    Layout.Alignment.ALIGN_CENTER,
                    1f,  // line spacing multiplier
                    4f * context.resources.displayMetrics.density,  // line spacing add
                    true  // include pad
                )
                lyricLayouts.add(layout)
            } else {
                lyricLayouts.add(null)
            }
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (lyrics.isNotEmpty()) {
            updateLyricLayouts()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val padding = 16f * context.resources.displayMetrics.density
        val centerX = width / 2f
        
        if (showNoLyricsMessage) {
            val text = "当前歌曲没有匹配的歌词"
            val layout = StaticLayout(
                text,
                lyricsPaint,
                width - (padding * 2).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1f,
                0f,
                false
            )
            val textHeight = layout.height
            val y = (height - textHeight) / 2f
            canvas.save()
            canvas.translate(centerX - layout.width / 2f, y)
            layout.draw(canvas)
            canvas.restore()
            return
        }
        
        if (lyrics.isEmpty()) {
            val text = "加载歌词中..."
            val layout = StaticLayout(
                text,
                lyricsPaint,
                width - (padding * 2).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1f,
                0f,
                false
            )
            val textHeight = layout.height
            val y = (height - textHeight) / 2f
            canvas.save()
            canvas.translate(centerX - layout.width / 2f, y)
            layout.draw(canvas)
            canvas.restore()
            return
        }
        
        if (lyricLayouts.isEmpty() || lyricLayouts.size != lyrics.size) {
            updateLyricLayouts()
        }
        
        // 计算可显示的行数，充分利用可用空间
        val lineHeight = 24f * context.resources.displayMetrics.density
        val maxLines = (height / lineHeight).toInt()
        val halfLines = maxLines / 2
        
        // 显示当前行和上下尽可能多的行
        val startLine = maxOf(0, currentLineIndex - halfLines + 1)
        val endLine = minOf(lyrics.size - 1, currentLineIndex + halfLines)
        
        var totalHeight = 0f
        val layoutsToDraw = mutableListOf<StaticLayout>()
        
        for (i in startLine..endLine) {
            val paint = if (i == currentLineIndex) highlightPaint else lyricsPaint
            val layout = StaticLayout(
                lyrics[i].text,
                paint,
                width - (padding * 2).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1f,  // line spacing multiplier
                4f * context.resources.displayMetrics.density,  // line spacing add
                true  // include pad
            )
            layoutsToDraw.add(layout)
            totalHeight += layout.height.toFloat()
        }
        
        // 垂直居中绘制所有歌词行
        var currentY = (height - totalHeight) / 2f
        for (layout in layoutsToDraw) {
            canvas.save()
            canvas.translate(centerX - layout.width / 2f, currentY)
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height
        }
    }
}