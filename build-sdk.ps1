param([string]$Sdk, [string]$JavaHome = $env:JAVA_HOME, [string]$BuildTools)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
if ($Sdk) { $env:ANDROID_HOME = $Sdk }
elseif (!$env:ANDROID_HOME -and !$env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
# Gradle resolves and merges the camera scanner library and its Android resources.
& .\gradlew.bat --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed. See the error above.' }
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' 'WebShelf-debug.apk' -Force
Write-Output "APK created: $PSScriptRoot\WebShelf-debug.apk"
