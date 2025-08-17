@echo off
chcp 65001
cls
echo 🎵 播放暂停按钮可见性修复验证脚本
echo ========================================

:: 设置工作目录
cd /d "d:\android app\maka\xiaoxiamusic"

:: 清理构建缓存
echo 📦 正在清理构建缓存...
call gradlew clean

:: 重新构建应用
echo 🔨 正在重新构建应用...
call gradlew assembleDebug

if %errorlevel% neq 0 (
    echo ❌ 构建失败！请检查代码错误
    pause
    exit /b 1
)

echo ✅ 构建成功！

:: 安装应用到设备
echo 📱 正在安装应用到设备...
adb install -r app\build\outputs\apk\debug\app-debug.apk

if %errorlevel% neq 0 (
    echo ❌ 安装失败！请检查设备连接
    pause
    exit /b 1
)

echo ✅ 安装成功！

:: 启动应用
echo 🚀 正在启动应用...
adb shell am start -n com.maka.xiaoxia/.MainActivity

echo.
echo 🔍 验证步骤：
echo 1. 观察主界面播放暂停按钮是否始终可见
echo 2. 检查按钮是否有明显的背景色（蓝色圆形）
echo 3. 点击按钮测试播放/暂停功能是否正常
echo 4. 横屏模式下检查按钮可见性
echo 5. 确认其他按钮（上一首/下一首）也正常显示

echo.
echo ⏳ 等待5秒后自动检查日志...
timeout /t 5 /nobreak > nul

:: 检查是否有相关错误日志
echo 📊 检查错误日志...
adb logcat -d | findstr "btn_play_pause" > nul
if %errorlevel% equ 0 (
    echo ⚠️ 发现按钮相关日志，请检查
) else (
    echo ✅ 无按钮相关错误日志
)

echo.
echo ✅ 修复验证完成！
echo 📋 预期效果：
echo    - 播放暂停按钮始终可见（蓝色圆形背景）
echo    - 白色播放/暂停图标在蓝色背景上清晰可见
echo    - 横竖屏模式下均正常显示
pause