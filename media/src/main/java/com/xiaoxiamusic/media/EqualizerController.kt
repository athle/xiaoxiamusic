import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer

class EqualizerController(private val context: Context) {
    private var equalizer: Equalizer? = null
    
    fun setup(audioSessionId: Int) {
        equalizer = Equalizer(0, audioSessionId).apply {
            enabled = true
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    fun release() {
        equalizer?.enabled = false
        equalizer?.release()
    }

    // 添加音效预设配置
    private val presets = mapOf(
        "rock" to Preset(intArrayOf(8, 6, 2, -1, 4)),
        "pop" to Preset(intArrayOf(4, 2, 0, 2, 5)),
        "jazz" to Preset(intArrayOf(5, 3, 2, 3, 4))
    )

    data class Preset(val bandLevels: IntArray)

    fun applyPreset(name: String) {
        presets[name]?.let {
            for (i in 0 until (equalizer?.numberOfBands ?: 0)) {
                setBandLevel(i.toShort(), it.bandLevels[i].toShort())
            }
        }
    }
    // 新增SharedPreferences存储逻辑
    private val prefs by lazy {
        context.getSharedPreferences("audio_presets", Context.MODE_PRIVATE)
    }

    fun saveCustomPreset(name: String, levels: IntArray) {
        val editor = prefs.edit()
        editor.putString(name, levels.joinToString(","))
        editor.apply()
    }

    private fun loadPreset(name: String): Preset? {
        return prefs.getString(name, null)?.let {
            Preset(it.split(",").map { s -> s.toInt() }.toIntArray())
        }
    }
}