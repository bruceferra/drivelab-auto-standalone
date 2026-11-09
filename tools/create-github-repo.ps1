# Creates a NEW public GitHub repository; does not modify bruceferra/drivelab.
# Requires git and GitHub CLI authenticated with `gh auth login`.
param([string]$Repository='bruceferra/drivelab-auto-standalone')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gh=(Get-Command gh -ErrorAction Stop).Source
$git=(Get-Command git -ErrorAction Stop).Source
Push-Location $root
try {
  if(Test-Path '.git'){throw 'This folder already has a git repository; review before pushing.'}
  & $git init -b main
  if($LASTEXITCODE -ne 0){throw 'git init failed'}
  & $git add .
  if($LASTEXITCODE -ne 0){throw 'git add failed'}
  & $git commit -m 'Initial independent DriveLab Auto Sender and Receiver'
  if($LASTEXITCODE -ne 0){throw 'git commit failed (configure git user.name / user.email)'}
  & $gh repo create $Repository --public --source . --remote origin --push
  if($LASTEXITCODE -ne 0){throw 'Remote repository creation/push failed'}
  Write-Host "Repository created: https://github.com/$Repository"
  Write-Host 'Next: provision permanent signing keys and run the release workflow.'
} finally { Pop-Location }
