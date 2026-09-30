param([string]$Sdk, [string]$JavaHome = $env:JAVA_HOME, [string]$BuildTools = '36.1.0')
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!$Sdk) {
    if ($env:ANDROID_HOME) { $Sdk = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $Sdk = $env:ANDROID_SDK_ROOT }
    else { $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}
if (!$JavaHome) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $javaCommand.Source
        $info.Arguments = '-XshowSettings:properties -version'
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardError = $true
        $javaProcess = [System.Diagnostics.Process]::Start($info)
        $properties = $javaProcess.StandardError.ReadToEnd()
        $javaProcess.WaitForExit()
        $homeMatch = [regex]::Match($properties, '(?m)^\s*java\.home\s*=\s*(.+)$')
        if ($homeMatch.Success) { $JavaHome = $homeMatch.Groups[1].Value.Trim() }
        $javaProcess.Dispose()
    }
}
if (!$JavaHome) { throw 'JDK 17 or newer is required. Set JAVA_HOME or pass -JavaHome "C:\path\to\jdk".' }
foreach ($toolName in @('java.exe','javac.exe','jar.exe','keytool.exe')) {
    if (!(Test-Path -LiteralPath (Join-Path $JavaHome "bin\$toolName"))) { throw "Missing $toolName in $JavaHome. Use a full JDK 17 or newer with -JavaHome." }
}
# Keep version and SDK settings in one place: app/build.gradle.
$appConfig = Get-Content 'app\build.gradle' -Raw
function Read-Setting([string]$Pattern, [string]$Name) {
    $settingMatch = [regex]::Match($appConfig, $Pattern)
    if (!$settingMatch.Success) { throw "Cannot read $Name from app/build.gradle." }
    return $settingMatch.Groups[1].Value
}
$compileSdk = Read-Setting 'compileSdk\s+(\d+)' 'compileSdk'
$minSdk = Read-Setting 'minSdk\s+(\d+)' 'minSdk'
$targetSdk = Read-Setting 'targetSdk\s+(\d+)' 'targetSdk'
$versionCode = Read-Setting 'versionCode\s+(\d+)' 'versionCode'
$versionName = Read-Setting "versionName\s+'([^']+)'" 'versionName'
$bt = Join-Path $Sdk "build-tools\$BuildTools"
$android = Join-Path $Sdk "platforms\android-$compileSdk\android.jar"
foreach ($requiredPath in @($android, "$bt\aapt2.exe", "$bt\d8.bat", "$bt\zipalign.exe", "$bt\apksigner.bat")) {
    if (!(Test-Path -LiteralPath $requiredPath)) { throw "Missing Android SDK file: $requiredPath. Install platform $compileSdk and build-tools $BuildTools using Android Studio SDK Manager." }
}
$buildRoot = Join-Path $PSScriptRoot 'build\sdk'
# A fresh staging folder prevents classes/resources removed from source leaking into a later APK.
$out = Join-Path $buildRoot ('work-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force "$out\classes", "$out\dex", "$out\generated" | Out-Null
$env:JAVA_HOME = $JavaHome
Copy-Item $android "$out\android.jar" -Force
$android = "$out\android.jar"
function Run([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
Run "$bt\aapt2.exe" @('compile','--dir','app\src\main\res','-o',"$out\resources.zip")
$manifest = (Get-Content 'app\src\main\AndroidManifest.xml' -Raw).Replace('<manifest ', '<manifest package="com.webshelf.app" ')
[IO.File]::WriteAllText("$out\AndroidManifest.xml", $manifest)
Run "$bt\aapt2.exe" @('link','-o',"$out\unsigned.apk",'-I',$android,'--manifest',"$out\AndroidManifest.xml",'--java',"$out\generated",'--min-sdk-version',$minSdk,'--target-sdk-version',$targetSdk,'--version-code',$versionCode,'--version-name',$versionName,"$out\resources.zip")
$sources = @(Get-ChildItem 'app\src\main\java',"$out\generated" -Filter '*.java' -Recurse | ForEach-Object FullName)
Run "$JavaHome\bin\javac.exe" (@('-encoding','UTF-8','--release','17','-classpath',$android,'-d',"$out\classes") + $sources)
Run "$JavaHome\bin\jar.exe" @('cf',"$out\classes.jar",'-C',"$out\classes",'.')
Run "$bt\d8.bat" @('--lib',$android,'--min-api',$minSdk,'--output',"$out\dex", "$out\classes.jar")
Run "$JavaHome\bin\jar.exe" @('uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
Run "$bt\zipalign.exe" @('-f','4',"$out\unsigned.apk","$out\aligned.apk")
$key = Join-Path $buildRoot 'debug.keystore'
if (!(Test-Path $key)) {
    Run "$JavaHome\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Run "$bt\apksigner.bat" @('sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',"$PSScriptRoot\WebShelf-debug.apk","$out\aligned.apk")
Run "$bt\apksigner.bat" @('verify','--verbose',"$PSScriptRoot\WebShelf-debug.apk")
Write-Output "APK created: $PSScriptRoot\WebShelf-debug.apk"
