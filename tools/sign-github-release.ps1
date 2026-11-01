# Signs the GitHub (full) release APK with RetroForge's GitHub key, carrying the rotation from the old key.
#
# 0.8.0 and 0.8.1 were signed with this PC's debug key. Android 9+ accepts the new key as an update because the APK
# carries a signed record (the lineage) saying the new key replaces the old one; older Android still sees the old key's
# v1/v2 signature, so it updates too. Both keys are needed for every release from now on.
#
# Build first:  .\gradlew :app:assembleFullRelease   (unsigned: there is no keystore.properties)
# Then run:     .\tools\sign-github-release.ps1
# It asks for the GitHub key's password; nothing is written to disk but the signed APK (and, the first time, the lineage).

param(
    [string]$Apk = "app\build\outputs\apk\full\release\app-full-release-unsigned.apk",
    [string]$Keys = "F:\RetroForge-keys"
)
$ErrorActionPreference = "Stop"

$old = Join-Path $Keys "retroforge-github-debug.keystore"
$new = Join-Path $Keys "retroforge-github-release.jks"
$lineage = Join-Path $Keys "retroforge-github-lineage.bin"
foreach ($f in @($Apk, $old, $new)) { if (-not (Test-Path $f)) { throw "Not found: $f" } }

$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object { [version]$_.Name } | Select-Object -Last 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"

$version = (Select-String -Path "app\build.gradle.kts" -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
$out = Join-Path (Split-Path $Apk) "RetroForge-$version.apk"

$secure = Read-Host "Password for retroforge-github-release.jks" -AsSecureString
$env:RF_GITHUB_KEY_PASS = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
try {
    # The old key is Android's standard debug key, whose password is the well-known "android".
    $oldSigner = @("--ks", $old, "--ks-pass", "pass:android", "--ks-key-alias", "androiddebugkey")
    $newSigner = @("--ks", $new, "--ks-pass", "env:RF_GITHUB_KEY_PASS", "--ks-key-alias", "retroforge")
    if (-not (Test-Path $lineage)) {
        & $apksigner rotate --out $lineage --old-signer @oldSigner --new-signer @newSigner
        if ($LASTEXITCODE -ne 0) { throw "apksigner rotate failed" }
        Write-Host "Made the lineage: $lineage (back it up with the keys)"
    }
    & $apksigner sign @oldSigner --next-signer @newSigner --lineage $lineage --rotation-min-sdk-version 28 --out $out $Apk
    if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }
} finally {
    Remove-Item Env:RF_GITHUB_KEY_PASS -ErrorAction SilentlyContinue
}

& $apksigner verify --verbose $out | Select-String "Verified using|Number of signers"
Write-Host "Signed: $out"
