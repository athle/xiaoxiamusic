@echo off
echo 运行gradle构建...
call gradlew.bat assembleDebug --stacktrace --info > build_output.txt 2>&1
echo 构建完成！
echo 查看 build_output.txt 获取详细信息
pause