# DriveLab Auto · Receiver 0.2.31 + Sender 0.2.14 (11/11/2026)

## Pedido del propietario

- **No más PIN de ocho dígitos** para subir diagnósticos y TRIP, ni copiar CSV a mano con ADB/WebADB.
- **QR en lugar de IP y seis cifras** para vincular Xiaomi con la T1.
- Botón **Exportar** conserva el CSV y lo envía por HTTPS al servidor privado si el dispositivo está autorizado.
- Espejos **AUTO en segundo plano**, incluso usando Waze, mapas o menú original, y después de recuperar espejos, volver a AUTO sin segundo botón.

## Dos QR totalmente separados

### QR 1 — VINCULAR XIAOMI, solo red local

En T1: Receiver → CONEXIÓN XIAOMI / AJUSTES → `VINCULAR XIAOMI POR QR`. Emite ticket criptográfico aleatorio de **192 bits**, efímero (**120 segundos**) y QR con URI `drivelabauto://pair?ip=<IPv4 LAN>&ticket=<48 hex>`. No envía datos a la nube. En Xiaomi: Sender 0.2.14 → `VINCULAR T1 POR QR` usa Google Code Scanner 16.1.0 (Google Play services; si no disponible, mantiene método manual con 6 dígitos). Android puede también abrir Sender desde un escáner externo con ese esquema URI.

El protocolo `BridgeCrypto.qrPairProof` usa **HMAC-SHA256(ticket, clave pública efímera ECDH del Sender)**. Se transmite el MAC en la nueva acción de socket `action=3`, **no el ticket**. El receptor valida tiempo, ticket y firma del MAC y autoriza esa sesión sin segunda comparación manual. Sigue confirmación autenticada AES-GCM de la misma sesión ECDH; ticket se elimina al completar. La acción manual anterior `action=1` sigue intacta. El QR no es una clave permanente de navegación.

### QR 2 — ACTIVAR SUBIDA DE CSV A NUBE, una sola vez

En T1: VEHÍCULO → DIAGNÓSTICO → `ACTIVAR SERVIDOR · MOSTRAR QR`. La T1 genera **clave de dispositivo aleatoria 256 bits**, cifrada en SharedPreferences mediante **AndroidKeyStore AES-GCM**. Solo manda su hash SHA-256 al servidor durante el alta, nunca lo incluye en el QR. La T1 envía un reto aleatorio de **192 bits** que vence a los **5 min**; el QR muestra URL HTTPS de un único uso para autorizar ese reto escaneándolo con cualquier cámara del Xiaomi. La T1 consulta el estado en segundo plano si se deja ese panel abierto. Después ya no se vuelve a pedir PIN ni QR para cada exportación. El token de autenticación de dispositivo se envía exclusivamente por HTTPS en header privado. No hay clave estática de servidor ni de GitHub en APK.

**Privacidad:** Edge Function `drivelab-auto-diagnostics` en proyecto Supabase ProManager `wgqbvbzhdnkkrpkrohej`, nuevo storage independiente bajo RLS `drivelab_auto_cloud_devices`, `drivelab_auto_diagnostic_csv_chunks`. No existe endpoint de lectura pública de CSV. Solo Edge Function con credencial de servicio inserta; la cuenta del propietario/ChatGPT con Supabase conectado puede consultar. Un QR temporal escaneado prueba posesión de la T1, **no constituye por sí mismo autenticación de una cuenta ProManager**. Alta por IP limitada para reducir abusos. La nube requiere conectividad HTTPS; al fallar, se conserva todo local.

## Qué se sube al exportar

- `phev_modes_YYYYMMDD_HHmmss.csv`: 1040 Smart/Forzado, 1041 SOC, 1043 EV/HEV/EVMax, 1046 regeneración, PDC radar/wiper crudos, etc.
- `oem_callbacks_YYYYMMDD_HHmmss.csv`: eventos OEM del consumo de 500 metros y 50 km.
- `code_diag_YYYYMMDD_HHmmss.csv`: captura CODE/tandas + estado del espejo AUTO, servicio vivo, marcha cruda, velocidad 2148/1089, última transición y fallo de fondo.
- **TRIP:** cuando se habilita la sincronización después del QR, se reusan los lotes `drivelab_auto_trip_batches` existentes con **lista blanca estricta de campos numéricos** (`ts`, `gear`, `speed`, `fuel`, SOC, HV, promedios OEM, etc.). No se sube el CSV crudo del viaje, que puede contener GPS/ubicación. `T1TripSync` elimina la necesidad de PIN. `EXPORTAR ÚLTIMO TRIP` solicita también envío de pendientes si la nube está activada.

Todos los archivos generados localmente se conservan. Los CSV de diagnóstico son enviados fragmentados (máximo 600 kB, 128 fragmentos idempotentes por archivo); no subir APK ni grandes archivos multimedia.

## Espejos AUTO — fondo real y recuperación

- `MirrorAutomationService` es **foreground Service**, no depende de mantener ReceiverActivity abierta. Antes abría y cerraba el Binder de `VehicleService` cada 250 ms; ahora **mantiene una conexión persistente**, reintenta ante fallas estrictamente de lectura sin mover actuadores hasta tener muestras válidas. Los fallos durante una secuencia de movimiento siguen parando AUTO para evitar referencia relativa incierta.
- UI muestra si el servicio está **ACTIVO** además de la preferencia AUTO. Conserva última transición y último error para diagnóstico remoto en CODE CSV.
- Botón `RECUPERAR ESPEJOS · 10 PASOS ARRIBA`: pausa AUTO, exige velocidad 0 y marcha conocida, ejecuta con límites OEM. **Solo si ambos lados completan todo**, reinicia FSM y rearma AUTO automáticamente para un **nuevo** evento de R. Si hay error o movimiento parcial, queda desarmado y requiere revisión.
- **No se afirma que el no-retorno por D>10 ya esté solucionado físicamente**: hay discrepancias candidatas entre 2148/1089 y falta contrastar datos reales. Este cambio evita saturar conexiones y permite capturar la causa sin ADB. Android/ROM OEM puede suspender o matar servicios; el sistema no puede prometer imposibilidad de cortes de fondo. Tras reinicio de proceso no se rearman movimientos ciegamente porque no se conoce la pose D física.
- No se modifica AVAS 2074, modo EV/HEV, radar ni seguridad de limpiaparabrisas; siguen lectura y diagnóstico únicamente.

## Compilación y actualización

Receiver `v0.2.31` versionCode **32**, Sender `v0.2.14` versionCode **15**, mismo package y certificado firmado que versiones anteriores. Workflow OTA publica primero Sender, luego Receiver como `latest`, con ambos manifiestos independientes `sender.json` y `receiver.json`. No desinstalar ni borrar datos del vehículo. Verificar QA Android y continuidad de firma antes de publicar. Los cambios de backend ya fueron desplegados como Edge Function v3; probar alta QR desde T1, export y receipt con una unidad real cuando estén instaladas ambas APK.

### Consultar sin ADB desde ChatGPT

Conector Supabase → proyecto Promanager:
- Diagnósticos: `select device_id, filename, file_id, chunk_index, chunk_total, received_at, csv_chunk from public.drivelab_auto_diagnostic_csv_chunks order by received_at desc, file_id, chunk_index;`
- Reconstruir CSV por `file_id` y orden `chunk_index`, verificar SHA256 conjunto contra `file_sha256`.
- Viajes: `select trip_id, row_offset, row_count, received_at, snapshot from public.drivelab_auto_trip_batches order by received_at desc limit 30;`
- No incluir credenciales del dispositivo ni números de VIN en consultas o resultados públicos.
