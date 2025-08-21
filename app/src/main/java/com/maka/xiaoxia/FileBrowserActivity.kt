package com.maka.xiaoxia

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileBrowserActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var pathTextView: TextView
    private lateinit var upButton: Button
    private lateinit var adapter: FileAdapter
    private lateinit var emptyView: TextView
    
    private var currentPath: String = "/sdcard"
    private val musicExtensions = listOf(".mp3", ".m4a", ".flac", ".wav", ".aac", ".ogg", ".wma")
    private val playlistExtensions = listOf(".m3u", ".m3u8")
    private val PREFS_NAME = "FileBrowserPrefs"
    private val LAST_PATH_KEY = "last_path"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)
        
        // 适配高版本安卓状态栏
        setupSystemBars()
        
        // 初始化视图
        recyclerView = findViewById(R.id.file_list)
        pathTextView = findViewById(R.id.current_path)
        upButton = findViewById(R.id.up_button)
        emptyView = findViewById(R.id.empty_view)
        
        // 设置RecyclerView
        adapter = FileAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "选择音乐文件"
        
        // 加载上次使用的路径，默认为/sdcard
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedPath = prefs.getString(LAST_PATH_KEY, "/sdcard")
        currentPath = if (savedPath != null && File(savedPath).exists() && File(savedPath).isDirectory) {
            savedPath
        } else {
            "/sdcard"
        }
        loadFiles(currentPath)
        
        // 设置向上按钮
        upButton.setOnClickListener {
            val parent = File(currentPath).parent
            if (parent != null) {
                currentPath = parent
                loadFiles(currentPath)
            }
        }
        
        // 设置导入当前文件夹所有歌曲按钮
        val importAllButton = findViewById<Button>(R.id.import_all_button)
        importAllButton.setOnClickListener {
            importAllMusicFromCurrentFolder()
        }

        // 设置关闭按钮
        val closeButton = findViewById<Button>(R.id.close_button)
        closeButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadFiles(path: String) {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            Toast.makeText(this, "无法访问目录: $path", Toast.LENGTH_SHORT).show()
            return
        }
        
        pathTextView.text = path
        
        var files = directory.listFiles()?.filter { file ->
            file.isDirectory || isMusicFile(file) || isPlaylistFile(file)
        }?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()
        
        // 在根目录下，优先显示常用存储路径
        if (path == "/") {
            val commonPaths = listOf(
                "/storage/emulated/0",
                "/sdcard",
                "/storage",
                "/mnt"
            ).map { File(it) }.filter { it.exists() && it.isDirectory }
            
            files = files + commonPaths.filter { !files.contains(it) }
        }
        
        adapter.submitList(files)
        
        // 显示空视图
        if (files.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun isMusicFile(file: File): Boolean {
        return file.isFile && musicExtensions.any { ext ->
            file.name.lowercase().endsWith(ext)
        }
    }
    
    private fun isPlaylistFile(file: File): Boolean {
        return file.isFile && playlistExtensions.any { ext ->
            file.name.lowercase().endsWith(ext)
        }
    }
    
    private fun onFileSelected(file: File) {
        if (file.isDirectory) {
            currentPath = file.absolutePath
            loadFiles(currentPath)
            saveCurrentPath()
        } else if (isPlaylistFile(file)) {
            // 处理播放列表文件
            val musicFiles = parsePlaylistFile(file)
            if (musicFiles.isNotEmpty()) {
                val paths = musicFiles.map { it.absolutePath }.toTypedArray()
                intent.putExtra("selected_files", paths)
                intent.putExtra("multiple_files", true)
                setResult(RESULT_OK, intent)
                Toast.makeText(this, "已导入播放列表中的 ${musicFiles.size} 个音乐文件", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "播放列表中没有找到有效的音乐文件", Toast.LENGTH_SHORT).show()
                return
            }
            finish()
        } else {
            // 返回选择的单个音乐文件
            intent.putExtra("selected_file", file.absolutePath)
            setResult(RESULT_OK, intent)
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onBackPressed() {
        val parent = File(currentPath).parent
        if (parent != null && currentPath != "/") {
            currentPath = parent
            loadFiles(currentPath)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupSystemBars() {
        // 使用传统的系统窗口适配方式，避免高版本多留空间
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 设置透明状态栏
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            
            // 设置状态栏图标为深色（适配浅色背景）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = 
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or 
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        
        // 对于高版本安卓，使用fitsSystemWindows自动处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val rootView = findViewById<View>(android.R.id.content)
            rootView.fitsSystemWindows = true
        }
    }

    private fun saveCurrentPath() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(LAST_PATH_KEY, currentPath).apply()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentPath()
    }
    
    inner class FileAdapter : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
        
        private var files: List<File> = emptyList()
        
        fun submitList(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_browser, parent, false)
            return FileViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val file = files[position]
            holder.bind(file)
        }
        
        override fun getItemCount(): Int = files.size
        
        inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconImageView: ImageView = itemView.findViewById(R.id.file_icon)
            private val nameTextView: TextView = itemView.findViewById(R.id.file_name)
            private val sizeTextView: TextView = itemView.findViewById(R.id.file_size)
            
            fun bind(file: File) {
                nameTextView.text = file.name
                
                if (file.isDirectory) {
                    iconImageView.setImageResource(R.drawable.ic_folder)
                    sizeTextView.text = "文件夹"
                } else if (isPlaylistFile(file)) {
                    iconImageView.setImageResource(R.drawable.ic_playlist)
                    sizeTextView.text = "播放列表"
                } else {
                    iconImageView.setImageResource(R.drawable.ic_music)
                    sizeTextView.text = formatFileSize(file.length())
                }
                
                itemView.setOnClickListener {
                    onFileSelected(file)
                }
            }
            
            private fun formatFileSize(bytes: Long): String {
                val kb = bytes / 1024
                val mb = kb / 1024
                return if (mb > 0) "${mb}MB" else "${kb}KB"
            }
        }
    }
    
    private fun importAllMusicFromCurrentFolder() {
        val rootDir = File(currentPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            Toast.makeText(this, "当前目录无效", Toast.LENGTH_SHORT).show()
            return
        }
        
        val musicFiles = mutableListOf<File>()
        val playlistFiles = mutableListOf<File>()
        
        // 查找所有音乐文件和播放列表文件
        findAllMusicAndPlaylistFiles(rootDir, musicFiles, playlistFiles)
        
        if (musicFiles.isEmpty() && playlistFiles.isEmpty()) {
            Toast.makeText(this, "当前目录及子目录中没有找到音乐文件或播放列表", Toast.LENGTH_SHORT).show()
            return
        }
        
        val allMusicFiles = mutableListOf<File>()
        
        // 添加直接的音乐文件
        allMusicFiles.addAll(musicFiles)
        
        // 解析所有播放列表文件
        var playlistImportCount = 0
        for (playlistFile in playlistFiles) {
            try {
                val playlistMusicFiles = parsePlaylistFile(playlistFile)
                allMusicFiles.addAll(playlistMusicFiles)
                playlistImportCount++
            } catch (e: Exception) {
                // 单个播放列表解析失败不影响其他文件
            }
        }
        
        if (allMusicFiles.isEmpty()) {
            Toast.makeText(this, "没有找到可导入的音乐文件", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 返回所有音乐文件路径（去重）
        val paths = allMusicFiles.distinct().map { it.absolutePath }.toTypedArray()
        intent.putExtra("selected_files", paths)
        intent.putExtra("multiple_files", true)
        setResult(RESULT_OK, intent)
        
        val message = if (playlistImportCount > 0) {
            "已导入 ${allMusicFiles.size} 个音乐文件（含 $playlistImportCount 个播放列表）"
        } else {
            "已导入 ${allMusicFiles.size} 个音乐文件"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun findAllMusicFiles(directory: File, musicFiles: MutableList<File>) {
        val files = directory.listFiles() ?: return
        
        for (file in files) {
            if (file.isDirectory) {
                // 递归处理子文件夹
                findAllMusicFiles(file, musicFiles)
            } else if (isMusicFile(file)) {
                musicFiles.add(file)
            }
        }
    }
    
    private fun findAllMusicAndPlaylistFiles(directory: File, musicFiles: MutableList<File>, playlistFiles: MutableList<File>) {
        val files = directory.listFiles() ?: return
        
        for (file in files) {
            if (file.isDirectory) {
                // 递归处理子文件夹
                findAllMusicAndPlaylistFiles(file, musicFiles, playlistFiles)
            } else {
                if (isMusicFile(file)) {
                    musicFiles.add(file)
                } else if (isPlaylistFile(file)) {
                    playlistFiles.add(file)
                }
            }
        }
    }
    
    private fun parsePlaylistFile(playlistFile: File): List<File> {
        val foundFiles = mutableListOf<Pair<Int, File>>() // 保存索引和文件对
        val processedPaths = mutableSetOf<String>() // 避免重复路径
        
        try {
            val playlistDir = playlistFile.parentFile ?: return emptyList()
            
            // 检测编码格式，优先使用UTF-8
            val content = try {
                playlistFile.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                playlistFile.readText(Charsets.ISO_8859_1)
            }
            
            val lines = content.lines()
            val basePaths = listOf(
                playlistDir.absolutePath,
                playlistDir.parentFile?.absolutePath ?: playlistDir.absolutePath,
                "/storage/emulated/0",
                "/sdcard",
                "/storage/emulated/0/Music",
                "/sdcard/Music",
                "/sdcard/Download",
                "/storage/emulated/0/Download"
            ).distinct()
            
            // 按播放列表顺序查找文件
            lines.forEachIndexed { index, line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    return@forEachIndexed
                }
                
                // 清理路径中的特殊字符
                var cleanPath = trimmedLine
                    .replace("\\", "/")
                    .replace("file://", "")
                    .trim()
                
                // 处理Windows风格路径
                if (cleanPath.contains(":")) {
                    cleanPath = cleanPath.substringAfter(":").removePrefix("/")
                }
                
                // 尝试多种路径解析方式
                val possibleFiles = mutableListOf<File>()
                
                if (cleanPath.startsWith("/")) {
                    // 绝对路径
                    possibleFiles.add(File(cleanPath))
                } else {
                    // 相对路径，尝试多种基准路径
                    for (basePath in basePaths) {
                        possibleFiles.add(File(basePath, cleanPath))
                        
                        // 处理路径中的..和.
                        if (cleanPath.contains("../")) {
                            val parts = cleanPath.split("../")
                            var currentDir = File(basePath)
                            for (i in 0 until parts.size - 1) {
                                currentDir = currentDir.parentFile ?: currentDir
                            }
                            possibleFiles.add(File(currentDir, parts.last()))
                        }
                        
                        // 处理直接文件名
                        possibleFiles.add(File(basePath, File(cleanPath).name))
                    }
                }
                
                // 检查所有可能的文件，按找到的第一个有效文件
                for (possibleFile in possibleFiles.distinct()) {
                    if (possibleFile.exists() && isMusicFile(possibleFile) && 
                        !processedPaths.contains(possibleFile.absolutePath)) {
                        foundFiles.add(index to possibleFile)
                        processedPaths.add(possibleFile.absolutePath)
                        break
                    }
                }
            }
            
            // 如果还是找不到，尝试更智能的匹配
            if (foundFiles.isEmpty()) {
                lines.forEachIndexed { index, line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                        return@forEachIndexed
                    }
                    
                    // 提取文件名，忽略路径
                    val fileName = File(trimmedLine).name
                    if (fileName.isNotEmpty()) {
                        // 在播放列表目录中查找
                        val localFile = File(playlistDir, fileName)
                        if (localFile.exists() && isMusicFile(localFile) && 
                            !processedPaths.contains(localFile.absolutePath)) {
                            foundFiles.add(index to localFile)
                            processedPaths.add(localFile.absolutePath)
                            return@forEachIndexed
                        }
                        
                        // 递归查找子目录
                        findMusicFileByName(playlistDir, fileName)?.let {
                            if (!processedPaths.contains(it.absolutePath)) {
                                foundFiles.add(index to it)
                                processedPaths.add(it.absolutePath)
                            }
                        }
                    }
                }
            }
            
            if (foundFiles.isEmpty()) {
                // 显示详细的错误信息给用户
                val errorMessage = buildString {
                    appendLine("播放列表: ${playlistFile.name}")
                    appendLine("路径: ${playlistFile.parent}")
                    appendLine("找到 ${lines.filter { it.trim().isNotEmpty() && !it.trim().startsWith("#") }.size} 个文件路径")
                }
                
                runOnUiThread {
                    Toast.makeText(this, "播放列表中没有找到有效的音乐文件，请检查文件路径", Toast.LENGTH_LONG).show()
                }
                
                // 打印调试信息到Logcat
                android.util.Log.d("PlaylistParser", "=== 播放列表解析调试 ===")
                android.util.Log.d("PlaylistParser", "播放列表文件: ${playlistFile.absolutePath}")
                android.util.Log.d("PlaylistParser", "播放列表目录: ${playlistDir.absolutePath}")
                android.util.Log.d("PlaylistParser", "尝试的基准路径: $basePaths")
                android.util.Log.d("PlaylistParser", "播放列表内容:")
                lines.forEachIndexed { index, line ->
                    android.util.Log.d("PlaylistParser", "  ${index + 1}: '$line'")
                }
            }
            
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "解析播放列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 按原始顺序排序并返回文件列表
        return foundFiles.sortedBy { it.first }.map { it.second }
    }
    
    private fun findMusicFileByName(directory: File, fileName: String): File? {
        val files = directory.listFiles() ?: return null
        
        for (file in files) {
            if (file.isDirectory) {
                findMusicFileByName(file, fileName)?.let { return it }
            } else if (file.name.equals(fileName, ignoreCase = true) && isMusicFile(file)) {
                return file
            }
        }
        
        return null
    }
}