package com.xiaoxiamusic.media

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.xiaoxiamusic.media.LyricItem

class LyricScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // 新增样式配置属性
    var textColor: Int = Color.WHITE
        set(value) {
            field = value
            lyricPaint.color = value
            invalidate()
        }

    var textSize: Float = 42f
        set(value) {
            field = value
            lyricPaint.textSize = value
            invalidate()
        }

    fun setTypeface(typeface: Typeface) {
        lyricPaint.typeface = typeface
        invalidate()
    }

    private val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private var lyrics: List<LyricItem> = emptyList()
    
    fun setLyrics(lyrics: List<LyricItem>) {
        this.lyrics = lyrics
        invalidate()
    }
    
    private var currentPosition = 0f
    private var targetPosition = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 500
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { 
            val fraction = it.animatedValue as Float
            currentPosition = currentPosition + (targetPosition - currentPosition) * fraction
            invalidate()
        }
    }

    fun updateLyricPosition(position: Float) {
        targetPosition = position
        animator.cancel()
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 示例歌词绘制（后续需替换真实数据）
        canvas.drawText("示例歌词", width / 2f, height / 2f + currentPosition, lyricPaint)
    }
}