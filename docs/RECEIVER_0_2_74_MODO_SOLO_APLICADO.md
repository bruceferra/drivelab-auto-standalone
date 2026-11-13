# Receiver 0.2.74 (code75) — el indicador superior solo muestra el perfil realmente aplicado

## Evidencia física del usuario
Con la aplicación 0.2.73, el GPS detectó CIUDAD y la barra mostró MODO CIUDAD en verde ANTES de completar las tres verificaciones. El perfil CIUDAD estaba condicionado a batería de al menos 15%, pero el vehículo tenía 13%; la app no lo había aplicado realmente.

## Causa concreta
T1GlobalToolbar.refreshRoadMode mostraba T1OptimizedModeStore.lastRoad (clasificación GPS) en verde por el simple hecho de que el optimizador estuviese ON. El mapa identificaba una calle, pero eso no es una confirmación del perfil aplicado al vehículo. El fallback HEV al incumplir SOC tampoco equivale al perfil CIUDAD.

## Corrección
- Nueva fuente **verifiedAppliedRoad**, separada de las lecturas OSM. Solo se publica cuando la misma vía lleva tres coincidencias GPS estables, las condiciones SOC de su perfil están satisfechas, el OEM 1043 está leído con el modo solicitado y la estrategia SOC/regeneración 1040/1046 (+1041 cuando corresponde) se ha confirmado.
- Antes de eso, el botón superior es GRIS, con mensajes de solo lectura: MODO · VERIFICANDO, SOC NO CUMPLE, SOC/REGEN PENDIENTE, VÍA SIN IDENTIFICAR. **No se muestra MODO CIUDAD, CARRETERA o AUTOPISTA en verde por una clasificación GPS aislada**.
- Batería 13%, perfil CIUDAD con mínimo 15% → **SOC NO CUMPLE, gris**. Aunque el controlador aplique HEV de respaldo, no lo presenta como modo CIUDAD. Los botones EV / HEV / EV MAX siguen reflejando la lectura OEM de 1043, independientemente del perfil optimizado.
- El sello de perfil aplicado se invalida en cambio de vía, incumplimiento de condición, ausencia de telemetría fiable, escritura no confirmada, desactivación del optimizador, cambios en configuración y reinicio del servicio. Si pasan 25 segundos sin refrescar confirmación válida, la selección verde expira automáticamente.
- Las 3 muestras de GPS se exigen en una transición real; no se revalida a ciegas un perfil ya confirmado en cada ciclo estable. El GPS sigue leyendo la vía para detectar nuevos cambios.
- Se conservan las escrituras automáticas OEM 1043 y 1040/1041/1046, el diagnóstico por etapas, el bloqueo de valores SOC fuera de 20..70 por decenas y la política de no reintentar un 1041 no confirmado.
- Waze, espejos, AVAS, ventilación, viajes y Sender 0.2.16 sin cambios.

## Prueba
Caso de regresión JVM: CIUDAD a 13/15, 1/3, 2/3, OEM pendiente, SOC/regeneración pendiente, TODO confirmado, GPS UNKNOWN, rango SOC máximo. Compilación Android CI, build firmado code75 con certificado 0.2.73, manifiesto OTA y Sender conservado. **Validar visualmente en la T1.**

## Selector independiente ACTIVADO/DESACTIVADO por bloque (13/11/2026)
- Los cuatro bloques CIUDAD, CARRETERA, AUTOPISTA y DESCONOCIDO tienen un selector individual junto al título.
- Se guarda inmediatamente en SharedPreferences por clave `road_<ROAD>_enabled`, sin necesidad de tocar GUARDAR LOS 4 PERFILES. Se respetan selecciones existentes: todos empiezan ACTIVADO por defecto tras la actualización hasta que el usuario cambie uno.
- Cuando el bloque detectado está DESACTIVADO, el servicio conserva lecturas GPS de solo observación pero omite de inmediato la decisión y **ninguna orden OEM sale de ese bloque**, ni 1043, ni HEV fallback 1043=2 por condición SOC, ni 1040/1041/1046. Otras vías activas siguen funcionando.
- Se borran los votos pendientes y el latch de SOC/regeneración al detectar un bloque apagado. El encabezado no muestra modo verde: indica PERFIL DESACTIVADO en gris. Rehabilitar un bloque exige una nueva comprobación de 3 lecturas y confirmación OEM, sin asumir valores antiguos.
- Los predicados de escritura en segundo plano también vuelven a comprobar que el bloque continúe activo antes de mandar una orden. Si se apaga durante una operación, se detienen las órdenes pendientes en cuanto el controlador lo observa, aunque no se puede deshacer una orden ya enviada.
- No se detiene OPTIMIZADO ON global ni se modifican las preferencias de los otros bloques.
- El bloqueo de UNKNOWN es explícito aunque el controlador existente ya evitaba escrituras para vías sin mapa fiable.

## Combustible que queda hasta corte térmico (CI186)
La lectura principal de ambos tableros Receiver, T1InstrumentView y T1PremiumInstrumentView, muestra **NAFTA UTILIZABLE = max(0, CI186 bruto − 5,30 L)**, con dos decimales. Ejemplo: CI186 30,10 L → 24,80 L hasta el corte observado. Con CI186 menor o igual a 5,30 L marca 0,00 L utilizables. No se descuenta una segunda vez en autonomía: T1ExperimentalRange sigue recibiendo el CI186 original y descuenta allí internamente 5,3 L.

Se conserva CI186 bruto como dato técnico secundario en T1TankDashboardActivity y en el detalle de autonomía de Cockpit. CSV/telemetría no cambian: los valores originales permanecen intactos. Referencia de tanque lleno utilizable **67,00 L**, redondeada desde llenados 66,865 y 66,230 L de surtidor. IMPORTANTE: CI186 es un indicador OEM, no un aforo físico; el flotante se satura en la zona alta de alrededor de 60,5 L OEM, por lo que la resta simple no es una calibración física precisa de todo el tanque. La interfaz identifica esa limitación. No inventar 67 L presentes a partir de sensor saturado.

Prueba JVM T1FuelDisplayTest 30.10→24.80, valores <=5.3→0, sin lectura, raw preservado y 67 L referencia. CI verifica ambos componentes de instrumentos y detalles de autonomía.
