package com.maka.xiaoxia

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PresetManagerActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 创建简单的预设管理界面
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
            text = "预设管理"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val addButton = android.widget.Button(this).apply {
            text = "添加预设"
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#4CAF50"))
            }
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                showAddPresetDialog()
            }
        }

        val presetList = android.widget.TextView(this).apply {
            text = "预设列表:\n- 流行\n- 摇滚\n- 古典\n- 爵士"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 32, 0, 0)
        }

        cardLayout.addView(titleText)
        cardLayout.addView(addButton)
        cardLayout.addView(presetList)
        layout.addView(cardLayout)
        setContentView(layout)
    }
    
    private fun showAddPresetDialog() {
        android.widget.Toast.makeText(this, "添加预设功能开发中...", android.widget.Toast.LENGTH_SHORT).show()
    }
}