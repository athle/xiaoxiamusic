package com.maka.xiaoxia

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 创建简单的布局
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        }

        val cardLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20f
                setColor(android.graphics.Color.parseColor("#2A2A2A"))
            }
        }

        val titleText = android.widget.TextView(this).apply {
            text = "主题设置"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val colorButton = android.widget.Button(this).apply {
            text = "切换歌词颜色"
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#FF6B6B"))
            }
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                val sharedPrefs = getSharedPreferences("music_prefs", MODE_PRIVATE)
                val currentColor = sharedPrefs.getInt("lyric_color", android.graphics.Color.WHITE)
                val newColor = if (currentColor == android.graphics.Color.WHITE) {
                    android.graphics.Color.YELLOW
                } else {
                    android.graphics.Color.WHITE
                }
                
                sharedPrefs.edit()
                    .putInt("lyric_color", newColor)
                    .apply()
            }
        }

        val seekBar = SeekBar(this).apply {
            max = 20
            progress = 8
        }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fontSize = 12 + progress
                getSharedPreferences("music_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("lyric_font_size", fontSize)
                    .apply()
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cardLayout.addView(titleText)
        cardLayout.addView(colorButton)
        cardLayout.addView(seekBar)
        layout.addView(cardLayout)
        setContentView(layout)
    }
}