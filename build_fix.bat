@echo off
cd /d "%~dp0"
echo 当前目录: %CD%
echo 开始构建...
gradlew.bat assembleDebug --stacktrace
echo 构建完成
pause