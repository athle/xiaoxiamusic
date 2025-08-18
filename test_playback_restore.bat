@echo off
echo 测试播放位置保存和恢复功能
echo ===================================

echo 1. 清理之前的构建缓存...
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

echo 5. 启动应用进行测试...
call adb shell am start -n com.maka.xiaoxia/.MainActivity

echo ===================================
echo 测试步骤：
echo 1. 播放一首歌曲，记住当前播放位置
echo 2. 将应用划至后台（按Home键）
echo 3. 再次打开应用，观察是否恢复到之前的位置
echo 4. 点击播放按钮，确认从保存位置继续播放
echo ===================================
pause