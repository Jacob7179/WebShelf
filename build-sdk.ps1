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

# Gradle 8.9 / Android Gradle Plugin 8.7.2 require a supported Gradle JVM.
# Java 23 (class file version 67) cannot run Gradle 8.9 build scripts.
function Get-SupportedJdkVersion([string]$Candidate) {
    if ([string]::IsNullOrWhiteSpace($Candidate)) { return $null }
    $jdkPath = $Candidate.Trim().Trim('"')
    $exe = Join-Path $jdkPath 'bin\java.exe'
    if (!(Test-Path -LiteralPath $exe -PathType Leaf)) { return $null }

    # Read JDK metadata instead of running java -version: Java prints its
    # version to stderr, which PowerShell can treat as a terminating error.
    $releaseFile = Join-Path $jdkPath 'release'
    if (Test-Path -LiteralPath $releaseFile -PathType Leaf) {
        $versionLine = Get-Content -LiteralPath $releaseFile -ErrorAction SilentlyContinue |
            Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($versionLine -match '^JAVA_VERSION="?(?:1\.)?(\d+)') {
            $major = [int]$Matches[1]
            if ($major -eq 17 -or $major -eq 21) { return $major }
            return $null
        }
    }

    # Fallback for non-standard JDK installs without a release metadata file.
    # Start-Process captures stderr as text without a PowerShell native error.
    $tmpError = [System.IO.Path]::GetTempFileName()
    $tmpOutput = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process -FilePath $exe -ArgumentList '-version' -Wait -PassThru `
            -NoNewWindow -RedirectStandardError $tmpError -RedirectStandardOutput $tmpOutput
        $versionInfo = (Get-Content -LiteralPath $tmpError -Raw -ErrorAction SilentlyContinue) +
                       (Get-Content -LiteralPath $tmpOutput -Raw -ErrorAction SilentlyContinue)
        if ($versionInfo -match '(?:openjdk|java) version\s+"(?:1\.)?(\d+)') {
            $major = [int]$Matches[1]
            if ($major -eq 17 -or $major -eq 21) { return $major }
        }
    }
    catch { return $null }
    finally { Remove-Item -LiteralPath $tmpError, $tmpOutput -Force -ErrorAction SilentlyContinue }
    return $null
}

# Prefer the configured JDK if compatible; otherwise search common Windows installs.
$jdkCandidates = New-Object 'System.Collections.Generic.List[string]'
if ($JavaHome) { $jdkCandidates.Add($JavaHome) }
if ($env:JAVA_HOME -and $env:JAVA_HOME -ne $JavaHome) { $jdkCandidates.Add($env:JAVA_HOME) }
if ($env:ProgramFiles) {
    foreach ($pattern in @('Eclipse Adoptium\jdk-17*', 'Eclipse Adoptium\jdk-21*',
                           'Java\jdk-17*', 'Java\jdk-21*',
                           'Microsoft\jdk-17*', 'Microsoft\jdk-21*',
                           'Amazon Corretto\jdk17*', 'Amazon Corretto\jdk21*',
                           'Android\Android Studio\jbr')) {
        Get-Item -Path (Join-Path $env:ProgramFiles $pattern) -ErrorAction SilentlyContinue |
            Where-Object { $_.PSIsContainer } |
            ForEach-Object { $jdkCandidates.Add($_.FullName) }
    }
}
if ($env:LOCALAPPDATA) {
    Get-Item -Path (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium\jdk-*') -ErrorAction SilentlyContinue |
        Where-Object { $_.PSIsContainer } | ForEach-Object { $jdkCandidates.Add($_.FullName) }
}
function Find-CompatibleJdk {
    param([System.Collections.Generic.List[string]]$Candidates)
    foreach ($candidate in $Candidates) {
        $major = Get-SupportedJdkVersion $candidate
        if ($major) { return $candidate.Trim().Trim('"') }
    }
    return $null
}
$selectedJdk = Find-CompatibleJdk $jdkCandidates
if (!$selectedJdk) {
    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if (!$winget) {
        throw 'JDK 17 or 21 was not found, and winget is unavailable. Install Eclipse Temurin JDK 17 manually.'
    }
    Write-Host 'Compatible JDK not found. Installing Eclipse Temurin JDK 17 using winget...'
    & $winget.Source install --id EclipseAdoptium.Temurin.17.JDK --exact --source winget --accept-source-agreements --accept-package-agreements --silent --disable-interactivity
    if ($LASTEXITCODE -ne 0) {
        throw "Temurin 17 installation failed (winget exit code $LASTEXITCODE). Install it manually and rerun build.bat."
    }
    # The installer may not update the current process environment variables.
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if (!$base) { continue }
        foreach ($pattern in @('Eclipse Adoptium\jdk-17*', 'Programs\Eclipse Adoptium\jdk-17*')) {
            Get-Item -Path (Join-Path $base $pattern) -ErrorAction SilentlyContinue |
                Where-Object { $_.PSIsContainer } |
                ForEach-Object { $jdkCandidates.Add($_.FullName) }
        }
    }
    $selectedJdk = Find-CompatibleJdk $jdkCandidates
}
if (!$selectedJdk) {
    throw 'JDK 17 not found after installation. Restart PowerShell and run build.bat again, or specify -JavaHome.'
}
Write-Host "Using compatible JDK: $selectedJdk"
$env:JAVA_HOME = $selectedJdk
$env:PATH = (Join-Path $selectedJdk 'bin') + ';' + $env:PATH

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
