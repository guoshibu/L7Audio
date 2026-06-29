Set-Location "E:\JAVA\L7Audio"
$env:ANDROID_AAPT2_DAEMON_PORT = "0"
$env:GRADLE_OPTS = "-Dorg.gradle.jvmargs=-Xmx4g"
& ".\gradlew.bat" --stop 2>&1 | Out-Null
Start-Sleep -Seconds 2
& ".\gradlew.bat" assembleDebug --no-daemon --console=plain 2>&1 | Out-File -FilePath "E:\JAVA\L7Audio\logs\build.log" -Encoding utf8
exit $LASTEXITCODE
