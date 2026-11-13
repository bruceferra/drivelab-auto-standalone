# Receiver 0.2.68 — Diagnóstico global de hardware y rescate de controles del Cockpit

**Problema observado físicamente en Jetour T1:** después de la actualización 0.2.65 se perdió la capacidad de accionar TODOS los botones de la fila superior del Cockpit. La 0.2.67 no lo solucionó. Los botones de pausa de viaje y EV/HEV/EV MAX sí reciben toques. Esa asimetría es consistente con una franja superior cubierta por otra ventana OEM o con una capa que roba eventos. No es prueba de saturación.

## Comparación reproducible de versiones

En la comparación `v0.2.64...v0.2.65` se añaden el análisis OSM histórico ECO, su TextView `setTextIsSelectable(true)`, callbacks de consumo dinámico y otros textos, **pero NO se modifica la ubicación de los botones originales ni el módulo `T1ImmersiveActivity`**. En 0.2.67 se fijó la fila superior pero el error siguió presente. Por eso se instrumenta la llegada de toques antes de seguir cambiando lógica.

## Cambios UI para recuperación

- Retirada **total** de la vista temporal `roadTransitionPopup` del `FrameLayout` (no solo disabled/hidden); el estado de la vía sigue en el banner OSM y optimizador. Evita superposiciones de alto Z-order.
- Franja de hardware `T1SystemLoadMonitor` de 29dp al principio del Cockpit, con 16dp de separación, NO intercepta toques.
- Reordenamiento del encabezado: primero monitor, luego modos EV/HEV/EV MAX, **después navegación**. La fila NAV se desplaza por debajo del área donde los botones OEM de modo sí funcionaban.
- Accesos duplicados de **rescate** inmediatamente después de INICIAR/PAUSAR/TERMINAR: OPTIMIZADO, PENDIENTES, PANTALLA, SISTEMA RAM/CPU e INICIO, todos independientes de la franja NAV. No hacen cambios OEM automáticos ni duplican escrituras; solo reutilizan los listeners existentes.
- El resto de funciones de viajes/historial/OSM/Eco no se modifica.

## Telemetría real del sistema completo (sin root)

- `ActivityManager.MemoryInfo.totalMem / availMem` mide la **RAM GLOBAL Android** (usada = total - disponible). Incluye Android, launcher Jetour, servicios OEM y otras apps, no solo DriveLab. `lowMemory` y `threshold` ayudan a detectar presión del sistema.
- `Debug.getPss()` mide RAM PSS **del proceso DriveLab únicamente**, para distinguir cuánto consume del total.
- `/proc/stat`: CPU GLOBAL (%) se estima por delta de ticks ocupados y ociosos. Si la ROM Android 11/SELinux restringe lectura, **CPU TOTAL = N/D**, nunca se presenta el CPU de la app como global.
- `Process.getElapsedCpuTime()` y `Runtime.availableProcessors()`: porcentaje de CPU utilizado por la app normalizado sobre los núcleos que Android informa.
- Identificadores Android `Build.HARDWARE`, `BOARD`, `MODEL`. No se puede identificar físicamente la CPU solo por una etiqueta genérica. La ficha oficial de Jetour T1 internacional menciona Qualcomm Snapdragon 8155; verificar Build/HW de la unidad argentina.
- Latencia del hilo principal: diferencia entre la fecha esperada y real de un callback `Handler.postDelayed(...,3000)`, muestra valor actual y máximo. 1 muestra/3 s; la lectura hardware corre en un hilo de trabajo separado, sin root ni acceso a procesos de terceros.
- `Activity.dispatchTouchEvent(ACTION_DOWN)` cuenta eventos **NAV** y **MODO** recibidos por la Activity; cada listener NAV incrementa además el contador **CLICK**.

## Interpretación de la prueba

1. Estacionar; instalar Receiver 0.2.68 desde el Hub, sin desinstalar para preservar viajes.
2. Abrir COCKPIT. Ver el monitor arriba: `RAM SISTEMA ...`, `CPU TOTAL ...` o `N/D`, `APP ...`, `UI ... ms`, `TOQUES NAV N/C MODO M`.
3. Pulsar EV/HEV y luego PENDIENTES/MODO OPTIMIZADO. **No realizar cambios de modos en marcha para diagnosticar gestos**.
4. Usar `SISTEMA RAM/CPU` en la fila de rescate (junto a los controles de viaje). Se muestra diálogo de hardware/lag/toques y botón COMPARTIR. Es posible enviar informe al servidor conectado por consentimiento explícito.
5. Si NAV no responde y **NAV Down no aumenta**, pero **MODO Down sí aumenta**, los eventos de esa zona no llegaron a la Activity: investigar overlay Jetour/ventana del sistema (no atribuirlo a Java). Si NAV Down aumenta pero CLICKS no, investigar el hit-test interno; si ambos aumentan y la Activity no abre, revisar ActivityManager/logcat de navegación. Si UI lag máximo está constantemente elevado y RAM libre muy baja, investigar saturación.
6. Los botones de rescate permiten INICIO / PANTALLA / PENDIENTES / OPTIMIZADO aun cuando la barra NAV vuelva a fallar. Comprobar que Android OS no tapa también esa franja.
7. No pedir una nueva actualización sin inspeccionar los contadores si la causa sigue sin resolverse.

## Optimizador: porcentajes sin teclado, SOC forzado solo allowlist OEM

- `CONDICIÓN DE BATERÍA`: desplegable táctil de **5% a 100%, de 5 en 5**, sin teclado.
- `OBJETIVO SOC FORZADO`: desplegable **independiente** con los únicos presets admitidos por la política de escritura OEM del código **1041**: **20%, 30%, 40%, 48%, 50%, 56%, 60%, 64%, 70%, 72%**. Se lee exactamente de `T1SocRegenWritePolicy.forcedSocPresets()`, fuente única de la validación `accepted(1041,value)`: la UI no puede ofrecer porcentajes que el escritor rechaza por política.
- Una configuración antigua no compatible se muestra en el preset permitido más cercano; no se cambia ninguna preferencia hasta pulsar **GUARDAR LOS 4 PERFILES**.
- No se convierten los presets irregulares (48%, 56%, 64%, 72%) en múltiplos de 5. No se deben confundir con el umbral libre de batería. La lista sale de la investigación del contrato OEM; la aceptación real por cada firmware sigue dependiendo de confirmación con readback físico.

## Restricciones / observaciones

La ficha oficial global Jetour T1 identifica **Snapdragon 8155**, chip automotriz; no es el Snapdragon usado en Galaxy S23/S25. La central de la T1 argentina podría variar y por eso se muestra `Build.HARDWARE` y `BOARD`. Medición RAM GLOBAL no implica permiso para enumerar memoria exacta de otros procesos; se muestra solo el total del sistema y RAM de DriveLab por separado.

No se altera firmware, HAL, root, launcher OEM, permisos, archivos de viajes ni Sender v0.2.16. El último programa optimizador 1040/1041/1046 sigue experimental hasta test físico y no forma parte de esta corrección.
