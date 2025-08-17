@echo off
chcp 65001
setlocal enabledelayedexpansion

echo ==========================================
echo MediaPlayer空列表修复验证脚本
echo ==========================================

echo.
echo 🎯 验证MediaPlayer在没有歌曲时不持续获取duration的修复效果

:: 设置工作目录
cd /d "d:\android app\maka\xiaoxiamusic"

:: 清理构建缓存
echo.
echo [1/4] 清理构建缓存...
call gradlew clean
if %errorlevel% neq 0 (
    echo ⚠️ 清理缓存失败，继续执行...
) else (
    echo ✅ 清理缓存完成
)

:: 执行构建
echo.
echo [2/4] 开始构建应用...
call gradlew assembleDebug --stacktrace
if %errorlevel% neq 0 (
    echo ❌ 构建失败！请检查错误信息
    pause
    exit /b 1
)
echo ✅ 构建成功

:: 安装应用
echo.
echo [3/4] 安装应用到设备...
call adb install -r app\build\outputs\apk\debug\app-debug.apk
if %errorlevel% neq 0 (
    echo ⚠️ 安装失败，请检查设备连接或手动安装
    echo 尝试重新连接设备...
    call adb kill-server
    call adb start-server
    timeout /t 3 /nobreak > nul
    call adb install -r app\build\outputs\apk\debug\app-debug.apk
)

:: 验证修复效果
echo.
echo [4/4] 验证MediaPlayer修复效果...
echo.
echo 📋 验证步骤：
echo 1. 打开应用（确保设备上没有音乐文件）
echo 2. 观察日志中是否还有"Attempt to call getDuration in wrong state"错误
echo 3. 检查应用是否在没有歌曲时仍尝试播放
necho 4. 验证进度条是否保持静止（不跳动）
echo 5. 添加一首歌曲后，确认播放功能正常
pause

echo.
echo ✅ 修复验证完成！
echo 📊 预期效果：
echo    - 空列表时无MediaPlayer错误日志
echo    - 进度条不跳动或更新
echo    - 播放按钮点击无响应（因为没有歌曲）
echo    - 添加歌曲后功能恢复正常
pause