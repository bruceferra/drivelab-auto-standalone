# Receiver 0.2.43 — grabación continua real y clientes que NO terminan al reiniciar el auto

Fecha 12/11/2026 · Jetour T1 PHEV Argentina · Sender 0.2.16 sin cambios.

## Problema reproducido

El usuario verificó que los tres destinos manuales EV, HEV y EV MAX cambiaron físicamente en P en la pantalla original. Durante la prueba de jornada, Supabase recibió **336 muestras, 18 lotes, 8.03 km estimados** en `20261112_071153` y después **40 muestras, 2 lotes, 3.15 km y seis eventos OEM de 500m por canal** en `20261112_073616`. Los dos juegos de muestras llegaron al servidor, por lo que el problema era de **continuidad local del grabador**, no que la nube descartara los datos.

La versión 0.2.42 creaba un `trip_id` nuevo cada vez que recibía `ACTION_START`, abría el CSV en modo truncado, reiniciaba distancia y contadores, y `T1OemConsumptionCallbacks` rechazaba abrir un archivo OEM existente. El cliente (por ejemplo `Tigre`) retenía el ID anterior en `SharedPreferences` y `T1JourneyMetrics.between` se negaba correctamente a unir checkpoints de sesiones diferentes; pero no había migración, por lo que ningún botón conseguía reparar el cliente.

La exigencia nueva del usuario prevalece: **jornada y subviajes permanecen abiertos tras apagar/encender la camioneta, cerrar la UI, matar el proceso o actualizar el APK; SOLAMENTE se finalizan cuando el usuario pulsa el botón FINALIZAR correspondiente.** Al contrario de una implementación temprana de esta rama, ya NO se crean archivos de cliente FINALIZADO automáticamente al detectar un reinicio.

## Patrón tomado del Driver Uber

Se revisó `bruceferra/drivelab/android/app/src/main/java/ar/com/rango5gps/ContinuousTripBootReceiver.java`: persistir la opción de modo continuo y restaurar en `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`, sin iniciar una sesión distinta; registrar falta de permisos o reinicio bloqueado, para reintentar cuando se abra la app. También se revisó su `TripStore`, que conserva un viaje con `ended_ms=0` y mantiene información de tiempo pausado/estacionado entre procesos.

## Implementación Receiver 0.2.43

### Jornada y CSV
- `T1TripRecorderService.DESIRED` representa **intención persistente**: solamente `ACTION_STOP` (el botón **CERRAR JORNADA**, impedido mientras haya cliente activo) la pone en `false`. `RUNNING` representa si hay muestras recientes y puede quedar falso durante un corte sin terminar la jornada.
- `ACTION_RESTORE`, `START_STICKY`, receptor de encendido y actualización y el propio `onResume` del cockpit intentan restaurar el proceso si estaba activo. Una excepción de Android/OEM se comunica en `RESTORE_STATUS`; no se borra el CSV. No se supone que todas las ROM permiten FGS automático en segundo plano.
- `T1TripRecoveryState` lee **el último registro CSV completo**, obtiene cantidad de muestras, km acumulados y energía HV bruta; omite la línea final incompleta si hubo un corte. El TRIP reanuda el **mismo ID**, conserva contadores y abre `FileOutputStream(path,true)`, sin escribir otro encabezado y sin borrar registros.
- `T1OemConsumptionCallbacks.startForTrip` admite el mismo `trip_oem_<id>.csv`: recompone contadores y sumatorias de los callbacks 500m desde el archivo y continúa anexando. No deduplica eventos de 500m porque dos segmentos contiguos pueden tener igual valor.
- `T1TripSync.readBatch` se hace robusto ante la línea parcial de un corte: filtra filas con menos columnas que el encabezado, avanza el cursor SOLO por muestras válidas y sigue enviando filas posteriores, sin subir coordenadas crudas.
- `PARKED_MS` acumula tiempo parado durante muestras activas y `OFFLINE_MS` mide intervalos de ausencia de datos entre apagado y recuperación. El tiempo sin telemetría **NO es distancia ni consumo inventado**, y no es un estacionamiento físicamente medido.

### Cliente / subviaje
- `T1JourneyStore.ACTIVE`, etiqueta y checkpoint permanecen guardados al salir o reiniciar el auto; `PENDING` ya no caduca automáticamente a los 90 s, sino que se cumple al volver una muestra o se cancela al pulsar el botón correspondiente.
- Si una versión anterior creó un segundo `trip_id` mientras `Tigre` estaba activo, `reconcileInterrupted` lee el CSV anterior y rescata el delta de ese tramo sin FINALIZAR. Registra el kilometraje y eventos recuperables como **carry-over**, mantiene el nombre y la hora original del cliente y sigue sumando desde el comienzo de la jornada nueva, con una cadena `trip_idA+trip_idB` para auditoría. No une los timestamps/km de las dos sesiones como si fueran la misma fuente.
- Si el archivo antiguo no se puede leer, **no se finaliza ni se borra Tigre**: aparece **REPARAR CONTINUIDAD SIN CERRAR**, que permite marcar el tramo anterior como incompleto y continuar con el mismo cliente, sin inventar kilómetros.
- SOLO `T1JourneyStore.finish`, invocado desde **✓ FINALIZAR TRAYECTO** con confirmación, agrega la fila a `client_trips.csv`, y conserva su hora original. No existe finalización automática en BootReceiver, Service ni reconciliación.
- El cliente muestra tiempo transcurrido incluyendo paradas y **detenido conocido vs. sin telemetría**; los tiempos anteriores a migraciones legacy sin información completa se marcan como aproximados.

### Confirmación visual de grabación
En la primera pantalla del cockpit, antes del velocímetro:
- **● JORNADA GRABANDO** en rojo intermitente (alterna con círculo apagado) si `RUNNING` y la última muestra es reciente; si no, **ESPERANDO OEM / DETENIDA**.
- **● CLIENTE GRABANDO** independiente y rojo intermitente solamente si existe un cliente y su checkpoint se actualiza con muestras frescas. Si está preparándose, sin datos o vinculado al ID viejo, lo advierte sin mostrar un falso REC.
- Botones **INICIAR JORNADA** e **INICIAR TRAYECTO** cambian de color durante la captura, pero las etiquetas informativas son las que validan que hay muestras nuevas.

## Validación requerida en el auto (detenido para navegar)

1. Instalar 0.2.43 **después de finalizar la prueba actual de la 0.2.42**, porque la versión vieja todavía puede interrumpir un trayecto durante la actualización. No desinstalar ni borrar datos antes de instalar.
2. Abrir cockpit. Si `Tigre` quedó abierto en la versión anterior, debe permanecer **cliente activo**, con el contador recuperado o el botón de reparar si faltó el archivo. No forzar `FINALIZAR` para destrabarlo.
3. Con jornada y cliente activos, copiar el nombre del `trip_id` y el contador de muestras. Apagar/encender el vehículo. Comprobar que el mismo ID y el kilometraje continúan aumentando, no que aparezca un nuevo nombre o 0,00 km. La sesión puede mostrar **ESPERANDO OEM** hasta recibir datos frescos.
4. Repetir con la app cerrada y con una actualización futura. Verificar que, si el sistema bloqueó autoarranque, abrir el cockpit reintenta restaurar.
5. Probar un cliente nuevo, estacionamiento y reanudación, y **✓ FINALIZAR TRAYECTO**. Solo este botón guarda el trayecto como terminado. **CERRAR JORNADA** queda bloqueado mientras el cliente siga activo.
6. Usar `client_trips.csv`, `trip_<id>.csv`, `trip_oem_<id>.csv`, estado de las preferencias y las muestras de Supabase para auditar distancias y timestamps.

## Seguridad y límites

- La migración NO inventa kilometraje, kWh, litros, coordenadas, movimiento ni eventos de 500m durante cortes de Android. Si faltan eventos al reconstruir, el tramo se etiqueta incompleto.
- Los datos económicos continúan como estimaciones crudas; el consumo de nafta y electricidad en eventos OEM 500m **NO tiene aún validación metrológica completa**.
- No hay cambios de modo durante la marcha, escrituras automáticas OSM ni controles nuevos de luces. EV/HEV/EV MAX están **confirmados físicamente en P** por el usuario.
- Esta versión conserva toda la funcionalidad del 0.2.42, firma original del Receiver, Sender 0.2.16.
- La compilación automatizada prueba la reconstrucción de CSV, las sumas de eventos y que dos trip_id distintos no se mezclan. El comportamiento real ante apagado requiere validación de esta ROM OEM.
