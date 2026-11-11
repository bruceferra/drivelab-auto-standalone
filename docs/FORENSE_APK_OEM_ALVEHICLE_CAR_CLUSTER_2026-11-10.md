# Jetour T1 PHEV – análisis estático de los 3 APK OEM (10/11/2026)

**Muestras obtenidas por ADB del vehículo, aportadas por el propietario.** Solo lectura: se inspeccionaron APK/DEX originales, no se alteró el software del vehículo.

| APK original | Bytes | SHA-256 |
|---|---:|---|
| ALVehicleService.apk | 10.295.125 | `6bb57141c4dc6cc2e8298ebd23c4587858ea64dfbb87a4fef87344689d1b7309` |
| ALCarService.apk | 569.758 | `566e4e7cf4d6eb777c0b73de5d380423087c9b659404e11f3535857127a41342` |
| ClusterInteraction.apk | 561.559 | `61c5e6238006225efdf2fd7178c664780d0c5596ad8a68724580d40b16af354f` |

## Descubrimiento confirmado en DEX de ALVehicleService

En `com.autolink.app.vehiclemanager.data.VehiclePropertyKey.<clinit>` están codificados como constantes de propiedad, con etiqueta en chino:

| Código | Etiqueta original | Traducción literal / alcance |
|---|---|---|
| **1034** | `转速表有效位` | Indicador de validez del tacómetro/RPM |
| **1035** | `转速` | Revoluciones por minuto |
| **1048** | `单次充电能量` | Energía de **una sesión de carga** (`KeyPHEVSingleChangeEnergy`) |
| **1049** | `单次放电能量` | Energía de **una sesión de descarga** (`KeyPHEVSingleDischargeEnergy`) |
| **1084** | `近50公里平均电耗变化` | Cambio de consumo eléctrico medio de los últimos **50 km** (`KeyPHEVAvgElecCns`) |
| **1085** | `近50公里平均油耗变化` | Cambio de consumo de combustible medio de los últimos **50 km** (`KeyPHEVAvgFuCns`) |
| **1086** | `500米平均电耗变化` | Cambio de consumo eléctrico medio de **500 metros** (`KeyPHEVAvgElec`) |
| **1087** | `500米平均油耗变化` | Cambio de consumo de combustible medio de **500 metros** (`KeyPHEVAvgFu`) |
| **1089** | `车速` | Velocidad del vehículo |
| **1090** | `获取电流电压功率` | Obtener corriente, voltaje y potencia |

**1086/1087 son el hallazgo prioritario para TRIP A/B.** Aún no está verificado si se actualizan por tramos discretos o una ventana móvil de 500 m ni si permanecen válidos cuando el vehículo está quieto con motor térmico encendido. Es necesario registrar datos reales y contrastar con surtidor.

## Ruta de los datos

- En ALVehicleService existe `com.autolink.app.vehicleservice.cluster.ClusterManager` con `mAvgFuCns` y `getAvgFuCns`, junto con `mAvgFuCnsListener` que almacena eventos `onAvgFuCnsChanged`; también existen `mAvgElecCns`, `mAvgFu`, `mAvgElec`.
- Su adaptador `com.autolink.app.vehicleservice.adapter.SDKWrapper` recibe `onAvgFuCnsChanged` y `onAvgFuChanged` y llama a `onPropertyChange`: las aplicaciones pueden observar señales con API sin calcularlas a partir de CSV.
- En ClusterInteraction están `com.autolink.clusterinteraction.manager.ResponseManager.onAvgFuCnsChanged`, `onAvgFuChanged`, `onAvgElecCnsChanged`, `onAvgElecChanged` y callbacks Binder `IAvgFuCnsCallback`, `IAvgFuCallback`, etc., que notifican cambios. También aparecen índices de datos `TC_AVG_FU_CNS_HIGH_INDEX`, `TC_AVG_FU_CNS_LOW_INDEX`, `AVG_FU_INDEX` y los equivalentes eléctricos. Esto respalda que la señal se **recibe** y se retransmite; no demuestra dónde se forma la media, que puede ser en otra ECU/HAL.
- ClusterInteraction implementa `getTripAValue` y `onTripAValueChanged` con `mTripAValue`; esto corresponde a **distancia Trip A**, no es un contador de litros confirmado.
- ALCarService `com.autolink.carservice.IALCarImpl.getFloatProperty` delega en `android.car.hardware.property.CarPropertyManager`: posible puerta de lectura al HAL, con permisos OEM.
- `559964335` / `SETTINGS_PHEV_SUM_FUEL_READ` y `559964336` / `SETTINGS_PHEV_SUM_CHRG_EGY_READ` aparecen en `android.hardware.automotive.vehicle.V2_0.VehicleProperty.toString/dumpBitfield` de ALCarService. **Evidencia de definición de constantes, no evidencia de uso activo ni acceso desde una APK de terceros.** No afirmar litros reales ni kWh hasta validar lectura/tipo/unidades y forma de reinicio.
- `ENGINE_FUEL_RATE` aparece en las tablas genéricas `DiagnosticFloatSensorIndex.toString/dumpBitfield` en ALCarService; **no se identificó ningún lector activo de esa señal en estos APK**. La tasa instantánea de combustible sigue sin estar verificada en la Jetour.

## Cálculo y trampas de ventanas móviles

- 1085 = 5,1 L/100 km implica **2,55 L equivalentes en sus propios 50 km**, NO 5,1 L totales y NO necesariamente el viaje actual.
- Para 1087, si la magnitud termina siendo L/100 km y corresponde a un **tramo cerrado, único, de 0,5 km**, volumen de ese tramo `litros = (L/100km) × 0,005`. Sumando solo tramos **no superpuestos** se obtiene una reconstrucción del viaje. No sumar cada muestra de 3 segundos: provocaría doble conteo.
- Si 1087 es una ventana **móvil** de 500 m, el cálculo requiere detección de saltos o tratamiento específico; no tenemos suficiente evidencia aún para convertirlo automáticamente a litros por viaje.
- Para consumo con térmico en marcha **pero vehículo detenido**, el promedio por distancia podría diferir o acumular diferido: contrastar frente a 1034/1035, nivel CI186 y variación eléctrica con reloj real, sin asignar litros inventados.
- Comparar distancia con Trip A OEM y GPS/velocidad del Receiver. Conservar las lecturas originales en CSV con timestamp y fuente.

## Estado de implementación

En la **rama experimental PR #26**, sin OTA pública:
- Receiver agrega lecturas `1084/1085`, `1086/1087` y `1034` a las muestras de TRIP; muestra los dos primeros y los dos últimos separados de la NAFTA PROXY y preserva CSV original.
- Edge Function privada `drivelab-auto-trip-upload` acepta las nuevas columnas, manteniendo autenticación y filtrado, sin enviar coordenadas ni VIN. **No se probó la señal en un viaje real ni la subida desde esta versión de APK**.
- La fórmula histórica de nafta PROXY permanece sin cambios para que no pase por dato medido.

## Prueba física siguiente, solo lectura

1. Con vehículo detenido, comparar `1034` y `1035` con el motor térmico apagado y encendido. Un valor de 0 en 1035 solo sirve como motor apagado si 1034 confirma validez.
2. Circular un tramo corto controlado, registrando distancia y `1086/1087` cada 2–3 segundos, primero EV y después HEV en condiciones normales.
3. Ver si cambian por saltos de 0,5 km o en cada muestra y si conservan valores tras superar 500 m.
4. Correlacionar con `1084/1085`, `1048/1049`, SOC, corriente, potencia, CI186 y un repostaje posterior; no mezclar períodos.
5. Solo entonces implementar TRIP A (desde llenado) y B (manual/reiniciable) con acumulados por ventanas validadas. Evitar lectura activa de HAL desconocido sin permiso/verificación de idoneidad.

**No hay demostración en estos tres APK de un acumulador universal de gasolina en litros inyectados o un método resettable de consumo por viaje.**

## Prueba de carretera reportada con Receiver 0.2.25 (10/11/2026)

Usuario realizó cerca de 1 km de marcha normal con TRIP activo y confirmó aumento del número de muestras. En pantalla en vivo:

- `1085`, promedio nafta últimos 50 km: **sí cambia**.
- `1084`, promedio eléctrico últimos 50 km: **sí cambia**.
- `1087`, promedio nafta de 500 m: **-1, sin cambios observados**.
- `1086`, promedio eléctrico de 500 m: **-1, sin cambios observados**.
- `1034`, validez de RPM: **1**. NO significa necesariamente motor térmico encendido: consultar RPM `1035` a la misma hora.

Por tanto **NO sumar los códigos 1086/1087 para consumo de viaje** ni interpretarlos como -1 L o -1 kWh. Son valores aparentemente no disponibles mediante el getter FLOAT actual, o candidatos que dependen de eventos/estado de ECU. El kilometraje recorrido debería permitir cambios de valores de 500 m si fueran publicados como lectura continua; falta corroborar.

Siguiente depuración: registrar en paralelo para 1086/1087 el getter `readInt` y `readFloat` en el mismo timestamp y distinguir error/centinela; verificar si `onAvgFuChanged`/`onAvgElecChanged` exponen datos por callback en lugar de propiedad persistente; comparar con el CSV original y si los eventos ocurren tras 500 m exactos, sin escribir propiedades OEM. Conservar histórico y los valores -1 crudos para reanalizar. **Todavía no hay medición real de combustible por tramos validada.**

## CSV real recibido y analizado — TRIP 2026-11-10 23:25:21 a 23:34:18 ART

**Fuente:** `trip_20261110_232521.csv` aportado por el usuario. 177 muestras, ~3,05 s de frecuencia, 8 min 57 s totales, 1,65441 km **estimados por velocidad OEM** (`notes=OEM_SPEED_UNIT_CANDIDATE` en todas las filas, GPS sin datos). No publicar coordenadas privadas.

| Lectura | Inicio | Fin | Observación |
|---|---:|---:|---|
| `1084` eléctrico últimos 50 km | -0,9 | -1,0 kWh/100km | **1 cambio** a las 23:29:34, distancia 1,10770 km |
| `1085` nafta últimos 50 km | 4,7 | 4,9 L/100km | **1 cambio** en el mismo timestamp que 1084 |
| `1086` eléctrica 500 m float | -1 | -1 | **-1 en las 177 muestras**; señal no disponible para uso actual |
| `1087` nafta 500 m float | -1 | -1 | **-1 en las 177 muestras**; señal no disponible para uso actual |
| `1034` validez RPM | 1 | 1 | Constante 1; no implica térmico encendido |
| `1035` RPM térmico | 0 | 0 | >0 en 51 muestras; pico ~1.736 RPM. Dos sesiones: 23:27:05–23:29:07 y 23:31:21–23:31:55 (muestreo cada 3 s) |
| `CI186` nivel nafta OEM | 62,7 | 62,5 L indicados | Saltos -0,1 L a 23:27:38 y 23:31:43. **No equivalen a volumen real consumido con alta precisión** |
| `1042` SOC interno | 17,5 | 18,5 % | +1 punto porcentual, sin kWh físico inferible |
| `1062` SOC tablero | 13,1 | 14,2 % | +1,1 puntos, resolución ~0,1 % |
| `1049` energía sesión descarga | 0,0 | 0,1 (crudo) | Un incremento cerca de 23:31:21; unidad/reset no validados |
| `1048` energía sesión carga | 0,0 | 0,0 (crudo) | Sin variación |
| `1063` voltaje HV | NaN | NaN | **Ausente en las 177 muestras**; por esta causa `hv_gross_discharge_kwh_est=0` y `hv_charge_kwh_est=0` **no son mediciones de cero consumo** |
| `gas_l_proxy` | 0 | 0,12353 L | Cálculo fijo 1,65441 km × 7,47 L/100 km, **no consumo real** |

Las cifras OEM de 50 km cambiaron simultáneamente *una vez* durante este tramo; la actualización puede ser por evento, distancia o ventana temporal. No atribuir el cambio de 4,7 a 4,9 al consumo directo de estos 1,65 km: se trata de promedios móviles con tramos que entran y salen de la ventana. Si `1086/1087=-1`, la prueba **no aporta segmentos de 500 m sumables**. No generar TRIP/tanque a partir de esos `-1` ni reintroducir el algoritmo descartado del T1Launcher.

### Próxima investigación de solo lectura

1. Verificar **getter INT frente a FLOAT** para IDs `1086/1087`, guardando por separado valor crudo y errores. Los -1 obtenidos del FLOAT actual no prueban que el getter INT sea válido.
2. Considerar la vía de **callbacks OEM** `onAvgFuChanged`/`onAvgElecChanged` en ClusterInteraction/VehicleService; la implementación de listener puede recibir eventos no persistidos por getter.
3. Investigar la ausencia total del voltaje `1063` y contrastar señales HV alternativas `1090` (solo lectura), sin poner números falsos de kWh.
4. Exportar o recibir próximos CSV, preservando origen de distancia y estado de RPM. Para consumo de tanque físico, continuar contrastando surtidor.

## Siguiente diagnóstico por doble getter, Receiver 0.2.27 (en prueba)

El CSV real `trip_20261110_232521.csv` contiene 177 muestras: `1086/1087=-1` sin variación y `1063` ausente. No basta llamar a este resultado 'sin cambios de ventana': el primer paso es comprobar si `1086/1087` son enteros en vez de float o datos enviados por eventos.

Se incorporan **sin alterar la fórmula del TRIP**, a la visualización del tablero y al final del CSV:
- `oem_1086_electric_int_raw` y `oem_1087_fuel_int_raw` — `VehicleService.readInt(5)` en paralelo a `readFloat(6)`.
- `hv_voltage_1063_int_raw` y `hv_current_1064_int_raw` — candidato de tipos INT para HV. No sustituye aún la tensión HV efectiva.
- `oem_1090_float_raw` y `oem_1090_int_raw` — prueba de propiedad OEM 1090 ('obtener corriente/voltaje/potencia'), naturaleza y escala desconocidas.
- Getter fallido muestra **SIN LECTURA** en pantalla y campo vacío en CSV; respuesta `-1` se conserva **explícitamente** y no se interpreta como consumo negativo.
- Se mantienen los 30 campos CSV anteriores, se agregan seis al final y se amplía el whitelist de subida privada; la sincronización no envía coordenadas ni VIN.
- Las señales INT nuevas **no se emplean** para energía HV estimada ni para calcular combustible hasta validar unidades y sensor físico.
- Si `1086/1087` responden `-1` con ambos getters, seguir investigando callbacks `onAvgFuChanged`/`onAvgElecChanged` en ClusterInteraction. No asumir que el setter 2107 modifica distancias reales.

## Prueba física de comparación int/float (Receiver 0.2.27)

**Fuente:** observaciones orales directas del propietario durante una prueba con vehículo en marcha (velocidad mencionada: ~14 km/h), además de una lectura con el vehículo sin circular. Aún no se recibió CSV de esta prueba.

| ID OEM | float | int | Conclusión |
|---|---|---|---|
| **1086**, electricidad 500 m | -1 | -1 | No disponible en los dos getters por ahora; **no** es kWh |
| **1087**, nafta 500 m | -1 | -1 | No disponible en los dos getters por ahora; **no** es litros |
| **1063**, voltaje HV | sin lectura | sin lectura | No hay tensión utilizable por este acceso; la falla no es solo de conversión de float a int |
| **1064**, corriente HV | **5,70 → 5,90, cambiante** | -1 | **Float responde** y tiene variación observable; signo/unidades y polaridad por validar |
| **1090**, corriente/voltaje/potencia candidato | -1 | -1 | No disponible por ambos getters |
| **1034**, RPM válidas | — | 1 | Señal de validez, no equivale por sí misma a motor encendido |

**Diagnóstico:** La vía de getters `VehicleService.readInt/readFloat` ha quedado sin datos para `1086/1087` y `1090`. No repetir lecturas de 500 m por carretera esperando un cambio si devuelven centinela en ambas modalidades. El valor `1064` sí varía: preservar el float original y no reemplazarlo con el int `-1`. La potencia eléctrica y los kWh siguen sin poder calcularse de forma validada: no atribuir `V×A=0` cuando falta V.

**Nueva pista del código original de los APK:** `ClusterInteraction.apk` define `IClusterInteraction.registerAvgFuCallback` y `registerAvgElecCallback` con callbacks `onAvgFuChanged` / `onAvgElecChanged`; se distinguen de `registerAvgFuCnsCallback`/`registerAvgElecCnsCallback` (promedios de 50 km). `ALVehicleService.apk` contiene listeners homólogos `ClusterManager$mAvgFuListener`, `ClusterManager$mAvgElecListener`. Es una **ruta alternativa potencial de solo suscripción** para intentar recibir datos de 500 m. Su existencia en DEX **no prueba que estén emitiendo eventos** en la variante de Argentina ni que acepten registros de terceros. Próximo desarrollo: investigar el contrato Binder de estas inscripciones, añadir un registro/desregistro controlado de eventos sin escritura del vehículo y contrastar contra el CSV de lectura, con errores explícitos. Considerar en paralelo buscar tensión HV en los callbacks OEM `IChargeVoltageCallback` y `IChargePowerInfoCallback`, distinguiendo tensión de carga de tensión del pack en movimiento.

## Ensayo final: suscripción a eventos OEM de consumo, Receiver 0.2.28

Análisis DEX original de ClusterInteraction.apk, contrato de IClusterInteraction$Stub:

| Evento | Descriptor del callback | Registro Binder | Cancelación Binder | Transacción callback | Firma |
|---|---|---:|---:|---:|---|
| Nafta 500 m | IAvgFuCallback | **135** | **136** | 1 | onAvgFuChanged(float) |
| Electricidad 500 m | IAvgElecCallback | **133** | **134** | 1 | onAvgElecChanged(float) |
| Nafta 50 km | IAvgFuCnsCallback | **137** | **138** | 1 | onAvgFuCnsChanged(float) |
| Electricidad 50 km | IAvgElecCnsCallback | **139** | **140** | 1 | onAvgElecCnsChanged(float) |

Los cuatro métodos de registro del proxy son register...Callback(I...Callback) y devuelven void. Los callbacks reciben exactamente un float, sin parámetros extra. La interfaz del servicio sigue usando CiService y descriptor IClusterInteraction, previamente verificados para CI186. En Android se implementan objetos Binder locales con descriptor exacto, escucha onTransact(1) y responde writeNoException, sin necesidad de modificar archivos OEM ni cargar librerías OEM.

**Implementación segura:** T1OemConsumptionCallbacks se enlaza a CiService con bindService de solo uso, registra cuatro callbacks, espera **como máximo 180 segundos** o hasta STOP, luego intenta desregistrar únicamente los callbacks que sí fueron aceptados, y libera el servicio. No invoca getters/setters ni manda comandos ECU. Verifica descriptor y registra errores por callback. Un rechazo no impide intentar las otras tres subscripciones. Si ninguna es aceptada, muestra error explícito.

**Instrumentación:** en TABLERO / TRIP hay botones «ESCUCHAR EVENTOS OEM · 3 MIN», «DETENER ESCUCHA OEM», «EXPORTAR CSV EVENTOS OEM». Panel muestra inscripciones aceptadas y por canal número de eventos y último valor crudo. Se genera oem_callbacks_YYYYMMDD_HHMMSS.csv con marca de tiempo, canal y float recibido; la exportación a Descargas/DriveLabAuto usa MediaStore sin selector, preservando el original interno. Sin cambios en Sender, GPS, radar o fórmulas del TRIP.

**Límites:** Que un registro sea aceptado no demuestra que lleguen eventos. Que no haya eventos con vehículo inmóvil no demuestra que no funcionen al circular 500 m. Los promedios de 50 km se escuchan como grupo de control, pero tampoco está garantizado que emitan cada 3 minutos. No sumar callbacks como litros; determinar si el evento representa tramo finalizado, ventana móvil o alguna otra unidad antes de utilizarlo. El voltaje HV sigue sin dato validado; la lectura de corriente 1064 no autoriza calcular kWh.

**Seguridad y UX:** no tocar botones durante marcha. Iniciar la escucha estacionado y leer resultados nuevamente estacionado; no hace falta provocar maniobras ni conducir específicamente para obtener un evento.
