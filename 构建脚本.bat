@echo off
chcp 65001 >nul
title Java Android Gradle 打包脚本
echo ==============================================
echo        Java Android 打包菜单
echo 1 - 编译 Debug 调试 APK
echo 2 - 编译 Release 正式签名 APK
echo 3 - 先清理缓存再打 Release（推荐）
echo 4 - 清理全部构建缓存
echo 5 - 一键打包 Debug + Release 两个版本
echo ==============================================
set /p choice=请输入序号回车执行：

set "apkType="
if "%choice%"=="1" (
    echo.
    echo 正在执行 assembleDebug 编译调试包...
    call gradlew.bat assembleDebug --no-daemon
    set "apkType=debug"
    goto result
)
if "%choice%"=="2" (
    echo.
    echo 正在执行 assembleRelease 编译正式包...
    call gradlew.bat assembleRelease --no-daemon
    set "apkType=release"
    goto result
)
if "%choice%"=="3" (
    echo.
    echo 清理旧产物 + 打包 Release...
    call gradlew.bat clean assembleRelease --no-daemon
    set "apkType=release"
    goto result
)
if "%choice%"=="4" (
    echo.
    echo 执行 clean 清理构建目录...
    call gradlew.bat clean --no-daemon
    echo 清理完成
    pause
    exit
)
if "%choice%"=="5" (
    echo.
    echo 清理缓存 + 同时编译 Debug、Release 双版本APK
    call gradlew.bat clean assemble --no-daemon
    set "apkType=both"
    goto result
)

echo 输入无效，退出脚本
pause
exit

:result
echo.
if %errorlevel% equ 0 (
    echo ==============================================
    echo ✅ 构建成功
    if "%apkType%"=="debug" (
        echo Debug 包路径：app\build\outputs\apk\debug\
        copy "app\build\outputs\apk\debug\*.apk" ".\" >nul 2>&1
        echo 已复制 Debug APK 到项目根目录
    )
    if "%apkType%"=="release" (
        echo Release 包路径：app\build\outputs\apk\release\
        copy "app\build\outputs\apk\release\*.apk" ".\" >nul 2>&1
        echo 已复制 Release APK 到项目根目录
    )
    if "%apkType%"=="both" (
        echo Debug 包路径：app\build\outputs\apk\debug\
        echo Release 包路径：app\build\outputs\apk\release\
        copy "app\build\outputs\apk\debug\*.apk" ".\" >nul 2>&1
        copy "app\build\outputs\apk\release\*.apk" ".\" >nul 2>&1
        echo 已复制 Debug + Release 两个APK到项目根目录
    )
    echo ==============================================
) else (
    echo ❌ 构建失败，往上查看日志定位错误
)
pause