param(
    [Parameter(Mandatory = $true)][string]$UnsignedApk,
    [Parameter(Mandatory = $true)][string]$OutputApk
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = (Get-Content -LiteralPath (Join-Path $projectRoot '.tools/java-home.txt') -Raw).Trim()
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
$apksigner = Join-Path $projectRoot '.tools/android-sdk/build-tools/35.0.0/apksigner.bat'
$signingDirectory = Join-Path $env:USERPROFILE '.waktusolat-my/signing'
$publicDirectory = Join-Path $projectRoot 'signing'
$releaseStore = Join-Path $signingDirectory 'fitrirusli-release.p12'
$legacyStore = Join-Path $signingDirectory 'legacy-upgrade.p12'
$passwordFile = Join-Path $signingDirectory 'password.dpapi'
$lineage = Join-Path $publicDirectory 'rotation.lineage'
$publicCertificate = Join-Path $publicDirectory 'fitrirusli-release.pem'
New-Item -ItemType Directory -Force -Path $signingDirectory, $publicDirectory | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $signingDirectory /inheritance:r /grant:r "$($identity):(OI)(CI)F" 'SYSTEM:(OI)(CI)F' /q | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing-directory access.' }

if (!(Test-Path -LiteralPath $passwordFile)) {
    if ((Test-Path -LiteralPath $releaseStore) -or (Test-Path -LiteralPath $legacyStore) -or (Test-Path -LiteralPath $lineage)) {
        throw 'Signing material exists but its protected password is missing. Restore the original signing backup.'
    }
    $passwordBytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
    $newPassword = ConvertTo-SecureString -String ([Convert]::ToBase64String($passwordBytes)) -AsPlainText -Force
    ConvertFrom-SecureString -SecureString $newPassword | Set-Content -LiteralPath $passwordFile -Encoding utf8
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $newPassword.Dispose()
}
$protectedPassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($protectedPassword)
$previousPassword = [Environment]::GetEnvironmentVariable('WAKTUSOLAT_SIGN_PASS', 'Process')
$previousLegacyPassword = [Environment]::GetEnvironmentVariable('WAKTUSOLAT_LEGACY_PASS', 'Process')
try {
    $env:WAKTUSOLAT_SIGN_PASS = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:WAKTUSOLAT_LEGACY_PASS = 'android'
    if (!(Test-Path -LiteralPath $releaseStore)) {
        if (Test-Path -LiteralPath $lineage) { throw 'Release key missing; restore the original key instead of replacing it.' }
        & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $releaseStore -alias fitrirusli-release -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -dname 'CN=fitrirusli, O=fitrirusli, C=MY' -storepass:env WAKTUSOLAT_SIGN_PASS -keypass:env WAKTUSOLAT_SIGN_PASS
        if ($LASTEXITCODE -ne 0) { throw 'Release key creation failed.' }
    }
    if (!(Test-Path -LiteralPath $legacyStore)) {
        $originalDebugStore = Join-Path $env:USERPROFILE '.android/debug.keystore'
        if (!(Test-Path -LiteralPath $originalDebugStore)) { throw 'The original key is needed to preserve upgrades from 0.1.0/0.1.1.' }
        & $keytool -importkeystore -noprompt -srckeystore $originalDebugStore -srcalias androiddebugkey -srcstorepass:env WAKTUSOLAT_LEGACY_PASS -srckeypass:env WAKTUSOLAT_LEGACY_PASS -destkeystore $legacyStore -deststoretype PKCS12 -destalias legacy-upgrade -deststorepass:env WAKTUSOLAT_SIGN_PASS -destkeypass:env WAKTUSOLAT_SIGN_PASS
        if ($LASTEXITCODE -ne 0) { throw 'Legacy upgrade key preservation failed.' }
    }
    if (!(Test-Path -LiteralPath $lineage)) {
        & $apksigner rotate --out $lineage --old-signer --ks $legacyStore --ks-key-alias legacy-upgrade --ks-pass env:WAKTUSOLAT_SIGN_PASS --set-installed-data true --set-shared-uid false --set-permission false --set-auth false --set-rollback false --new-signer --ks $releaseStore --ks-key-alias fitrirusli-release --ks-pass env:WAKTUSOLAT_SIGN_PASS
        if ($LASTEXITCODE -ne 0) { throw 'Signing-key rotation failed.' }
    }
    & $keytool -exportcert -rfc -keystore $releaseStore -alias fitrirusli-release -storepass:env WAKTUSOLAT_SIGN_PASS -file $publicCertificate
    if ($LASTEXITCODE -ne 0) { throw 'Public certificate export failed.' }
    & $apksigner sign --in $UnsignedApk --out $OutputApk --min-sdk-version 26 --rotation-min-sdk-version 28 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --lineage $lineage --ks $legacyStore --ks-key-alias legacy-upgrade --ks-pass env:WAKTUSOLAT_SIGN_PASS --next-signer --ks $releaseStore --ks-key-alias fitrirusli-release --ks-pass env:WAKTUSOLAT_SIGN_PASS
    if ($LASTEXITCODE -ne 0) { throw 'Release APK signing failed.' }
    Write-Output 'Release signed with rotation from the original key. Private keys remain outside the project.'
} finally {
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $protectedPassword.Dispose()
    [Environment]::SetEnvironmentVariable('WAKTUSOLAT_SIGN_PASS', $previousPassword, 'Process')
    [Environment]::SetEnvironmentVariable('WAKTUSOLAT_LEGACY_PASS', $previousLegacyPassword, 'Process')
}
