class StorageManager(context: Context) {
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java, "music.db"
        ).allowMainThreadQueries() // 简化处理
         .build()
    }

    fun scanLocalMusic(): List<LocalMusicEntity> {
        return File(Environment.getExternalStorageDirectory(), "Music")
            .listFiles { file -> 
                file.extension.lowercase() in setOf("mp3", "flac", "wav")
            }
            .map { file ->
                LocalMusicEntity(
                    id = file.absolutePath.hashCode().toString(),
                    path = file.absolutePath,
                    lastModified = file.lastModified(),
                    fileSize = file.length()
                )
            }
    }
}