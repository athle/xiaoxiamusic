package com.maka.xiaoxia

import android.content.Context
import android.content.SharedPreferences

class ScanSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScanSettings", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_SCAN_MODE = "scan_mode"
        private const val KEY_MIN_DURATION = "min_duration"
        private const val KEY_MAX_DURATION = "max_duration"
        private const val KEY_EXCLUDE_PATHS = "exclude_paths"
        private const val KEY_INCLUDE_PATHS = "include_paths"
        
        const val MODE_SMART = "smart"
        const val MODE_QUICK = "quick"
        const val MODE_FULL = "full"
        const val MODE_CUSTOM = "custom"
    }
    
    fun getScanMode(): String {
        return prefs.getString(KEY_SCAN_MODE, MODE_SMART) ?: MODE_SMART
    }
    
    fun setScanMode(mode: String) {
        prefs.edit().putString(KEY_SCAN_MODE, mode).apply()
    }
    
    fun getScanOptions(): MusicScanner.ScanOptions {
        return when (getScanMode()) {
            MODE_SMART -> MusicScanner.getDefaultScanOptions()
            MODE_QUICK -> MusicScanner.getQuickScanOptions()
            MODE_FULL -> MusicScanner.ScanOptions(
                minDuration = 0,
                maxDuration = Long.MAX_VALUE,
                excludePath = emptyList()
            )
            MODE_CUSTOM -> getCustomOptions()
            else -> MusicScanner.getDefaultScanOptions()
        }
    }
    
    private fun getCustomOptions(): MusicScanner.ScanOptions {
        val minDuration = prefs.getLong(KEY_MIN_DURATION, 30_000)
        val maxDuration = prefs.getLong(KEY_MAX_DURATION, 60 * 60 * 1000)
        val excludePaths = prefs.getString(KEY_EXCLUDE_PATHS, "")?.split("|") ?: emptyList()
        val includePaths = prefs.getString(KEY_INCLUDE_PATHS, "")?.split("|") ?: emptyList()
        
        return MusicScanner.ScanOptions(
            minDuration = minDuration,
            maxDuration = maxDuration,
            excludePath = excludePaths.filter { it.isNotEmpty() },
            includePath = includePaths.filter { it.isNotEmpty() }
        )
    }
    
    fun setCustomOptions(minDuration: Long, maxDuration: Long, excludePaths: List<String>, includePaths: List<String>) {
        prefs.edit()
            .putLong(KEY_MIN_DURATION, minDuration)
            .putLong(KEY_MAX_DURATION, maxDuration)
            .putString(KEY_EXCLUDE_PATHS, excludePaths.joinToString("|"))
            .putString(KEY_INCLUDE_PATHS, includePaths.joinToString("|"))
            .apply()
    }
}