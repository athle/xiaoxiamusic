@echo off
setlocal

cd /d "%~dp0"

if exist gradlew.bat (
    echo 正在使用gradlew.bat构建项目...
    call gradlew.bat assembleDebug
) else (
    echo 错误：找不到gradlew.bat文件
    pause
    exit /b 1
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo 构建成功完成！
    echo APK文件位置：app\build\outputs\apk\debug\
) else (
    echo.
    echo 构建失败，请检查错误信息。
)

echo.
pause