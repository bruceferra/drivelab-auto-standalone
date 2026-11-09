# One-time provisioning for a NEW, independent GitHub repository.
# Requires keytool (JDK 17) and optionally GitHub CLI 'gh auth login'.
param([string]$Repository='bruceferra/drivelab-auto-standalone', [switch]$UploadSecrets)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$keytool=(Get-Command keytool -ErrorAction Stop).Source
$dir=Join-Path $root 'signing'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
function New-Password {
  $bytes=New-Object byte[] 24
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  return [Convert]::ToBase64String($bytes)
}
foreach($app in @('SENDER','RECEIVER')) {
  $lower=$app.ToLowerInvariant()
  $file=Join-Path $dir ($lower+'.p12')
  if(Test-Path -LiteralPath $file) { throw "Signing key already exists: $file. Never rotate it for an installed app." }
  $pass=New-Password
  $alias='drivelab-auto-'+$lower
  & $keytool -genkeypair -alias $alias -keyalg RSA -keysize 3072 -validity 3650 `
      -storetype PKCS12 -keystore $file -storepass $pass -keypass $pass `
      -dname "CN=DriveLab Auto $app, O=DriveLab, C=AR" -noprompt
  if($LASTEXITCODE -ne 0) { throw "Could not create $app signing certificate" }
  $passFile=Join-Path $dir ($lower+'.password.txt')
  Set-Content -LiteralPath $passFile -Value $pass -NoNewline
  if($UploadSecrets) {
    $gh=(Get-Command gh -ErrorAction Stop).Source
    $b64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($file))
    & $gh secret set "${app}_KEYSTORE_B64" --body $b64 --repo $Repository
    & $gh secret set "${app}_STORE_PASSWORD" --body $pass --repo $Repository
    & $gh secret set "${app}_KEY_ALIAS" --body $alias --repo $Repository
    & $gh secret set "${app}_KEY_PASSWORD" --body $pass --repo $Repository
  }
  Write-Host "Signing key created: $file | alias $alias"
}
Write-Warning "Back up the entire signing/ directory OFFLINE, including passwords. NEVER upload it to GitHub or share it in chat."
if(!$UploadSecrets){Write-Host "To upload 8 secrets later, run this script's documented gh commands manually; do NOT re-run key generation."}
