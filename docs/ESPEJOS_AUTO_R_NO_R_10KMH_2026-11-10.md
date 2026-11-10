# Espejos T1: propuesta de auto-reversa por pasos y retorno >10 km/h (10/11/2026)

**Estado:** política de decisión implementada como Java puro en `MirrorParkingPolicy` y cubierta con pruebas; **NO conectada aún a escrituras OEM ni monitor en segundo plano**. El Receiver 0.2.17 sigue permitiendo pulsos **manuales** en P y 0 km/h. No anunciar auto-reversa operativa.

## Reglas solicitadas por el propietario

El conductor configura manualmente la posición normal de espejos (equivalente a «D») usando mando original. No necesitamos saber el ángulo físico absoluto. El número **10** es solo ejemplo modificable; no existe medición validada de la distancia de 10 pulsos.

- **Primera transición conocida hacia R** (`VehicleLink.getInt(1033)` R = 2 **candidato de APK; pendiente confirmación física en unidad argentina**), vehículo casi quieto: ordenar **una única bajada** de N pasos en cada espejo, cada uno como `comando BAJAR → liberar 0`. Se registra cuántas órdenes completó cada lado, no se asume desplazamiento geométrico de cada orden.
- **R ↔ otras marchas durante estacionamiento:** no repetir bajada ni intentar subir solo porque salió de R; la posición de estacionamiento permanece mientras la velocidad sea ≤10 km/h.
- **Retorno a pose normal:** cuando marcha sea **conocida y distinta de R** (D o N, y P si hubiera velocidad validada) y velocidad OEM sea **estrictamente >10 km/h durante 800 ms**, iniciar **una sola secuencia de subida** de los pasos registrados. Si vuelve a R o cae a ≤10 km/h antes de completar los 800 ms, reiniciar espera.
- **Marcha desconocida, velocidad inaccesible, app reiniciada sin referencia, liberación no confirmada, comandos interrumpidos, o cambios manuales de posición:** detener secuencia/no recuperar automáticamente; conservar estado de error para evitar marcha en falso.
- **Al volver a NORMAL tras recuperación completa:** rearmar para próxima transición a R. Si se recibe R al arrancar sin evento previo conocido, no ejecutar movimiento súbito.

**Corrección de interpretación (10/11):** en punto muerto N **puede haber velocidad** si el auto rueda. Por eso se exige **marcha no-R + velocidad >10 real**, sin presuponer que N es velocidad 0. No necesitamos saber qué valor numérico de `1033` representa D, pero sí identificar inequívocamente R y no confundir errores/desconocido con no-R.

## Confirmado / por validar

**CONFIRMADO en vehículo por propietario:** controles del espejo derecho **2069** y del izquierdo **2068** mueven en ambos sentidos con un toque. Para derecho, setter `1` sube y `2` baja, liberación `0`. **Izquierdo:** ambos sentidos físicos validados; asignación específica `1`/ `2` para arriba/abajo aún debe anotarse separadamente. Los comandos de 500, 1000 y 2000 ms provocan desplazamiento equivalente, por lo cual no se puede calibrar desde milisegundos de duración. Segundo toque no siempre responde de inmediato; la app tenía cooldown artificial 1800ms, retirado en código 0.2.17 para intentar siguiente movimiento **al terminar el previo**.

**CONFIRMADO en campo:** marcha P detectada y velocidad 0 cuando parado. **SIN validar:** leer `1033` en R y en otro estado mientras se mantiene el vehículo frenado, velocidad 2148/1089 realmente km/h, lectura fiablemente continua con teléfono y Receiver en background, demora/rechazo OEM entre toques y tiempo de retorno tras N pasos en cada lado. Los getters RAW 2068/2069 devuelven 0 en posición intermedia con mandos liberados; todavía no es sensor de ángulo confirmado.

## Implementación técnica ahora

- `MirrorParkingPolicy` usa `Gear.R` versus `Gear.P/N/D`, `Gear.UNKNOWN` nunca habilita retorno. Espera 800ms continuos estrictamente >10 km/h. Desarma al perder datos, y solo enciende acción tras una transición fresca a R.
- Una FSM almacena los pasos emitidos por lado, y se bloquea en `UNKNOWN_POSITION` ante secuencias interrumpidas. `MirrorParkingPolicyTest` cubre maniobra R/D a baja velocidad, retorno con D y con N, ruido de velocidad, datos desconocidos, interrupciones y reentrada.
- Este módulo es **una política aún sin actuadores**; el `MirrorPulsePilot` manual aún exige P y velocidad casi cero. Para volver operativa la automatización se necesita un controlador **separado** que pueda leer marcha y velocidad con continuidad, revalidar condiciones entre todos los movimientos, ejecutar una secuencia acotada y liberar a cero incluso ante errores. No usar el `ReceiverService` de mapas/puente como medio de prueba.
- Por seguridad, no iniciar una ráfaga predeterminada de 10 acciones en ambos espejos durante circulación sin validar con el propietario el efecto y duración; permitir configuración de N pasos **por lado**, con máximo y modo inactivo por defecto.

## Limpieza de UX de Receiver 0.2.17

- **Se retiran botones engañosos «Guardar referencia D/R»**: solamente guardaban texto de consulta HAL y no una posición recuperable.
- **Se retiran «Plegar» y «Desplegar»** en UI: Android Automotive exige permiso OEM `CONTROL_CAR_MIRRORS` que el Receiver no posee; no forzar ni adivinar vendor codes.
- **Se quita cooldown artificial de 1.800 ms** entre toques de espejo; se conserva un comando por vez, verificación P/0 y liberación 0.
- **Alarma peatón 2074:** toggle de un toque sin cartel, pero únicamente con marcha P y velocidad cero confirmadas por OEM. No se desactiva automáticamente al encender/circular.
- **Ventanas total ventilación/cierre:** confirmadas funcionando; no tocar los controles existentes.
- No modificar Sender, Waze/mapas, puente de navegación, viajes, datos del tablero ni cálculo nuevo de combustible/surtidor.

## Siguiente ensayo útil y corto

Con vehículo inmóvil y freno, sin mover espejos, pulsar **LEER MARCHA Y VELOCIDAD** en P, R y otro estado (D o N) para anotar códigos crudos OEM. El control manual de espejos continúa limitado a P; **no probar órdenes de espejo en R/D**. Asegurar el vehículo y no consultar pantalla mientras circula. Luego probar cada lado y contar cuántos pulsos realmente necesita la inclinación deseada desde la posición normal fijada por el conductor.
