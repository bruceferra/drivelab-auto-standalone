# Incidente OTA Receiver 0.2.44 — 12/11/2026

## Síntoma
En la Jetour T1, Receiver 0.2.43 respondió "Esta es la última versión" ante la versión 0.2.44 publicada.

## Causa raíz comprobada
APK v0.2.44: versionCode **45**. El archivo `receiver.json` generado por `.github/workflows/publish-receiver-0244.yml`: versionCode **44**. En `Updater.latest()`, solo hay oferta si `manifest.versionCode > installed.versionCode`. Receiver 0.2.43 era code44; manifest44 no era mayor y daba falsa indicación de versión al día. No era fallo confirmado de WiFi ni de la Jetour.

## Solución
Receiver **0.2.45 / code46**, nuevo workflow `publish-receiver-0245.yml` con comparación explícita del code46 en APK (aapt) y manifiesto `receiver.json`, igual certificado de firma y mismo Sender 0.2.16. Mantiene jornada, clientes, electricidad experimental y diagnóstico de freno de solo lectura.

Instalación encima de Receiver actual, sin desinstalar ni borrar datos; botón **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA**.

## Prevención
Los siguientes workflows deben generar el manifiesto con el código real de la APK y verificar explícitamente esa equivalencia antes de publicar. Un simple release correcto no demuestra que el actualizador vaya a ofrecer la versión.
