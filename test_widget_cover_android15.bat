@echo off
echo 正在测试安卓15/16小组件封面获取修复（v2.0）...
echo.
echo 1. 编译项目...
call .\gradlew.bat assembleDebug --no-daemon
if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    exit /b 1
)
echo.
echo 2. 编译成功，开始安装...
call .\gradlew.bat installDebug --no-daemon
if %errorlevel% neq 0 (
    echo 安装失败！
    pause
    exit /b 1
)
echo.
echo 3. 安装完成！
echo.
echo 4. 修复内容总结：
echo    - 修复了SharedPreferences键名不匹配问题
echo    - 增强了日志记录便于调试
echo    - 优化了封面获取逻辑
echo    - 适配了安卓15/16权限系统
echo.
echo 5. 测试步骤：
echo    - 在安卓15/16设备上打开应用
echo    - 播放一首有封面的歌曲
echo    - 检查小组件是否正确显示封面
echo    - 查看Logcat中"MusicWidget"标签的日志
echo.
echo 6. 关键日志检查：
echo    - 应该看到"文件路径"不为空
echo    - 应该看到"成功从文件获取封面"或"成功从专辑ID获取封面"
echo    - 不应该看到"无文件路径"
echo.
echo 7. 如果仍有问题：
echo    - 检查应用权限设置
echo    - 确认文件路径是否有效
echo    - 检查专辑ID是否正确
echo    - 查看详细日志
    
echo.
echo 按任意键退出...
pause