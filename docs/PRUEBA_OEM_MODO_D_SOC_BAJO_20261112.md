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

**Aclaración posterior del propietario (confirmación expresa):** el paso HEV→EV MAX fue **solicitado por él mediante el selector original** y la vuelta EV MAX→HEV también formó parte de sus selecciones manuales. El CSV captura respuestas del vehículo, no los eventos de toque, pero la autoría de las selecciones quedó aclarada por testimonio directo: **NO describir la vuelta a HEV como automática**. En la prueba el tablero marcaba aproximadamente **14%** y el SOC interno 1042 **17,6%**. Según pruebas repetidas del propietario, **EV MAX se puede seleccionar incluso con SOC muy bajo y ha permitido consumir hasta el 0% indicado**, mientras que **HEV→EV normal sí puede ser rechazado por SOC insuficiente**. El umbral cercano a 16-17% es una observación orientativa del usuario, no un límite universal demostrado. Que EV MAX sea seleccionable a SOC muy bajo **no demuestra ausencia total de protecciones del BMS o del tren motriz**. La diferencia de SOC entre display e interno tampoco demuestra que uno sea incorrecto.

La prueba valida: (1) el **selector OEM solicitado manualmente** cambió 1043 entre HEV y EV MAX con **marcha D y velocidad 0 km/h**; (2) EV MAX está disponible con SOC bajo en esta T1 y, según el usuario, incluso cerca de 0%; (3) el rechazo por SOC observado afecta al **ingreso en EV normal desde HEV**, no a EV MAX. El CSV **no prueba** una transición espontánea de la ECU ni escritura desde DriveLab estando en D.

## Arquitectura y límites

La UI Android de DriveLab es una capa sobre servicios OEM. Sin embargo, **estar por encima en la UI no demuestra equivalencia de ruta ni de validación de seguridad**. La pantalla original puede invocar un `VehicleSettingCarPropertyManager.setCarPropertyValue` del HAL, mientras el experimento DriveLab utiliza `IVehicleControl` Binder `transact(15)` con `propertyId=1043`. Que ambos finalmente lleguen a ECU/BMS y respeten restricciones de SOC es plausible, pero sigue siendo una **hipótesis**, no prueba de que la llamada de terceros sea autorizada/correcta mientras circula.

Los tres comandos `1043=1 EV / 2 HEV / 3 EV MAX` ya fueron probados físicamente **estacionado en P** por el propietario con lectura posterior y confirmación en el tablero original. La prueba del selector OEM en D no constituye validación de escritura de DriveLab en D ni a velocidad mayor a cero. Por lo tanto **no quitar comprobaciones P/0 km/h ni habilitar el automatismo OSM** basándose solo en esta captura.

## Siguiente validación técnica (sin nuevos riesgos durante conducción)

- Examinar la implementación del selector PHEV en el APK OEM de configuración de Jetour: `VehicleSettingCarPropertyManager.setCarPropertyValue` y propiedad HAL `557843642` escritura, `557867211` lectura. Verificar propiedad, tipo, área, permisos, modo y ruta exacta antes de decidir si reproducir la operación está permitido.
- Registrar cambios del menú original con la camioneta **inmóvil** en D (solo lectura), y comparar 1043 con 557867211 y 1042/1062. No pedir ensayos de comandos experimentales mientras circula.
- Para reglas ciudad/autopista, separar detección OSM y validación de SOC/temperatura de la autorización de cambios de propulsión, con controles del vehículo como fuente definitiva de estados.

**Estado de producto:** Receiver firmado 0.2.43 sigue con cambio manual detenido en P y asesor OSM sin escritura. Esta nota registra evidencia física, no modifica código ni firmware.
