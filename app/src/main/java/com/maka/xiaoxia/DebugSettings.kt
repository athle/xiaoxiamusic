package com.maka.xiaoxia

import android.content.Context
import android.content.SharedPreferences

object DebugSettings {
    private const val PREF_NAME = "debug_settings"
    private const val KEY_ENABLE_ID3_DEBUG = "enable_id3_debug"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    fun isId3DebugEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLE_ID3_DEBUG, false)
    }
    
    fun setId3DebugEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_ID3_DEBUG, enabled).apply()
    }
}