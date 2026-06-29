@echo off
cd /d D:\L7\L7Audio
call gradlew.bat assembleRelease > full_build_result.txt 2>&1
echo BUILD_EXIT_CODE=%ERRORLEVEL% >> full_build_result.txt
