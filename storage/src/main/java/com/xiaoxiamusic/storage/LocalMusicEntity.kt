@Entity(tableName = "local_music")
data class LocalMusicEntity(
    @PrimaryKey
    val id: String,
    val path: String,
    val lastModified: Long,
    val fileSize: Long
)