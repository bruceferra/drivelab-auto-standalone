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

## Investigación de lectura real de posición y deriva (10/11/2026)

### Evidencia física del propietario — Receiver 0.2.11

- **CONFIRMADO en la T1 argentina (usuario):** el comando OEM **2069=2** baja el espejo **derecho**, **2069=1** lo sube. Se probaron cuatro pulsos en cada sentido, se observaron los movimientos y el retorno. En estos ensayos no se ha medido carrera total ni ángulo; no demuestra que cada pulso equivalga a un mismo desplazamiento.
- El launcher de terceros se **descalibra repetidamente incluso sin tocar los mandos manuales**, según el usuario. No atribuir causa única sin telemetría. Posibles motivos a ensayar: órdenes rechazadas/interrumpidas, duraciones variables, aceleración y fricción del motor, movimiento OEM externo/memoria al arrancar, límite alcanzado o actualizaciones de estado locales fuera de secuencia.
- El método de recalibración del launcher: marcar cero en tope superior, almacenar D y R, estimar `pos_actual` mediante pasos; **no garantiza retorno geométrico exacto**. Es un sistema en lazo abierto.

### Qué reconoce el propio APK de T1Launcher

Los strings oficiales embebidos en `classes2.dex` incluyen el aviso **«El auto no nos dice en qué ángulo está el espejo. No hay forma de preguntárselo.»**, además de una advertencia de que mover el espejo con el volante después de marcar el cero desajusta el contador. Esto es evidencia de que **EspejoRService NO basa su posición en un ángulo OEM validado**. No constituye prueba de que el firmware carezca de toda señal de posición, dado que su catálogo también enumera identificadores HAL.

### Propiedades HAL candidatas a lectura REAL, sin validar en la Jetour

| ID decimal | Nombre en catálogo del APK / Android Automotive | Qué probar |
|---:|---|---|
| **339741504** | `MIRROR_Z_POS` | **Posición vertical relativa**; Android Automotive documenta un entero entre límites min/max, con área de espejo izquierdo/derecho. No es ángulo en grados. |
| **339741505** | `MIRROR_Z_MOVE` | Movimiento vertical, puede dar estado de desplazamiento, **no es sensor de posición**. |
| **339741506** | `MIRROR_Y_POS` | Posición horizontal relativa, no vertical. |
| **557843649** | `VC.PASSENGER MIRROR LOCATION (lee)` | Vendor candidate: podría ser valor real, memoria OEM o estado, **desconocido**. |
| **557867096** | `SOC SEAT EXT MIRROR POSITION` | Vendor candidate: posición/memoria del asiento-espejo **no diferenciada**. |
| **557875203** | `SETTINGS SEAT EXT MIRROR POSITION` | Vendor candidate sin tipo/unidad confirmada. |
| **557879310** | `SOC SEAT EXT MIRROR POSITION BY QNX` | Vendor candidate sin interpretación física demostrada. |

**Referencia oficial:** [Android Automotive `VehiclePropertyIds`](https://developer.android.com/reference/android/car/VehiclePropertyIds), `MIRROR_Z_POS` 339741504 y `MIRROR_Y_POS` 339741506 (posiciones relativas), `MIRROR_Z_MOVE` 339741505 (movimiento). Su API estándar requiere permiso **`android.car.permission.CONTROL_CAR_MIRRORS` (signature|privileged)** y soporte real de la ROM; que un catálogo incluya el nombre NO implica que Receiver tenga autorización ni valores útiles. También existen servicios OEM Binder, pero no está confirmado el contrato de lectura para estos IDs HAL.

### Prueba determinante: leer antes y después, SIN homing al tope

1. **SOLO lectura**, en P, vehículo quieto. Enumerar si `MIRROR_Z_POS` está soportada, sus `areaId` para cada espejo, rangos min/max, tipo y permisos. Registrar valor bruto y estado de acceso sin disparar órdenes.
2. Leer posición vertical de ambos espejos. Ajustar una distancia pequeña mediante el mando OEM (sin llegar a topes), leer de nuevo y observar **si el dato cambia** en el lado correcto y repite el mismo valor al volver.
3. Si no responde `MIRROR_Z_POS`, investigar `VC.PASSENGER MIRROR LOCATION (lee)`, `SOC SEAT EXT MIRROR POSITION` y las demás propiedades HAL **una por una**, sin escribir ni modificar memorias. Diferenciar lectura no soportada / permiso denegado / valor constante / valor real.
4. Repetir desde la aplicación con un movimiento corto validado (`2069` derecho; **2068 izquierdo sigue pendiente de validación física**). Comparar posiciones reportadas antes y después. Guardar log de duración del comando, valor previo/posterior, confirmación de paro, marcha, app/ROM.
5. **Si existe retroalimentación real:** almacenar `normalZ` y `reverseZ` por espejo y regresar por **lectura de posición** (lazo cerrado) sin adivinar número de pulsos; no exceder límites OEM y detener el motor al alcanzar el objetivo.
6. **Si NO existe:** no se puede saber dónde quedó el espejo ni detectar el final de carrera solo porque un setter Binder responde; el método de contar pulsos/tiempos es de **lazo abierto** y puede derivar. No sustituir la falta de sensor por llevar el motor al tope con la app.

**Respuesta a la hipótesis del usuario:** colocar el derecho arriba y el izquierdo abajo **NO permite a DriveLab conocer automáticamente el límite**. Solo es posible si un getter OEM devuelve de manera fiable la posición/fin de carrera del espejo correspondiente. Probar primero cambios pequeños y reversibles con el mando original; **no usar el final mecánico como sensor**.

### Estado de integración

Hasta aquí se verificaron los textos/códigos **en APK** y el accionamiento del espejo derecho **en el vehículo**. Ningún campo HAL de posición ha sido leído todavía desde DriveLab Receiver. **No habilitar auto-bajada al engranar R/retorno a D** hasta validar estado de marcha, lectura real o procedimiento de referencia seguro, duración y parada. La clave de la solución es medir primero, no aumentar la cantidad de pulsos.

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
