package com.maka.xiaoxia

import android.app.Application
import android.util.Log
import android.widget.Toast

class MusicApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MusicApp", "全局异常捕获: ${throwable.message}", throwable)
            throwable.printStackTrace()
            
            // 尝试显示错误信息
            try {
                Toast.makeText(applicationContext, "应用崩溃: ${throwable.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("MusicApp", "显示Toast失败: ${e.message}")
            }
        }
        

    }
}