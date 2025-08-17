@echo off
echo 正在验证安卓15状态栏显示修复...
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
    pause
    exit /b 1
)

echo.
echo ✅ 构建成功！
echo.
echo 📱 应用已构建完成，可以安装到安卓15设备进行测试
echo.
echo 🔍 状态栏测试步骤：
echo 1. 安装应用到安卓15设备
echo 2. 启动应用，检查状态栏是否正常显示
echo 3. 确认状态栏显示：时间、电量、网络图标
echo 4. 检查状态栏颜色是否与主题协调（深橙色背景）
echo 5. 测试横竖屏切换时状态栏是否正常

pause