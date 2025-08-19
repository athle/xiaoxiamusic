@echo off
echo ================================================
echo 车机小组件尺寸适配测试脚本
echo ================================================

REM 检查APK是否构建成功
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ✅ APK构建成功：app-debug.apk
) else (
    echo ❌ APK构建失败
    pause
    exit /b 1
)

echo.
echo 支持的尺寸规格：
echo 2x1 (120dp x 60dp)   - 最小尺寸，紧凑显示
echo 2x2 (120dp x 120dp)  - 标准尺寸，完整功能
echo 2x4 (120dp x 240dp)  - 大尺寸，专辑封面
echo 4x1 (240dp x 60dp)   - 超宽单行
echo 4x2 (240dp x 120dp)  - 宽屏标准
echo 4x4 (240dp x 240dp)  - 最大尺寸，完整展示
echo.

REM 检查设备连接
adb devices

echo.
echo 安装APK到车机...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if %errorlevel% neq 0 (
    echo ❌ 安装失败，请检查车机连接
    pause
    exit /b 1
)

echo.
echo 测试车机小组件功能...
echo.

REM 发送测试广播验证小组件更新
echo 测试广播更新...
adb shell am broadcast -a com.maka.xiaoxia.action.UPDATE_CAR_WIDGET

echo.
echo 查看已安装的小组件...
adb shell dumpsys appwidget | findstr "车机音乐控制"

echo.
echo 测试完成！
echo 请在车机上：
echo 1. 长按桌面空白处
echo 2. 选择"车机音乐控制"小组件
echo 3. 调整尺寸到所需规格 (2x1, 2x2, 2x4, 4x1, 4x2, 4x4)
echo 4. 验证不同尺寸下的显示效果
pause