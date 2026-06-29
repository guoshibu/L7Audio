$adb = "C:\Users\HAN11\AppData\Local\Android\android-sdk\platform-tools\adb.exe"

# 清空日志
& $adb logcat -c 2>&1 | Out-Null

# 启动应用
& $adb shell am start -n com.aug32.l7audio/com.aug32.l7audio.ui.activity.MainActivity 2>&1 | Out-Null
Start-Sleep -Seconds 3

# 模拟点击麦克风按钮
& $adb shell input tap 159 690 2>&1 | Out-Null
Start-Sleep -Seconds 2

# 模拟点击"仅车外"按钮
& $adb shell input tap 900 200 2>&1 | Out-Null
Start-Sleep -Seconds 2

# 截屏
& $adb shell screencap -p /sdcard/mic.png 2>&1 | Out-Null
& $adb pull /sdcard/mic.png E:\JAVA\L7Audio\logs\mic.png 2>&1 | Out-Null

# 模拟点击 TTS 按钮
& $adb shell input tap 640 690 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb shell input tap 900 200 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/tts.png 2>&1 | Out-Null
& $adb pull /sdcard/tts.png E:\JAVA\L7Audio\logs\tts.png 2>&1 | Out-Null

# 模拟点击音乐按钮
& $adb shell input tap 1120 690 2>&1 | Out-Null
Start-Sleep -Seconds 3
& $adb shell input tap 900 200 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/music.png 2>&1 | Out-Null
& $adb pull /sdcard/music.png E:\JAVA\L7Audio\logs\music.png 2>&1 | Out-Null

# 收集错误日志
& $adb logcat -d 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\errors.log" -Encoding utf8
exit 0
