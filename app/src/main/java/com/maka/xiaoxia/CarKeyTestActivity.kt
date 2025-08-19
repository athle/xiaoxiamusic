package com.maka.xiaoxia

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 车机按键测试Activity
 * 用于测试领克02 CS11车机实体按键功能
 */
class CarKeyTestActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private var testCount = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 简单的测试界面
        setContentView(R.layout.activity_car_key_test)
        
        statusText = findViewById(R.id.status_text)
        
        findViewById<Button>(R.id.btn_test_play).setOnClickListener {
            simulateKeyPress(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        
        findViewById<Button>(R.id.btn_test_next).setOnClickListener {
            simulateKeyPress(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        
        findViewById<Button>(R.id.btn_test_previous).setOnClickListener {
            simulateKeyPress(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        
        findViewById<Button>(R.id.btn_start_service).setOnClickListener {
            startMusicService()
        }
        
        updateStatus("车机按键测试已启动")
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("CarKeyTest", "收到按键事件: $keyCode")
        
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                updateStatus("收到播放/暂停按键")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                updateStatus("收到下一首按键")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                updateStatus("收到上一首按键")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                updateStatus("收到停止按键")
                return true
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    private fun simulateKeyPress(keyCode: Int) {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        
        sendBroadcast(intent)
        updateStatus("模拟按键: $keyCode")
    }
    
    private fun startMusicService() {
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_PAUSE
        }
        startService(intent)
        updateStatus("已启动音乐服务")
    }
    
    private fun updateStatus(message: String) {
        testCount++
        runOnUiThread {
            statusText.text = "测试 #$testCount: $message"
        }
    }
}