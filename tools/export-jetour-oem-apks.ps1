# Exportación de APK OEM vía ADB. SOLO LECTURA, sin root y sin cambiar la T1.
# Ejecutar en PowerShell de Windows con adb instalado y autorizado.
# Destino: carpeta local Jetour-OEM-Audit y un ZIP para análisis.
param([string]$OutputFolder = ".\\Jetour-OEM-Audit")

$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "ADB no está en PATH. Abrí PowerShell desde la carpeta platform-tools o agregala a PATH."
}
$devices = @(adb devices | Select-String '\tdevice$')
if ($devices.Count -ne 1) {
    throw "Se requiere exactamente un dispositivo ADB autorizado. Revisá 'adb devices'."
}
$packages = @(
    'com.autolink.app.vehicleservice',
    'com.autolink.carservice',
    'com.autolink.clusterinteraction'
)
New-Item -ItemType Directory -Force -Path $OutputFolder | Out-Null
$manifest = @()
foreach ($pkg in $packages) {
    $paths = @(adb shell pm path $pkg)
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "No se pudo consultar paquete: $pkg"
        continue
    }
    $validPaths = @($paths | ForEach-Object { $_.Trim() -replace '^package:', '' } |
        Where-Object { $_ -match '^/.*\.apk$' })
    if ($validPaths.Count -eq 0) {
        Write-Warning "No se encontró APK de $pkg"
        continue
    }
    $target = Join-Path $OutputFolder $pkg
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($remotePath in $validPaths) {
        $name = [System.IO.Path]::GetFileName($remotePath)
        $localPath = Join-Path $target $name
        & adb pull $remotePath $localPath
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $localPath)) {
            Write-Warning "No se pudo leer $remotePath (permisos de Android)"
            continue
        }
        $hash = (Get-FileHash -Path $localPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $manifest += [pscustomobject]@{
            Package = $pkg
            Apk = $name
            Sha256 = $hash
            Bytes = (Get-Item $localPath).Length
        }
    }
}
$manifest | Export-Csv -Path (Join-Path $OutputFolder 'manifest.csv') -NoTypeInformation -Encoding UTF8
if ($manifest.Count -eq 0) { throw "No se pudieron extraer APK accesibles; no se modificó la T1." }
$zipName = Join-Path (Get-Location).Path 'Jetour-OEM-Audit.zip'
if (Test-Path $zipName) { Remove-Item $zipName -Force }
Compress-Archive -Path (Join-Path $OutputFolder '*') -DestinationPath $zipName
Write-Output "LISTO: $zipName"
Write-Output "Paquetes descargados: $($manifest.Count). No se ejecutaron escrituras en el auto."
