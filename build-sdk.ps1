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

# Discover the actual SDK before starting Gradle or prompting for signing secrets.
# Environment variables may point to stale folders; local.properties can carry a
# valid SDK path from an earlier Android Studio setup.
function Get-LocalSdkPath {
    $localFile = Join-Path $PSScriptRoot 'local.properties'
    if (!(Test-Path -LiteralPath $localFile -PathType Leaf)) { return $null }
    foreach ($line in (Get-Content -LiteralPath $localFile)) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.*?)\s*$') {
            $value = $Matches[1].Trim().Trim('"')
            # Java properties escape backslashes and colons.
            return $value.Replace('\:', ':').Replace('\\', '\')
        }
    }
    return $null
}

function Test-AndroidSdk([string]$Candidate) {
    if ([string]::IsNullOrWhiteSpace($Candidate)) { return $false }
    $root = $Candidate.Trim().Trim('"').TrimEnd('\', '/')
    if ([string]::IsNullOrWhiteSpace($root)) { return $false }
    # Stale ANDROID_HOME/local.properties entries can refer to disconnected drives.
    # Check the root first and skip unreachable or invalid paths without throwing.
    try {
        if (!(Test-Path -LiteralPath $root -PathType Container -ErrorAction SilentlyContinue)) {
            return $false
        }
        foreach ($folder in @('platforms', 'build-tools', 'cmdline-tools', 'platform-tools')) {
            $child = Join-Path -Path $root -ChildPath $folder -ErrorAction Stop
            if (Test-Path -LiteralPath $child -PathType Container -ErrorAction SilentlyContinue) {
                return $true
            }
        }
    }
    catch {
        Write-Host "Skipping inaccessible Android SDK path: $root" -ForegroundColor DarkYellow
    }
    return $false
}

# Bootstrap the official Android command-line SDK, without needing Android Studio.
# A download and software license acceptance are required on first use.
function Install-MissingSdkComponents([string]$Root) {
    $manager = Join-Path $Root 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (!(Test-Path -LiteralPath $manager -PathType Leaf)) {
        Write-Host 'Android SDK command-line tools are not installed.' -ForegroundColor Yellow
        $reply = Read-Host 'Download official Google Android SDK tools and install API 35? [Y/n]'
        if ($reply -and $reply -notmatch '^(?i:y|yes)$') {
            throw 'Android SDK installation declined. Install SDK Platform 35 and Build-Tools using Android Studio, or supply -Sdk.'
        }
        $archive = Join-Path $env:TEMP 'webshelf-android-commandlinetools.zip'
        $extract = Join-Path $env:TEMP 'webshelf-android-commandlinetools-extract'
        $url = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
        Write-Host "Downloading official Android command-line tools from $url"
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing -ErrorAction Stop
            if (Test-Path -LiteralPath $extract) { Remove-Item $extract -Recurse -Force }
            Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
            $source = Join-Path $extract 'cmdline-tools'
            if (!(Test-Path -LiteralPath (Join-Path $source 'bin\sdkmanager.bat') -PathType Leaf)) {
                throw 'Downloaded Android command-line tools archive does not have the expected structure.'
            }
            $target = Join-Path $Root 'cmdline-tools\latest'
            New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
            if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
            Move-Item -LiteralPath $source -Destination $target
        }
        catch {
            throw "Could not download/install official SDK tools: $($_.Exception.Message). Check internet access or install Android SDK in Android Studio."
        }
        finally {
            Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $extract -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
    $manager = Join-Path $Root 'cmdline-tools\latest\bin\sdkmanager.bat'
    Write-Host 'Installing Android SDK Platform 35, Build-Tools 35.0.0 and platform-tools...'
    Write-Host 'Google Android SDK license prompts may appear. Read and accept them to continue.'
    & $manager "--sdk_root=$Root" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed. Check the SDK license prompts, network, disk space, or Android Studio SDK Manager.' }
}

$sdkCandidates = New-Object 'System.Collections.Generic.List[string]'
if ($Sdk) { $sdkCandidates.Add($Sdk) }
if ($env:ANDROID_HOME) { $sdkCandidates.Add($env:ANDROID_HOME) }
if ($env:ANDROID_SDK_ROOT) { $sdkCandidates.Add($env:ANDROID_SDK_ROOT) }
$localSdk = Get-LocalSdkPath
if ($localSdk) { $sdkCandidates.Add($localSdk) }
if ($env:LOCALAPPDATA) {
    $sdkCandidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
}
if ($env:ANDROID_USER_HOME) {
    $sdkCandidates.Add((Join-Path $env:ANDROID_USER_HOME 'Sdk'))
}
if ($env:ProgramFiles) {
    $sdkCandidates.Add((Join-Path $env:ProgramFiles 'Android\Sdk'))
}
if (${env:ProgramFiles(x86)}) {
    $sdkCandidates.Add((Join-Path ${env:ProgramFiles(x86)} 'Android\android-sdk'))
}
$sdkCandidates.Add('C:\Android\Sdk')
$sdkCandidates.Add('C:\android-sdk')

$sdkRoot = $null
foreach ($candidate in $sdkCandidates) {
    if (Test-AndroidSdk $candidate) {
        $sdkRoot = (Resolve-Path -LiteralPath $candidate.Trim().Trim('"').TrimEnd('\', '/')).Path
        break
    }
}
if (!$sdkRoot) {
    # There is no SDK in the known locations. Offer a supported first-run installation.
    if (!$env:LOCALAPPDATA) { throw 'LOCALAPPDATA is unavailable. Install the Android SDK and run build.bat -Sdk "C:\path\to\Sdk".' }
    $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    Write-Host "No Android SDK detected. Default installation directory: $sdkRoot" -ForegroundColor Yellow
}
if (!(Test-Path -LiteralPath (Join-Path $sdkRoot 'platforms\android-35') -PathType Container) -or
    !(Test-Path -LiteralPath (Join-Path $sdkRoot 'build-tools\35.0.0\zipalign.exe') -PathType Leaf) -or
    !(Test-Path -LiteralPath (Join-Path $sdkRoot 'build-tools\35.0.0\apksigner.bat') -PathType Leaf)) {
    Install-MissingSdkComponents $sdkRoot
}
if (!(Test-Path -LiteralPath (Join-Path $sdkRoot 'platforms\android-35') -PathType Container)) {
    throw "Android SDK Platform 35 still missing at $sdkRoot."
}
# Gradle reads local.properties; use a forward-slash path to avoid Java
# properties interpreting Windows backslash sequences as escapes.
$gradleSdkPath = $sdkRoot.Replace('\', '/')
$localPropertiesPath = Join-Path $PSScriptRoot 'local.properties'
$existingProperties = @()
if (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf) {
    $existingProperties = @(Get-Content -LiteralPath $localPropertiesPath | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' })
}
$existingProperties += "sdk.dir=$gradleSdkPath"
Set-Content -LiteralPath $localPropertiesPath -Value $existingProperties -Encoding ASCII
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
Write-Host "Using Android SDK: $sdkRoot"

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
