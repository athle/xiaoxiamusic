import android.content.Context
import java.io.File

class CacheCleaner(private val context: Context) {
    
        fun cleanExpiredCache(expireDays: Int): Long {
            val cacheDir = context.externalCacheDir ?: return 0
            val cutoff = System.currentTimeMillis() - (expireDays * 24 * 3600 * 1000)
            
            return cacheDir.walk()
                .filter { it.isFile && it.lastModified() < cutoff }
                .sumOf { file -> 
                    file.length().also { 
                        file.delete()
                    }
                }
        }
    
        fun getCacheSize(): String {
            val size = context.externalCacheDir?.walk()
                ?.sumOf { it.length() } ?: 0
            return "%.2f MB".format(size / 1024.0 / 1024.0)
        }
}