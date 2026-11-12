# Receiver 0.2.39 — luces por asiento, temperaturas del tren motriz, motor eléctrico

Fecha 12/11/2026 (Buenos Aires). El Receiver mantiene Sender v0.2.16, TRIP, grabaciones, fullscreen y franja superior "Hola Jetour" ajustable de 0.2.38.

## Por qué este cambio

* Al probar el escáner 0.2.36, 14 propiedades HAL de luces leídas con `areaId=0` fueron legibles pero **ninguna cambió** al accionar las luces traseras. Las propiedades AOSP `READING_LIGHTS_STATE=356519683` y `READING_LIGHTS_SWITCH=356519684` tienen área `SEAT`, no necesariamente global. Una lectura del área 0 es insuficiente para descartar el control de las luces.
* El usuario pidió incorporar al cockpit **temperatura del motor térmico, temperatura de batería de tracción, RPM térmicas y estado ON/OFF/RPM del motor eléctrico**, omitidos en el anterior Receiver. El motor térmico `1035` ya mostraba RPM, pero no las temperaturas y aún no existe código RPM de motor eléctrico confirmado.

## Implementación de luces — NO controla encendido

Nuevo `T1RearSeatLightsProbe.java` con botón de comparación:
* `READING_LIGHTS_STATE=356519683`; `READING_LIGHTS_SWITCH=356519684`.
* `areaId` **0** (control global), **16** (segunda fila izquierda), **32** (segunda fila centro), **64** (segunda fila derecha). La ROM puede usar áreas combinadas/distintas; este escáner no afirma soporte.
* Consultas `IALCar.getInt(id,areaId)` transacción Binder **2**, sin setters y sin `WRITE`. Informa cada valor bruto, errores OEM y diferencias entre snapshots.

Ruta: **COCKPIT T1 → ZONAS GPS · EV/HEV (sección expandida) → LUCES DE LECTURA · SEGUNDA FILA POR ASIENTO**.

Prueba con auto inmóvil en P:
1. Apagar las luces de la segunda fila con el botón original. Tocar **ASIENTOS A · APAGADAS**.
2. Encender la luz trasera con botón original, dejarla encendida. Tocar **ASIENTOS B · ENCENDIDAS**.
3. Leer las diferencias en la pantalla y **COPIAR INFORME ASIENTOS**. Pegar el resultado en ChatGPT.
4. Repetir según convenga para izquierda, derecha o centro sin escribir comandos.

Las pruebas son **solo lectura**. El resultado puede ser todo 0 si la HAL expone propiedades AOSP sin estar cableadas al BCM; en ese caso investigar `CarPropertyConfig.areaIds`/permisos o APK de carrocería específico. No activar una salida BCM desconocida.

## Temperatura del motor y batería

`T1ThermalMotorProbe.java` consulta `ALCarService` por código y tipo correcto a intervalo de aproximadamente 12 segundos cuando el cockpit está abierto, con botón **TEMPERATURAS / MOTOR · VER CÓDIGOS** para reporte completo, incluyendo resultados negativos y errores. Es de **solo lectura**.

Códigos inventariados en APK T1Launcher T1N134:

| ID HAL | Etiqueta | Tratamiento |
| ---: | --- | --- |
| **291504897** | ENGINE_COOLANT_TEMP | Float estándar Android Automotive **°C**, si hay lectura válida |
| **291504900** | ENGINE_OIL_TEMP | Float estándar **°C**, solo en diagnóstico |
| **559940105** | CLUSTER ENGINE TEMP | Float crudo alternativo; escala sin verificar |
| **559957277** | CLUSTER ENGINE TEMP VAL | Float crudo alternativo; escala sin verificar |
| **557860126** | CLUSTER ENGINE TEMP STS | Int crudo (estado, no grados) |
| **557860127** | CLUSTER BATT TEMP | Int crudo; **NO se afirma que sea °C** |
| **557860159** | CLUSTER BATT TEMP STS | Int crudo, estado |
| **557860106** | CLUSTER BAT TEMP HIGH | Int crudo, alarma |
| **291504901** | ENGINE RPM | Float estándar; alternativa al ya operativo 1035 |
| **557860187** | CLUSTER ENGINE SPEED | Int crudo (motor térmico, NO eléctrico) |
| **557867145** | PHEV ENERGY FLOW | Int crudo, **NO identifica por sí solo RPM ni ON/OFF motor eléctrico** |

La temperatura batería se presenta como valor bruto hasta compararla con el OEM, porque podría tener offset/escala o ser indicador distinto. El sensor de refrigerante puede requerir permiso firma/privilegiado `CAR_ENGINE_DETAILED`, por lo que es posible que aparezca `SIN LECTURA`.

### Motor eléctrico: límite actual

Se añadió un **bloque visible** "MOTOR ELÉCTRICO", con **ESTADO SIN VALIDAR / RPM SIN LECTURA**. No se ha encontrado aún un código en los 2.117 del catálogo que inequívocamente sea velocidad de rotación del motor eléctrico. El auto puede circular en EV sin térmico; eso **no prueba** que un determinado motor eléctrico esté girando (podría haber carga o regeneración). Tampoco se pueden usar `557860187` o `291504901` como RPM eléctricas: son códigos del motor térmico.

Para llegar al encendido/apagado y RPM reales del motor eléctrico necesitamos extraer información de un módulo/servicio OEM de propulsión específico o el diagrama de señales CAN, y verificar lectura directa. Un registro **sin dato** es deliberado: no se simulan RPM ni estado.

## Cinturones traseros y volumen de alertas (nuevo explorador)

Se incorporó `T1RearSeatBeltProbe.java` con consultas `getInt` **solo lectura** al ALCarService, con el vehículo en P, y capturas `CINTURONES A · SUELTOS` / `CINTURONES B · ABROCHADO` para cada asiento posterior. En el cockpit expandido se puede **COPIAR INFORME CINTURONES**.

Señales candidatas extraídas del mismo catálogo APK:
- `557856516` CLUSTER SEC LEFT SEAT BELT.
- `557856533` CLUSTER SEC MID SEAT BELT.
- `557856534` CLUSTER SEC RIGHT SEAT BELT.
- `354421634` SEAT BELT BUCKLED con `area=16,32,64` (lecturas por asiento).
- `356518832` SEAT OCCUPANCY con `area=16,32,64`.
- `557843384` CLUSTER MID SEAT WARN, aviso asiento central.
- **`557847073`** STAT AMP ALARM VOL, estado de volumen del amplificador.
- **`557862921`** CLUSTER AUDIBLE BEEP RATE, ritmo/frecuencia de avisos.
- `557867199` SETTINGS VOICE REMINDER, recordatorios de voz distintos y sin vinculación demostrada con cinturones.

El catálogo también contiene `557847072` **SET AMP ALARM VOL** (escribe), pero **NO se llama ni se propone su escritura**: podría afectar alarmas de ABS, colisión, puertas u otros avisos de seguridad, y no hay vínculo probado con la advertencia de cinturón. Los getter se mostrarán como valores crudos/errores. Una lectura sin cambios no excluye fuente por otra ECU. La versión **no** anula, silencia ni deshabilita el recordatorio.

Para el problema reportado de cajas sobre los asientos: probar con los objetos fijados en el baúl y, si hay una preferencia oficial de volumen/tono en la configuración *Audio/Sonido* de la ROM, verificar su alcance antes de usarla; no colocar falsas hebillas ni puentear sensores SRS. La alarma puede depender de lógica de ocupación, del estado de hebilla o de otro ECU.

## Persistencia y seguridad

* No hay cambios en los CSV existentes, historial de clientes, cargas eléctricas, garantía, ROOT ni OEM UI.
* No se envían comandos nuevos de luces ni motor.
* Mantiene controles manuales EV/HEV experimentales ya presentes sin cambiar las precondiciones.
* El escáner de asientos y el térmico solo leen códigos fijos, sin aceptar ID arbitrario desde UI y sin usar Binder setter.
* Incluye regresión estática de áreas/códigos y compilación Android. APK firmado con la clave del Receiver existente y Sender 0.2.16 sin modificar.

Referencia AOSP: https://developer.android.com/reference/android/car/VehiclePropertyIds y https://android.googlesource.com/platform/packages/services/Car/+/master/car-lib/src/android/car/VehiclePropertyIds.java
