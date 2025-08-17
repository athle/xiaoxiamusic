package com.maka.xiaoxia

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xiaoxiamusic.media.VisualizerView

class PlayerActivity : AppCompatActivity() {
    
    private lateinit var visualizerView: VisualizerView
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var visualizer: Visualizer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        }

        val cardLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20f
                setColor(android.graphics.Color.parseColor("#2A2A2A"))
            }
            gravity = android.view.Gravity.CENTER
        }

        visualizerView = VisualizerView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                400
            ).apply {
                setMargins(0, 0, 0, 32)
            }
        }

        val buttonLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val presetButton = android.widget.Button(this).apply {
            text = "预设"
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#4CAF50"))
            }
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                startActivity(android.content.Intent(this@PlayerActivity, PresetManagerActivity::class.java))
            }
        }

        val styleButton = android.widget.Button(this).apply {
            text = "样式"
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#FF9800"))
            }
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                startActivity(android.content.Intent(this@PlayerActivity, LyricStyleActivity::class.java))
            }
        }

        buttonLayout.addView(presetButton)
        buttonLayout.addView(styleButton)
        cardLayout.addView(visualizerView)
        cardLayout.addView(buttonLayout)
        layout.addView(cardLayout)
        setContentView(layout)
        
        // 初始化MediaPlayer (使用空白音频)
        mediaPlayer = MediaPlayer()
        
        // 设置音频可视化
        setupVisualizer()
    }
    
    private fun setupVisualizer() {
        try {
            // 创建Visualizer
            visualizer = Visualizer(0) // 使用默认会话
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
            
            visualizer.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        // 生成随机数据用于演示
                        val amplitudes = FloatArray(waveform.size) { 
                            kotlin.math.sin(it * 0.1f) * 0.5f + 0.5f
                        }
                        visualizerView.updateAmplitudes(amplitudes)
                    }
                    
                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {
                        // 可选的FFT数据处理
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            
            visualizer.enabled = true
        } catch (e: Exception) {
            // 处理可能的异常
            android.widget.Toast.makeText(this, "音频可视化初始化失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            visualizer.release()
            mediaPlayer.release()
        } catch (e: Exception) {
            // 忽略清理异常
        }
    }
}