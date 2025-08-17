object LyricParser {
    fun parse(lrcText: String): List<LyricItem> {
        return lrcText.lines()
            .mapNotNull { line ->
                Regex("\\[(\\d+):(\\d+\\.?\\d*)\\]").find(line)?.let {
                    val (min, sec) = it.destructured
                    val time = min.toLong() * 60000 + (sec.toFloat() * 1000).toLong()
                    LyricItem(time, line.substring(it.range.last + 1))
                }
            }
            .sortedBy { it.timestamp }
    }
}

data class LyricItem(val timestamp: Long, val text: String)