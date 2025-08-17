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

class SwipeSongAdapter(
    private val context: Context,
    private val songs: List<Song>
) : BaseAdapter() {

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
        
        // 加载专辑封面
        loadAlbumArt(song, albumArtImage)

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
            if (song.albumId > 0) {
                val albumArtUri = Uri.parse("content://media/external/audio/albumart")
                val albumArtFullUri = ContentUris.withAppendedId(albumArtUri, song.albumId)
                
                val inputStream = context.contentResolver.openInputStream(albumArtFullUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(R.drawable.ic_music_default)
                }
                inputStream?.close()
            } else {
                imageView.setImageResource(R.drawable.ic_music_default)
            }
        } catch (e: Exception) {
            // 出错时使用默认图标
            imageView.setImageResource(R.drawable.ic_music_default)
        }
    }
}