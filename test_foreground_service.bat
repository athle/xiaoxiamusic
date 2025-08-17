@echo off
echo 正在测试前台服务修复...
echo.

:: 清理构建缓存
echo 清理构建缓存...
call gradlew clean

:: 构建项目
echo 构建项目...
call gradlew assembleDebug

:: 安装应用
echo 安装应用...
call adb install -r app\build\outputs\apk\debug\app-debug.apk

:: 启动应用
echo 启动应用...
call adb shell am start -n com.maka.xiaoxia/.MainActivity

:: 等待应用启动
timeout /t 5 /nobreak > nul

echo.
echo 测试步骤：
echo 1. 启动应用后，点击播放按钮
echo 2. 观察是否出现前台通知
echo 3. 旋转屏幕，检查是否闪退
echo 4. 验证通知控制功能
pause

echo.
echo 检查前台服务状态...
call adb shell dumpsys activity services | findstr "com.maka.xiaoxia"

echo.
echo 检查日志...
call adb logcat -d | findstr "MusicService"

echo.
echo 测试完成！按任意键退出...
pause