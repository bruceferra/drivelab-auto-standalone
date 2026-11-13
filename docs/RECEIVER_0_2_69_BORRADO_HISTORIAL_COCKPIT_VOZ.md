# Receiver v0.2.69 (code70) · Historial, Cockpit y voz

## Funciones de historial
- En COCKPIT → DETALLES/HISTORIAL: seleccionar un registro de la columna VIAJES 1/2 o JORNADAS y pulsar **BORRAR SELECCIONADO**; requiere aceptar un diálogo que identifica el registro.
- **ELIMINAR TODO** exige escribir literalmente **ELIMINAR TODO** en el diálogo; una frase distinta no ejecuta el borrado.
- Solo se trabaja sobre archivos **locales propios del Receiver** (`t1_trips`); nunca sobre DriveLab original/Uber Beta, Sender, Google Drive o Supabase. Las copias previamente transmitidas a la nube persisten. No se borran `grid_charges.csv`, mapas offline, perfiles de optimización ni configuración OEM.
- Viaje 1/2: se quita su fila de `client_trips.csv` / `client_trips_2.csv`, su sidecar de km, corrección y resumen QR local propio; conserva íntegra la jornada maestra compartida.
- Jornada: retira sus `trip_...`, `trip_oem_...` y `eco_...` solamente si no está referenciada por viajes individuales. Ante referencias se informa que primero deben eliminarse esos viajes o usar ELIMINAR TODO.
- Eliminación total: los CSV de jornadas, eventos OEM, Eco Score, viajeros y correcciones, así como los informes QR locales identificados como resúmenes de viajes, se retiran; no se eliminan los diagnósticos generales.
- Rechazar la operación si cualquiera de los tres espacios está activo, pausado, en restauración o pendiente. No detener ni cerrar viajes automáticamente para poder borrar.
- El trabajo ocurre en segundo plano; después de éxito se recalculan las listas e indicadores sin bloquear la pantalla táctil. No promete eliminar datos ya sincronizados.

## Cockpit: scroll intacto y una sola botonera
- Las filas EV/HEV/EV MAX y navegación siguen **fijas**; el resto del cockpit conserva scroll debajo.
- Se retira la botonera secundaria de rescate duplicada (OPTIMIZADO/PENDIENTES/PANTALLA/INICIO).
- **SISTEMA RAM/CPU** pasa a la fila principal para no perder el diagnóstico.
- Los textos de modo OEM, SOC, OSM, estado de grabación, sincronización, consumo reciente 500m, Eco Score y hardware se ubican en **dos columnas debajo de las seis tarjetas** (consumo, km eléctricos, combustible, electricidad, costo y autonomía). Se conserva nombre DRIVELAB / T1 en cabecera. No cambia la semántica OEM.
- Se limita visualmente a cuatro renglones el Eco Score para evitar una cascada de texto; su detalle completo se conserva en HISTORIAL.

## Voz Gemini / ChatGPT y Waze
- La preferencia de IA de consultas generales ahora muestra claramente cuál está elegida (✓ GEMINI o ✓ CHATGPT). No cambia el asistente por defecto de Android/Jetour.
- Se suma **PROBAR VOZ DEL SISTEMA** mediante el estándar `ACTION_VOICE_COMMAND`, sin forzar la apertura a pantalla completa de Gemini si el headunit no lo admite.
- Los botones de **PROBAR APPS INSTALADAS** siguen para abrir Gemini o ChatGPT manualmente, diferenciados del router de consultas y del asistente OEM.
- No se afirma que `ACTION_VOICE_COMMAND` inicie Gemini Live ni que exista una API pública para forzar la integración vocal de Android Auto. Requiere prueba física de la ROM.
- El reconocimiento de destinos y lanzamiento de Waze en el clúster se conservan intactos.

## Pruebas
1. Actualizar por OTA sin desinstalar ni perder ninguna jornada; Sender 0.2.16 intacto.
2. Confirmar que las dos filas de controles permanecen fijas al hacer scroll y que ya no hay duplicación de botones.
3. Comprobar acceso único a SISTEMA RAM/CPU; ver consumo reciente **500m OEM experimental** bajo los seis indicadores, incluso sin grabar viaje.
4. Para prueba de borrado, utilizar exclusivamente un registro histórico prescindible, jamás la jornada de consumo del tanque; cancelar diálogo primero, comprobar que sigue presente; después confirmar un registro descartable.
5. ELIMINAR TODO solo después de respaldar explícitamente todo el historial y de cerrar las tres sesiones, pues es irreversible localmente. No ejecutar durante pruebas si se desean conservar datos reales.
6. Probar selección de Gemini y ChatGPT (marca visible) y PROBAR VOZ DEL SISTEMA estacionado. Si abre solo pantalla completa, reportar el resultado: no es equivalente a Gemini en Android Auto.
7. Validar compilación, certificado de firma sin cambios, manifiesto OTA Receiver code70 y Sender code17 sin variaciones.

**Importante:** la existencia de código y compilación en GitHub no comprueba interacción táctil, permiso OEM ni voz en la unidad Jetour T1.