param([string]$JavaHome = $env:JAVA_HOME, [string]$WipScript)
$ErrorActionPreference = 'Stop'
if ($WipScript) { $WipScript = (Resolve-Path -LiteralPath $WipScript).Path }
Set-Location $PSScriptRoot
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac.exe' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java.exe' }
foreach ($tool in @($javac, $java, 'node.exe')) {
    if (!(Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing $tool. Tests need a JDK and Node.js. Set JAVA_HOME or pass -JavaHome." }
}
function Run([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
$out = Join-Path $PSScriptRoot ('build\tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $out | Out-Null
Run $javac @('--release','17','-d',$out,'app\src\main\java\com\webshelf\app\WebsitePreferences.java','app\src\main\java\com\webshelf\app\WipLanguageAdapter.java','app\src\main\java\com\webshelf\app\SiteMatcher.java','tests\SiteMatcherTest.java','tests\ExportPreferenceScripts.java','tests\WipLanguageAdapterTest.java')
Run $java @('-cp',$out,'SiteMatcherTest')
Run $java @('-cp',$out,'ExportPreferenceScripts',"$out\scripts")
Run 'node.exe' @('tests\website-preferences.test.cjs',"$out\scripts")
if ($WipScript) {
    Run $java @('-cp',$out,'WipLanguageAdapterTest',$WipScript,"$out\adapted-language.js")
    Run 'node.exe' @('tests\wip-language-runtime.test.cjs',"$out\adapted-language.js")
}
