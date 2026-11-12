# DriveLab Auto Receiver 0.2.59 (code 60) · Historial de viajes y jornadas a lo ancho

## Objetivo
- Historial con **dos columnas visuales en paralelo, ocupando toda la anchura** del Cockpit en pantalla apaisada:
  - **Izquierda:** VIAJES, incluyendo Viaje 1 y Viaje 2.
  - **Derecha:** JORNADAS, Viaje Total, basado exclusivamente en cada CSV principal `trip_YYYYMMDD_HHMMSS.csv`.
  - Al tocar cualquier elemento se muestra **una fila completa con los mismos seis indicadores** del tablero: consumo L/100 y km/L, km eléctricos puros, combustible consumido, electricidad consumida, costo combustible y autonomía total. La autonomía de un viaje anterior se muestra `NO GUARDADA` si no se registró, nunca se reemplaza por la autonomía actual.
- Se mantiene información de fecha/hora, identificador de sesión, km registrados y km corregidos cuando hay evidencia válida. Las listas pueden mostrar hasta 30 elementos en cada columna; están separadas visualmente y comparten la fila de detalle.
- **Nunca sumar Viaje 1 + Viaje 2** para obtener Viaje Total: pueden estar solapados. Cada Jornada tiene su propio contador.
- Lectura y reconstrucción histórica en **hilo de fondo** en lugar de recorrer todos los CSV desde UI cada 5 segundos. El resumen `JORNADAS HOY` usa el mismo conjunto de distancias evaluadas por el hilo, evitando contradicciones entre la tarjeta y el resumen del día.

## Reconstrucción
### Viajes individuales cerrados
- El historial de viajes se basa en `client_trips.csv` y `client_trips_2.csv`; no se borran ni reescriben.
- `T1HistoricalDistanceRepair` recorre la cadena de sesiones OEM `trip_...csv` dentro de cada ventana real `inicio..fin` y calcula la distancia de la velocidad OEM validada y los milisegundos entre muestras; filtra muestras demasiado separadas.
- Si el número independiente de callbacks OEM 500m del viaje coincide con el resultado de velocidad (tolerancia 12%), el estado es `CORREGIDO` y se muestra como **VERIFICADO OEM**.
- Una diferencia importante que no cuenta con suficientes callbacks, pero tiene **al menos 92% de cobertura temporal con 30 intervalos válidos** y muestra el antiguo GPS como fuente dominante, se presenta como `CORREGIDO_ESTIMADO`; en pantalla y consumo se etiqueta **ESTIMADO, NO VERIFICADO**. Así no se afirma certeza inexistente. La cobertura alta tampoco permite detectar todas las pausas antiguas: el usuario debe revisar esos casos antes de tomar los litros/100 como definitivos.
- Si las muestras son escasas, el contador de 500m contradice la velocidad o el tramo ya era correcto, se conserva el km original con estado `REVISAR` o `SIN CAMBIO`; **no se crean kilómetros** faltantes.
- El último Viaje 2 sigue expresamente protegido de reconstrucción (el usuario comprobó que fue medido después de la corrección OEM).
- Los cambios aprobados se guardan únicamente en un **sidecar privado con respaldo y reemplazo atómico**: `client_distance_corrections_v1.csv`. CSV principales y eventos OEM son inmutables.

### Jornadas completas
- `T1MasterTripHistory` lee cada TRIP cerrado desde almacenamiento interno y reconstruye los kilómetros por velocidad OEM calibrada provisionalmente con `22.9/24.55`.
- Para mostrar el valor como **corregido**, la distancia estimada debe coincidir con eventos OEM 500m independientes (máximo 8% de divergencia o margen mínimo 1,5 km). Si no hay testigo suficiente, el original se preserva como no verificado.
- Caso histórico estudiado en Supabase: Viaje Total **136,50 km original**, aproximadamente **164,87 km reconstruido**, y ~164 km respaldados por 328 eventos OEM de 500m. Ese resultado aparecerá automáticamente si el CSV local está disponible en el vehículo y los eventos son consistentes; no depende de sincronizar manualmente el servidor.
- La lectura de litros en Jornada **NO** toma la columna `gas_l_proxy`, porque multiplicaba el km erróneo por consumo móvil. Solo intenta estimar litros experimentales desde callbacks 500m `trip_oem_...csv`, con cantidad de eventos compatible; sin esa información dice `SIN DATO`. El costo usa la tarifa de combustible **actual** y es orientativo, no un precio histórico certificado. La electricidad HV gross no equivale a electricidad comprada en red.
- El sistema es histórico/estadístico; no modifica el control EV/HEV del vehículo.

## Pruebas
- `T1HistoricalMasterRebuildTest` JVM: reconstrucción y confirmación de jornada mediante testigos OEM, no doble corrección de una jornada nueva, conservación ante falta de testigo, compatibilidad de CSV antiguos, aplicación auditada en Viaje 1, etiqueta estimada si no hay testigo, protección de Viaje 2 más reciente.
- `T1RoadSpeedClassifierTest`, `T1OptimizedAutoGateTest`, `T1TripDistancePolicyTest` y tests existentes.
- Receiver 0.2.59/code 60 con certificado idéntico y Sender 0.2.16 intacto.
- Además se incluye el ajuste `requestLocationUpdates(...,0f)`: **Modo optimizado AUTO** puede confirmar tres nuevas posiciones GPS estando el vehículo estacionado en P sin exigir movimiento físico de tres metros. Sigue requiriendo ubicación precisa reciente, carretera estable, SOC/marcha/velocidad OEM válidos y los demás bloqueos previos.

## Restricciones de validez
La compilación prueba software, no valida la Jetour física. El propietario deberá abrir HISTORIAL, verificar primero la columna JORNADAS y los valores originales/corregidos, comprobar el consumo y reportar los casos `REVISAR`. Si los CSV originales de una jornada faltan del almacenamiento local, no se inventan datos aunque haya batches parciales en Supabase.
