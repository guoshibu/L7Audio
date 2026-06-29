$adb = "C:\Users\HAN11\AppData\Local\Android\android-sdk\platform-tools\adb.exe"

# 关闭设置页，回到应用
& $adb shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
Start-Sleep -Seconds 2

# 通过 am start 重新启动 Activity
& $adb shell am start -n com.aug32.l7audio/com.aug32.l7audio.ui.activity.MainActivity 2>&1 | Out-Null
Start-Sleep -Seconds 3

# 截屏主界面
& $adb shell screencap -p /sdcard/main.png 2>&1 | Out-Null
& $adb pull /sdcard/main.png E:\JAVA\L7Audio\logs\main.png 2>&1 | Out-Null

# 获取当前 Activity
& $adb shell dumpsys activity activities 2>&1 | Select-String -Pattern "topResumedActivity" | Select-Object -First 2 | Out-File -FilePath "E:\JAVA\L7Audio\logs\main_activity.log" -Encoding utf8

# 获取应用进程
& $adb shell pidof com.aug32.l7audio 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\main_pid.log" -Encoding utf8

# 获取最新日志
& $adb logcat -d -t 50 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\main_log.log" -Encoding utf8
exit 0
