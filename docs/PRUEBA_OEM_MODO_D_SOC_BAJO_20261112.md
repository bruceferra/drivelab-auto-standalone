# Prueba real selector original Jetour T1 en D, SOC bajo — 12/11/2026

## Fuente verificada

Supabase privado `public.drivelab_auto_diagnostic_csv_chunks`, archivo `phev_modes_20261112_090604.csv`; carga única completa (1/1) recibida a las 12:07:18 UTC (09:07 ART), 22 filas de datos desde 09:06:04.991 hasta 09:07:09.100 ART. El propietario realizó acciones sobre **el selector ORIGINAL de Jetour**, **no** escrituras desde DriveLab.

## Transiciones confirmadas (lecturas, no solicitudes)

| Hora Argentina | 1033 Marcha | 2148 Velocidad | 1043 Potencia | 1040 SOC | 1042 SOC interno |
|---|---:|---:|---:|---:|---:|
| 09:06:44.637 | 4 (D) | 0.0 km/h | 2 HEV | 2 SMART | 17.6% |
| 09:06:47.688 | 4 (D) | 0.0 km/h | **3 EV MAX** | 2 SMART | 17.6% |
| 09:06:50.748 | 4 (D) | 0.0 km/h | 3 EV MAX | 2 SMART | 17.6% |
| 09:06:53.814 | 4 (D) | 0.0 km/h | **2 HEV** | 2 SMART | 17.6% |
| 09:06:59.926 | 1 (P) | 0.0 km/h | 2 HEV | 2 SMART | 17.6% |

Propietario reportó indicador de batería aproximadamente **14%** y que algunas selecciones eran bloqueadas por disponibilidad de SOC. No hay registro de eventos de **toque/solicitud** del selector original, solo de **estado posterior**, por lo que no puede concluirse solo con este archivo si la transición EV MAX→HEV de 09:06:53 fue impuesta automáticamente por el controlador o se produjo tras otra selección manual. Tampoco se conoce el umbral exacto de batería (16-17% es hipótesis del propietario, no validación). La diferencia entre ~14% tablero y 17.6% interno es un hecho de lectura de dos representaciones SOC, no prueba que uno esté mal.

La prueba valida: (1) la selección OEM puede cambiar 1043 entre modos **con marcha D, pero velocidad 0 km/h**; (2) el sistema permite al menos EV MAX con el SOC mostrado; (3) la gestión del tren motriz responde a condiciones adicionales al estado de la interfaz.

## Arquitectura y límites

La UI Android de DriveLab es una capa sobre servicios OEM. Sin embargo, **estar por encima en la UI no demuestra equivalencia de ruta ni de validación de seguridad**. La pantalla original puede invocar un `VehicleSettingCarPropertyManager.setCarPropertyValue` del HAL, mientras el experimento DriveLab utiliza `IVehicleControl` Binder `transact(15)` con `propertyId=1043`. Que ambos finalmente lleguen a ECU/BMS y respeten restricciones de SOC es plausible, pero sigue siendo una **hipótesis**, no prueba de que la llamada de terceros sea autorizada/correcta mientras circula.

Los tres comandos `1043=1 EV / 2 HEV / 3 EV MAX` ya fueron probados físicamente **estacionado en P** por el propietario con lectura posterior y confirmación en el tablero original. La prueba del selector OEM en D no constituye validación de escritura de DriveLab en D ni a velocidad mayor a cero. Por lo tanto **no quitar comprobaciones P/0 km/h ni habilitar el automatismo OSM** basándose solo en esta captura.

## Siguiente validación técnica (sin nuevos riesgos durante conducción)

- Examinar la implementación del selector PHEV en el APK OEM de configuración de Jetour: `VehicleSettingCarPropertyManager.setCarPropertyValue` y propiedad HAL `557843642` escritura, `557867211` lectura. Verificar propiedad, tipo, área, permisos, modo y ruta exacta antes de decidir si reproducir la operación está permitido.
- Registrar cambios del menú original con la camioneta **inmóvil** en D (solo lectura), y comparar 1043 con 557867211 y 1042/1062. No pedir ensayos de comandos experimentales mientras circula.
- Para reglas ciudad/autopista, separar detección OSM y validación de SOC/temperatura de la autorización de cambios de propulsión, con controles del vehículo como fuente definitiva de estados.

**Estado de producto:** Receiver firmado 0.2.43 sigue con cambio manual detenido en P y asesor OSM sin escritura. Esta nota registra evidencia física, no modifica código ni firmware.
