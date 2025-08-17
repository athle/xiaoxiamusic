package com.xiaoxiamusic.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFF4CAF50.toInt()
        strokeWidth = 4f
        isAntiAlias = true
    }

    private var amplitudes: FloatArray = floatArrayOf()

    fun updateAmplitudes(newAmplitudes: FloatArray) {
        amplitudes = newAmplitudes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (amplitudes.isEmpty()) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / amplitudes.size
        
        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = amplitude * height
            val left = index * barWidth
            val top = height - barHeight
            val right = left + barWidth
            val bottom = height
            
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}