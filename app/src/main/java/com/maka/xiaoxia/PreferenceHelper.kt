package com.maka.xiaoxia

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_SONG_LIST = "song_list"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
        private const val KEY_CURRENT_POSITION = "current_position"
        private const val KEY_IS_PLAYING = "is_playing"
        private const val KEY_CURRENT_PLAYLIST = "current_playlist"
    }

    fun saveSongList(songs: List<Song>) {
        val json = gson.toJson(songs)
        prefs.edit().putString(KEY_SONG_LIST, json).apply()
    }

    fun loadSongList(): List<Song> {
        val json = prefs.getString(KEY_SONG_LIST, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<Song>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun saveCurrentSongIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT_SONG_INDEX, index).apply()
    }

    fun getCurrentSongIndex(): Int {
        return prefs.getInt(KEY_CURRENT_SONG_INDEX, 0)
    }

    fun saveCurrentPosition(position: Int) {
        prefs.edit().putInt(KEY_CURRENT_POSITION, position).apply()
    }

    fun getCurrentPosition(): Int {
        return prefs.getInt(KEY_CURRENT_POSITION, 0)
    }

    fun saveIsPlaying(isPlaying: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PLAYING, isPlaying).apply()
    }

    fun getIsPlaying(): Boolean {
        return prefs.getBoolean(KEY_IS_PLAYING, false)
    }

    fun saveCurrentPlaylist(playlistName: String) {
        prefs.edit().putString(KEY_CURRENT_PLAYLIST, playlistName).apply()
    }

    fun getCurrentPlaylist(): String {
        return prefs.getString(KEY_CURRENT_PLAYLIST, "") ?: ""
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    fun savePlaybackState(songList: List<Song>, currentIndex: Int, position: Int, isPlaying: Boolean) {
        saveSongList(songList)
        saveCurrentSongIndex(currentIndex)
        saveCurrentPosition(position)
        saveIsPlaying(isPlaying)
    }
    
    fun restorePlaybackState(): Triple<List<Song>, Int, Int> {
        val songs = loadSongList()
        val index = getCurrentSongIndex()
        val position = getCurrentPosition()
        return Triple(songs, index, position)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun getStringSet(key: String, defValues: Set<String>): Set<String> {
        return prefs.getStringSet(key, defValues) ?: defValues
    }

    fun saveStringSet(key: String, values: Set<String>) {
        prefs.edit().putStringSet(key, values).apply()
    }
}