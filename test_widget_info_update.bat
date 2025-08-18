@echo off
chcp 65001 >nul
echo 正在测试安卓15/16小组件歌曲信息更新修复...
echo.

REM 清理缓存
echo 清理项目缓存...
call gradlew clean

REM 构建项目
echo 构建项目...
call gradlew assembleDebug

REM 安装APK
echo 安装应用...
call adb install -r app\build\outputs\apk\debug\app-debug.apk

if %errorlevel% neq 0 (
    echo 安装失败，请检查设备连接
    pause
    exit /b 1
)

echo.
echo 安装成功！现在开始测试小组件歌曲信息更新功能...
echo.
echo 测试步骤：
echo 1. 启动应用并播放一首歌曲
echo 2. 添加音乐小组件到桌面
echo 3. 观察小组件显示的当前歌曲信息
echo 4. 点击小组件的"下一首"按钮
echo 5. 观察小组件是否立即更新为新歌曲信息
echo 6. 重复步骤4-5多次验证
echo.

REM 启动应用
echo 启动应用...
call adb shell am start -n com.maka.xiaoxia/.MainActivity

echo.
echo 等待5秒让应用启动...
timeout /t 5 /nobreak >nul

REM 发送测试广播
echo 发送测试广播验证小组件更新...
call adb shell am broadcast -a com.maka.xiaoxia.action.UPDATE_WIDGET --es current_title "测试歌曲" --es current_artist "测试艺术家" --ez is_playing true --es cover_path "/test/path"

echo.
echo 测试广播已发送！请观察小组件是否更新显示"测试歌曲 - 测试艺术家"
echo.
echo 日志查看命令：
echo adb logcat -v time | findstr "MusicService\|MusicWidget"
echo.
echo 按任意键开始详细日志监控...
pause

echo.
echo 开始监控日志...
call adb logcat -v time | findstr "MusicService\|MusicWidget\|UPDATE_WIDGET"

pause