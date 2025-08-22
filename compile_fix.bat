@echo off
setlocal enabledelayedexpansion

rem 设置项目路径
set "PROJECT_PATH=d:\android app\maka\xiaoxiamusic"

rem 切换到项目目录
cd /d "%PROJECT_PATH%"

rem 运行Gradle编译，显示详细错误信息
echo 正在编译项目...
call gradlew.bat assembleDebug --info

echo.
echo 编译完成！
pause