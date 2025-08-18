@echo off
title 安卓15/16 UI同步修复测试 - 小组件点击后界面更新
cls

echo ╔═══════════════════════════════════════════════════════════════╗
echo ║               安卓15/16 UI同步修复测试                        ║
echo ║         验证小组件点击后返回App界面正确更新                    ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.

:: 设置项目路径
set PROJECT_PATH=d:\android app\maka\xiaoxiamusic
set APK_PATH=%PROJECT_PATH%\app\build\outputs\apk\debug\app-debug.apk

:: 检查并清理之前的构建
if exist "%PROJECT_PATH%\app\build" (
    echo [1/7] 清理之前的构建缓存...
    cd /d "%PROJECT_PATH%"
    call gradlew clean
    if errorlevel 1 (
        echo ❌ 清理失败，继续构建...
    ) else (
        echo ✅ 清理完成
    )
) else (
    echo [1/7] 无需清理，开始构建...
)

:: 构建项目
echo.
echo [2/7] 开始构建项目...
cd /d "%PROJECT_PATH%"
call gradlew assembleDebug
if errorlevel 1 (
    echo ❌ 构建失败！请检查错误信息
    pause
    exit /b 1
) else (
    echo ✅ 构建成功
)

:: 安装APK
echo.
echo [3/7] 安装APK到设备...
adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo ❌ 安装失败！请检查设备连接
    pause
    exit /b 1
) else (
    echo ✅ 安装成功
)

:: 启动应用
echo.
echo [4/7] 启动应用...
adb shell am start -n com.maka.xiaoxia/.MainActivity
echo ✅ 应用已启动

:: 等待应用完全启动
echo.
echo [5/7] 等待应用完全启动...
timeout /t 3 /nobreak > nul

:: 测试步骤说明
echo.
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║                    测试步骤说明                                ║
echo ╠═══════════════════════════════════════════════════════════════╣
echo ║ 1. 在应用中播放一首歌曲                                       ║
echo ║ 2. 按Home键将应用划至后台                                     ║
echo ║ 3. 点击桌面小组件的"下一首"按钮                               ║
echo ║ 4. 观察小组件是否更新显示新歌曲                              ║
echo ║ 5. 从最近任务列表重新打开应用                                 ║
echo ║ 6. 验证应用界面是否正确显示当前播放的歌曲和状态               ║
echo ║ 7. 检查播放/暂停按钮状态是否正确                              ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.

:: 显示当前状态
echo [6/7] 当前设备信息：
adb shell getprop ro.build.version.release
adb shell dumpsys activity activities | findstr "mResumedActivity"
echo.

:: 等待用户测试
echo [7/7] 请按照上述步骤进行测试...
echo 测试完成后按任意键查看日志...
pause

:: 显示相关日志
echo.
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║                    查看相关日志                                ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.
echo [日志1] MusicService状态更新：
adb logcat -d | findstr "MusicService\|UPDATE_UI\|UPDATE_WIDGET" | tail -20

echo.
echo [日志2] MainActivity状态同步：
echo.
adb logcat -d | findstr "MainActivity.*同步" | tail -10

echo.
echo [日志3] 小组件点击事件：
echo.
adb logcat -d | findstr "Widget\|MusicWidget" | tail -10

echo.
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║                    测试完成                                    ║
echo ║ 如果界面能够正确同步显示当前歌曲，说明修复成功！               ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.
pause