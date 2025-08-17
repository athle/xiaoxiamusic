@echo off
echo 正在验证安卓15广播兼容性修复...
echo.

REM 设置工作目录
cd /d "%~dp0"

echo 当前时间: %date% %time%
echo 工作目录: %cd%
echo.

REM 清理构建缓存
echo 清理构建缓存...
call gradlew.bat clean
if %errorlevel% neq 0 (
    echo ❌ 清理失败，继续构建...
)

REM 构建应用
echo 开始构建应用...
call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo.
    echo ❌ 构建失败！请检查错误信息
    echo 可能的错误：
    echo 1. 网络连接问题
    echo 2. Gradle配置错误  
    echo 3. 依赖库版本冲突
    pause
    exit /b 1
)

echo.
echo ✅ 构建成功！
echo.
echo 📱 应用已构建完成，可以安装到安卓15设备进行测试
echo.
echo 安装命令：
echo adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
echo 🧪 测试步骤：
echo 1. 安装应用到安卓15设备
echo 2. 启动应用，检查是否正常进入主界面
echo 3. 测试播放音乐功能
echo 4. 测试后台播放和前台切换
echo 5. 验证广播接收器是否正常工作
echo.

pause