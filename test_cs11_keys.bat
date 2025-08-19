@echo off
REM CS11车机按键测试脚本
REM 用于测试领克02 CS11车机实体按键功能

echo === CS11车机按键测试脚本 ===
echo.
echo 正在准备测试环境...

REM 检查ADB连接
adb devices
if %errorlevel% neq 0 (
    echo 错误：ADB未连接或设备未授权
    pause
    exit /b 1
)

echo.
echo === 测试选项 ===
echo 1. 测试CS11播放/暂停按键 (键值164)
echo 2. 测试CS11下一首按键 (键值163)
echo 3. 测试CS11上一首按键 (键值165)
echo 4. 测试标准播放/暂停按键 (键值85)
echo 5. 测试标准下一首按键 (键值87)
echo 6. 测试标准上一首按键 (键值88)
echo 7. 查看实时日志
echo 8. 退出

echo.
set /p choice="请选择测试选项 (1-8): "

if "%choice%"=="1" (
    echo 正在发送CS11播放/暂停按键...
    adb shell am broadcast -a android.intent.action.MEDIA_BUTTON --es android.intent.extra.KEY_EVENT "KeyEvent{action=ACTION_DOWN, keyCode=164}"
    echo 已发送CS11播放/暂停按键 (键值164)
)

if "%choice%"=="2" (
    echo 正在发送CS11下一首按键...
    adb shell am broadcast -a android.intent.action.MEDIA_BUTTON --es android.intent.extra.KEY_EVENT "KeyEvent{action=ACTION_DOWN, keyCode=163}"
    echo 已发送CS11下一首按键 (键值163)
)

if "%choice%"=="3" (
    echo 正在发送CS11上一首按键...
    adb shell am broadcast -a android.intent.action.MEDIA_BUTTON --es android.intent.extra.KEY_EVENT "KeyEvent{action=ACTION_DOWN, keyCode=165}"
    echo 已发送CS11上一首按键 (键值165)
)

if "%choice%"=="4" (
    echo 正在发送标准播放/暂停按键...
    adb shell am broadcast -a android.intent.action.MEDIA_BUTTON --es android.intent.extra.KEY_EVENT "KeyEvent{action=ACTION_DOWN, keyCode=85}"
    echo 已发送标准播放/暂停按键 (键值85)
)

if "%choice%"=="5" (
    echo 正在发送标准下一首按键...
    adb shell am broadcast -a android.intent.action.MEDIA_BUTTON --es android.intent.extra.KEY_EVENT "KeyEvent{action=ACTION_DOWN, keyCode=87}"
    echo 已发送标准下一首按键 (键值87)
)

if "%choice%"=="6" (
    echo 正在发送标准上一首按键...
    adb shell am broadcast -a android.intent.action.MEDIA_BUTTON --es android.intent.extra.KEY_EVENT "KeyEvent{action=ACTION_DOWN, keyCode=88}"
    echo 已发送标准上一首按键 (键值88)
)

if "%choice%"=="7" (
    echo 正在启动实时日志监控...
    echo 按 Ctrl+C 退出日志查看
    adb logcat | findstr "CarMediaButtonReceiver\|CarKeyTest\|MusicService"
)

if "%choice%"=="8" (
    echo 退出测试
    exit /b 0
)

echo.
echo === 测试完成 ===
echo 请检查车机或应用响应情况
pause