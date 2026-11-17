param([switch]$Setup, [switch]$Development)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ($Setup -or !(Test-Path -LiteralPath '.tools/java-home.txt')) {
    python tools/setup_android.py
    if ($LASTEXITCODE -ne 0) { throw 'Android tool setup failed.' }
}
$env:JAVA_HOME = (Get-Content -LiteralPath '.tools/java-home.txt' -Raw).Trim()
$variant = if ($Development) { 'debug' } else { 'release' }
if ($Development) {
    & .\gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain
} else {
    & .\gradlew.bat assembleRelease lintRelease testReleaseUnitTest --console=plain
}
if ($LASTEXITCODE -ne 0) { throw 'Build or checks failed.' }
New-Item -ItemType Directory -Force -Path dist | Out-Null
$apkMetadata = Get-Content -LiteralPath "app/build/outputs/apk/$variant/output-metadata.json" -Raw | ConvertFrom-Json
$apkVersion = $apkMetadata.elements[0].versionName
$apkDestination = "dist/WaktuSolatMY-v$apkVersion-$variant.apk"
if ($Development) {
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $apkDestination
} else {
    & .\tools\sign_release.ps1 -UnsignedApk (Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release-unsigned.apk') -OutputApk (Join-Path $PSScriptRoot $apkDestination)
    python tools/verify_release.py $apkDestination
    if ($LASTEXITCODE -ne 0) { throw 'Release security checks failed.' }
}
Get-FileHash -LiteralPath $apkDestination -Algorithm SHA256
