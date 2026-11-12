# Receiver v0.2.56 code57 — corregido el conteo de kilómetros de los tres viajes

## Reproducción del problema en campo
El 12/11/2026 el propietario informó que **Viaje 2 marcaba 8 km cuando el coche había recorrido unos 15 km**, y que **Viaje 1 también sumaba mal**. La revisión de muestras existentes en Supabase del viaje `20261112_111217` confirmó que durante varios tramos de 10 minutos los kilómetros GPS eran aproximadamente la mitad de la integración de velocidad OEM calibrada (ej. 3,16 km frente a 6,40; 4,66 frente a 8,78), lo que es consistente con la pérdida de fixes GPS y los filtros de precisión. Este análisis no demuestra una calibración final: es evidencia del error de fuente.

## Causa raíz
`T1TripRecorderService.attachGps()` activaba `gpsMode=true` al existir permiso GPS. Esto detenía por completo el contador de distancia por velocidad OEM (`if(!gpsMode)...`) y cada callback de ubicación escribía `distanceKm=gpsKm`. Cuando el GPS filtraba puntos, el viaje quedaba corto. **Los tres espacios de viaje utilizan el mismo DISTANCE de la jornada**, por lo que los tres heredaban el error aunque sus botones fueran independientes.

## Reparación
- `T1TripDistancePolicy` es una política Java pura y testeada. Si la velocidad OEM es válida [0,220] km/h, calcula **velocidad * tiempo * factor provisional 22,9/24,55**, incluso con permiso GPS. Mantiene velocidad cruda inalterada en CSV para recalibrar posteriormente.
- Si la velocidad OEM está ausente, admite **un incremento de GPS validado** (fix reciente, umbral anti-deriva de al menos 8 metros, velocidad de desplazamiento plausible y ventana de tiempo acotada). Nunca suma OEM y GPS en el mismo intervalo. Un valor de velocidad OEM 0 evita que el GPS incremente los km estando detenido. Intervalos de servicio superiores a 10 segundos no generan distancia ficticia.
- La recepción GPS continúa para coordenadas y clasificación OSM, pero **ya no puede modificar ni reiniciar** `distanceKm` asíncronamente. Los incrementos GPS disponibles para respaldo se consumen en el loop de muestreo, incluso si ese loop eligió OEM, para evitar que se reusen más tarde en otra ventana.
- **Viaje Total, Viaje 1 y Viaje 2** reciben exactamente los mismos deltas de distancia de la jornada; sus inicios/pausas/finales siguen siendo independientes. El desglose en kilómetros con RPM1035=0/1/desconocido se basa en esos mismos incrementos.
- `DIST_SOURCE` y la columna `notes` indican por muestra `OEM_SPEED_CAL_0.93279_PRELIMINAR`, `OEM_SPEED_ZERO`, `GPS_FALLBACK_OEM_MISSING`, `TIME_GAP_NO_DISTANCE` o `NO_VALID_SPEED_OR_GPS`, para auditar el cálculo posterior.
- Regresión de JVM `T1TripDistancePolicyTest`: un recorrido de unos 15 km con GPS que informa la mitad sigue acumulando cerca de 15 km; ambos viajes 1 y 2 coinciden; detenerse con GPS ruidoso no suma; el GPS solo se usa si falta OEM; intervalos enormes y GPS inválidos no suman.

## Tratamiento de viajes anteriores / activos
- La actualización conserva la distancia y el CSV anteriores, sin alterar sus filas ni sus checkpoints activos. **La distancia histórica subestimada no se corrige de manera retrospectiva** automáticamente, porque podría mezclar tramos pausados o cerrar viajes con valores artificiales.
- A partir de la actualización instalada, los nuevos incrementos adoptan la fuente OEM; recomendamos cerrar los viajes de prueba y comenzar un viaje nuevo para validar contra el odómetro físico.
- Los datos crudos de velocidad y timestamps que están en CSV y en Supabase permiten recalcular los viajes antiguos por separado, con un algoritmo de reconstrucción por intervalos y revisión de pausas/segmentos; no mezclar esos kilómetros revisados con las filas originales sin etiquetar procedencia.
- No se cambia la calibración provisional 0,93279: necesita ensayos adicionales frente al odómetro Jetour antes de declararla definitiva.

## Compatibilidad
Receiver **v0.2.56 code57**, misma firma, actualización OTA sobre 0.2.55; Sender **0.2.16 code17** se conserva sin cambios. Mantiene mapas offline, cuatro perfiles de Modo Optimizado, avisos grandes, seis bloques, histórico/simulador experimental y las nuevas señales OEM. Los resultados en la Jetour deben confirmarse con una prueba física después de instalar.
