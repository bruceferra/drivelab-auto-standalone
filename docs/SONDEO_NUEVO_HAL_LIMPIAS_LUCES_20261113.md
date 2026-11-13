# Jetour T1 Argentina — Segunda ruta HAL de carrocería (sin root, solo lectura)

Fecha: 2026-11-13. Resultado de las 18 pruebas iniciales: mayormente constante / sin señal. La lectura `VehicleService` 2126 (AUTO) devolvió -1, 2125 (coordinación trasera) 0. Esto **no descarta** un control porque pueden ser propiedades de configuración y no la señal del mando o el motor.

## Nuevos hallazgos en catálogo conservado del APK T1Launcher T1N134

| Propiedad | ID | Acceso **inferido por nombre** | Uso propuesto |
|---|---:|---|---|
| VC.WIPER MODE (lee) | 557843863 | LECTURA candidata | Comparar con mando de barrido delantero |
| VC.WIPER MODE (escribe) | 557843862 | ESCRITURA candidata | **No ejecutar** sin conocer contrato/permiso |
| VC.REAR WIPER (lee) | 557843559 | LECTURA candidata | Comparar trasero OFF/ON |
| VC.REAR WIPER (escribe) | 557843558 | ESCRITURA candidata | **No ejecutar** |
| VC.WIPER SEN (escribe) | 557843861 | ESCRITURA candidata | **No ejecutar** |
| HEADLIGHTS STATE | 289410560 | ESTADO genérico | Faros, no es un sensor de luces de freno |
| HIGH BEAM LIGHTS STATE | 289410561 | ESTADO genérico | Luces altas |
| FOG LIGHTS STATE | 289410562 | ESTADO genérico | Antiniebla |
| HAZARD LIGHTS STATE | 289410563 | ESTADO genérico | Balizas |
| CABIN LIGHTS STATE | 289410817 | ESTADO genérico | Luz de habitáculo (no por asiento) |
| VC.DRL STS (lee) | 557844233 | LECTURA candidata | Luces diurnas |
| CLUSTER DRL STS | 557856517 | LECTURA candidata | Estado DRL de tablero |

Fuente de nombres: `Jetour_T1_Catalogo_HAL_2117_APK.csv`, originado en APK T1Launcher-T1N134 y almacenado en Library. **Catálogo no significa ECU real, permiso de lectura ni setter operativo.**

Los tres APK oficiales originales `ALVehicleService.apk`, `ALCarService.apk` y `ClusterInteraction.apk` ya demuestran que existen varias capas: `IVehicleControl` en `VehicleService`, `IALCar` en `ALCarService`, además de algunas callbacks del clúster. El comando del mando OEM puede provenir de otra aplicación de interfaz del vehículo, de QNX o directamente del BCM/CAN. No se ha identificado aún su originador.

## Implementación nueva

Ampliado `WiperParkingProbe.java` para consultar los nueve IDs HAL anteriores que son de **lectura** o **estado**, a través del mismo enlace existente y validado a `com.autolink.carservice.ALCarService`, descriptor `com.autolink.adapterinterface.car.IALCar` y `getIntProperty(id,0)` transacción **2**.

- Los códigos de **escritura 557843862, 557843558, 557843861 no entran en la whitelist**.
- `VehicleService` mantiene únicamente `readInt(id)` Binder **5** para 2126/2125/2107/2049.
- No se agregan permisos privilegiados, métodos setter, código de CAN, root ni acciones automáticas. La consulta se realiza al pulsar, en hilo separado, y muestra valores crudos o error.
- `T1ValidationWizardActivity` actualiza los pasos ya existentes del limpiaparabrisas para comparar ambos caminos; agrega el paso número 19 de faros/DRL. **No borra resultados de los 18 pasos anteriores.** La base A del comparador persiste 30 minutos como antes.

## Cómo probar en Jetour

1. Estacionar en P, velocidad cero, lugar seguro y limpio de obstáculos. Abrir **PENDIENTES**.
2. **Limpia delantero · modo HAL nuevo**: CAPTURA A con mando apagado. Elegir desde el mando **original** una velocidad manual, CAPTURA B, devolver a apagado. Ver si `557843863` cambia o devuelve error.
3. **Limpia trasero**: CAPTURA A con mando original OFF. Activar el mando OEM, CAPTURA B, volver a OFF. Ver `557843559` junto a 2125.
4. **NUEVO · Estado de faros y DRL HAL**: CAPTURA A con configuración original, CAPTURA B después de cambiar exclusivamente una opción legítima en pantalla/mando OEM. Ver `289410560` y `557844233/557856517`. No inferir que estos IDs controlan luces traseras.
5. Compartir los tres informes, incluso con códigos `-1` o `SIN LECTURA (SecurityException)`. Si no hay cambios, investigar la APK de **Ajustes/Control del Vehículo**: identificar la Activity visible y su llamada exacta a Binder/Intent/QNX, no ensayar setters por nombres.

## Otras vías, en orden

1. **Encontrar el APK de la interfaz original (HMI)**: al tocar un ajuste OEM, mirar `adb shell dumpsys activity activities` para saber cuál es la Activity en primer plano; obtener su package/path con `pm path`, extraer una copia mediante `adb pull`, examinar handlers de botones y el contrato exacto de servicios OEM. Trabajar off-line.
2. **Capturar el comportamiento nativo con ADB**: `adb logcat -v time` mientras se conmuta un único control en P; contrastar filtrado por `ALCarService`, `VehicleService`, `CarService`, `CarProperty`, `Wiper`, `Light`. El log puede estar restringido/limpio en release y no contener Binder.
3. **Verificar permisos/configuración**: `CarPropertyManager.getPropertyList()`, flags READ/WRITE, áreas y permiso de lectura/escritura de las propiedades compatibles. El catálogo del APK no sustituye esta comprobación; permisos Automotive `signature|privileged` generalmente impiden escrituras de terceros.
4. **Integración por UI OEM oficial**: si el firmware ofrece una actividad/Intent exportado con permiso permitido, invocarlo desde un botón de DriveLab. Alternativa frágil: un `AccessibilityService` habilitado expresamente por el usuario, que opere las opciones originales y confirme visualmente el resultado. Nunca inyectar toques ciegos en circulación ni ocultar la UI de seguridad.
5. **Bus de carrocería/CAN en modo pasivo** solamente para correlacionar eventos; no inyectar ni repetir frames sin mapeo, protecciones y validación especializada.

## Restricciones de seguridad

Las luces reglamentarias exteriores, limpia delantero y asistencias de seguridad no deben desactivarse ni alterarse durante la conducción para pruebas. No confundir luz ambiental, iluminación de lectura, faros/DRL y luces de freno o posición. El diagnóstico nuevo NO controla nada. Si la ruta real está gobernada por el BCM/QNX y está bloqueada a terceros, no afirmaremos que el acceso sea posible sin software privilegiado/homologado.
