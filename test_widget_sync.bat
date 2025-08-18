@echo off
title 安卓15-16小组件同步测试脚本
color 0A
echo ================================================
echo     安卓15-16小组件信息同步测试脚本
echo ================================================
echo.

:: 设置项目路径
set PROJECT_PATH=d:\android app\maka\xiaoxiamusic
set APK_PATH=%PROJECT_PATH%\app\build\outputs\apk\debug\app-debug.apk
set PACKAGE_NAME=com.maka.xiaoxia

:: 检查ADB连接
echo [1/7] 检查ADB连接...
adb devices
echo.

:: 清理缓存和重新构建
echo [2/7] 清理并重新构建项目...
cd /d "%PROJECT_PATH%"
call gradlew clean
call gradlew assembleDebug
if not exist "%APK_PATH%" (
    echo 构建失败！请检查项目配置
echo.
    pause
    exit /b 1
)
echo.

:: 卸载旧版本并安装新版本
echo [3/7] 安装应用...
adb uninstall %PACKAGE_NAME%
adb install "%APK_PATH%"
echo.

:: 启动应用并播放歌曲
echo [4/7] 启动应用并播放歌曲...
adb shell am start -n %PACKAGE_NAME%/.MainActivity
timeout /t 5 /nobreak > nul
echo.

:: 添加小组件到桌面（需要手动操作提示）
echo [5/7] 请手动添加小组件到桌面：
echo    - 长按桌面空白处
echo    - 选择"小组件"
echo    - 找到"小小音乐"
echo    - 添加到桌面
echo.
pause
echo.

:: 测试小组件信息同步
echo [6/7] 测试小组件信息同步...
echo.

:: 播放第一首歌
echo 当前播放第一首歌...
adb shell am start -n %PACKAGE_NAME%/.MainActivity -a android.intent.action.VIEW -d "xiaoxiamusic://play?index=0"
timeout /t 3 /nobreak > nul

:: 通过小组件点击下一首
echo 通过小组件点击下一首...
adb shell am broadcast -a com.maka.xiaoxia.action.NEXT -n %PACKAGE_NAME%/.MusicWidgetProvider
timeout /t 2 /nobreak > nul

:: 检查日志
echo [7/7] 检查日志...
echo.
echo 正在检查小组件更新日志...
echo.
echo ===== 小组件相关日志 =====
adb logcat -d | findstr "MusicWidget\|MusicService" | findstr /v "dalvik" | tail -20
echo.

:: 循环测试
echo.
echo 测试完成！
echo.
echo 要验证修复效果：
echo 1. 观察小组件是否立即显示"胆小鬼"信息
echo 2. 检查日志中是否有"使用直接数据更新小组件: 胆小鬼"
echo 3. 多次点击小组件的下一首/上一首按钮，确认信息同步

echo.
echo 按任意键开始连续测试...
pause > nul

:loop_test
echo.
echo ===== 连续测试模式 =====
echo 正在发送下一首命令...
adb shell am broadcast -a com.maka.xiaoxia.action.NEXT -n %PACKAGE_NAME%/.MusicWidgetProvider
timeout /t 3 /nobreak > nul

adb logcat -d | findstr "MusicWidget\|MusicService" | findstr /v "dalvik" | tail -10

echo.
echo 按Ctrl+C退出，或等待5秒后自动继续...
timeout /t 5 /nobreak > nul
goto loop_test