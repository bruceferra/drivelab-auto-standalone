# Receiver 0.2.52 code 53 — Modo optimizado (cuatro perfiles)

## Contexto y alcance
El usuario pidió un modo configurable por tipo de camino con combinaciones de EV/HEV/EV MAX, SOC Smart/Forzado y objetivo, umbral porcentual independiente de batería y regeneración. La clasificación offline pasó de dos categorías a **cuatro**: CIUDAD, CARRETERA, AUTOPISTA y DESCONOCIDO. Expresamente pidió **poder configurar qué recomendar cuando la vía sea DESCONOCIDA**, en lugar de tratar ese caso como error no configurable.

Este build implementa **el editor de perfiles, persistencia local, clasificador offline y la evaluación de reglas de solo lectura**. **NO habilita cambios automáticos** de propulsión, ajuste SOC ni regeneración: aunque los botones manuales EV/HEV/EV MAX pueden invocar comprobaciones OEM, no hay evidencia física suficiente para justificar transferir esos comandos a GPS en movimiento. Un rechazo del vehículo no garantiza ausencia de efectos parciales. Todavía falta localizar/probar los setters de estrategia, objetivo SOC y recuperación.

## Editor del Cockpit
- Botón **MODO OPTIMIZADO** en la cabecera del Cockpit, accesible también desde ZONAS / DIAGNÓSTICOS.
- Editor `T1OptimizedModeActivity` con cuatro filas independientes:
  - **Ciudad:** residencial, living_street, service, tertiary, unclassified.
  - **Carretera:** trunk/trunk_link, primary/primary_link, secondary/secondary_link. No se asume que trunk sea motorway.
  - **Autopista:** motorway y motorway_link.
  - **Desconocido:** camino no mapeado, vía demasiado alejada, GPS impreciso, falta de mapa o vías paralelas ambiguas. Tiene exactamente las mismas opciones que los otros tres perfiles.
- Cada fila guarda en SharedPreferences `t1_optimized_mode_profiles_v1`:
  1. Propulsión: **EV / HEV / EV MAX**.
  2. Gestión de SOC: **SMART / SOC FORZADO**, con objetivo **0–100 %** si Forzado.
  3. Condición opcional **exigir batería > X %** (0–100), separada del objetivo de SOC. Si no hay SOC OEM válido o no supera X, recomendación informativa **HEV SMART (respaldo)**.
  4. Preferencia de regeneración **BAJA / MEDIA / ALTA**.
- Validación numérica en el editor: no admite valores negativos ni superiores a 100 %. Los porcentajes se aplican a la regla seleccionada en la evaluación, nunca a controles OEM de forma automática.
- Valor inicial del perfil desconocido: **HEV SMART**, pero editable libremente. La preferencia de "desconocido" no sobreescribe ningún otro perfil.
- El botón **GUARDAR LOS 4 PERFILES** confirma persistencia local y previsualización de la recomendación. No transmite preferencias/GPS a nube.

## Clasificador actualizado
- `T1OsmRoadAdvisor.Road`: nuevo enum `ROAD` para carretera. `T1OsmRoadAdvisor.classify()` ahora devuelve ROAD para etiquetas trunk/primary/secondary y sus enlaces, sin confundirlas con motorway.
- `T1OfflineRoadMap.roadAt()` usa la misma clasificación en base SQLite PBA+CABA ya descargada, **sin Overpass**. Las calles sin clasificación se mantienen UNKNOWN y pasan al perfil Desconocido.
- El Cockpit consulta la categoría offline cada **~10 segundos** cuando está abierto y asesor activado; continúa requiriendo GPS reciente y precisión válida. Sin ubicación reciente, muestra el motivo y evalúa el perfil Desconocido como recomendación.
- Persistencia efímera de clasificación en `T1OptimizedModeStore` solo durante **45 segundos**; nunca reutiliza una autopista vieja luego de dejar de usar el Cockpit. No graba coordenadas.
- El banner superior muestra `OSM OFFLINE · TIPO VÍA · perfil previsto`; el panel avanzado explica razón y confirma explícitamente **SOLO SUGERENCIA**, sin acción del vehículo.

## Regla y seguridad
- `T1OptimizedModePolicy.evaluate()` es Java puro sin imports Android ni código Binder, y sin ninguna operación de escritura. Se prueba como módulo JVM.
- El requisito de SOC es **estrictamente mayor que X**, como solicitó el propietario. No permite pasar la condición con una muestra NaN/inválida.
- El perfil Desconocido es configurado y utilizado para la **sugerencia** incluso si no hay GPS o clasificación, pero nunca se traduce en una orden automotora sin autorización adicional y pruebas OEM.
- Las preferencias de regeneración no han sido asociadas a códigos OEM ni simuladas como activas. Se muestra selección objetivo, no confirmación de aplicación.
- No cambia el modo EV/HEV existente, el guardado de viajes, la batería, el cálculo de combustible ni las seis tarjetas inferiores de Receiver 0.2.51.

## Compatibilidad y validación pendiente
Receiver **0.2.52 / code53**, firma original y OTA. Sender **0.2.16 / code17** sin cambios. Compilación y tests JVM son verificables en GitHub Actions; exactitud física del mapa y selección OEM requiere aún ensayo en Jetour.

### Pendiente para automatización futura
Antes de considerar escritura automotora desde GPS: demostrar transiciones seguras en marcha y en P; identificar los setters OEM de SOC Smart/Forzado, objetivo SOC y frenado regenerativo; verificar lectura posterior; confirmar que el rechazo no deja valores parcialmente cambiados; diseñar estabilidad GPS/histéresis y evitar conmutaciones frecuentes; y plan para restaurar **HEV SMART** tras fallos comprobados sin fabricar escrituras no verificadas. Hasta entonces, el control automático no se activa.
