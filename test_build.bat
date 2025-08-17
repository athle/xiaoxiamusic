@echo off
cd /d "d:\android app\maka\xiaoxiamusic"
echo 正在测试构建...
call gradlew.bat assembleDebug --stacktrace
echo 构建完成！
pause