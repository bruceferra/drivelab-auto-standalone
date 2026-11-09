# One-time setup for independent standalone app project.
# Requires: git configured, GitHub CLI authenticated, JDK17/keytool.
# Creates a PUBLIC repository; release assets remain available for OTA downloads.
param([string]$Repository='bruceferra/drivelab-auto-standalone')
$ErrorActionPreference='Stop'
$gh=(Get-Command gh -ErrorAction Stop).Source
$null=Get-Command git -ErrorAction Stop
$null=Get-Command keytool -ErrorAction Stop
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Write-Host "NEW public repo: https://github.com/$Repository"
Write-Host 'Nothing will be changed in bruceferra/drivelab.'
Write-Host 'The signing keys will be generated locally and MUST be backed up offline.'
& (Join-Path $PSScriptRoot 'create-github-repo.ps1') -Repository $Repository
if($LASTEXITCODE -ne 0){throw 'New GitHub repository step failed'}
& (Join-Path $PSScriptRoot 'setup-signing.ps1') -Repository $Repository
if($LASTEXITCODE -ne 0){throw 'Signing key generation failed'}
& (Join-Path $PSScriptRoot 'upload-signing-secrets.ps1') -Repository $Repository
if($LASTEXITCODE -ne 0){throw 'Signing secret upload failed'}
& $gh workflow run publish-release.yml --ref main --repo $Repository -f tag=v0.2.0
if($LASTEXITCODE -ne 0){throw 'Could not trigger signed release'}
Write-Host "Release CI triggered: https://github.com/$Repository/actions"
Write-Warning 'BACK UP the signing/ folder offline. Debug APKs are not production installers.'
