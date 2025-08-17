package com.xiaoxiamusic.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private var amplitudes: FloatArray = floatArrayOf()
    
    fun updateAmplitudes(newAmplitudes: FloatArray) {
        amplitudes = newAmplitudes.copyOf()
        invalidate() // 触发重绘
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (amplitudes.isEmpty()) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / amplitudes.size
        
        for (i in amplitudes.indices) {
            val amplitude = kotlin.math.abs(amplitudes[i])
            val barHeight = amplitude * height
            
            val left = i * barWidth
            val right = left + barWidth - 1
            val top = height - barHeight
            val bottom = height
            
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
    
    fun setColor(color: Int) {
        paint.color = color
    }
}