# Upload existing permanent keystores created by setup-signing.ps1. Safe to retry.
param([string]$Repository='bruceferra/drivelab-auto-standalone')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dir=Join-Path $root 'signing'
$gh=(Get-Command gh -ErrorAction Stop).Source
foreach($app in @('SENDER','RECEIVER')) {
  $lower=$app.ToLowerInvariant()
  $file=Join-Path $dir ($lower+'.p12')
  $passFile=Join-Path $dir ($lower+'.password.txt')
  if(!(Test-Path $file) -or !(Test-Path $passFile)){throw "Missing keys for $app"}
  $password=[IO.File]::ReadAllText($passFile)
  $alias='drivelab-auto-'+$lower
  $b64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($file))
  & $gh secret set "${app}_KEYSTORE_B64" --body $b64 --repo $Repository
  if($LASTEXITCODE -ne 0){throw 'GitHub keystore secret upload failed'}
  & $gh secret set "${app}_STORE_PASSWORD" --body $password --repo $Repository
  if($LASTEXITCODE -ne 0){throw 'GitHub store password secret upload failed'}
  & $gh secret set "${app}_KEY_ALIAS" --body $alias --repo $Repository
  if($LASTEXITCODE -ne 0){throw 'GitHub signing alias upload failed'}
  & $gh secret set "${app}_KEY_PASSWORD" --body $password --repo $Repository
  if($LASTEXITCODE -ne 0){throw 'GitHub key password secret upload failed'}
}
Write-Host 'Both permanent signing identities uploaded to GitHub Actions secrets.'
