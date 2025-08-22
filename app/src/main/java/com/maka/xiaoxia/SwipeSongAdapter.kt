package com.maka.xiaoxia

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.content.ContentUris
import android.net.Uri
import android.os.AsyncTask

class SwipeSongAdapter(
    private val context: Context,
    private val songs: List<Song>
) : BaseAdapter() {

    // 缓存专辑封面，避免重复加载
    private val albumArtCache = mutableMapOf<Long, Bitmap>()
    private val defaultBitmap by lazy { 
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_music_default) 
    }

    override fun getCount(): Int = songs.size
    override fun getItem(position: Int): Song = songs[position]
    override fun getItemId(position: Int): Long = songs[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_song, parent, false)

        val song = getItem(position)

        val titleText = view.findViewById<TextView>(R.id.song_title)
        val artistText = view.findViewById<TextView>(R.id.song_artist)
        val durationText = view.findViewById<TextView>(R.id.song_duration)
        val albumArtImage = view.findViewById<ImageView>(R.id.album_art)

        titleText.text = song.title
        artistText.text = song.artist
        durationText.text = formatTime(song.duration)
        
        // 设置默认图片，异步加载专辑封面
        albumArtImage.setImageBitmap(defaultBitmap)
        loadAlbumArtAsync(song, albumArtImage)

        // 设置点击事件
        view.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null && position >= 0 && position < songs.size) {
                activity.playSong(position)
            }
        }

        return view
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun loadAlbumArt(song: Song, imageView: ImageView) {
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(song.path)
            val artBytes = mmr.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(R.drawable.ic_music_default)
            }
            mmr.release()
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.ic_music_default)
        }
    }

    private fun loadAlbumArtAsync(song: Song, imageView: ImageView) {
        // 先从缓存获取
        val cachedBitmap = albumArtCache[song.id]
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 异步加载专辑封面
        AsyncTask.execute {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(song.path)
                val artBytes = mmr.embeddedPicture
                val bitmap = if (artBytes != null) {
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                } else {
                    defaultBitmap
                }
                mmr.release()

                // 缓存图片
                albumArtCache[song.id] = bitmap

                // 在主线程更新UI
                (context as? android.app.Activity)?.runOnUiThread {
                    if (imageView.tag == song.id) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                // 出错时使用默认图片
                (context as? android.app.Activity)?.runOnUiThread {
                    if (imageView.tag == song.id) {
                        imageView.setImageBitmap(defaultBitmap)
                    }
                }
            }
        }
        
        // 设置tag防止图片错位
        imageView.tag = song.id
    }
}