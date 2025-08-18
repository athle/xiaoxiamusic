@echo off
echo 测试安卓15/16小组件点击功能
echo ===================================

echo 1. 清理构建缓存...
call .\gradlew.bat clean

echo 2. 重新构建项目...
call .\gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo 构建失败！请检查代码错误
    pause
    exit /b 1
)

echo 3. 构建成功！
echo 4. 安装APK到设备...
call adb install -r app\build\outputs\apk\debug\app-debug.apk

echo 5. 添加小组件到桌面...
echo    请在设备上手动添加音乐小组件到桌面

echo 6. 启动应用并播放音乐...
call adb shell am start -n com.maka.xiaoxia/.MainActivity

echo ===================================
echo 测试步骤：
echo 1. 在应用中播放一首歌曲
echo 2. 将应用划至后台
echo 3. 在桌面小组件上点击播放/暂停按钮
echo 4. 点击下一首/上一首按钮
echo 5. 观察是否正常工作

echo 6. 检查日志...
echo    运行: adb logcat | findstr "MusicWidget"
echo    查看小组件相关日志

echo ===================================
pause