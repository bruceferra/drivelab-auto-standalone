# Receiver 0.2.42 — modo EV / HEV / EV MAX + asesor de ciudad/autopista con OSM

Fecha 12/11/2026. Jetour T1 Argentina. Sender se mantiene 0.2.16. Versión Receiver 0.2.42, versionCode 43. Reemplaza visualmente el cockpit 0.2.41 y mantiene todos los viajes, controles, lectura HV, motor, luces, cinturones y pantalla completa.

## Evidencia física aportada por el propietario

El usuario confirmó en su T1 que **el botón experimental de DriveLab sí cambió EV → HEV** (lectura 1043=2 y cambio visible en pantalla original) con el vehículo detenido. Esta es la primera escritura de modo de propulsión **comprobada en la unidad concreta**. El usuario también afirma que la interfaz original del vehículo permite seleccionar EV y HEV mientras se circula.

**Limitación técnica de seguridad:** el hecho de que el selector oficial funcione circulando **no valida** la ruta Binder `VehicleService` transacción 15, código 1043, desde un proceso Android tercero en marcha. Por ello **esta versión NO quita las comprobaciones de P y velocidad exactamente cero** para escrituras de DriveLab. No incluye cambios automáticos mientras se conduce. No modifica las protecciones de la ECU. El cambio oficial desde Jetour sigue disponible durante la marcha.

## Nueva primera pantalla del cockpit

* Cabecera compacta: título `DRIVELAB / T1`, versión `0.2.42`, botones `PANTALLA` y `VOLVER`.
* `PANTALLA` agrupa opciones antes grandes: mostrar/ocultar barras Android y ajustar la franja superior `Hola Jetour`. Ambas funciones siguen disponibles.
* **Tres botones grandes de una fila** encima del velocímetro: **EV** / **HEV** / **EV MAX**. El modo real confirmado de `1043` se destaca en turquesa; los otros quedan azules. No se cambia el aspecto visual antes de obtener confirmación OEM, y al perder telemetría el indicador lo advierte.
* Un resumen pequeño indica el modo real, las precondiciones y qué opciones siguen en fase experimental. No reemplaza el cluster original ni el selector oficial.

## Comandos manuales experimentales

Clase `T1ManualThreeModePilot.java`: un único intento por pulsación, con diálogo previo y doble lectura directa del Binder OEM para `1033` (P), `2148` (velocidad 0), `1043` (modo actual). Exige Activity visible, Binder vivo, la misma lectura de origen y `1033=1`, velocidad OEM entre 0 y 0.001 km/h. No escribe si ya está en destino. Una transacción `15` única con `1043=1` EV, `1043=2` HEV o `1043=3` EV MAX. Poll posterior por 1043 y reporte local `T1OemDiagnosticLog`; si no confirma, NO se reintenta automáticamente.

* **HEV (1043=2)**: verificado manualmente EV→HEV, estacionado.
* **EV (1043=1)** y **EV MAX (1043=3)**: valores observados en lecturas OEM, pero **escrituras no verificadas**. Solo ensayo manual estacionado y confirmado; verificar en pantalla de Jetour.
* La función antigua de ensayo HEV (en panel diagnóstico) permanece para compatibilidad, sin relajar ninguna de sus protecciones.
* Es importante no tocar controles de la pantalla durante la conducción. Los botones no quedan habilitados para escrituras en D/N/movimiento.

## OpenStreetMap / Overpass — asesor automático de solo lectura

La sección expandida `ZONAS GPS · EV/HEV` incorpora:
* Botón de consentimiento **OSM · ACTIVAR ASESOR**. De forma predeterminada está **DESACTIVADO**. La consulta de categoría de vía necesita internet y envía coordenadas aproximadas (6 decimales) a un servidor público de Overpass, `https://overpass-api.de/api/interpreter`. No usa Supabase ni publica historiales.
* Con opt-in, mientras el cockpit está abierto consulta como máximo ~1 vez/95 segundos. Consulta manual `CONSULTAR VÍA OSM · NO ESCRIBE` disponible independientemente. No funciona en segundo plano con cockpit cerrado.
* Exige ubicación GPS con precisión de **20 m o mejor** y antigüedad de **25 s o menos**, distancia a geometría OSM del segmento seleccionado menor a `min(35, 1.5·accuracy+7)`, y descarta resultados ambiguos (vías paralelas de clases distintas a 8 m).
* Clasificación conservadora: `highway=motorway` o `motorway_link` → **AUTOPISTA**. `residential`, `living_street`, `service`, `tertiary`, `unclassified` → **CIUDAD/CALLE**. `trunk`, `primary`, `secondary`, caminos desconocidos u otras clases → **SIN CLASIFICACIÓN** (no hay carretera de alta capacidad garantizada solo por etiqueta de jerarquía).
* Reglas visuales: CIUDAD → **EV**. AUTOPISTA → **HEV SMART** predeterminado o **HEV FORZADO** si se elige la opción `AUTOPISTA · HEV FORZADO`. Si el GPS falla, no hubo carretera claramente próxima o Overpass no responde, muestra **SIN CAMBIO SUGERIDO**.
* **SOLO SUGERENCIA:** no invoca escrituras 1043, 1040, 1041, no acciona cambios de modo cuando el usuario empieza a circular, y nunca modifica SOC SMART/FORZADO de forma automática. El código de escritura del SOC forzado no fue verificado. Una ruta OSM no es una autoridad de control para propulsión.
* El usuario puede cambiar entre `HEV SMART` y `HEV FORZADO` como configuración local **de la recomendación**, no como comando.

### Ejemplos visuales (no órdenes)

| Segmento OSM | Recomendación |
| --- | --- |
| `highway=residential` | EV |
| `highway=motorway` | HEV Smart |
| `highway=motorway`, opción forzado | HEV Forzado |
| `highway=primary`, GPS inexacto, vías paralelas o red fallida | Sin sugerencia |

Fuente de clasificación OSM: https://wiki.openstreetmap.org/wiki/Key:highway — **motorway** tiene características legales/funcionales propias; **trunk/primary** no equivalen automáticamente a autopista.

## Pruebas y próximos pasos

* Software: verificar aspecto compacto, indicador resaltado por 1043, bloqueos en D/movimiento/sin muestra, no op si modo actual igual al destino, doble lectura y un solo setter para un cambio estacionado, informe legible. OSM tiene que devolver estado desconocido con GPS antiguo o inexacto, servidor sin respuesta y vías paralelas.
* Físico, estacionado P: probar primero lectura y estado de los tres botones; HEV ya está validado. Si se decide hacer ensayos de EV y EV MAX, hacerlo una sola vez por modo y corroborar cuadro OEM; no repetir a ciegas.
* Las transiciones automáticas por OSM requieren una arquitectura distinta con control OEM validado para uso circulando, mapa de carriles/rumbo, confirmación de entrada/salida y cancelación manual. **No habilitarlas con la transacción Binder experimental sin conocer su contrato de seguridad.**
* No altera AVAS, cinturones, pedales, frenos, selector de marcha ni alarmas y conserva los registros de viajes.

