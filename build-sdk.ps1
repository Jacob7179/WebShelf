param(
    [string]$Sdk,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildTools,
    [switch]$CustomSign,
    [string]$Keystore,
    [string]$KeyAlias,
    [string]$StorePassword,
    [string]$KeyPassword
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function ConvertTo-PlainText([Security.SecureString]$SecureString) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Resolve-BuildToolsPath([string]$SdkRoot, [string]$RequestedVersion) {
    $root = Join-Path $SdkRoot 'build-tools'
    if (!(Test-Path -LiteralPath $root)) {
        throw "Android build-tools directory was not found: $root"
    }

    if ($RequestedVersion) {
        $requested = Join-Path $root $RequestedVersion
        if (!(Test-Path -LiteralPath $requested)) {
            throw "Android Build Tools $RequestedVersion was not found at: $requested"
        }
        return $requested
    }

    $latest = Get-ChildItem -LiteralPath $root -Directory |
        Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
        Select-Object -First 1

    if (!$latest) {
        throw "No Android Build Tools installation was found under: $root"
    }
    return $latest.FullName
}

if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
if ($Sdk) {
    $env:ANDROID_HOME = $Sdk
}
elseif (!$env:ANDROID_HOME -and !$env:ANDROID_SDK_ROOT) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }

if (!$CustomSign) {
    # Normal development build. Gradle signs this with Android's standard debug key.
    & .\gradlew.bat --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed. See the error above.' }

    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'WebShelf-debug.apk' -Force
    Write-Output "APK created: $PSScriptRoot\WebShelf-debug.apk"
    exit 0
}

# Custom release signing flow.
if (!$Keystore) {
    # Launch the native Windows file picker rather than requiring a typed path.
    Add-Type -AssemblyName System.Windows.Forms
    $picker = New-Object System.Windows.Forms.OpenFileDialog
    $picker.Title = 'Select Android signing keystore'
    $picker.Filter = 'Keystore files (*.jks;*.keystore)|*.jks;*.keystore|All files (*.*)|*.*'
    $picker.CheckFileExists = $true
    $picker.Multiselect = $false
    $picker.RestoreDirectory = $true
    try {
        if ($picker.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
            throw 'Keystore selection cancelled.'
        }
        $Keystore = $picker.FileName
    }
    finally {
        $picker.Dispose()
    }
}
$Keystore = $Keystore.Trim().Trim('"')
if (!(Test-Path -LiteralPath $Keystore -PathType Leaf)) {
    throw "Keystore file was not found: $Keystore"
}
$Keystore = (Resolve-Path -LiteralPath $Keystore).Path

if (!$KeyAlias) {
    $KeyAlias = Read-Host 'Enter key alias'
}
if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
    throw 'Key alias cannot be empty.'
}

if (!$StorePassword) {
    $StorePassword = ConvertTo-PlainText (Read-Host 'Enter keystore password' -AsSecureString)
}
if (!$KeyPassword) {
    $keyPasswordSecure = Read-Host 'Enter key password (press Enter to use the keystore password)' -AsSecureString
    $KeyPassword = ConvertTo-PlainText $keyPasswordSecure
    if ([string]::IsNullOrEmpty($KeyPassword)) { $KeyPassword = $StorePassword }
}

& .\gradlew.bat --no-daemon assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Gradle release build failed. See the error above.' }

$unsignedApk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (!(Test-Path -LiteralPath $unsignedApk)) {
    throw "Unsigned release APK was not found: $unsignedApk"
}

$buildToolsPath = Resolve-BuildToolsPath -SdkRoot $sdkRoot -RequestedVersion $BuildTools
$zipalign = Join-Path $buildToolsPath 'zipalign.exe'
$apksigner = Join-Path $buildToolsPath 'apksigner.bat'
if (!(Test-Path -LiteralPath $zipalign)) { throw "zipalign.exe was not found: $zipalign" }
if (!(Test-Path -LiteralPath $apksigner)) { throw "apksigner.bat was not found: $apksigner" }

$alignedApk = Join-Path $PSScriptRoot 'WebShelf-release-aligned.apk'
$signedApk = Join-Path $PSScriptRoot 'WebShelf-release.apk'
Remove-Item -LiteralPath $alignedApk, $signedApk -Force -ErrorAction SilentlyContinue

& $zipalign -f -p 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }

# Keep passwords out of the command-line arguments by passing them through temporary environment variables.
$env:WEBSHELF_STORE_PASSWORD = $StorePassword
$env:WEBSHELF_KEY_PASSWORD = $KeyPassword
try {
    & $apksigner sign `
        --ks $Keystore `
        --ks-key-alias $KeyAlias `
        --ks-pass 'env:WEBSHELF_STORE_PASSWORD' `
        --key-pass 'env:WEBSHELF_KEY_PASSWORD' `
        --out $signedApk `
        $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }

    & $apksigner verify --verbose --print-certs $signedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
}
finally {
    Remove-Item Env:WEBSHELF_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:WEBSHELF_KEY_PASSWORD -ErrorAction SilentlyContinue
    $StorePassword = $null
    $KeyPassword = $null
}

Remove-Item -LiteralPath $alignedApk -Force -ErrorAction SilentlyContinue
Write-Output "Custom signed APK created: $signedApk"
