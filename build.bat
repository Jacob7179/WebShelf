@echo off
setlocal
rem All arguments are forwarded to the PowerShell build script.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-sdk.ps1" %*
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" echo Build failed. See the error above.
exit /b %BUILD_EXIT%
