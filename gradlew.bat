@echo off
setlocal
set GRADLE_VERSION=8.11.1
set DIST=%USERPROFILE%\.gradle\gradle-%GRADLE_VERSION%
if exist "%DIST%\bin\gradle.bat" goto run
if not exist "%USERPROFILE%\.gradle" mkdir "%USERPROFILE%\.gradle"
curl.exe -fsSL "https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip" -o "%USERPROFILE%\.gradle\gradle.zip"
powershell -NoProfile -Command "Expand-Archive -Force '%USERPROFILE%\.gradle\gradle.zip' '%USERPROFILE%\.gradle'"
:run
call "%DIST%\bin\gradle.bat" %*
