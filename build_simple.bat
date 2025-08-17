@echo off
cd /d "%~dp0"
echo 正在构建应用...
echo 当前目录: %CD%
gradlew.bat assembleDebug --stacktrace
pause