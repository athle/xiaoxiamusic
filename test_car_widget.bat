@echo off
echo 正在测试车机小组件识别...
echo.

:: 检查APK是否构建成功
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ✅ APK构建成功
) else (
    echo ❌ APK构建失败
    pause
    exit /b 1
)

echo.
echo 正在安装应用到车机...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

echo.
echo 检查车机是否识别小组件...
echo 正在查询系统小组件列表...
adb shell dumpsys appwidget | findstr "车机音乐控制"

echo.
echo 检查所有小组件注册状态...
adb shell dumpsys appwidget | findstr "com.maka.xiaoxia"

echo.
echo 测试车机专用小组件广播...
adb shell am broadcast -a com.maka.xiaoxia.action.UPDATE_CAR_WIDGET

echo.
echo 测试完成！
echo 请手动在车机桌面长按空白处，查看是否能找到"车机音乐控制"小组件
pause