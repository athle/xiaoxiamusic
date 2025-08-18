package com.maka.xiaoxia

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.EditText
import android.widget.TextView
import android.widget.SeekBar
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.Button
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import com.maka.xiaoxia.LyricsParser
import com.maka.xiaoxia.Song
import com.maka.xiaoxia.LyricLine
import com.maka.xiaoxia.LyricsView
import com.maka.xiaoxia.PreferenceHelper
import com.maka.xiaoxia.PermissionHelper
import com.maka.xiaoxia.MusicScanner
import com.maka.xiaoxia.SwipeSongAdapter
import com.maka.xiaoxia.MusicScanner.ScanOptions
import java.io.File
import java.util.concurrent.TimeUnit
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.io.BufferedReader
import java.io.InputStreamReader
class MainActivity : AppCompatActivity() {

    private var playPauseButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var prevButton: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var currentTimeText: TextView? = null
    private var totalTimeText: TextView? = null
    private var songTitleText: TextView? = null
    private var artistText: TextView? = null
    private var albumArtImage: ImageView? = null
    private var songListView: ListView? = null
    private var emptyView: TextView? = null
    private var lyricsView: LyricsView? = null
    private var playlistTitleText: TextView? = null
    
    // 横屏底栏控件
    private var songTitleControl: TextView? = null
    private var artistControl: TextView? = null
    
    // 添加缺失的变量
    private var songTitle: TextView? = null
    private var artistName: TextView? = null
    private var albumArt: ImageView? = null

    // 移除MediaPlayer实例，使用服务控制播放
    
    private var currentSongIndex = 0
    private var songList = mutableListOf<Song>()
    private var isPlaying = false
    private var handler = Handler()
    private var updateSeekBarRunnable: Runnable? = null
    private var preferenceHelper: PreferenceHelper? = null
    private var scanSettings: ScanSettings? = null
    private var drawerLayout: DrawerLayout? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    
    // 广播接收器
    private var musicControlReceiver: BroadcastReceiver? = null

    companion object {
        private const val REQUEST_CODE_PICK_MUSIC = 1001
        private const val REQUEST_IMPORT_M3U = 1002
        private const val TAG = "SimpleMusicPlayer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "应用崩溃: ${throwable.message}", throwable)
            throwable.printStackTrace()
            try {
                android.widget.Toast.makeText(this, "应用崩溃: ${throwable.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // 防止Toast异常
            }
        }
        
        try {
            // 延迟设置窗口属性，避免安卓15上的空指针异常
            Handler(Looper.getMainLooper()).post {
                try {
                    // 确保状态栏可见（解决安卓15状态栏显示问题）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // 安卓15状态栏显示优化
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                // 确保内容不延伸到状态栏区域
                                window.setDecorFitsSystemWindows(true)
                                window.insetsController?.let { controller ->
                                    // 确保状态栏可见
                                    controller.show(android.view.WindowInsets.Type.statusBars())
                                    // 设置状态栏图标颜色为深色（适合浅色主题）
                                    controller.setSystemBarsAppearance(
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    )
                                }
                                // 设置状态栏背景色（使用主题主色调）
                                window.statusBarColor = resources.getColor(R.color.colorPrimaryDark)
                            } catch (e: Exception) {
                                Log.d(TAG, "WindowInsetsController设置失败: ${e.message}")
                                // 回退到兼容模式
                                @Suppress("DEPRECATION")
                                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                                window.statusBarColor = resources.getColor(R.color.colorPrimaryDark)
                            }
                        } else {
                            // 兼容旧版本状态栏设置
                            @Suppress("DEPRECATION")
                            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                            window.statusBarColor = resources.getColor(R.color.colorPrimaryDark)
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "窗口设置延迟执行失败: ${e.message}")
                }
            }
            
            setContentView(R.layout.activity_main)
            
            // 添加菜单按钮
            supportActionBar?.setDisplayHomeAsUpEnabled(false)

            // 初始化PreferenceHelper
            preferenceHelper = PreferenceHelper(this)
            scanSettings = ScanSettings(this)
            
            // 安全地初始化视图
            drawerLayout = findViewById(R.id.drawer_layout)
            
            // 设置底部控制区域的菜单按钮（横竖屏通用）
            try {
                findViewById<ImageButton>(R.id.menu_button_control)?.setOnClickListener {
                    drawerLayout?.openDrawer(GravityCompat.START)
                }
            } catch (e: Exception) {
                Log.d(TAG, "菜单按钮初始化失败: ${e.message}")
            }
            
            // 设置侧边菜单按钮
            setupSideMenu()
            
            // 使用新的初始化方法
            initializeViews()
            
            // 延迟执行耗时操作，避免启动超时
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // 检查权限加载保存的数据
                    loadSavedData()
                    
                    // 检查并请求权限
                    checkPermissions()
                    
                    // 注册广播接收器
                    registerBroadcastReceiver()
                    
                    // 保存歌曲列表到服务使用的SharedPreferences
                    saveSongsToServicePrefs()
                    
                    // 启动后台音乐服务
                    startMusicService()
                    
                    // 绑定音乐服务
                    bindMusicService()
                } catch (e: Exception) {
                    Log.e(TAG, "延迟初始化失败: ${e.message}")
                }
            }, 50) // 延迟50毫秒执行，给UI线程更多时间
            
        } catch (e: Exception) {
            Log.e(TAG, "应用启动失败: ${e.message}")
            Toast.makeText(this, "应用启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // 安全退出
        }
    }

    private fun checkPermissions() {
        // 检查权限 - 初次安装不自动导入
        val isFirstRun = preferenceHelper?.getBoolean("first_run", true) ?: true
        
        // 记录权限状态
        Log.d(TAG, "检查权限 - 首次运行: $isFirstRun")
        Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "所有权限状态: ${PermissionHelper.hasAllPermissions(this)}")
        
        try {
            if (isFirstRun) {
                preferenceHelper?.putBoolean("first_run", false)
                emptyView?.visibility = View.VISIBLE
                songListView?.visibility = View.GONE
                emptyView?.text = "欢迎使用音乐播放器\n点击左上角菜单开始使用"
            } else if (PermissionHelper.hasAllPermissions(this)) {
                Log.d(TAG, "权限已授予，开始加载音乐数据")
                loadMusicData()
            } else {
                Log.d(TAG, "需要请求权限")
                // 安卓15及以上需要特殊处理权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (PermissionHelper.shouldShowRequestPermissionRationale(this)) {
                        // 显示权限说明对话框
                        android.app.AlertDialog.Builder(this)
                            .setTitle("权限说明")
                            .setMessage(PermissionHelper.getPermissionDescription())
                            .setPositiveButton("确定") { _, _ ->
                                PermissionHelper.requestPermissions(this)
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        PermissionHelper.requestPermissions(this)
                    }
                } else {
                    PermissionHelper.requestPermissions(this)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "权限检查异常: ${e.message}")
            // 安卓15上权限异常时不直接退出，给用户手动处理的机会
            runOnUiThread {
                emptyView?.text = "权限检查异常，请在系统设置中手动授予权限\n设置 > 应用 > 音乐播放器 > 权限"
                emptyView?.visibility = View.VISIBLE
                songListView?.visibility = View.GONE
                Toast.makeText(this, "请在系统设置中手动授予权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMusicData() {
        if (songList.isEmpty()) {
            loadMusicList()
        } else {
            updateSongList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQUEST_CODE_PERMISSIONS) {
            try {
                if (PermissionHelper.hasAllPermissions(this)) {
                    loadMusicData()
                } else {
                    // 权限被拒绝，显示提示
                    Toast.makeText(this, PermissionHelper.getPermissionDescription(), Toast.LENGTH_LONG).show()
                    
                    // 显示空列表提示
                    emptyView?.text = "需要权限才能访问音乐文件\n请在设置中授予音频访问权限"
                    emptyView?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "权限处理异常: ${e.message}")
                Toast.makeText(this, "权限处理异常，请重启应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateViewsVisibility() {
        if (songList.isEmpty()) {
            songListView?.visibility = View.GONE
            emptyView?.visibility = View.VISIBLE
        } else {
            songListView?.visibility = View.VISIBLE
            emptyView?.visibility = View.GONE
        }
    }

    // 添加服务连接
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private var isReceiverRegistered = false
    
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true
            
            // 同步歌曲列表到服务
            if (songList.isNotEmpty()) {
                musicService?.setSongList(songList)
            }
            
            // 从服务获取最新状态，确保与后台操作同步
            musicService?.let { service ->
                // 获取最新的歌曲索引和播放状态
                val latestIndex = service.getCurrentSongIndex()
                val latestPlaying = service.isPlaying()
                val latestSong = service.getCurrentSong()
                
                Log.d(TAG, "服务连接成功，获取最新状态: 索引=$latestIndex, 播放=$latestPlaying, 歌曲=${latestSong?.title}")
                
                // 更新本地状态
                if (latestIndex >= 0 && latestIndex < songList.size) {
                    currentSongIndex = latestIndex
                    if (latestSong != null) {
                        songList[latestIndex] = latestSong
                    }
                }
                this@MainActivity.isPlaying = latestPlaying
                
                // 强制更新UI
                updateUI()
                Log.d(TAG, "服务连接后UI同步完成")
            }
            
            Log.d(TAG, "服务已连接")
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            musicService = null
            isServiceBound = false
            Log.d(TAG, "服务已断开")
        }
    }
    
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun unbindMusicService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun loadSavedData() {
        val savedSongs = preferenceHelper?.loadSongList() ?: emptyList()
        if (savedSongs.isNotEmpty()) {
            songList.addAll(savedSongs)
            currentSongIndex = preferenceHelper?.getCurrentSongIndex() ?: 0
            isPlaying = preferenceHelper?.getIsPlaying() ?: false
            val savedPosition = preferenceHelper?.getCurrentPosition() ?: 0
            Log.d(TAG, "从保存数据中恢复: ${songList.size} 首歌曲, 当前索引: $currentSongIndex, 播放位置: $savedPosition")
            
            // 如果之前有保存的歌曲，准备播放
            if (songList.isNotEmpty() && currentSongIndex < songList.size) {
                val song = songList[currentSongIndex]
                Log.d(TAG, "准备播放: ${song.title} 从位置: ${formatTime(savedPosition)}")
                
                // 更新UI显示
                updateUI()
                
                // 显示恢复提示
                Toast.makeText(this, "已恢复上次播放: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "没有保存的播放列表，需要重新扫描")
        }
    }

    private fun saveCurrentState() {
        if (songList.isNotEmpty()) {
            preferenceHelper?.saveSongList(songList)
            preferenceHelper?.saveCurrentSongIndex(currentSongIndex)
            val currentPosition = if (isServiceBound) musicService?.getCurrentPosition() ?: 0 else 0
            preferenceHelper?.saveCurrentPosition(currentPosition)
            preferenceHelper?.saveIsPlaying(isPlaying)
        }
    }

    private fun registerBroadcastReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction("com.maka.xiaoxia.UPDATE_UI")
                addAction("com.maka.xiaoxia.PLAYBACK_COMPLETE")
                addAction("com.maka.xiaoxia.action.UPDATE_WIDGET") // 添加对小组件更新广播的监听
            }
            
            // 安卓14+需要指定RECEIVER_EXPORTED或RECEIVER_NOT_EXPORTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(uiUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(uiUpdateReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun saveSongsToServicePrefs() {
        val servicePrefs = getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        val editor = servicePrefs.edit()
        
        editor.putInt("song_count", songList.size)
        
        songList.forEachIndexed { index, song ->
            editor.putLong("song_${index}_id", song.id)
            editor.putString("song_${index}_title", song.title)
            editor.putString("song_${index}_artist", song.artist)
            editor.putString("song_${index}_album", song.album)
            editor.putLong("song_${index}_duration", song.duration)
            editor.putString("song_${index}_path", song.path)
        }
        
        editor.apply()
    }

    private fun startMusicService() {
        val serviceIntent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun loadSongsFromServicePrefs() {
        val servicePrefs = getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        val songCount = servicePrefs.getInt("song_count", 0)
        
        if (songCount > 0) {
            songList.clear()
            
            for (i in 0 until songCount) {
                val id = servicePrefs.getLong("song_${i}_id", 0)
                val title = servicePrefs.getString("song_${i}_title", "未知歌曲") ?: "未知歌曲"
                val artist = servicePrefs.getString("song_${i}_artist", "未知艺术家") ?: "未知艺术家"
                val album = servicePrefs.getString("song_${i}_album", "未知专辑") ?: "未知专辑"
                val duration = servicePrefs.getLong("song_${i}_duration", 0)
                val path = servicePrefs.getString("song_${i}_path", "") ?: ""
                val albumId = servicePrefs.getLong("song_${i}_albumId", 0)
                val lyrics = servicePrefs.getString("song_${i}_lyrics", null)
                
                if (path.isNotEmpty()) {
                    val song = Song(id, title, artist, album, duration, path, albumId, lyrics)
                    songList.add(song)
                }
            }
            
            Log.d(TAG, "从Service Prefs重新加载了 ${songList.size} 首歌曲")
        }
    }

    private fun loadMusicList() {
        songList.clear()
        
        // 显示加载状态
        emptyView?.text = "正在扫描音乐文件..."
        emptyView?.visibility = View.VISIBLE
        
        // 使用异步线程加载音乐数据，避免阻塞主线程
        Thread {
            try {
                val options = scanSettings?.getScanOptions() ?: ScanOptions()
                val scannedSongs = MusicScanner.scanMusic(this, options)
                
                Log.d(TAG, "扫描到 ${scannedSongs.size} 首歌曲 (模式: ${scanSettings?.getScanMode()})")
                
                // 回到主线程更新UI
                runOnUiThread {
                    if (scannedSongs.isEmpty()) {
                        // 如果当前扫描模式没有找到歌曲，提示用户
                        emptyView?.text = "没有找到音乐文件\n点击设置调整扫描模式"
                        emptyView?.setOnClickListener {
                            showScanSettingsDialog()
                        }
                    } else {
                        songList.addAll(scannedSongs)
                        emptyView?.setOnClickListener(null)
                        
                        // 保存新的音乐列表
                        preferenceHelper?.saveSongList(songList)
                        
                        // 同步歌曲列表到服务
                        saveSongsToServicePrefs()
                        if (isServiceBound && musicService != null) {
                            musicService?.setSongList(songList)
                            Log.d(TAG, "已同步歌曲列表到服务，共 ${songList.size} 首歌曲")
                        }
                        
                        Log.d(TAG, "加载完成，共找到 ${songList.size} 首歌曲")
                    }
                    
                    updateSongList()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "加载音乐列表失败: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "加载音乐失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    emptyView?.text = "加载音乐失败\n${e.message}"
                    updateSongList()
                }
            }
        }.start()
    }
    
    private fun showScanSettingsDialog() {
        val modes = arrayOf("智能扫描", "快速扫描", "完整扫描", "自定义扫描")
        val modeValues = arrayOf(ScanSettings.MODE_SMART, ScanSettings.MODE_QUICK, ScanSettings.MODE_FULL, ScanSettings.MODE_CUSTOM)
        val currentMode = scanSettings?.getScanMode() ?: ScanSettings.MODE_SMART
        val currentIndex = modeValues.indexOf(currentMode)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("选择扫描模式")
            .setSingleChoiceItems(modes, currentIndex) { dialog, which ->
                scanSettings?.setScanMode(modeValues[which])
                dialog.dismiss()
                loadMusicList()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, 1, 0, "扫描设置")
            .setIcon(android.R.drawable.ic_menu_preferences)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                showScanSettingsDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun setupSideMenu() {
        try {
            // 检查是否存在按钮，避免空指针异常
            findViewById<Button>(R.id.btn_home)?.setOnClickListener {
                drawerLayout?.closeDrawer(GravityCompat.START)
                // 主页功能已实现，无需额外操作
            }
            
            findViewById<Button>(R.id.btn_playlist)?.setOnClickListener {
                showPlaylistDialog()
                drawerLayout?.closeDrawer(GravityCompat.START)
            }
            
            findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
                showScanSettingsDialog()
                drawerLayout?.closeDrawer(GravityCompat.START)
            }
            
            findViewById<ImageButton>(R.id.btn_exit)?.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("退出应用")
                    .setMessage("确定要退出应用吗？")
                    .setPositiveButton("确定") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            
            // 检查侧边菜单列表是否存在
            val sideMenuList = findViewById<ListView>(R.id.side_menu_list)
            if (sideMenuList != null) {
                // 横屏布局使用ListView作为侧边菜单
                val menuItems = listOf("主页", "播放列表", "设置", "退出")
                val menuAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, menuItems)
                sideMenuList.adapter = menuAdapter
                
                sideMenuList.setOnItemClickListener { _, _, position, _ ->
                    when (position) {
                        0 -> {
                            // 主页
                            drawerLayout?.closeDrawer(GravityCompat.START)
                        }
                        1 -> {
                            // 播放列表
                            showPlaylistDialog()
                            drawerLayout?.closeDrawer(GravityCompat.START)
                        }
                        2 -> {
                            // 设置
                            showScanSettingsDialog()
                            drawerLayout?.closeDrawer(GravityCompat.START)
                        }
                        3 -> {
                            // 退出
                            AlertDialog.Builder(this)
                                .setTitle("退出应用")
                                .setMessage("确定要退出应用吗？")
                                .setPositiveButton("确定") { _, _ ->
                                    finish()
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                }
            }
            
            // 初始化播放列表显示
            updatePlaylistDisplay()
        } catch (e: Exception) {
            Log.e(TAG, "侧边菜单设置失败: ${e.message}")
            // 侧边菜单初始化失败不影响主功能
        }
    }
    
    private fun showPlaylistDialog() {
        val options = arrayOf("导入M3U播放列表", "创建新播放列表", "管理播放列表")
        AlertDialog.Builder(this)
            .setTitle("播放列表管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importM3UPlaylist()
                    1 -> createNewPlaylist()
                    2 -> managePlaylists()
                }
            }
            .show()
    }
    
    private fun importM3UPlaylist() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, REQUEST_IMPORT_M3U)
    }

    private fun importM3UPlaylist(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            reader.close()
            
            val playlistName = uri.lastPathSegment?.removeSuffix(".m3u") ?: "导入播放列表"
            val songPaths = lines.filter { it.isNotEmpty() && !it.startsWith("#") }
            
            if (songPaths.isEmpty()) {
                Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 创建播放列表
            createPlaylist(playlistName)
            
            // 添加歌曲到播放列表
            val playlistKey = "playlist_$playlistName"
            val songIds = mutableSetOf<String>()
            
            for (path in songPaths) {
                for (song in songList) {
                    if (song.path == path) {
                        songIds.add(song.id.toString())
                        break
                    }
                }
            }
            
            if (songIds.isNotEmpty()) {
                preferenceHelper?.saveStringSet(playlistKey, songIds)
                Toast.makeText(this, "已导入 ${songIds.size} 首歌曲到 $playlistName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未找到匹配的歌曲", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_IMPORT_M3U -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        importPlaylistFromUri(uri)
                    }
                }
            }
            REQUEST_CODE_PICK_MUSIC -> {
                if (resultCode == Activity.RESULT_OK) {
                    // 处理选择音乐文件的逻辑
                    data?.data?.let { uri ->
                        addSongFromUri(uri)
                    }
                }
            }
        }
    }

    private fun addSongFromUri(uri: Uri) {
        // 处理添加单个音乐文件的逻辑
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val titleIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val title = if (titleIndex != -1) it.getString(titleIndex) else "未知歌曲"
                    
                    val song = Song(
                        id = System.currentTimeMillis(),
                        title = title,
                        artist = "未知艺术家",
                        album = "未知专辑",
                        duration = 0,
                        path = uri.toString(),
                        albumId = 0
                    )
                    
                    songList.add(song)
                    updateSongList()
                    saveCurrentState()
                    Toast.makeText(this, "已添加: $title", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateSongList() {
        val adapter = SwipeSongAdapter(this, songList)
        songListView?.adapter = adapter
        updateViewsVisibility()
        updatePlaylistTitle()
    }

    private fun deleteSong(position: Int) {
        if (position < 0 || position >= songList.size) return
        
        try {
            val songToDelete = songList[position]
            
            // 如果正在播放要删除的歌曲，先停止播放
            if (currentSongIndex == position) {
                if (isServiceBound) {
                    musicService?.stopService()
                }
                isPlaying = false
                updatePlayPauseButton()
                stopSeekBarUpdate()
            }
            
            // 调整当前播放索引
            if (currentSongIndex > position) {
                currentSongIndex--
            } else if (currentSongIndex == position && songList.size > 1) {
                // 如果删除的是当前播放的歌曲，播放下一个
                if (currentSongIndex >= songList.size - 1) {
                    currentSongIndex = 0
                }
            }
            
            // 从列表中移除歌曲
            songList.removeAt(position)
            
            // 保存更新后的播放列表
            saveCurrentState()
            
            // 更新适配器
            updateSongList()
            updateViewsVisibility()
            
            Toast.makeText(this, "已删除: ${songToDelete.title}", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "删除歌曲失败: ${e.message}")
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 在MainActivity类中添加playSong方法
    fun playSong(position: Int) {
        if (songList.isEmpty()) {
            Log.e(TAG, "无法播放：歌曲列表为空")
            Toast.makeText(this, "请先扫描音乐文件", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (position >= 0 && position < songList.size) {
            currentSongIndex = position
            
            // 通过服务播放歌曲
            if (isServiceBound && musicService != null) {
                // 确保服务有最新的歌曲列表
                musicService?.setSongList(songList)
                musicService?.playSong(position)
                isPlaying = true
                updatePlayPauseButton()
                updateUI()
            } else {
                // 服务未连接，先启动服务
                Log.d(TAG, "服务未连接，先同步歌曲列表到服务")
                
                // 先保存歌曲列表到服务Prefs
                saveSongsToServicePrefs()
                
                val serviceIntent = Intent(this, MusicService::class.java).apply {
                    action = "PLAY_SONG"
                    putExtra("song_index", position)
                }
                startService(serviceIntent)
                
                // 延迟绑定服务
                Handler().postDelayed({
                    bindMusicService()
                }, 500)
            }
            
            // 更新歌词
            updateLyrics()
            
            // 保存状态
            saveCurrentState()
            
            // 更新小部件
            updateWidget()
        } else {
            Log.e(TAG, "无效的播放位置: $position，列表大小: ${songList.size}")
        }
    }
    
    // 添加其他必要的方法
    private fun updatePlayPauseButton() {
        playPauseButton?.let {
            if (isPlaying) {
                it.setImageResource(R.drawable.ic_pause)
            } else {
                it.setImageResource(R.drawable.ic_play)
            }
        }
    }
    
    private fun updateLyrics() {
        if (currentSongIndex >= 0 && currentSongIndex < songList.size) {
            val song = songList[currentSongIndex]
            
            // 更新竖屏主界面歌曲信息
            songTitleText?.text = song.title
            artistText?.text = song.artist
            
            // 更新横屏底栏歌曲信息
            songTitleControl?.text = song.title
            artistControl?.text = song.artist
            
            totalTimeText?.text = formatTime(song.duration)
            loadAlbumArt(song.albumId)
            loadLyrics(song)
        }
    }
    
    private fun updateWidget() {
        val intent = Intent(this, MusicWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, MusicWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun initializeViews() {
        try {
            // 安全地初始化视图，处理可能的null情况
            playPauseButton = findViewById(R.id.btn_play_pause)
            nextButton = findViewById(R.id.btn_next)
            prevButton = findViewById(R.id.btn_prev)
            seekBar = findViewById(R.id.seek_bar)
            currentTimeText = findViewById(R.id.current_time)
            totalTimeText = findViewById(R.id.total_time)
            songTitleText = findViewById(R.id.song_title)
            artistText = findViewById(R.id.artist)
            albumArtImage = findViewById(R.id.album_art)
            songListView = findViewById(R.id.song_list)
            emptyView = findViewById(R.id.empty_view)
            lyricsView = findViewById(R.id.lyrics_view)
            playlistTitleText = findViewById(R.id.playlist_title)
            
            // 安全初始化横屏底栏控件 - 仅在横屏布局存在时获取
            try {
                songTitleControl = findViewById(R.id.song_title_control)
                artistControl = findViewById(R.id.artist_control)
            } catch (e: Exception) {
                // 竖屏模式下这些视图不存在，忽略异常
                Log.d(TAG, "横屏视图初始化失败: ${e.message}")
                songTitleControl = null
                artistControl = null
            }
            
            // 设置播放控制按钮的点击监听器
            setupPlaybackControls()
            
            // 检查关键视图是否初始化成功
            if (songListView == null || emptyView == null) {
                Log.e(TAG, "关键视图初始化失败: songListView=${songListView}, emptyView=${emptyView}")
                // 不返回，继续尝试运行
            }
            
            // 记录初始化状态
            Log.d(TAG, "视图初始化完成: 共找到 ${songList?.size ?: 0} 个列表项")
        } catch (e: Exception) {
            Log.e(TAG, "视图初始化失败: ${e.message}")
            Log.e(TAG, "异常详情: ${e.stackTraceToString()}")
            // 在安卓15上，捕获异常但不退出，尝试继续运行
            runOnUiThread {
                Toast.makeText(this, "界面初始化遇到小问题，尝试继续运行...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 格式化时间显示
    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun setupPlaybackControls() {
        // 设置播放/暂停按钮点击事件
        playPauseButton?.setOnClickListener {
            togglePlayPause()
        }
        
        // 设置下一首按钮点击事件
        nextButton?.setOnClickListener {
            playNext()
        }
        
        // 设置上一首按钮点击事件
        prevButton?.setOnClickListener {
            playPrevious()
        }
        
        // 设置进度条拖动事件
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isServiceBound) {
                    val duration = musicService?.getDuration() ?: 0
                    if (duration > 0) {
                        val newPosition = (duration * progress) / 100
                        musicService?.seekTo(newPosition)
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 暂停进度条更新
                stopSeekBarUpdate()
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 恢复进度条更新
                startSeekBarUpdate()
            }
        })
    }

    private fun updateUI() {
        updateCurrentSongInfo()
        updatePlayPauseButton()
        updateWidget()
        updateServicePrefs()
        startSeekBarUpdate()
    }

    private fun updateCurrentSongInfo() {
        if (currentSongIndex >= 0 && currentSongIndex < songList.size) {
            val song = songList[currentSongIndex]
            
            // 安全更新竖屏主界面歌曲信息
            songTitleText?.text = song.title
            artistText?.text = song.artist
            
            // 安全更新横屏底栏歌曲信息（仅在横屏布局存在时）
            try {
                songTitleControl?.text = song.title
                artistControl?.text = song.artist
            } catch (e: Exception) {
                // 捕获可能的空指针异常，不影响主功能
                Log.d(TAG, "横屏视图更新失败: ${e.message}")
            }
            
            totalTimeText?.text = formatTime(song.duration)
            loadAlbumArt(song.albumId)
            loadLyrics(song)
        }
    }

    private fun togglePlayPause() {
        if (isServiceBound) {
            musicService?.togglePlayPause()
            updateUI()
        } else {
            Toast.makeText(this, "音乐服务未连接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playNext() {
        if (isServiceBound) {
            musicService?.playNext()
            currentSongIndex = musicService?.getCurrentSongIndex() ?: currentSongIndex
            updateUI()
        } else {
            Toast.makeText(this, "音乐服务未连接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPrevious() {
        if (isServiceBound) {
            musicService?.playPrevious()
            currentSongIndex = musicService?.getCurrentSongIndex() ?: currentSongIndex
            updateUI()
        } else {
            Toast.makeText(this, "音乐服务未连接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(position: Int) {
        if (position < 0 || position >= songList.size) return
        
        val song = songList[position]
        AlertDialog.Builder(this)
            .setTitle("删除歌曲")
            .setMessage("确定要删除歌曲 " + song.title + " 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteSong(position)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "播放列表名称"
        
        AlertDialog.Builder(this)
            .setTitle("创建播放列表")
            .setView(input)
            .setPositiveButton("创建") { dialog, _ ->
                val playlistName = input.text.toString().trim()
                if (playlistName.isNotEmpty()) {
                    createPlaylist(playlistName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val playlistNames = getSavedPlaylists().keys.toList()
        if (playlistNames.isEmpty()) {
            Toast.makeText(this, "没有可用的播放列表", Toast.LENGTH_SHORT).show()
            return
        }
        
        val playlists = playlistNames.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("添加到播放列表")
            .setItems(playlists) { dialog, which ->
                val playlistName = playlists[which]
                addToPlaylist(song, playlistName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPlaylistOptionsDialog(playlistName: String) {
        val options = arrayOf("播放", "重命名", "删除")
        AlertDialog.Builder(this)
            .setTitle(playlistName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playPlaylist(playlistName)
                    1 -> showRenamePlaylistDialog(playlistName)
                    2 -> showDeletePlaylistConfirmation(playlistName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenamePlaylistDialog(oldName: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(oldName)
        
        AlertDialog.Builder(this)
            .setTitle("重命名播放列表")
            .setView(input)
            .setPositiveButton("重命名") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    renamePlaylist(oldName, newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeletePlaylistConfirmation(playlistName: String) {
        AlertDialog.Builder(this)
            .setTitle("删除播放列表")
            .setMessage("确定要删除播放列表 '$playlistName' 吗？")
            .setPositiveButton("删除") { _, _ ->
                deletePlaylist(playlistName)
            }
            .setNegativeButton("取消", null)
            .show()
    }



    private fun loadAlbumArt(albumId: Long) {
        if (currentSongIndex < 0 || currentSongIndex >= songList.size) {
            albumArtImage?.setImageResource(R.drawable.ic_music_default)
            return
        }
        
        val song = songList[currentSongIndex]
        loadAlbumArtFromSong(song)
    }

    private fun loadAlbumArtFromSong(song: Song) {
        try {
            Log.d(TAG, "从歌曲文件加载专辑封面: ${song.title}")
            
            val retriever = MediaMetadataRetriever()
            try {
                // 设置数据源为歌曲文件路径
                retriever.setDataSource(song.path)
                
                // 获取嵌入的专辑封面
                val art = retriever.embeddedPicture
                if (art != null) {
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    if (bitmap != null) {
                        albumArtImage?.setImageBitmap(bitmap)
                        Log.d(TAG, "成功从ID3标签加载专辑封面")
                        retriever.release()
                        return
                    }
                }
                
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "从ID3标签加载专辑封面失败: ${e.message}")
                try {
                    retriever.release()
                } catch (releaseEx: Exception) {
                    Log.w(TAG, "释放MediaMetadataRetriever失败: ${releaseEx.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "加载专辑封面失败: ${e.message}")
        }
        
        // 使用默认图片
            albumArtImage?.setImageResource(R.drawable.ic_music_default)
            Log.d(TAG, "使用默认专辑封面")
    }

    private fun startSeekBarUpdate() {
        stopSeekBarUpdate()
        
        // 如果没有歌曲，不启动更新循环
        if (songList.isEmpty()) {
            Log.d(TAG, "没有歌曲，跳过进度条更新")
            return
        }
        
        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                if (isServiceBound) {
                    musicService?.let { service ->
                        // 检查MediaPlayer是否已准备就绪
                        if (!service.isMediaPlayerReady()) {
                            Log.d(TAG, "MediaPlayer未就绪，跳过进度更新")
                            handler.postDelayed(this, 1000)
                            return
                        }
                        
                        try {
                            val currentPosition = service.getCurrentPosition()
                            val duration = service.getDuration()
                            
                            // 只在有有效duration时更新UI
                            if (duration > 0 && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                                val progress = (currentPosition * 100) / duration
                                seekBar?.progress = progress
                                currentTimeText?.text = formatTime(currentPosition.toLong())
                                totalTimeText?.text = formatTime(duration.toLong())
                                
                                // 更新歌词显示
                                lyricsView?.updateProgress(currentPosition.toLong())
                                
                                // 同步播放状态
                                isPlaying = service.isPlaying()
                                updatePlayPauseButton()
                            } else {
                                // 如果歌曲列表为空，停止更新
                                stopSeekBarUpdate()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "更新进度时出错: ${e.message}")
                        }
                    }
                }
                
                handler.postDelayed(this, 1000)
            }
        }
        
        handler.post(updateSeekBarRunnable as Runnable)
    }

    private fun loadLyrics(song: Song) {
        try {
            val parser = LyricsParser()
            val (lyricsText, lyricLines) = parser.getLyricsForSong(song)
            
            Log.d(TAG, "加载歌词: ${song.title}")
            Log.d(TAG, "从ID3标签找到歌词: ${!lyricsText.isNullOrEmpty()}")
            Log.d(TAG, "解析到的歌词行数: ${lyricLines.size}")
            
            if (lyricLines.isNotEmpty()) {
                currentLyrics = lyricLines
                lyricsView?.setLyrics(currentLyrics)
                Log.d(TAG, "歌词加载成功: ${lyricLines.size} 行")
            } else {
                currentLyrics = emptyList()
                lyricsView?.setNoLyricsMessage()
                Log.d(TAG, "未找到歌词，显示无歌词消息")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "加载歌词失败: ${e.message}")
            currentLyrics = emptyList()
            lyricsView?.setNoLyricsMessage()
        }
    }

    private fun stopSeekBarUpdate() {
        updateSeekBarRunnable?.let { handler.removeCallbacks(it) }
    }




    

    
    private fun unregisterBroadcastReceiver() {
        try {
            // 只在销毁时真正注销广播接收器
            // 注销音乐控制广播接收器
            if (isReceiverRegistered && musicControlReceiver != null) {
                try {
                    unregisterReceiver(musicControlReceiver)
                    musicControlReceiver = null
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "音乐控制广播接收器未注册，忽略注销错误")
                }
            }
            
            // 注销UI更新广播接收器
            try {
                unregisterReceiver(uiUpdateReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "UI更新广播接收器未注册，忽略注销错误")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "注销广播接收器异常: ${e.message}")
        }
        isReceiverRegistered = false
    }
    
    private fun startMusicService(action: String) {
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        startService(serviceIntent)
    }

    private fun updateServicePrefs() {
        val servicePrefs = getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        servicePrefs.edit().apply {
            val isServicePlaying = if (isServiceBound) musicService?.isPlaying() else false
            putBoolean("is_playing", isServicePlaying ?: false)
            putInt("current_song_index", currentSongIndex)
            
            if (currentSongIndex >= 0 && currentSongIndex < songList.size) {
                val song = songList[currentSongIndex]
                putString("current_song_path", song.path)
                putString("current_title", song.title)
                putString("current_artist", song.artist)
                putString("current_album", song.album)
                putLong("current_album_id", song.albumId)
                putString("current_lyrics", song.lyrics ?: "")
            }
            apply()
        }
    }

    fun createNewPlaylist() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("创建新播放列表")
            .setMessage("请输入播放列表名称：")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val playlistName = editText.text.toString().trim()
                if (playlistName.isNotEmpty()) {
                    createPlaylist(playlistName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun managePlaylists() {
        // 管理播放列表界面
        val playlists = getSavedPlaylists()
        if (playlists.isEmpty()) {
            Toast.makeText(this, "暂无播放列表", Toast.LENGTH_SHORT).show()
            return
        }
        
        val playlistNames = playlists.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择播放列表")
            .setItems(playlistNames) { dialog, which ->
                val selectedPlaylist = playlistNames[which]
                showPlaylistOptionsDialog(selectedPlaylist)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun createPlaylist(name: String) {
        val playlists = preferenceHelper?.getStringSet("playlists", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        playlists.add(name)
        preferenceHelper?.saveStringSet("playlists", playlists)
        Toast.makeText(this, "已创建播放列表: $name", Toast.LENGTH_SHORT).show()
    }

    fun importPlaylistFromUri(uri: Uri) {
        importM3UPlaylist(uri)
    }

    fun addToPlaylist(song: Song, playlistName: String) {
        val playlistKey = "playlist_$playlistName"
        val playlistSongs = preferenceHelper?.getStringSet(playlistKey, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        playlistSongs.add(song.id.toString())
        preferenceHelper?.saveStringSet(playlistKey, playlistSongs)
        Toast.makeText(this, "已添加到播放列表: $playlistName", Toast.LENGTH_SHORT).show()
    }

    fun playPlaylist(playlistName: String) {
        val playlistKey = "playlist_$playlistName"
        val songIds = preferenceHelper?.getStringSet(playlistKey, mutableSetOf()) ?: mutableSetOf()
        if (songIds.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        val playlistSongs = songList.filter { song -> songIds.contains(song.id.toString()) }
        if (playlistSongs.isEmpty()) {
            Toast.makeText(this, "播放列表中没有找到歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        
        songList.clear()
        songList.addAll(playlistSongs)
        currentSongIndex = 0
        playSong(0)
        updateSongList()
        
        // 保存当前播放列表名称
        preferenceHelper?.saveCurrentPlaylist(playlistName)
        updatePlaylistTitle()
    }

    fun deletePlaylist(playlistName: String) {
        val playlists = preferenceHelper?.getStringSet("playlists", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        playlists.remove(playlistName)
        preferenceHelper?.saveStringSet("playlists", playlists)
        
        // 删除播放列表的歌曲数据
        val playlistKey = "playlist_$playlistName"
        preferenceHelper?.remove(playlistKey)
        
        Toast.makeText(this, "已删除播放列表: $playlistName", Toast.LENGTH_SHORT).show()
    }

    fun renamePlaylist(oldName: String, newName: String) {
        val playlists = preferenceHelper?.getStringSet("playlists", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        playlists.remove(oldName)
        playlists.add(newName)
        preferenceHelper?.saveStringSet("playlists", playlists)
        
        // 重命名播放列表的歌曲数据
        val oldKey = "playlist_$oldName"
        val newKey = "playlist_$newName"
        val songs = preferenceHelper?.getStringSet(oldKey, mutableSetOf()) ?: mutableSetOf()
        preferenceHelper?.saveStringSet(newKey, songs)
        preferenceHelper?.remove(oldKey)
        
        Toast.makeText(this, "已重命名播放列表", Toast.LENGTH_SHORT).show()
    }

    fun getSavedPlaylists(): Map<String, Set<String>> {
        val playlists = preferenceHelper?.getStringSet("playlists", mutableSetOf()) ?: mutableSetOf()
        val result = mutableMapOf<String, Set<String>>()
        
        for (playlistName in playlists) {
            val playlistKey = "playlist_$playlistName"
            val songs = preferenceHelper?.getStringSet(playlistKey, mutableSetOf()) ?: mutableSetOf()
            result[playlistName] = songs
        }
        
        return result
    }
    
    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun updatePlaylistDisplay() {
        updateSongList()
        updatePlaylistTitle()
    }
    
    private fun updatePlaylistTitle() {
        val currentPlaylist = preferenceHelper?.getCurrentPlaylist() ?: ""
        if (currentPlaylist.isNotEmpty()) {
            playlistTitleText?.text = "播放列表: $currentPlaylist"
        } else {
            playlistTitleText?.text = "所有歌曲"
        }
    }
    
    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.maka.xiaoxia.UPDATE_UI" -> {
                    // 收到UI更新广播，优先使用广播中的数据
                    Log.d(TAG, "收到UPDATE_UI广播，准备更新UI")
                    
                    // 从广播获取完整信息
                    val songIndex = intent.getIntExtra("current_song_index", -1)
                    val isPlaying = intent.getBooleanExtra("is_playing", false)
                    val songTitle = intent.getStringExtra("current_title") ?: ""
                    val songArtist = intent.getStringExtra("current_artist") ?: ""
                    val songAlbum = intent.getStringExtra("current_album") ?: ""
                    val songPath = intent.getStringExtra("current_path") ?: ""
                    val songAlbumId = intent.getLongExtra("current_album_id", 0L)
                    val songDuration = intent.getLongExtra("current_duration", 0L)
                    val songCount = intent.getIntExtra("song_count", 0)
                    
                    Log.d(TAG, "UPDATE_UI广播数据: 索引=$songIndex, 标题=$songTitle, 播放=$isPlaying")
                    
                    if (songIndex >= 0 && songIndex < songList.size) {
                        // 使用广播中的数据更新状态
                        currentSongIndex = songIndex
                        this@MainActivity.isPlaying = isPlaying
                        
                        // 创建新的Song对象更新当前歌曲信息
                        val updatedSong = Song(
                            id = songList[songIndex].id,
                            title = songTitle,
                            artist = songArtist,
                            album = songAlbum,
                            duration = songDuration,
                            path = songPath,
                            albumId = songAlbumId
                        )
                        
                        // 更新歌曲列表中的对应位置
                        songList[songIndex] = updatedSong
                        Log.d(TAG, "从广播同步歌曲信息: $songTitle")
                        
                        updateUI()
                        Log.d(TAG, "UPDATE_UI广播处理完成，当前歌曲: $songTitle, 索引: $songIndex")
                    } else if (isServiceBound) {
                        // 广播数据不完整，尝试从服务获取
                        musicService?.let { service ->
                                // 确保服务有最新的歌曲列表
                                if (songList.isNotEmpty()) {
                                    service.setSongList(songList)
                                    Log.d(TAG, "服务绑定后同步歌曲列表，共 ${songList.size} 首")
                                }
                                
                                currentSongIndex = service.getCurrentSongIndex()
                                this@MainActivity.isPlaying = service.isPlaying()
                                
                                val currentSong = service.getCurrentSong()
                                if (currentSong != null && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                                    songList[currentSongIndex] = currentSong
                                    Log.d(TAG, "从服务同步歌曲信息: ${currentSong.title}")
                                }
                                
                                updateUI()
                                Log.d(TAG, "UPDATE_UI广播处理完成（从服务），当前歌曲: ${currentSong?.title}, 索引: $currentSongIndex")
                            }
                    } else {
                        // 最后才从SharedPreferences获取
                        loadSavedData()
                        updateUI()
                        Log.d(TAG, "服务未绑定，从SharedPreferences更新状态")
                    }
                }
                "com.maka.xiaoxia.PLAYBACK_COMPLETE" -> {
                    updateUI()
                    Log.d(TAG, "收到播放完成广播")
                }
                "com.maka.xiaoxia.action.UPDATE_WIDGET" -> {
                    // 处理小组件更新广播
                    Log.d(TAG, "收到UPDATE_WIDGET广播")
                    if (isServiceBound) {
                        // 优先从服务获取状态
                        musicService?.let { service ->
                            currentSongIndex = service.getCurrentSongIndex()
                            this@MainActivity.isPlaying = service.isPlaying()
                            
                            val currentSong = service.getCurrentSong()
                            if (currentSong != null && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                                songList[currentSongIndex] = currentSong
                            }
                            
                            updateUI()
                            Log.d(TAG, "从UPDATE_WIDGET广播同步，当前歌曲: ${currentSong?.title}")
                        }
                    } else {
                        // 备用：从广播获取基本信息
                        val songIndex = intent.getIntExtra("current_song_index", 0)
                        val isPlaying = intent.getBooleanExtra("is_playing", false)
                        
                        if (songIndex >= 0 && songIndex < songList.size) {
                            currentSongIndex = songIndex
                            this@MainActivity.isPlaying = isPlaying
                            updateUI()
                            Log.d(TAG, "从UPDATE_WIDGET广播获取基本信息，索引: $songIndex")
                        }
                    }
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // 应用进入后台时保存当前播放位置
        if (isServiceBound && musicService != null) {
            val currentPosition = musicService?.getCurrentPosition() ?: 0
            preferenceHelper?.saveCurrentPosition(currentPosition)
            Log.d(TAG, "应用进入后台，保存播放位置: ${formatTime(currentPosition)}")
        }
        
        // 保留广播接收器注册，确保即使App在后台也能收到更新
        // 不再注销广播接收器，避免后台状态下无法同步
        Log.d(TAG, "MainActivity暂停，但保留广播接收器")
    }
    
    override fun onResume() {
        super.onResume()
        
        // 确保广播接收器已注册
        registerBroadcastReceiver()
        
        // 延迟同步状态，确保服务已完全就绪
        Handler(Looper.getMainLooper()).postDelayed({
            // 主动从服务同步当前状态
            if (isServiceBound) {
                musicService?.let { service ->
                    currentSongIndex = service.getCurrentSongIndex()
                    this@MainActivity.isPlaying = service.isPlaying()
                    
                    val currentSong = service.getCurrentSong()
                    if (currentSong != null && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                        songList[currentSongIndex] = currentSong
                    }
                    
                    updateUI()
                    Log.d(TAG, "onResume时主动同步状态，当前歌曲: ${currentSong?.title}")
                }
            } else {
                // 如果服务未绑定，重新绑定并同步
                bindMusicService()
                
                // 延迟再次尝试同步
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isServiceBound) {
                        musicService?.let { service ->
                            // 确保服务有最新的歌曲列表
                            if (songList.isNotEmpty()) {
                                service.setSongList(songList)
                                Log.d(TAG, "服务绑定后同步歌曲列表，共 ${songList.size} 首")
                            }
                            
                            currentSongIndex = service.getCurrentSongIndex()
                            this@MainActivity.isPlaying = service.isPlaying()
                            
                            val currentSong = service.getCurrentSong()
                            if (currentSong != null && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                                songList[currentSongIndex] = currentSong
                            }
                            
                            updateUI()
                            Log.d(TAG, "重新绑定后同步状态，当前歌曲: ${currentSong?.title}")
                        }
                    }
                }, 300)
            }
        }, 100)
    }
    
    override fun onStop() {
        super.onStop()
        
        // 停止时解绑服务，但保留服务运行以支持后台播放
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
                isServiceBound = false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "服务未绑定，忽略解绑错误")
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        
        // 启动时重新绑定服务
        if (!isServiceBound) {
            bindMusicService()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 应用完全退出时保存完整的播放状态
        try {
            // 保存当前播放状态
            saveCurrentState()
            
            // 如果服务已绑定，获取最新的播放位置
            if (isServiceBound && musicService != null) {
                val currentPosition = musicService?.getCurrentPosition() ?: 0
                preferenceHelper?.saveCurrentPosition(currentPosition)
                Log.d(TAG, "应用退出，保存完整播放状态: ${formatTime(currentPosition)}")
            }
            
            // 清理所有资源，防止内存泄漏
            unregisterBroadcastReceiver()
            
            // 解绑服务
            if (isServiceBound) {
                try {
                    unbindService(serviceConnection)
                    isServiceBound = false
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "服务未绑定，忽略解绑错误")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy清理资源异常: ${e.message}")
        }
    }
}