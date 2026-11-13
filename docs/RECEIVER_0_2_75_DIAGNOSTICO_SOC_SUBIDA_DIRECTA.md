# Receiver 0.2.75 code76 — subir diagnóstico SOC directamente al servidor

## Petición y evidencia
El usuario probó una transición estacionada en CIUDAD a EV MAX y regeneración 1046 de ALTA 3 a BAJA 1. La ECU aplicó el cambio físico y el lector de DriveLab registró las tres etapas. La escritura de regeneración está, por tanto, físicamente confirmada en ese ensayo; la escritura/lectura de objetivo SOC 1041 sigue sin confirmación física definitiva.

Al tocar COMPARTIR DIAGNÓSTICO, la versión anterior abría el selector ACTION_SEND de otras aplicaciones. El usuario requiere la misma subida privada HTTPS ya existente, sin selector Android.

## Corrección
- Pantalla CONFIGURAR OPT. -> VER / SUBIR DIAGNÓSTICO SOC · 1/2/3 -> SUBIR AL SERVIDOR.
- Este botón es una acción explícita. Crea un CSV privado code_diag_YYYYMMDD_HHMMSS.csv en la misma carpeta local de diagnóstico/TRIP, empaquetando el último y el anterior intento OEM como una celda de CSV correctamente entrecomillada.
- El informe conserva ETAPA_1_PRECHECK, ETAPA_2_BINDER_OK, ETAPA_3_READBACK, intRaw/floatRaw 1041, fallas y texto original. No agrega ubicación GPS, VIN, claves ni cuentas y no modifica los CSV originales de viajes.
- Se invoca directamente T1CloudDiagnostics.upload, que exige vinculación previa mediante QR, verifica respuesta HTTPS, envía en fragmentos idempotentes y muestra en la pantalla SERVIDOR OK o la causa de error. **No** se llama ACTION_SEND, createChooser ni a otra aplicación.
- Si la nube todavía no está vinculada aparece VINCULAR QR, que abre ReceiverVehicleActivity en su pestaña de nube. Si falla la red, el CSV privado permanece en la T1 para reintentar.
- **La función Supabase Edge ya desplegada (drivelab-auto-diagnostics, ACTIVE, versión 5) admite el prefijo code_diag_ en su contrato FILE**: no se requiere nueva función ni exponer datos públicamente.

## Condiciones OEM
- T1SocRegenWritePolicy.regenDriveEligible acepta **P con velocidad OEM 0** o **D con lectura válida de velocidad 0..160 km/h**, nunca R/N ni velocidad desconocida. Por eso se puede comparar un intento de SOC detenido en P con uno en D.
- La regeneración 1046=1 ya se verificó desde 3 en la T1 del usuario estacionado, pero esta confirmación no demuestra por sí misma que el setter o getter de 1041 funcione.
- Si 1041 no confirma, se detiene el intento automático sin repetir ni mandar rollback 56; el usuario verifica el estado en la pantalla original de Jetour.

## Validación
T1OemReportCsvTest puro JVM: formato RFC4180 con comillas y líneas OEM, rechazo de reporte vacío, contrato filename code_diag, sin rutas arbitrarias. Revisión CI Android de ausencia ACTION_SEND/createChooser en editor y presencia de subida QR autenticada. OTA firma contra Receiver 0.2.74 y conserva Sender 0.2.16.
