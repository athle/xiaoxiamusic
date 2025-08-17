@echo off
echo 正在验证安卓15字体兼容性修复...
echo.

REM 设置工作目录
cd /d "%~dp0"

REM 清理构建缓存
echo 清理构建缓存...
call gradlew.bat clean

REM 构建应用
echo 开始构建应用...
call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo.
    echo ❌ 构建失败！请检查错误信息
    pause
    exit /b 1
)

echo.
echo ✅ 构建成功！
echo.
echo 📱 应用已构建完成，可以安装到安卓15设备进行测试
echo.
echo 安装命令：
echo adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.

pause