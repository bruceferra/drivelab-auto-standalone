# DriveLab Auto — estado recuperado del chat saturado (11/11/2026)

> **Fuente de verdad para continuar:** `main`, commit `e991210991bad323fbdb5cb63bd6da941777db6e`. Se recuperó directamente de GitHub; no se reconstruyó de memoria una APK diferente.

## Versiones efectivas confirmadas

| App | Destino | Versión código fuente actual | APK firmada publicada | Canal |
| --- | --- | --- | --- | --- |
| **Sender** | Xiaomi | **0.2.16, versionCode 17** | [DriveLab-Auto-Sender-v0.2.16.apk](https://github.com/bruceferra/drivelab-auto-standalone/releases/download/v0.2.16/DriveLab-Auto-Sender-v0.2.16.apk) | APK añadida al tag histórico `v0.2.16`; `sender.json` de la release **latest** `v0.2.32` apunta a la nueva APK |
| **Receiver** | Jetour T1 | **0.2.32, versionCode 33** | [DriveLab-Auto-Receiver-v0.2.32.apk](https://github.com/bruceferra/drivelab-auto-standalone/releases/download/v0.2.32/DriveLab-Auto-Receiver-v0.2.32.apk) | Release **latest** `v0.2.32`; `receiver.json` conservado |

**No confundir etiquetas:** la release histórica `v0.2.16` también contiene un *Receiver 0.2.16 antiguo*; **NO** actualizar el Receiver T1 desde ese APK por error. En la página de `v0.2.32` hay un *Sender 0.2.15 anterior*; tampoco confundirlo con el Sender actualizado. Los actualizadores internos independientes están preparados para ofrecer las versiones correctas sin reinstalar ni volver a vincular.

- Repositorio: https://github.com/bruceferra/drivelab-auto-standalone
- Sender 0.2.16: [compilación y publicación firmada exitosas](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/43092816629). Se verificó mismo certificado de firma que Sender 0.2.15, identidad de paquete, versión, permiso Android 14, SHA-256 y manifest OTA.
- CI en `main` para commit `e991210`: [QA independiente exitoso](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/43092816676).
- Release conjunta Receiver 0.2.32 / Sender 0.2.15 anterior: [v0.2.32](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.32). [Workflow firmado exitoso](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/43087728930).
- Al revisar el 11/11/2026 no había pull requests abiertos. Se conservaron las ramas históricas; **no** limpiar ramas o datos de viajes como parte de la recuperación.

## Qué quedó efectivamente guardado en código

### Sender 0.2.15 → 0.2.16 (Xiaomi / Uber)

- `RouteActivity` ahora es una actividad transparente y breve, sin historial, que entrega el enlace a `RouteDispatchService` y se cierra: el objetivo es que **Uber permanezca visible** cuando se toca navegar o enviar al clúster.
- En modo **SOLO CLÚSTER**, no abrir Waze/DriveLab en Xiaomi al confirmar la entrega cifrada; notificación solo ante fallo. En modo **CLÚSTER + XIAOMI** se permite intentar Waze en el teléfono.
- Versión **0.2.16** corrige un fallo específico Android 14+: la declaración del permiso `FOREGROUND_SERVICE_DATA_SYNC` para el foreground service `dataSync`, agrega manejo de fallos de arranque/foreground y guarda el último estado de navegación. El estado se muestra cuando el propietario abre Sender; no obliga a traer la UI al frente.
- **Compilado, firmado y QA correctos; falta prueba física en Xiaomi con Uber real.** No afirmar todavía que el cierre desapareció en el dispositivo.

### Receiver 0.2.32 (Jetour T1)

- `TABLERO T1 → INICIAR TRIP` registra en paralelo `trip_<id>.csv` (muestreo principal ~3 s) y `trip_oem_<id>.csv` (todos los callbacks de consumo OEM **500 m**, más promedio 50 km compactando repeticiones idénticas). Se detienen juntos al tocar **DETENER TRIP**.
- Los contadores/últimos valores **crudos** de los callbacks 500 m y 50 km aparecen en pantalla y en columnas del TRIP, y hay exportación local del CSV OEM **sin ADB**.
- Prueba de campo disponible: dos segmentos sucesivos combustible `9 → 8`, eléctrico `−12 → −5`; promedios 50 km como `5,2` combustible y `1,3 → 1,7` eléctrico. **No hay unidades confirmadas.** Negativo eléctrico no implica por sí solo regeneración. No inferir litros/kWh hasta calibrar 100 segmentos contra mediciones reales.
- `VEHÍCULO → DIAGNÓSTICO` conserva la captura independiente de 3 minutos.
- Sincronización de TRIP por lotes numéricos saneados, sin GPS/VIN, más propuesta de subir CSV OEM sin GPS al endpoint privado del servidor tras finalizar el listener o al exportar. **El código del cliente y del servidor está en GitHub; no se comprobó en esta recuperación que la última modificación de la Edge Function esté desplegada y operativa.**
- QR de vinculación Xiaomi/T1 y QR independiente de autorización de CSV/nube; conservados, sin pedir PIN repetidamente. Módulos de tablero, ventanillas, AVAS manual, CODE, mapas, controles/diagnósticos de espejos y modo AUTO permanecen en Receiver. La recuperación de espejos y retorno en marcha todavía requieren pruebas reales: **no darlo por validado**.

## Pruebas pendientes para retomar sin perder avances

1. **Xiaomi:** comprobar en Sender → versión `0.2.16`. Si no está instalada, usar **ACTUALIZAR SENDER · INSTALACIÓN DIRECTA**. Si ya está instalada, no reinstalar. No desinstalar: preservar QR/pareo y preferencias.
2. **Uber → Navegar:** con `SOLO CLÚSTER`, elegir Sender, comprobar que Uber queda visible, que la ruta llega a Waze del clúster y que Xiaomi no se cierra. Si falla, abrir Sender y registrar el campo **Última navegación desde Uber**, sin repetir vinculación innecesariamente.
3. **T1:** confirmar Receiver `0.2.32` y usar su actualizador interno si falta. Iniciar un TRIP estacionado, avanzar un tramo de prueba de forma segura, detenerlo; validar conteos 500 m, CSV principal y `trip_oem`, exportación sin ADB y recepción en nube si ya está autorizada.
4. **Datos:** contrastar valores crudos del evento 500 m con velocidad/odómetro y ventanas de 50 km, sin imponer conversión física o signo antes de medir.
5. **Espejos AUTO:** comprobar retorno tras abandonar R y superar umbral de 10 km/h solo en prueba segura; conservar registro de error/estado. Evitar comandos OEM desconocidos, no desactivar sistemas de seguridad y no borrar DriveLab GPS/Uber Beta ni los archivos de viajes originales.

## Reglas de continuidad

- **No recompilar a ciegas:** Sender 0.2.16 ya fue compilado, firmado, publicado y agregado a OTA; Receiver 0.2.32 también. La tarea inmediata es **prueba física** y, únicamente si reproduce fallos, corregirlos con versiones nuevas.
- No mezclar `versionName` y `versionCode`: Sender 0.2.16/code17 y Receiver 0.2.32/code33.
- Respetar identidades de paquete y certificados existentes, actualizaciones nativas con validación de hash/firma, datos y vinculación.
- Mantener el cálculo viejo de litros del launcher descartado; ningún signo de energía se clasifica automáticamente como consumo o recuperación.
- Documentación de implementación previa: [TRIP OEM 500 m + Sender oculto](RECEIVER_0_2_32_SENDER_0_2_15_TRIP_500M_UBER_OCULTO.md), [QR, nube y espejos](RECEIVER_0_2_31_SENDER_0_2_14_QR_NUBE_ESPEJOS.md), [handoff histórico](HANDOFF_ENTRE_SOLAPAS.md), [índice maestro](INDICE_MAESTRO.md).
