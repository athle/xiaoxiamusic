package com.xiaoxiamusic.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class LyricScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            16f,
            context.resources.displayMetrics
        )
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    private var lyrics: List<String> = listOf("暂无歌词")
    
    fun setLyrics(newLyrics: List<String>) {
        lyrics = newLyrics
        invalidate()
    }
    
    fun setTextColor(color: Int) {
        paint.color = color
        invalidate()
    }
    
    fun setTextSize(size: Float) {
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            size,
            context.resources.displayMetrics
        )
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        var currentY = height / 2f
        
        for (line in lyrics) {
            canvas.drawText(line, centerX, currentY, paint)
            currentY += paint.textSize * 1.5f
        }
    }
}