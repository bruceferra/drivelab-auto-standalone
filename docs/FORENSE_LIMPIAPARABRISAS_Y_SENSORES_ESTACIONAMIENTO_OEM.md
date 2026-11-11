# Jetour T1 — señales OEM de limpiaparabrisas y sensores de estacionamiento

**Fecha de inspección:** 10–11 noviembre 2026. APK originales exportados por WebADB desde la T1: `ALVehicleService.apk`, `ALCarService.apk`, `ClusterInteraction.apk`. Inspección estática, sin acciones sobre el vehículo.

## Limpiaparabrisas

En `ALVehicleServiceclasses.dex` constan:

- `KeyVehiclePropertyWiperSensitivity` y `MapperVehicleWiperSensitivity`, `CarWiperSensitivityMode` (Low/Middle/High/VeryHigh). Sensibilidad de limpiaparabrisas automático.
- `KeyVehiclePropertyRearWiper`, `MapperVehicleRearWiper`. Señal de limpiaparabrisas trasero.
- `KeyVehiclePropertyWiperMaintenanceSts`, `MapperVehicleWiperMaintenanceSts`; etiqueta china `雨刮维修模式开关` (interruptor de modo mantenimiento de limpiaparabrisas).

En `ALCarServiceclasses.dex` aparecen símbolos HAL genéricos `HAL_PROPID_WIPER_MODE_READ`, `HAL_PROPID_WIPER_MODE_WRITE`, `HAL_PROPID_WIPER_SEN_WRITE`, `HAL_PROPID_VC_VENDOR_REAR_WIPER_READ`, `HAL_PROPID_VC_VENDOR_REAR_WIPER_WRITE`, `SETTINGS_WIPER_SENSITIVITY`, `SETTINGS_WIPER_STS_READ` y `SETTINGS_RAIN_DETECTED_READ`.

**Cautela:** que existan etiquetas/readers o setters no demuestra permisos de acceso ni funcionamiento real en el hardware argentino. No implementar escritura de estos comandos sin identificar tipos, escalas, estados y seguridad.

## Sensores de estacionamiento y radar

En `ALVehicleServiceclasses.dex` constan `KeyVehiclePropertyPas` y dos etiquetas chinas:

- `驻车雷达开关`: interruptor de radar de estacionamiento
- `驻车雷达报警距离分段`: segmentación de distancia de aviso del radar de estacionamiento

También `getReverseRadarConfiguration` y propiedades `persist.vendor.oem.cfg.cc.ihu.reverse.radar.configuration` (configuración de vehículo, no distancias observadas).

En `ALCarServiceclasses.dex` hay identificadores HAL genéricos:

- `CLUSTER_FRONT_PDC_OBJECT_DISTANCE`, `CLUSTER_REAR_PDC_OBJECT_DISTANCE`
- `CLUSTER_LHF_RADAR_DISTANCE`, `CLUSTER_LHMF_RADAR_DISTANCE`, `CLUSTER_LHMR_RADAR_DISTANCE`, `CLUSTER_LHR_RADAR_DISTANCE`
- `CLUSTER_RHF_RADAR_DISTANCE`, `CLUSTER_RHMF_RADAR_DIATANCE`, `CLUSTER_RHMR_RADAR_DIATANCE`, `CLUSTER_RHR_RADAR_DISTANCE`
- `CLUSTER_RADAR_DETECT`, `CLUSTER_RADAR_WAOK_STS`, `HAL_PROPID_AVM_RADAR_RATE_READ`

En `ClusterInteractionclasses.dex` hay una API de **estado de advertencia**: `IRadarWarningStatusCallback`, `registerRadarWarningStatusCallback`, `onRadarWarningStatusChanged` y configuración de audio `RadarWarningSoundSwitch`.

La presencia de nombres de distancias no prueba que la Jetour T1 exponga medidas métricas en vivo; algunos símbolos vienen de catálogos HAL compartidos. No se conocen los **IDs de lectura ni escala validados en esta unidad**.

## Próxima prueba segura

1. Identificar la ruta exacta HAL/Binder, tipo y unidad de cada lectura, y confirmar que es read-only.
2. Comparar valores en parado, con el sistema original activado, frente a la visualización OEM de distancia/alerta.
3. No utilizar comandos de escritura de limpias, radar, buzzer ni PAS mientras falte validación.
4. No incorporar todavía a la app productiva controles de seguridad ni afirmaciones de distancia exacta.

**Estado:** hallazgos de ingeniería inversa documentados, no una nueva funcionalidad de la APK 0.2.22. Esta última agrega solamente telemetría de consumo 1084–1087 y RPM válidas 1034 al TRIP.
