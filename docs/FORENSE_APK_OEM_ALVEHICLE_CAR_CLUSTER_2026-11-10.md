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
