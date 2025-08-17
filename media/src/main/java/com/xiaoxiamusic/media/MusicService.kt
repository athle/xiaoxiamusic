import android.app.Service
import android.content.Intent
import android.os.IBinder

class MusicService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
    // 移除不兼容的MediaSessionCompat回调实现
}