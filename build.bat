@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem If arguments are supplied, forward them directly to the PowerShell build script.
rem Examples:
rem   build.bat -CustomSign
rem   build.bat -CustomSign -Keystore "C:\keys\my-app.jks" -KeyAlias mykey
if not "%~1"=="" goto :run_args

echo.
echo ========================================
echo              WebShelf Build
echo ========================================
echo [1] Debug APK ^(default Android debug key^)
echo [2] Release APK ^(custom keystore/signature^)
echo.
rem CHOICE accepts a single keypress immediately (no Enter needed).
choice /c 12 /n /m "Choose signing option [1/2]: "
if errorlevel 2 goto :custom
if errorlevel 1 goto :debug
echo Unable to read signing option.
exit /b 2

:debug
echo.
echo Building debug APK...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-sdk.ps1"
goto :finish

:custom
echo.
echo Building release APK with a custom signing key...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-sdk.ps1" -CustomSign
goto :finish

:run_args
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-sdk.ps1" %*

:finish
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" (
    echo.
    echo Build failed. See the error above.
) else (
    echo.
    echo Build completed successfully.
)
exit /b %BUILD_EXIT%
