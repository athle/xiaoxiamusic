@echo off
cd /d "D:\android app\maka\xiaoxiamusic"
echo 正在编译项目...
call gradlew.bat compileDebugKotlin --no-daemon
echo.
echo 编译完成，按任意键退出...
pause