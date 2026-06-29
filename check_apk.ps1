$aapt = "C:\Users\HAN11\AppData\Local\Android\android-sdk\build-tools\36.0.0\aapt.exe"
$apk = "E:\JAVA\L7Audio\app\build\outputs\apk\debug\app-debug.apk"
& $aapt dump xmltree $apk AndroidManifest.xml 2>&1 | Select-String -Pattern "activity|receiver" | Out-File -FilePath "E:\JAVA\L7Audio\logs\apk_manifest.log" -Encoding utf8
exit 0
