# Receiver 0.2.36 — diagnóstico visible EV→HEV y explorador de luces interiores (solo lectura)

Fecha 11/11/2026 · Jetour T1 PHEV versión argentina · Sender Xiaomi continúa en 0.2.16.

## Incidente reproducible / hipótesis

El usuario pulsó el botón experimental EV→HEV de Receiver 0.2.35 y **no observó cambio ni respuesta visible**. Sin registro no podemos concluir si:
* El botón fue bloqueado **en la interfaz** antes de llamar al servicio porque 1033 no era 1 (P), 2148 no era 0, 1043 no era 1/3, o la muestra tenía >10 s.
* `VehicleService` rechazó el enlace, lectura de permisos o `transact(15)` para 1043.
* La orden se envió sin que la ECU admitiera el modo; 1043 quedó distinto de 2.
* El estado sí cambió, pero el usuario no vio la etiqueta actualizada al instante.

En 0.2.35 `askHevExperiment` deja un mensaje en `feedback`, al final de la página, **no junto al botón**. La inexistencia de un mensaje no prueba ninguna transacción de escritura.

## Cambios

* Resultado visible debajo del botón manual: un `AlertDialog` detalla valores `1033` (P=1), `2148` (velocidad), `1043` (EV/HEV/EV MAX), edad de última muestra y si **NO SE ESCRIBIÓ** por bloqueo de UI.
* **DIAGNOSTICAR · NO ESCRIBE**: el mismo `VehicleService` y getters (5,6), lee 1033/2148/1043, muestra bloqueo o aptitud; **no** ejecuta el setter. Se puede usar primero para decidir si tiene sentido ensayar el botón de escritura.
* **VER / COPIAR INFORME**: registro privado de etapas con fecha/hora, identificador de interfaz, progreso y error de Binder/permiso: `BIND`, `INTERFAZ_OK`, `1043_PRE_1`, `CONTROL_1`, `1043_PRE_2`, `CONTROL_2`, `ANTES_DE_ESCRIBIR`, `TRANSACT_15`, `ESCRITURA_OK`, `VERIFICACIÓN_1043`, `RESULTADO`, `ERROR`. No guarda VIN, GPS, matrícula ni identidades. Se puede copiar desde la T1 al chat. ADB opcional: `adb logcat -d -s DriveLabHevDiag:I '*:S'`.
* Mantiene **exactamente el mismo intento explícito, de un solo comando 1043=2, en P y velocidad cero**, sin automatismo GPS y sin cambio de 1040/1041 o modos de recuperación.
* No reenviar a ciegas. Una respuesta Binder aceptada no demuestra que el tren motriz haya cambiado; se exige lectura posterior de 1043 y comprobación en pantalla original.

## Luces traseras de segunda fila y de portón

El propietario confirma que funcionan físicamente (al menos el plafón trasero y la luz de baúl), pero quiere control manual desde DriveLab y encendido automático al abrir puertas, sin alterar el funcionamiento nativo. **0.2.36 solo investiga señales, NO implementa escrituras de luces.**

### Propiedades candidatas (APK OEM y estándar Android Automotive)

| ID HAL decimal | Nombre | Alcance probable | Confirmación |
| --- | --- | --- | --- |
| 289410817 | `CABIN_LIGHTS_STATE` | Estado de iluminación interior global | Solo constante AOSP encontrada en ALVehicleService/ALCarService; disponibilidad NO probada |
| 289410818 | `CABIN_LIGHTS_SWITCH` | Selector OFF/ON/AUTO de cabina | Permiso privilegiado `CONTROL_CAR_INTERIOR_LIGHTS`; NO escribir sin validar |
| 356519683 | `READING_LIGHTS_STATE` | Luz por área de asiento | Requiere área de asiento distinta de global; no identificada |
| 356519684 | `READING_LIGHTS_SWITCH` | Selector por área de asiento | Puede ser lectura o lectura/escritura, permiso privilegiado; NO escribir |
| 557860128/129 | `CLUSTER DOOR DRIVER/PSNGE` | Estados de puertas delanteras | Catálogo T1Launcher; no probados |
| 557860131/132 | `CLUSTER DOOR RHR/LHR` | Estados puertas traseras | Catálogo T1Launcher; no probados |
| 557860133 | `CLUSTER DOOR TRUNK` | Estado de baúl | Catálogo T1Launcher; no probado |
| 557842982 | `CLUSTER TRUNK DOOR STATUS` | Estado de baúl alternativo | Solo catálogo |
| 557843530 | `VC.ATMOSPHERE LIGHT (lee)` | Iluminación ambiental (NO necesariamente plafón) | Solo catálogo |
| 557843555 | `VC.WELCOME (lee)` | Luces bienvenida, función distinta de plafón | Solo catálogo |
| 557843637 | `VC.WELCOME PASSENGER (lee)` | Bienvenida acompañante | Solo catálogo |
| 557843850 | `EXT.REAR DOOR STS T1N (lee)` | Estado de portón trasero | Solo catálogo |

Fuentes estándares:
* https://developer.android.com/reference/android/car/VehiclePropertyIds (lecturas, acceso READ/READ_WRITE, permiso CONTROL_CAR_INTERIOR_LIGHTS)
* https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/main/car-lib/src/android/car/hardware/property/VehicleLightSwitch.java (OFF 0, ON 1, AUTOMATIC 256)

**No debe confundirse "luz trasera" con luces exteriores de posición/freno/niebla.** No modificar las exteriores desde un botón genérico.

### Captura sin escrituras

En `COCKPIT → ZONAS GPS · EV/HEV` (panel diagnóstico expandido) se añade un bloque **LUCES TRASERAS / BAÚL · SOLO LECTURA**:
1. Estacionar en P y esperar telemetría actual.
2. Con iluminación trasera apagada y portón cerrado, tocar **CAPTURA A · ANTES**.
3. Sin circular, accionar **el interruptor original** de luz trasera, o abrir el portón; tocar **CAPTURA B · DESPUÉS**.
4. Ver tabla de diferencias de valores HAL y los errores de lectura por código. Guardar texto con captura de pantalla. No cerrar portón ni tocar interruptores antes del segundo snapshot.
5. El Binder de ALCarService usa `IALCar`, transacción **2 de lectura**, area 0; **no usa** transacción 15 ni setters. Los códigos por área de asiento pueden fallar con area 0. Los códigos existen en catálogo, no prueban implementación en ROM argentina.

### Próximo paso técnico (NO integrado)
Si alguno cambia al accionar la luz original, investigar la ruta exacta del OEM (permiso, `CarPropertyConfig`, area id para asientos, capacidad read/write) y, solo si el hardware lo permite, diseñar controles manuales + automatización por puertas con temporizador, sin interferir con plafones OEM, seguridad o consumo en reposo. Aún no hay ningún comando seguro de encendido confirmado.

## Batería auxiliar de 12 V y conversor DC-DC — investigación agregada

El usuario observó carga pequeña negativa en `1064` (batería HV), aunque el motor térmico estaba apagado, y sospecha que la batería de 12 V recarga la HV. **Eso NO está demostrado**. En un PHEV lo usual es que el conversor DC-DC alimente el circuito de 12 V desde el paquete HV, no viceversa. Sin lectura de tensión HV simultánea, signo/polaridad confirmados y medición de IBS 12 V, no atribuir −1 A a ninguna transferencia específica.

Nuevas candidatas halladas en `Jetour_T1_Catalogo_HAL_2117_APK.csv` (2.117 etiquetas APK; NO se comprobó su disponibilidad operativa):

| ID HAL decimal | Nombre catálogo | Tipo según nibble de propiedad |
| ---: | --- | --- |
| **559964342** | SETTINGS IBS VOLTAGE (lee) | FLOAT |
| **559940910** | EXT.BATT VOLT (lee) | FLOAT |
| **559940911** | EXT.BATT CURRENT (lee) | FLOAT |
| **557867192** | SETTINGS IBS CURRENT | INT |
| **559964408** | SETTINGS IBS QUIESCENT CURRENT (lee) | FLOAT |
| **557867191** | SETTINGS IBS SOC (lee) | INT |
| **557867252** | SETTINGS IBS STAT SOC (lee) | INT |
| **557867255** | SETTINGS IBS SOH (lee) | INT |
| **557867257** | SETTINGS IBS CURRENT RANGE (lee) | INT |
| **557867264** | SETTINGS IBS BATTERY TEMP (lee) | INT |
| **557856583** | CLUSTER DCDC ENABLE STS | INT |
| **557867051** | SETTINGS DCLC WORKING | INT |
| **557843182** | CLUSTER DCDC SYSFAULT | INT |
| **557843808** | EXT.BATT POWER (lee) | INT |

El nuevo recuadro del cockpit **BATERÍA AUXILIAR 12 V · IBS / DC-DC** ofrece **LEER 12 V**, con resultado y errores específicos por código, y copia del informe. La consulta usa solamente `IALCar.getInt` Binder transacción 2 o `IALCar.getFloat` transacción 9, `area=0`. No implementa escrituras. Requiere estacionar en P con velocidad cero. Hasta contrastar con multímetro externo, muestra **RAW int/float**, no afirma carga, descarga o SOC real.

El catálogo no asegura que `EXT.BATT` pertenezca a 12 V y no a una subfunción de la batería de tracción. Verificar comparando voltajes de rango 12–15 V, corriente y estado DC-DC con equipo real. `IBS` habitualmente significa **Intelligent Battery Sensor** de batería auxiliar y monitorea voltaje, corriente y temperatura, pero eso debe comprobarse en esta T1.

Fuentes metodológicas: [HELLA: Intelligent Battery Sensor](https://www.hella.com/techworld/ae/technical/sensors-and-actuators/intelligent-battery-sensor-%28ibs%29-design-function-and-diagnostics/), [Vicor: sistema HV→12V de HEV/EV](https://edit.vicorpower.com/resource-library/white-papers/distributing-power-hevev-with-dcms).

## Pruebas mínimas
* Verificar que el nuevo botón **DIAGNOSTICAR · NO ESCRIBE** consulta sin modificar EV/HEV; si modo actual HEV, el informe debe decir no apto, **NO ENVIADO**.
* Ver que el botón de ensayo manual muestra `BLOQUEO_EN_UI` explícito cuando falta P/marcha/EV/muestra, en lugar de quedar aparentemente inactivo.
* Confirmar que `VER / COPIAR INFORME` funciona sin ADB, y el tag `DriveLabHevDiag` también en logcat.
* Para luces, probar dos capturas con botón original y verificar códigos cambiados; si todos NO DISPONIBLE, registrar esa limitación, **no probar a ciegas códigos de escritura**.
* Compilación Receiver con certificado original firmado y OTA Sender 0.2.16 sin modificaciones. La prueba física de una escritura efectiva a 1043 continúa pendiente.

