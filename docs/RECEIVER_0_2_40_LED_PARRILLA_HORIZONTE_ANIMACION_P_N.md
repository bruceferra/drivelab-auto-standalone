# Receiver 0.2.40 — LED blanco de parrilla «horizonte», animación en P

Fecha: 12/11/2026 · Jetour T1 PHEV argentina · Sender 0.2.16 sin cambios.

## Qué observó el propietario

En la parrilla frontal hay **tiras LED blancas** que realizan una animación repetitiva al estacionar en **P**, mientras que en **N** quedan aparentemente fijas. El efecto es molesto y se busca conservar el LED encendido **pero fijo en P**, sin modificar luces de seguridad, DRL ni marcha.

La prueba de prensa del Shanhai T1 identifica el conjunto como `地平线灯带` (*horizon LED light strip*), que tiene ritmo de pulsación al ralentí/detenido y efecto de bienvenida en desbloqueo. No es por sí mismo señal de avería:
https://auto.sina.cn/newcar/2024-10-15/detail-incsrkfx0890280.d.html

## Análisis estático del APK T1Launcher T1N134 (2.117 IDs HAL)

Las siguientes etiquetas se encuentran en `Jetour_T1_Catalogo_HAL_2117_APK.csv`, originado del APK suministrado por el propietario. El catálogo refleja **nombres, NO compatibilidad, valores, interfaz o permisos probados**.

| Código decimal | Etiqueta en catálogo | Acceso inferido |
| ---: | --- | --- |
| 557843630 | `SCENE VENDOR LIGHT SHOW` | LECTURA candidata |
| 557843627 | `SCENE VENDOR SHOW MODE` | ESCRITURA candidata. **NO USAR SIN VALIDACIÓN** |
| 557843797 | `SCENE VENDOR SHOW MODE T1P` | ESCRITURA candidata. **NO USAR SIN VALIDACIÓN** |
| 557867037 | `SETTINGS WELCOME LIGHT SHOW MODE` | ESCRITURA candidata. **NO USAR SIN VALIDACIÓN** |
| 557843784 | `EXT.LAMP LED` | LECTURA candidata |
| 557843785 | `EXT.LAMP LED` | ESCRITURA candidata. **NO USAR SIN VALIDACIÓN** |
| 557844233 | `VC.DRL STS` | LECTURA candidata |
| 557856517 | `CLUSTER DRL STS` | LECTURA candidata |
| 557867035 | `SETTINGS WELCOME LIGHT POLLING WORKING` | LECTURA candidata |
| 557879316 | `SETTINGS WELCOME LIGHT POLLING WORKING BY QNX` | LECTURA candidata |
| 557867047 | `SETTINGS SPECIAL PARKING WORKING` | LECTURA candidata |
| 557879382 | `SETTINGS SPECIAL PARKING WORKING BY QNX` | LECTURA candidata |
| 557843555 | `VC.WELCOME` | LECTURA candidata |
| 557843845 | `EXT.LIGHT CONTROL` | ESCRITURA candidata. **NO USAR SIN VALIDACIÓN** |

Que un código se llame `SHOW MODE` **no prueba** que controle las tiras LED, que tenga un estado «fijo», ni que pueda cambiarse con seguridad desde un APK sin privilegios. En particular, `EXT.LAMP LED` podría afectar luces exteriores reglamentarias, no solamente la animación estética. No probar escrituras de códigos a ciegas.

## Prueba integrada (sin comandos de luces)

En **COCKPIT T1 → ZONAS GPS · EV/HEV (desplegar) → PARRILLA LED DELANTERA · EFECTO EN P**:

* Botón **REGISTRAR LED 30 S · NO ESCRIBE**: inicia solo si el grabador ve `P=1`, velocidad `0 km/h` y telemetría fresca; registra hasta 16 capturas sucesivas, con 1033 (marcha) y estado de 16 propiedades OEM del módulo ALCarService.
* Si el propietario, **estacionado con freno de estacionamiento y pedal de freno accionado**, puede reproducir el paso P → N → P con seguridad, el registro lo capturará **sin que tenga que tocar la pantalla en N**. No se recomienda realizar la transición si hay pendiente, tráfico, personas alrededor o dudas sobre el procedimiento; en tal caso dejarlo siempre en P y registrar solo el efecto.
* En cada muestra verifica velocidad OEM legible y ≤ 0,5 km/h. Si detecta velocidad superior, suspende la captura. No escribe marcha, no interviene iluminación y no monitoriza ni interfiere con la ECU de frenos.
* Botón **COPIAR INFORME LED**: incluye códigos crudos, cambios observados, errores y secuencia de marchas, para validar qué propiedad de estado se modifica al pasar de animado a fijo.
* `T1HorizonLedReadOnlyProbe.java` usa `IALCar.getInt` `transact(2)` con ID fijo, `area=0`; **no existe ninguna transacción de escritura** en este probe.
* Si todos los estados son constantes, revisar si la animación la gobierna directamente el BCM con señales CAN o un servicio QNX, y si hay un ajuste nativo de «luces de bienvenida», «light show», «exterior lighting», «animación de estacionamiento».

La prensa y manuales de otras marcas ofrecen ejemplos de animación configurable, pero **no confirman que Jetour T1 argentina disponga de tal menú o del estado fijo en P**.

## Corrección térmica confirmada por prueba del propietario

Con motor térmico encendido y calentándose, el propietario observó que la mayoría de candidatos del panel térmico devolvieron **0 constante**, mientras **559940105 (CLUSTER ENGINE TEMP)** fue el único valor que **aumentó progresivamente**. Eso respalda su correlación con la temperatura del motor, pero **no demuestra aún si está expresado en °C, décimas, un índice de barras o una escala propia**.

A partir de esta revisión el cockpit prioriza automáticamente **559940105** para la tarjeta **TEMPERATURA MOTOR · 559940105 · EXP.**, etiquetada `RAW (exp.)`, y ya no muestra 0 °C ficticios de `ENGINE_COOLANT_TEMP=291504897` cuando esa propiedad está sin datos. Se mantiene el AOSP como lectura secundaria únicamente si ofrece una temperatura positiva plausible y el cluster alternativo no informa nada. `ENGINE_OIL_TEMP=291504900` y `CLUSTER BATT TEMP=557860127` constantes a 0 se muestran como **SIN LECTURA**, no como temperatura válida. Las revoluciones del motor térmico en el instrumento siguen leyendo el **1035 validado**; los 9 dígitos `ENGINE_RPM=291504901` devolvieron 0 y no sustituyen al código útil.

**Prueba de calibración pendiente:** dos lecturas crudas de 559940105 con motor frío/caliente y comparación contra la escala de temperatura del cuadro OEM; hasta entonces no indicar °C de forma concluyente.

## Limitación, seguridad y próximos pasos

Todavía **no existe** una opción DriveLab para dejar fijas las tiras en P. Esta versión busca primero demostrar qué señal cambia, manteniendo los faros normales y los DRL sin intervención.

Si surge un estado específico de espectáculo y el OEM implementa un setter independiente, requerirá una nueva validación de tipos, área, controles de acceso, lectura posterior y efecto físico antes de ofrecer un botón manual. De lo contrario, la alternativa segura sería usar una configuración oficial de carrocería si está expuesta en la ROM.

Conserva la función de pantalla completa con espacio libre «Hola Jetour», TRIP, EV/HEV, luces interiores, motor y batería, cinturones e inspección 12 V; Sender 0.2.16 inalterado.
