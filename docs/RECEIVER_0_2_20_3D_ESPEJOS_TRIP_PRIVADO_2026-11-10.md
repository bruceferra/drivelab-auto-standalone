# DriveLab Auto Receiver 0.2.20 — 3D, espejos y TRIP privado

**Estado:** rama de desarrollo / PR #24. CI Android; SIN publicación OTA firmada ni prueba física de esta versión. Sender queda en 0.2.13.

## UI solicitada
- Todos los botones de inicio, VEHÍCULO, ENERGÍA, CODE y TABLERO/TRIP tienen relieve 3D: en reposo sobresalen; al tocarlos se hunden y cambian sombreado.
- Los modos persistentes ESPEJOS AUTO/MANUAL y SIEMPRE INTERIOR/AIRE AUTO aparecen resaltados/hundidos con borde amarillo; las pestañas VEHÍCULO, ENERGÍA y CODE resaltan la seleccionada.
- Los botones de acción no fingen quedar activados tras tocar. Solo reflejan la pulsación mientras se mantiene el dedo; el resultado funcional se comunica en mensajes de estado.

## Espejos
- En R los espejos bajan físicamente, pero el propietario confirmó que **no suben** en D incluso pasando 20 km/h con v0.2.19: incidencia abierta, no considerarla corregida hasta prueba nueva.
- Se comparan velocidades candidatas OEM 2148 / 1089. Un valor 2148 aparentemente válido pero anclado en 0 ya no anula automáticamente 1089; se muestran ambas fuentes en estado para diagnóstico.
- Nueva acción manual **RECUPERAR ESPEJOS · 10 PASOS ARRIBA**: siempre interrumpe AUTO, solo se ejecuta con velocidad OEM de parada (<=0.5 km/h), emite 10 pulsos 2068 y 10 pulsos 2069 con neutralización 0. Es recuperación **relativa**, no una posición absoluta medida. Antes de circular verificar físicamente el encuadre.
- PLEGAR/DESPLEGAR ya no exige que el conductor elija ESPEJOS MANUAL: la orden desarma AUTO, serializa con otros movimientos y llama al ID 2003. Protecciones de velocidad/marcha OEM permanecen.
- Máximo pasos por lado configurable 20; no se modifica el valor previo ni el valor por defecto de 2.
- No probar el retorno a más de 10 km/h hasta validar la fuente de velocidad y verificar los espejos físicamente. Evitar el modo AUTO si deja los espejos sin volver.

## TRIP, Testigo y sincronización
- Se conserva TRIP CSV existente y se añaden campos **candidatos** RPM OEM 1035 y motor térmico encendido inferido por RPM. Ausencia o rechazo de lectura = desconocido (-1), no inventar actividad térmica. Contrastar con Testigo mediante captura estacionaria.
- Se conserva nafta PROXY histórica (distancia × 7.47 L/100 km), **no** se presenta como combustible efectivamente inyectado. No se reutiliza el cálculo viejo T1Launcher.
- Subida opt-in a Edge Function independiente `drivelab-auto-trip-upload` y tabla privada `drivelab_auto_trip_batches` (RLS activo, sin acceso a anon/authenticated), con PIN de 8 cifras almacenado cifrado en Android Keystore, jamás en repo.
- Envía tandas hasta 40 filas, cada ~60 s durante grabación, y ofrece SUBIR PENDIENTES AHORA. Cursor local avanza solo cuando el servidor confirma; se reintenta cuando vuelve Internet.
- Se sube exclusivamente una lista permitida: timestamp, gear, speed, litros OEM, SOC, volt/corr, RPM candidata, térmico candidato, distancia de TRIP y acumulados proxy. No se envían GPS, coordenadas, VIN ni CSV completo. Los CSV privados locales se conservan. No se altera T1Testigo ni Uber Beta.
- El servidor fue desplegado, pero queda **pendiente prueba de extremo a extremo con PIN y conexión real de la T1**. No afirmar que ya se sincronizan los viajes físicos.

## QA para publicar
1. Confirmar GitHub Actions CI de la rama y versión 0.2.20, code21; verificar firma release anterior antes de OTA.
2. Con coche **detenido**: comprobar botón recuperar, pulsos y sentidos; no asumir posición normal absoluta.
3. Desde fuente OEM, comprobar si 2148/1089 miden velocidad en km/h, si AUTO al salir de R restituye físicamente ambos espejos y qué estados quedan después de fallo.
4. Confirmar que los botones tienen relieve, presión visual y selección persistente sin perder scroll.
5. Sincronización: PIN válido, envío, confirmación de filas en tabla privada, reconexión y deduplicación; comprobar que no se transmiten coordenadas.
6. Mantener Sender y navegación intactos. No publicar si hay duda de seguridad de espejos.
