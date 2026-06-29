$adb = "C:\Users\HAN11\AppData\Local\Android\android-sdk\platform-tools\adb.exe"
$apk = "E:\JAVA\L7Audio\app\build\outputs\apk\debug\app-debug.apk"

# 彻底卸载
& $adb uninstall com.aug32.l7audio 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\step1.log" -Encoding utf8
Start-Sleep -Seconds 2

# 安装
& $adb install -r $apk 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\step2.log" -Encoding utf8
Start-Sleep -Seconds 2

# 验证安装的 APK
& $adb shell pm path com.aug32.l7audio 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\step3.log" -Encoding utf8

# 启动应用
& $adb shell am start -n com.aug32.l7audio/com.aug32.l7audio.ui.activity.MainActivity 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\step4.log" -Encoding utf8

Start-Sleep -Seconds 4

# 获取进程 ID
$processId = & $adb shell pidof com.aug32.l7audio
"Process ID: $processId" | Out-File -FilePath "E:\JAVA\L7Audio\logs\step5.log" -Encoding utf8

# 获取日志
& $adb logcat -d -t 200 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\step6.log" -Encoding utf8
exit 0
