@echo off
cd /d "%~dp0"
echo 正在构建项目...
echo 当前目录: %CD%
gradlew.bat assembleDebug --stacktrace
echo 构建完成
pause