@echo off
chcp 65001
setlocal enabledelayedexpansion

echo ==========================================
echo UI视图顶部不进入状态栏修复验证脚本
echo ==========================================

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

:: 验证UI视图顶部不进入状态栏
echo.
echo [4/4] 验证UI视图顶部不进入状态栏...
echo.
echo 🎯 验证步骤：
echo 1. 打开应用，观察主界面顶部
echo 2. 检查状态栏下方是否有足够的间距
echo 3. 测试横屏模式，确保内容不进入状态栏
echo 4. 检查专辑封面和标题是否被状态栏遮挡
pause

echo.
echo ✅ 修复验证完成！
echo 📋 如果状态栏仍遮挡内容，请检查：
echo    - 布局文件中fitsSystemWindows=true是否生效
necho    - 主题颜色colorPrimaryDark是否正确设置
necho    - 设备系统版本兼容性
pause