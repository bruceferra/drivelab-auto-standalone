# Inspección del APK T1Launcher T1N134 — espejos y contadores PHEV

**Fecha:** 2026-11-10. **Fuente:** archivo APK original entregado por el propietario en conversación; inspección **estática** de DEX, sin instalar, abrir el vehículo ni enviar comandos. **Estado:** referencias de código verificadas en el archivo; funcionamiento físico de los IDs **PENDIENTE**.

## Integridad y procedencia

- APK: `T1Launcher-T1N134.apk` (21.652.547 bytes), SHA-256: `6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480`.
- Coincide exactamente con la muestra del análisis previo del 04/11 y con la publicación pública `MainthorCar/t1launcher`, release `T1N134`.
- DEX inspeccionados: `classes.dex` y `classes2.dex`. SHA-256 de `classes2.dex`: `3b2b00c393e61461c30963cd9800b674ef633482ac96c6ee9115c196f698cf79`.
- Los nombres y números de abajo se extrajeron de tablas de métodos/strings y las instrucciones de llamada en `classes2.dex`. No representan una API OEM documentada por Jetour, no autorizan escritura desde Receiver y no son una prueba física.
- **No subir el APK ni el código descompilado** a este repositorio público; conservar exclusivamente estos hallazgos de interoperabilidad y el hash de la muestra.

## Hallazgo: dos módulos llamados «Espejo» no son lo mismo

1. `com.t1launcher.espejo.EspejoService` utiliza `MediaProjection`, `VirtualDisplay` y `Surface`: **espejo de pantalla / duplicación visual**, no control de retrovisores.
2. `com.t1launcher.espejor.EspejoRService` implementa **retrovisor al engranar marcha atrás**. La interfaz se encuentra en `com.t1launcher.ui.EspejoRKt` y el método de pulsos también en `EspejoR$escalon`. No confundir las clases.

### IDs OEM identificados desde invocaciones reales

| ID | Canal / método observado en APK | Uso de la versión analizada | Confianza |
|---|---|---|---|
| **1033** | `VehicleLink.getInt(1033)` | Lectura de marcha. En la lógica del APK se compara **2 con reversa** y **1 con P**, inferido de las condiciones y textos de evento. | **CONFIRMADO en bytecode**; mapeo físico en T1 argentina **PENDIENTE**. |
| **2069** | `VehicleLink.setIntData(2069, valor)` | Movimiento de retrovisor por impulsos. Aparecen valores de movimiento **1/2** y retorno a **0** para soltar/detener el mando. Es un **comando de escritura**, no sensor de posición. | **CONFIRMADO en bytecode**; dirección exacta, eje/lado y comportamiento físico **PENDIENTES**. |
| **2148** | `VehicleLink.getFloat(2148)` | Primera opción de lectura de velocidad usada por `EspejoRService.leerVelocidad`. | **CONFIRMADO en bytecode**, unidad/escala física pendiente. |
| **1089** | `VehicleLink.getInt(1089)` | Alternativa de velocidad si falla/no corresponde la primera. | **CONFIRMADO en bytecode**, escala pendiente. |

Las llamadas de escritura siguen la misma familia de `VehicleLink.setIntData` que se investigó para otros mandos, pero **no usar 2069 como si fuera un setpoint absoluto**. No emitir valores de prueba sin confirmar contrato OEM, estado del vehículo y un plan de restitución seguro.

### Por qué NO copiar directamente el modo de retroceso del launcher

- En `EspejoRService` aparecen preferencias `pos_a`, `pos_b` y `pos_actual`, con valor inicial por defecto **−1**. Son **posiciones contadas/guardadas por la app**, no una lectura OEM demostrada del ángulo real del espejo.
- `irA` calcula diferencia de pasos entre posición actual y objetivo. El bytecode limita la cantidad de pasos a **150** mediante `coerceAtMost`, y acciona el ID 2069 en pulsos seguidos de retorno a 0; guarda después una nueva `pos_actual` local. Por lo tanto existe riesgo de **descalibración** si se mueve manualmente el espejo, se interrumpe la ejecución o una orden no se aplica.
- Preferencia `intervalo_ms`: valor por defecto **200 ms**; esto es **tiempo del algoritmo**, no duración de un movimiento físicamente validado para Receiver.
- Preferencia `automatico`: por defecto false (requiere activación). El servicio interpreta reversa para bajar a `pos_b` y restaurar `pos_a` según marcha/P y preferencia `modo_15`; la interfaz incluye mensajes `reversa: bajando`, `salio de reversa: subiendo`, `quedo en P: subiendo`, `paso los 15 km/h: subiendo`.
- En el APK, `onDestroy` intenta enviar `2069 = 0`, pero esto **no demuestra** restitución física de la posición anterior; solo libera la orden activa.

**Decisión para DriveLab Receiver:** NO integrar directamente los pulsos automáticos. Primero implementar descubrimiento/lectura verificada de marcha y velocidad en una sesión detenida, modelar una posición inicial reconocible/restaurable, conservar la memoria OEM, permitir anulación manual y restauración segura. El inicio automático de una función motorizada queda desactivado. No copiar código ni comportamiento potencialmente inseguro; usar implementación independiente tras validar las transacciones con la T1.

## Decisión de interfaz propuesta: pulsación sostenida para espejos (10/11/2026)

**Origen:** observación del propietario en la T1 argentina: los controles manuales OEM permiten **mantener presionado** para desplazar el espejo de forma continua y **soltarlo** en el punto deseado. En T1Launcher T1N134 el módulo de retroceso usa desplazamientos por pulsos/pasos (incluidos grupos de cinco), almacena posiciones relativas de la aplicación y advierte que el ajuste manual posterior descalibra esas memorias.

**Decisión de UX:** en el futuro módulo del Receiver, preferir **botones “mantener para subir/bajar”** (y, si se documentan, izquierda/derecha) por espejo; el usuario ve el movimiento y lo detiene al levantar el dedo. Ofrecer memorias de **posición normal** y **posición de reversa** separadas por espejo únicamente si puede garantizarse una restauración comprobable. La elección manual/automática y el lado afectado deben ser opt-in y reversibles.

### Limitación crítica: NO equiparar el botón físico con un setter Binder

El APK usa valores `2069=1/2` seguidos de `2069=0`, compatibles con **pulsos**. **NO se ha demostrado** si un solo `setIntData(2069, 1 o 2)` sostenido genera movimiento continuo, si se requiere repetir la orden o cuál es la dirección asociada a cada valor. No simular un HOLD enviando una orden persistente antes de validar que un fallo de UI/proceso **no deje el espejo moviéndose**.

Señales a comprobar en pruebas físicas estacionado:
1. ¿Se inicia movimiento con el cambio de estado `0 → 1/2`? ¿Continúa hasta recibir `0` o solo ejecuta un paso? ¿Cuánto desplaza por comando?
2. ¿`0` detiene inmediatamente y de forma segura? ¿Qué pasa ante `ACTION_CANCEL`, pérdida de foco, app en segundo plano, corte Binder, apagado de pantalla o proceso terminado?
3. ¿Los sentidos y lados coinciden con la suposición? ¿Existe respuesta de **posición real** o solo contadores locales? ¿Puede identificarse memoria OEM de espejo sin modificarla?
4. Probar velocidad y marcha solo como señales **leídas** y calibradas, nunca mandar escrituras de espejo en circulación para experimentar.

### Contrato técnico propuesto, NO IMPLEMENTADO

- **Pulsar:** comenzar una acción de duración **limitada**, con un solo movimiento pendiente y estado claramente visible; no enviar simultáneamente órdenes opuestas ni duplicarlas por eventos táctiles.
- **Soltar / cancelar / perder foco:** ordenar liberación `0` **solo después de validar que ese es el comando seguro**; bloquear nuevas órdenes si falla una liberación o readback; mantener mecanismo de desactivación independiente.
- **Timeout/watchdog:** limitar tanto duración total de una pulsación como longitud de cada acción. Una **limitación de software no garantiza paro** ante bloqueo total de Android: si el protocolo OEM requiere soltar activamente, no habilitar HOLD sin mecanismo de parada fiable.
- **Topes físicos:** la observación de que el espejo se detiene al llegar a sus extremos **no demuestra** que el comando software no mantenga carga sobre el motor ni garantiza que protecciones OEM funcionen igual desde Binder. Nunca usarlos como método de calibración/homing automático.
- **Memorias:** registrar si son ángulos reales o pasos estimados. Ante movimiento manual, reinicio, error de lectura o descalibración, no prometer restauración automática. Si no hay lectura de ángulo ni memoria OEM fiable, dejar modo de ajuste **manual HOLD** solamente y exigir recalibración explícita antes de un eventual auto-tilt en R.
- **No regresiones:** mantener Receiver v0.2.10, Sender, mapas, controles actuales, viajes y puente sin modificaciones. Crear y probar esta pieza de manera desacoplada antes de ofrecerla como opción experimental.

**Estado de esta decisión:** UX acordada como preferencia del usuario; contrato de control continuo, sentido de 1/2, verificación de STOP, posiciones reales y futura automatización en R **PENDIENTES**.

## Hallazgo adicional relevante para el tablero de combustible/energía

En las clases `mainthor.MainthorRepo` y corutinas de lectura se encontraron propiedades HAL adicionales:

| Propiedad HAL decimal | Etiqueta textual encontrada en APK | Lectura en el código | Interpretación prudente |
|---|---|---|---|
| **559964335** | `SETTINGS PHEV SUM FUEL (lee)` | `MainthorRepo$acumulados$2` → `leerHalCrudo` | **Candidato de acumulador PHEV de combustible**, **NO** confirmado como litros consumidos ni pulsos de inyección. |
| **559964336** | `SETTINGS PHEV SUM CHRG EGY (lee)` | `MainthorRepo$acumulados$2` → `leerHalCrudo` | **Candidato de energía cargada acumulada**, unidad/escala/origen sin validar. |
| **559940910** | `EXT.BATT VOLT (lee)` | `MainthorRepo$tensionYCorriente$2` → `leerHalCrudo` | Etiqueta de tensión **EXT.BATT**; NO suponer que sea voltaje HV del pack. |
| **559940911** | `EXT.BATT CURRENT (lee)` | `MainthorRepo$tensionYCorriente$2` → `leerHalCrudo` | Corriente **EXT.BATT**; NO suponer que sea corriente HV. |

**La mejor nueva línea de investigación es 559964335.** Una lectura de acumulador que realmente reflejara consumo físico —si el firmware la expone en esta unidad y si su semántica resultara validada— ayudaría a estimar los primeros litros del tanque mientras la resistencia permanece en 56 Ω. Pero, por ahora, solo conocemos la **etiqueta del catálogo y la consulta en el APK**: no conocemos unidades, accesibilidad desde el UID de DriveLab, qué cuenta, si se resetea, si escala de forma continua o si representa consumo instantáneo/total.

En el mismo archivo, `MainthorRepo$litrosNafta$2` invoca `CiLink.litrosNafta()`: coincide con la lectura de combustible OEM **CI186** ya contrastada con el menú oculto. **No constituye una segunda fuente física independiente**. El **cálculo VIEJO de nafta del launcher sigue completamente DESCARTADO**: no utilizarlo en ninguna fórmula ni comparación.

### Trazado de uso: ¿participa PHEV SUM FUEL en el viaje?

En el **análisis de llamadas directas del DEX**:

- `MainthorRepo$acumulados$2` consulta **559964336** y **559964335** por `leerHalCrudo`.
- La única invocación directa identificada de `MainthorRepo.acumulados()` desde los módulos de aplicación fue `MainthorScreenKt$MainthorScreen$4.invokeSuspend`: **pantalla de Mainthor**.
- `ViajeService.bucle` sí llama directamente a `MainthorRepo.litrosNafta()`, y esa ruta invoca `CiLink.litrosNafta()` (CI186).
- **Conclusión acotada:** en el código recorrido, el acumulador 559964335 aparece como **lectura de interfaz/diagnóstico**, NO como la entrada directa de combustible consumido de `ViajeService`. El dato nuevo de nafta del viaje **parece depender de la variación CI186** y no de una validación independiente por el HAL `PHEV SUM FUEL`. Análisis estático: no excluye rutas indirectas/reflexión, pero no hay evidencia para afirmar que el contador sea el dato físico real del viaje.

Esto refuerza la decisión de **no promediar** `litros_real` del launcher con el descenso de CI186 como si fueran sensores independientes. Se debe comprobar contra surtidor.

### Prueba propuesta, solo lectura y estacionado

1. Verificar que los getters HAL permitidos de Receiver puedan leer 559964335 y 559964336 **sin setter ni comandos genéricos**, y registrar errores de permiso si no.
2. En condiciones seguras, comparar **valor bruto y timestamp** antes y después de períodos de térmico/EV ya ocurridos naturalmente. Contrastar únicamente muestras del mismo ciclo.
3. Validar escala/signo/unidades y comprobar si el acumulado aumenta exclusivamente cuando el térmico gasta combustible. Si varía con SOC o reinicia, estudiar su semántica antes de llamarlo «litros».
4. Solo después decidir si aporta una **medición independiente** frente al indicador CI186, flotante y surtidor, y si permite alimentar consumos instantáneos o corregir la zona alta saturada.
5. Registrar el método y la incertidumbre en [Calibración CSV](CALIBRACION_CSV_T1_2026-11-10.md). Nunca reemplazar con valores inventados el dato faltante.

## Límites de la investigación

- **Estática, sin prueba física:** se verificaron referencias de bytecode, no que funcionen desde `ar.com.rango5gps.drivelabauto.receiver`.
- No hemos activado espejos, conducido el auto, verificado dirección de valores 1/2, leído esas propiedades HAL en la T1 ni generado una nueva APK.
- El Receiver v0.2.10 se mantiene **intacto**; preservar Waze, Sender, puente LAN, mapas, ventanas, AVAS, Spotify y viajes.
- Identificadores HAL/CAN de terceros, especialmente los marcados «escribe», no deben exponerse en una UI genérica de escritura.
- La lógica hallada pertenece a software de terceros; se anotan únicamente hechos de interoperabilidad, sin copiar su código ni alterar licencias.

**Clasificación:** hallazgos de archivo CONFIRMADOS; uso y unidades en vehículo PENDIENTES; beneficios para cálculo de consumo HIPÓTESIS.
