@echo off
cd /d "d:\android app\maka\xiaoxiamusic"
echo 正在编译项目...
call gradlew.bat assembleDebug --no-daemon
if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    exit /b 1
) else (
    echo 编译成功！
    pause
)