# Requires Java 17, Android SDK platform 35 and Gradle 8.7 on PATH.
param([ValidateSet('Debug','Release')][string]$Variant='Debug')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    $gradle=Get-Command gradle -ErrorAction Stop
    & $gradle.Source --no-daemon (':sender:assemble'+$Variant) (':receiver:assemble'+$Variant)
    if($LASTEXITCODE -ne 0){throw 'Gradle build failed'}
    Write-Host "Sender APK: $root\sender\build\outputs\apk\$($Variant.ToLower())\sender-$($Variant.ToLower()).apk"
    Write-Host "Receiver APK: $root\receiver\build\outputs\apk\$($Variant.ToLower())\receiver-$($Variant.ToLower()).apk"
    if($Variant -eq 'Release'){
        Write-Warning 'Use the repository release workflow with dedicated permanent signing keys and SHA-256 manifest for user updates.'
    }
} finally { Pop-Location }
