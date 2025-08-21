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
import android.widget.PopupMenu
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
// 扫描功能已移除 - MusicScanner导入已删除
import com.maka.xiaoxia.SwipeSongAdapter
// 扫描功能已移除 - ScanOptions导入已删除
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
    // 扫描功能已移除 - scanSettings变量已删除
    private var drawerLayout: DrawerLayout? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    
    // 广播接收器
    private var musicControlReceiver: BroadcastReceiver? = null
    
    // 播放模式控制
    private var playModeButton: ImageButton? = null
    private var currentPlayMode = PlayMode.REPEAT_ALL
    
    // 播放模式枚举
    enum class PlayMode {
        REPEAT_ONE,      // 循环播放当前1首歌曲
        PLAY_ORDER,      // 顺序播放完整个播放列表不循环
        REPEAT_ALL,      // 按列表顺序循环播放整个列表
        SHUFFLE          // 乱序播放整个播放列表
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PICK_MUSIC = 1001
        private const val REQUEST_ADD_SINGLE = 1003
        private const val REQUEST_IMPORT_FOLDER_LEGACY = 1006
        private const val REQUEST_ADD_SINGLE_LEGACY = 1007
        private const val REQUEST_ADD_SINGLE_FILE_BROWSER = 1008
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
            // 扫描功能已移除 - ScanSettings初始化已删除
            
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
            }, 100) // 延迟100毫秒执行，给UI线程更多时间
            
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
        Log.d(TAG, "ColorOS通知权限状态: ${ColorOSHelper.getNotificationPermissionStatus(this)}")
        
        try {
            if (isFirstRun) {
                preferenceHelper?.putBoolean("first_run", false)
                emptyView?.visibility = View.VISIBLE
                songListView?.visibility = View.GONE
                emptyView?.text = "欢迎使用音乐播放器\n点击左上角菜单开始使用"
            } else if (PermissionHelper.hasAllPermissions(this)) {
                Log.d(TAG, "权限已授予，开始加载音乐数据")
                
                // 检查ColorOS 15通知权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!ColorOSHelper.checkColorOS15NotificationPermission(this)) {
                        showColorOS15NotificationDialog()
                        return
                    }
                }
                
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
    
    /**
     * 显示ColorOS 15通知权限对话框
     */
    private fun showColorOS15NotificationDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("ColorOS 15通知权限")
            .setMessage("检测到您使用的是ColorOS 15系统，需要开启通知权限才能正常显示播放控制通知。\n\n请前往设置 > 通知与状态栏 > 音乐播放器 > 允许通知")
            .setPositiveButton("前往设置") { _, _ ->
                ColorOSHelper.openColorOS15NotificationSettings(this)
            }
            .setNegativeButton("稍后") { _, _ ->
                Toast.makeText(this, "通知权限未开启，可能影响使用体验", Toast.LENGTH_LONG).show()
                loadMusicData()
            }
            .setCancelable(false)
            .show()
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
                addAction("com.maka.xiaoxia.UPDATE_ALL_COMPONENTS") // 新的统一广播
                addAction("com.maka.xiaoxia.UPDATE_UI") // 兼容旧版本
                addAction("com.maka.xiaoxia.PLAYBACK_COMPLETE")
                addAction("com.maka.xiaoxia.action.UPDATE_WIDGET") // 兼容旧版本
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
        
        // 扫描功能已移除 - 不再自动扫描音乐
        // 应用启动时显示空列表，等待用户手动添加歌曲
        Thread {
            runOnUiThread {
                emptyView?.visibility = View.VISIBLE
                songListView?.visibility = View.GONE
                emptyView?.text = "没有音乐文件\n请手动添加歌曲到播放列表"
                
                // 清空现有列表
                songList.clear()
                updateSongList()
                updateViewsVisibility()
                
                // 保存空列表到服务
                saveSongsToServicePrefs()
                if (isServiceBound && musicService != null) {
                    musicService?.setSongList(songList)
                }
                
                Log.d(TAG, "音乐列表已清空，等待用户手动添加歌曲")
            }
        }.start()
    }
    
    // showScanSettingsDialog() 方法已移除 - 删除扫描设置功能
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        // 移除扫描设置菜单项
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        // 移除扫描设置相关处理
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
                // 设置功能已移除扫描选项
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
                            // 设置功能已移除扫描选项
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
        val options = arrayOf("创建新播放列表", "管理播放列表", "清空当前列表")
        AlertDialog.Builder(this)
            .setTitle("播放列表管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createNewPlaylist()
                    1 -> managePlaylists()
                    2 -> clearCurrentPlaylist()
                }
            }
            .show()
    }
    

    


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_PICK_MUSIC -> {
                if (resultCode == Activity.RESULT_OK) {
                    // 处理选择音乐文件的逻辑
                    data?.data?.let { uri ->
                        addSongFromUri(uri)
                    }
                }
            }
            REQUEST_ADD_SINGLE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        addSongFromUri(uri)
                    }
                }
            }

            REQUEST_IMPORT_FOLDER_LEGACY -> {
                // 扫描功能已移除 - 不再处理文件夹导入
                Toast.makeText(this, "扫描功能已移除", Toast.LENGTH_SHORT).show()
            }
            REQUEST_ADD_SINGLE_LEGACY -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        val path = getRealPathFromUri(uri)
                        if (path != null) {
                            val file = File(path)
                            if (file.exists() && file.isFile) {
                                addSingleFileToPlaylist(file)
                            } else {
                                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            addSongFromUri(uri)
                        }
                    }
                }
            }
            REQUEST_ADD_SINGLE_FILE_BROWSER -> {
                if (resultCode == Activity.RESULT_OK) {
                    val multipleFiles = data?.getBooleanExtra("multiple_files", false) ?: false
                    if (multipleFiles) {
                        // 处理多文件导入
                        val filePaths = data?.getStringArrayExtra("selected_files")
                        if (filePaths != null && filePaths.isNotEmpty()) {
                            val files = filePaths.map { File(it) }.filter { it.exists() && it.isFile }
                            if (files.isNotEmpty()) {
                                addMultipleFilesToPlaylist(files)
                            } else {
                                Toast.makeText(this, "没有有效的音乐文件", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // 处理单文件导入（原有逻辑）
                        val filePath = data?.getStringExtra("selected_file")
                        if (filePath != null) {
                            val file = File(filePath)
                            if (file.exists() && file.isFile) {
                                addSingleFileToPlaylist(file)
                            } else {
                                Toast.makeText(this, "文件不存在: $filePath", Toast.LENGTH_SHORT).show()
                            }
                        }
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
            
            // 初始化新添加的按钮
            val menuHamburgerButton: ImageButton? = findViewById(R.id.btn_menu_hamburger)
            val playlistToggleButton: ImageButton? = findViewById(R.id.btn_playlist_toggle)
            
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
            
            // 设置播放模式按钮
            playModeButton = findViewById(R.id.btn_play_mode)
            setupPlayModeControls()
            
            // 设置新按钮的点击监听器
            menuHamburgerButton?.setOnClickListener {
                drawerLayout?.openDrawer(GravityCompat.START)
            }
            
            playlistToggleButton?.setOnClickListener {
                togglePlaylistVisibility()
            }
            
            // 设置播放列表管理按钮的点击监听器
            setupPlaylistManagementControls()
            
            // 加载保存的播放模式
            loadPlayMode()
            
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
    
    private fun setupPlayModeControls() {
        playModeButton?.setOnClickListener {
            cyclePlayMode()
        }
    }
    
    private fun cyclePlayMode() {
        currentPlayMode = when (currentPlayMode) {
            PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.PLAY_ORDER
            PlayMode.PLAY_ORDER -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ALL
        }
        
        updatePlayModeIcon()
        savePlayMode()
        
        // 发送到音乐服务
        if (isServiceBound) {
            musicService?.setPlayMode(currentPlayMode.ordinal)
        }
        
        // 显示提示
        val modeName = when (currentPlayMode) {
            PlayMode.REPEAT_ONE -> "单曲循环"
            PlayMode.PLAY_ORDER -> "顺序播放"
            PlayMode.REPEAT_ALL -> "列表循环"
            PlayMode.SHUFFLE -> "随机播放"
        }
        Toast.makeText(this, "播放模式: $modeName", Toast.LENGTH_SHORT).show()
    }
    
    private fun updatePlayModeIcon() {
        val iconRes = when (currentPlayMode) {
            PlayMode.REPEAT_ONE -> R.drawable.ic_repeat_one
            PlayMode.PLAY_ORDER -> R.drawable.ic_play_order
            PlayMode.REPEAT_ALL -> R.drawable.ic_repeat_all
            PlayMode.SHUFFLE -> R.drawable.ic_shuffle
        }
        playModeButton?.setImageResource(iconRes)
    }
    
    private fun savePlayMode() {
        preferenceHelper?.putInt("play_mode", currentPlayMode.ordinal)
    }
    
    private fun loadPlayMode() {
        val modeOrdinal = preferenceHelper?.getInt("play_mode", PlayMode.REPEAT_ALL.ordinal) ?: PlayMode.REPEAT_ALL.ordinal
        currentPlayMode = PlayMode.values()[modeOrdinal.coerceIn(0, PlayMode.values().size - 1)]
        updatePlayModeIcon()
    }

    private fun setupPlaylistManagementControls() {
        try {
            // 获取播放列表管理按钮
            val btnAddSingle: ImageButton? = findViewById(R.id.btn_add_single)
            val btnClearPlaylist: ImageButton? = findViewById(R.id.btn_clear_playlist)
            val btnMoreOptions: ImageButton? = findViewById(R.id.btn_more_options)

            btnAddSingle?.setOnClickListener {
                addSingleMusicFile()
            }

            btnClearPlaylist?.setOnClickListener {
                showClearPlaylistConfirmation()
            }

            btnMoreOptions?.setOnClickListener { view ->
                showPlaylistMoreOptionsMenu(view)
            }

        } catch (e: Exception) {
            Log.d(TAG, "播放列表管理按钮初始化失败: ${e.message}")
        }
    }

    private fun addSingleMusicFile() {
        // 使用真正的文件浏览器，支持按文件路径浏览
        val intent = Intent(this, FileBrowserActivity::class.java)
        startActivityForResult(intent, REQUEST_ADD_SINGLE_FILE_BROWSER)
    }



    private fun showClearPlaylistConfirmation() {
        if (songList.isEmpty()) {
            Toast.makeText(this, "播放列表已为空", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("清空播放列表")
            .setMessage("确定要清空当前播放列表吗？")
            .setPositiveButton("是") { _, _ ->
                clearCurrentPlaylist()
            }
            .setNegativeButton("否", null)
            .show()
    }

    private fun clearCurrentPlaylist() {
        // 停止当前播放
        if (isServiceBound) {
            musicService?.stopService()
        }
        isPlaying = false
        updatePlayPauseButton()
        stopSeekBarUpdate()

        // 清空列表
        songList.clear()
        currentSongIndex = 0

        // 更新界面
        updateSongList()
        updateViewsVisibility()
        updateCurrentSongInfo()

        // 保存状态
        saveCurrentState()

        Toast.makeText(this, "播放列表已清空", Toast.LENGTH_SHORT).show()
    }

    private fun showPlaylistMoreOptionsMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.playlist_more_options, popupMenu.menu)
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_create_playlist -> {
                    createNewPlaylist()
                    true
                }
                R.id.action_manage_playlists -> {
                    managePlaylists()
                    true
                }
                R.id.action_clear_playlist -> {
                    clearCurrentPlaylist()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }





    private fun getRealPathFromUri(uri: Uri): String? {
        // 对于文件URI，使用传统方法
        try {
            var path: String? = null
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    path = it.getString(columnIndex)
                }
            }
            return path
            
        } catch (e: Exception) {
            Log.e(TAG, "获取路径失败: ${e.message}")
            return null
        }
    }

    private fun getFolderPathFromUri(uri: Uri): String? {
        // 对于文件夹URI，直接返回一个有效路径
        // 在实际应用中，这里应该解析URI为实际文件夹路径
        // 简化处理：返回音乐文件夹路径
        return "/storage/emulated/0/Music"
    }

    // showManualPathInputDialog() 方法已移除 - 删除扫描功能

    // scanMusicFromPath() 方法已移除 - 删除扫描功能

    // scanCommonMusicDirectories() 方法已移除 - 删除扫描功能

    // scanCommonMusicDirectoriesForSingleFile() 方法已移除 - 删除扫描功能

    // scanSingleFileFromDirectory() 方法已移除 - 删除扫描功能

    private fun addSingleFileToPlaylist(file: File) {
        try {
            // 先测试ID3标签解析
            testId3Tags(file)
            
            val song = parseMusicFile(file)
            if (song != null) {
                songList.add(song)
                updateSongList()
                saveCurrentState()
                Toast.makeText(this, "已添加: ${song.title}", Toast.LENGTH_SHORT).show()

                // 如果播放列表为空，自动播放
                if (songList.size == 1) {
                    playSong(0)
                }
            } else {
                Toast.makeText(this, "无法解析音乐文件信息", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "添加单个文件失败: ${e.message}")
        }
    }
    
    private fun addMultipleFilesToPlaylist(files: List<File>) {
        val addedSongs = mutableListOf<Song>()
        var failedCount = 0
        
        for (file in files) {
            try {
                val song = parseMusicFile(file)
                if (song != null) {
                    addedSongs.add(song)
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "解析文件失败: ${file.name} - ${e.message}")
            }
        }
        
        if (addedSongs.isNotEmpty()) {
            songList.addAll(addedSongs)
            updateSongList()
            saveCurrentState()
            
            val message = if (failedCount > 0) {
                "成功导入 ${addedSongs.size} 首歌曲，${failedCount} 个文件导入失败"
            } else {
                "成功导入 ${addedSongs.size} 首歌曲"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            
            // 如果播放列表为空，自动播放第一首
            if (currentSongIndex == 0 && songList.isNotEmpty() && !isPlaying) {
                playSong(0)
            }
        } else {
            Toast.makeText(this, "没有成功导入任何音乐文件", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testId3Tags(file: File) {
        try {
            Log.d(TAG, "=== 测试ID3标签解析 ===")
            Log.d(TAG, "测试文件: ${file.absolutePath}")
            Log.d(TAG, "文件名: ${file.name}")
            Log.d(TAG, "文件大小: ${file.length()} 字节")
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            // 获取所有可能的元数据
            val metadataKeys = listOf(
                MediaMetadataRetriever.METADATA_KEY_TITLE to "标题",
                MediaMetadataRetriever.METADATA_KEY_ARTIST to "艺术家", 
                MediaMetadataRetriever.METADATA_KEY_ALBUM to "专辑",
                MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "专辑艺术家",
                MediaMetadataRetriever.METADATA_KEY_COMPOSER to "作曲家",
                MediaMetadataRetriever.METADATA_KEY_DATE to "日期",
                MediaMetadataRetriever.METADATA_KEY_DURATION to "时长",
                MediaMetadataRetriever.METADATA_KEY_GENRE to "流派"
            )
            
            metadataKeys.forEach { (key, name) ->
                try {
                    val value = retriever.extractMetadata(key)
                    Log.d(TAG, "$name: $value")
                } catch (e: Exception) {
                    Log.d(TAG, "$name: 获取失败 - ${e.message}")
                }
            }
            
            // 检查封面
            val art = retriever.embeddedPicture
            if (art != null) {
                Log.d(TAG, "找到封面数据: ${art.size} 字节")
                
                // 尝试不同的解码方式
                try {
                    val bitmap1 = BitmapFactory.decodeByteArray(art, 0, art.size)
                    Log.d(TAG, "解码结果1: ${bitmap1 != null}")
                    
                    if (bitmap1 != null) {
                        Log.d(TAG, "封面尺寸: ${bitmap1.width}x${bitmap1.height}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解码失败1: ${e.message}")
                }
                
                // 检查图片格式
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(art, 0, art.size, options)
                Log.d(TAG, "图片格式: ${options.outMimeType}, 尺寸: ${options.outWidth}x${options.outHeight}")
                
            } else {
                Log.d(TAG, "未找到封面数据")
            }
            
            retriever.release()
            Log.d(TAG, "=== ID3标签测试完成 ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "ID3标签测试失败: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseMusicFile(file: File): Song? {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            // 获取元数据
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: "未知艺术家"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) 
                ?: "未知专辑"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0
            
            // 使用文件路径的哈希值作为albumId，确保一致性
            val albumId = file.absolutePath.hashCode().toLong()
            
            retriever.release()
            
            return Song(
                id = System.currentTimeMillis(),
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                path = file.absolutePath,
                albumId = albumId
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析音乐文件元数据失败: ${e.message}")
            // 如果解析失败，返回基础信息
            return Song(
                id = System.currentTimeMillis(),
                title = file.nameWithoutExtension,
                artist = "未知艺术家",
                album = "未知专辑",
                duration = 0,
                path = file.absolutePath,
                albumId = file.absolutePath.hashCode().toLong()
            )
        }
    }

    // showManualFilePathInputDialog() 方法已移除 - 删除扫描功能

    // showDirectorySelection() 方法已移除 - 删除扫描功能

    // scanAllAvailableStorage() 方法已移除 - 删除扫描功能

    private fun addSongsToPlaylist(newSongs: List<Song>): Int {
        var addedCount = 0
        val existingPaths = songList.map { it.path }.toSet()
        
        for (song in newSongs) {
            if (!existingPaths.contains(song.path)) {
                songList.add(song)
                addedCount++
            }
        }
        
        if (addedCount > 0) {
            updateSongList()
            saveCurrentState()
        }
        
        return addedCount
    }

    private fun togglePlaylistVisibility() {
        try {
            // 获取新的布局容器引用
            val playerContent = findViewById<View>(R.id.player_content)
            val playlistContent = findViewById<View>(R.id.playlist_content)
            
            if (playlistContent.visibility == View.VISIBLE) {
                // 显示播放控制界面（显示歌曲封面和歌词）
                playerContent.visibility = View.VISIBLE
                playlistContent.visibility = View.GONE
            } else {
                // 显示播放列表界面
                playerContent.visibility = View.GONE
                playlistContent.visibility = View.VISIBLE
                
                // 检查是否有歌曲，显示空视图或列表
                val emptyView = findViewById<View>(R.id.empty_view)
                
                if (songList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    songListView?.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    songListView?.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换播放列表显示失败: ${e.message}")
            // 回退方案：直接切换列表可见性
            songListView?.visibility = if (songListView?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
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
            loadAlbumArtFromSong(song)
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
            Log.d(TAG, "文件路径: ${song.path}")
            
            val file = File(song.path)
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: ${song.path}")
                albumArtImage?.setImageResource(R.drawable.ic_music_default)
                return
            }
            
            if (!file.canRead()) {
                Log.e(TAG, "没有读取权限: ${song.path}")
                albumArtImage?.setImageResource(R.drawable.ic_music_default)
                return
            }
            
            Log.d(TAG, "文件大小: ${file.length()} 字节")
            
            val retriever = MediaMetadataRetriever()
            try {
                // 设置数据源为歌曲文件路径
                retriever.setDataSource(song.path)
                
                // 检查所有可用的元数据
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                Log.d(TAG, "ID3标签 - 标题: $title, 艺术家: $artist, 专辑: $album")
                
                // 获取嵌入的专辑封面
                val art = retriever.embeddedPicture
                if (art != null) {
                    Log.d(TAG, "找到ID3封面数据，大小: ${art.size} 字节")
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    if (bitmap != null) {
                        Log.d(TAG, "成功解码封面图片: ${bitmap.width}x${bitmap.height}")
                        albumArtImage?.setImageBitmap(bitmap)
                        retriever.release()
                        return
                    } else {
                        Log.w(TAG, "封面数据解码失败")
                    }
                } else {
                    Log.w(TAG, "未找到ID3封面数据")
                }
                
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "从ID3标签加载专辑封面失败: ${e.message}")
                e.printStackTrace()
                try {
                    retriever.release()
                } catch (releaseEx: Exception) {
                    Log.w(TAG, "释放MediaMetadataRetriever失败: ${releaseEx.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "加载专辑封面失败: ${e.message}")
            e.printStackTrace()
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
        showCreatePlaylistDialog()
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

    // 这个方法已被删除，使用上面的clearCurrentPlaylist方法
    
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
                "com.maka.xiaoxia.UPDATE_ALL_COMPONENTS" -> {
                    // 收到统一广播，优先使用广播中的数据
                    Log.d(TAG, "收到统一广播，准备更新UI")
                    
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
                    
                    Log.d(TAG, "统一广播数据: 索引=$songIndex, 标题=$songTitle, 播放=$isPlaying")
                    
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
                        Log.d(TAG, "从统一广播同步歌曲信息: $songTitle")
                        
                        updateUI()
                        Log.d(TAG, "统一广播处理完成，当前歌曲: $songTitle, 索引: $songIndex")
                    } else if (isServiceBound) {
                        // 广播数据不完整，尝试从服务获取
                        musicService?.let { service ->
                            currentSongIndex = service.getCurrentSongIndex()
                            this@MainActivity.isPlaying = service.isPlaying()
                            
                            val currentSong = service.getCurrentSong()
                            if (currentSong != null && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                                songList[currentSongIndex] = currentSong
                                Log.d(TAG, "从服务同步歌曲信息: ${currentSong.title}")
                            }
                            
                            updateUI()
                            Log.d(TAG, "统一广播处理完成（从服务），当前歌曲: ${currentSong?.title}, 索引: $currentSongIndex")
                        }
                    } else {
                        // 最后才从SharedPreferences获取
                        loadSavedData()
                        updateUI()
                        Log.d(TAG, "服务未绑定，从SharedPreferences更新状态")
                    }
                }
                "com.maka.xiaoxia.UPDATE_UI", "com.maka.xiaoxia.action.UPDATE_WIDGET" -> {
                    // 兼容旧版本广播
                    Log.d(TAG, "收到旧版本广播: ${intent.action}")
                    handleLegacyBroadcast()
                }
                "com.maka.xiaoxia.PLAYBACK_COMPLETE" -> {
                    updateUI()
                    Log.d(TAG, "收到播放完成广播")
                }
            }
        }
        
        private fun handleLegacyBroadcast() {
            // 兼容旧版本广播处理
            if (isServiceBound) {
                musicService?.let { service ->
                    currentSongIndex = service.getCurrentSongIndex()
                    this@MainActivity.isPlaying = service.isPlaying()
                    
                    val currentSong = service.getCurrentSong()
                    if (currentSong != null && currentSongIndex >= 0 && currentSongIndex < songList.size) {
                        songList[currentSongIndex] = currentSong
                    }
                    
                    updateUI()
                    Log.d(TAG, "兼容模式处理完成，当前歌曲: ${currentSong?.title}")
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