@echo off
echo 开始构建安卓15兼容版本...
cd /d "%~dp0"

:: 使用Gradle构建
echo 执行Gradle构建...
call gradlew.bat assembleDebug --info

if %errorlevel% neq 0 (
    echo 构建失败，错误码: %errorlevel%
    pause
    exit /b %errorlevel%
)

echo 构建成功完成！
echo 生成的APK文件位置:
echo %~dp0app\build\outputs\apk\debug\app-debug.apk
pause