import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.xiaoxiamusic.media.LyricScrollView

data class LyricItem(val time: Float, val text: String)

class LyricSyncController(private val player: ExoPlayer) {
    private var lyricScrollView: LyricScrollView? = null

    fun attachLyricView(view: LyricScrollView) {
        lyricScrollView = view
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                lyricScrollView?.updateLyricPosition(player.currentPosition.toFloat())
            }
        })
    }

    fun updateLyricData(lyricItems: List<com.xiaoxiamusic.media.LyricItem>) {
        lyricScrollView?.post {
            lyricScrollView?.setLyrics(lyricItems)
            lyricScrollView?.updateLyricPosition(player.currentPosition.toFloat())
        }
    }
}