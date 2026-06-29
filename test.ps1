$adb = "C:\Users\HAN11\AppData\Local\Android\android-sdk\platform-tools\adb.exe"

# 截屏
& $adb shell screencap -p /sdcard/l7audio_test.png 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb pull /sdcard/l7audio_test.png E:\JAVA\L7Audio\logs\screenshot.png 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\screenshot.log" -Encoding utf8

# 获取当前 Activity
& $adb shell dumpsys activity activities 2>&1 | Select-String -Pattern "topResumedActivity|ResumedActivity" | Select-Object -First 5 | Out-File -FilePath "E:\JAVA\L7Audio\logs\activity.log" -Encoding utf8

# 模拟点击授权按钮（返回到应用主界面）
& $adb shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
Start-Sleep -Seconds 2

# 再次截屏
& $adb shell screencap -p /sdcard/l7audio_test2.png 2>&1 | Out-Null
& $adb pull /sdcard/l7audio_test2.png E:\JAVA\L7Audio\logs\screenshot2.png 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\screenshot2.log" -Encoding utf8

# 获取日志
& $adb logcat -d -t 100 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\final.log" -Encoding utf8
exit 0
