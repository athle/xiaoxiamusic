@echo off
echo.
echo ================================================
echo    通知栏音乐控件功能测试脚本
echo    支持安卓15及小米、OPPO、vivo定制系统
echo ================================================
echo.

REM 检查项目路径
cd /d "%~dp0"

REM 1. 清理并重新构建项目
echo [1/5] 重新构建项目...
call gradlew.bat clean assembleDebug
if %errorlevel% neq 0 (
    echo ❌ 项目构建失败，请检查代码错误
    pause
    exit /b 1
)
echo ✅ 项目构建成功
echo.

REM 2. 检查APK文件
echo [2/5] 检查APK文件...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ✅ APK文件已生成：app\build\outputs\apk\debug\app-debug.apk
) else (
    echo ❌ APK文件未找到
    pause
    exit /b 1
)
echo.

REM 3. 检查设备连接
echo [3/5] 检查设备连接...
adb devices
if %errorlevel% neq 0 (
    echo ❌ ADB连接失败，请检查设备连接
    pause
    exit /b 1
)

REM 获取已连接设备数量
for /f "skip=1 tokens=1" %%a in ('adb devices ^| findstr /v "List"') do (
    set device_connected=true
    goto :device_found
)

:device_found
if "%device_connected%"=="true" (
    echo ✅ 设备已连接
) else (
    echo ❌ 未检测到设备，请连接手机或车机
    pause
    exit /b 1
)
echo.

REM 4. 安装APK
echo [4/5] 安装应用到设备...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if %errorlevel% neq 0 (
    echo ❌ APK安装失败，请检查设备权限
    pause
    exit /b 1
)
echo ✅ APK安装成功
echo.

REM 5. 启动应用并测试通知栏控件
echo [5/5] 启动应用并测试通知栏控件...
echo.
echo 正在启动小虾音乐...
adb shell am start -n com.maka.xiaoxia/.MainActivity

REM 等待应用启动
timeout /t 3 /nobreak >nul

echo.
echo ================================================
echo    通知栏控件测试步骤
echo ================================================
echo.
echo 1. 在应用中播放一首音乐
echo 2. 下拉通知栏查看音乐控制通知
echo 3. 测试以下功能：
echo    ✅ 播放/暂停按钮
echo    ✅ 上一首/下一首按钮
echo    ✅ 停止按钮
echo    ✅ 专辑封面显示
echo    ✅ 进度条显示
echo.
echo 4. 锁屏测试（安卓5.0+）：
echo    ✅ 锁屏界面控制
echo    ✅ 锁屏专辑封面
echo.
echo 5. 车机测试（领克02 CS11）：
echo    ✅ 方向盘按键控制
echo    ✅ 车机通知栏显示
echo    ✅ 中控屏触控控制

:menu
echo.
echo ================================================
echo    测试选项菜单
echo ================================================
echo 1. 重新安装APK
echo 2. 查看日志
echo 3. 清除应用数据
echo 4. 退出测试
set /p choice=请选择操作(1-4): 

if "%choice%"=="1" goto reinstall
if "%choice%"=="2" goto logs
if "%choice%"=="3" goto clear_data
if "%choice%"=="4" goto exit

goto menu

:reinstall
echo 重新安装APK...
goto :eof

:logs
echo 查看应用日志...
adb logcat | findstr "EnhancedMediaNotification\|MusicService"
pause
goto menu

:clear_data
echo 清除应用数据...
adb shell pm clear com.maka.xiaoxia
echo ✅ 应用数据已清除
goto menu

:exit
echo.
echo ================================================
echo    测试完成！
echo    感谢使用小虾音乐
echo ================================================
pause