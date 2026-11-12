# Receiver 0.2.41 — resultados de fotografías y correcciones

Fecha: 12 noviembre 2026. Sender 0.2.16 sin cambios.

## Prueba real EV→HEV: no se envió comando

En la fotografía, marcha 1033=1 (P), velocidad 2148=0,00 km/h, fuente 1043=2 (**HEV ya seleccionado**) y última muestra de 150 ms. Son condiciones de estacionamiento adecuadas, pero el botón experimental era solo para EV / EV MAX → HEV. No se intentó enviar la orden porque el vehículo **ya estaba en HEV**. Esto no prueba ni refuta que VehicleService acepte la escritura a 1043.

Cambios 0.2.41:
* Estado visible MODO ACTUAL: HEV/EV/EV MAX junto al botón. Si 1043=2, mostrar "YA ESTÁS EN HEV · NO HIZO FALTA ESCRIBIR" y explicar por qué. Log distingue YA_EN_HEV_SIN_ESCRITURA de error OEM.
* La función de inspección Binder muestra el motivo específico de modo, marcha y velocidad.
* El intento manual existente EV/EV MAX→HEV mantiene única escritura experimental 1043=2, confirmación, doble lectura P y 0 km/h **exactos**, y lectura posterior. El setter no está físicamente validado en la ROM argentina. No se añade HEV→EV ni escritura GPS.

Para investigar sin enviar órdenes: estando parado, usar ENSAYO EV/HEV · SOLO LECTURA (registro 3 minutos) y cambiar modo desde el menú original del vehículo. Para ensayar la escritura manual existente, primero seleccionar EV original y confirmar 1043=1 (o EV MAX 1043=3), luego comprobar P/0 km/h y confirmar una única prueba si se decide hacerla. No repetir un fallo a ciegas.

La forense previa identifica los métodos OEM VehicleSettingCarPropertyManager.setCarPropertyValue y la definición VC_VENDOR_PHEV_POWER_MODE_WRITE, pero no demuestra que una app tercera pueda usar transacción 15 para cambiar propulsión. El catálogo técnico de 2.117 propiedades identifica HAL 557867211 como LECTURA PHEV POWER MODE y HAL 557843642 como ESCRITURA PHEV POWER MODE candidata. Por eso la función DIAGNOSTICAR · NO ESCRIBE de esta versión lee también 557867211 mediante IALCar.getInt (Binder transact 2, area 0) y la compara con VehicleService 1043. Si coinciden al cambiar EV/HEV desde el menú original, se confirmará una correlación entre dos interfaces. La propiedad de escritura 557843642 NO se ejecuta; falta validar permisos, tipos, valores, efectos y condiciones antes de cualquier ensayo.

## Temperatura del motor

La foto muestra CLUSTER ENGINE TEMP 559940105 = 56.25 (float); el propietario vio subir el valor al calentarse. El Receiver 0.2.40 ya priorizaba esa señal, etiquetada RAW experimental sin afirmar °C. ENGINE_COOLANT_TEMP AOSP 291504897=0, ENGINE_OIL_TEMP 291504900=0, CLUSTER BATT TEMP 557860127=0 y ENGINE RPM AOSP 291504901=0 no son mediciones válidas en esta T1. RPM térmicas del instrumento continúan desde 1035. Estado y RPM eléctricos siguen sin código validado.

## Batería 12 V y alta tensión

Foto: IBS VOLTAGE 559964342=0.0, IBS SOC=0 y demás IBS/DC-DC=0: no se ha demostrado lectura de 12 V. EXT.BATT VOLT 559940910=315.0; si la unidad es V corresponde a **circuito de alta tensión HV candidato**, no a 12 V. EXT.BATT CURRENT 559940911=-1.2 no debe atribuirse a batería auxiliar. El 1064 está relacionado con corriente HV, pero escala/polaridad cerca de cero también son hipótesis. 0.2.41 avisa y separa lecturas no validadas de mediciones reales.

## Otros problemas de UI

La foto antigua del Receiver mostraba versión 0.2.31 y ofrecía descargar 0.2.36; no refleja la última versión. También mostraba un diálogo de actualización de bajo contraste, con secuencia literal de barra-n en el mensaje. Esta revisión:
* muestra desde PackageManager la versión **realmente instalada** en la pantalla principal, sin banner fijo obsoleto;
* usa texto negro sobre fondo claro y botones oscuros en el aviso del updater;
* convierte la barra-n literal en un salto de línea real.

Conserva viajes, pantalla completa y franja libre Hola Jetour, lecturas de temperaturas, LEDs delanteros P/N y otros diagnósticos. No añade escrituras de luces ni modifica alarmas de cinturones.
