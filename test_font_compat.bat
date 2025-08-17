@echo off
echo ================================================
echo Android 4.4字体兼容性测试脚本
echo ================================================

echo 正在构建应用...
call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo ❌ 构建失败！
    pause
    exit /b 1
)

echo ✅ 构建成功！
echo.
echo 正在安装应用到设备...
adb install -r app\build\outputs\apk\debug\app-debug.apk

if %errorlevel% neq 0 (
    echo ❌ 安装失败！请检查设备连接
    pause
    exit /b 1
)

echo ✅ 安装成功！
echo.
echo 正在启动应用...
adb shell am start -n com.maka.xiaoxia/.MainActivity

echo.
echo ================================================
echo 测试说明：
echo 1. 观察应用是否正常启动
echo 2. 检查字体显示是否正常
echo 3. 验证无字体相关崩溃
echo 4. 测试各项功能是否正常
echo ================================================
pause