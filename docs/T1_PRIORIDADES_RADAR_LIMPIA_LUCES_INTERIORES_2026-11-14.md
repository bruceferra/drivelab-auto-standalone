# Jetour T1 Argentina — Prioridades de carrocería: radar, limpia y plafones

Fecha: 2026-11-14 · DriveLab Auto Receiver · trabajo experimental de diagnóstico

## Alcance de esta rama

El objetivo es acelerar la validación de **tres problemas reales** sin reprogramar ECU, BCM, radar, limpiaparabrisas, luces obligatorias ni actuadores. Se preservan los interruptores originales y el código de lectura OEM existente. **No es una calibración ni una corrección funcional todavía.**

Menú VEHÍCULO: cuatro accesos directos a PENDIENTES, con capturas A/B independientes y persistencia de la base por 30 minutos:

1. `pdc_front_distance`: comparar PDC delantero y 4 zonas delanteras.
2. `pdc_rear_distance`: comparar PDC trasero y 4 zonas traseras.
3. `wiper_auto`: comparar sensibilidad OEM 2126 y modo HAL 557843863.
4. `lights_tailgate`: comparar señales de luz de cabina/portón antes y después de apertura original.

El antiguo ensayo `pdc` de conmutación OEM ON/OFF se conserva con su ID y resultados ya guardados; no se borra ningún histórico. Lo mismo aplica a `lights_cabin`, `lights_seats`, `wiper_manual` y `wiper_rear`.

## 1. Radar/PDC

Lo documentado hasta ahora:

- `2049`: cambió de 0 a 1 al activar el **radar general OEM**. No desactivarlo, porque puede afectar delantero y trasero.
- `2107`: candidato de segmentación de aviso; fue 0 tanto con radar OFF como ON. **No es un umbral probado**, y no se escribe.
- `557862926/557862927`: PDC delantero/trasero global; valores crudos.
- `557843300..303`: cuatro zonas delanteras.
- `557843296..299`: cuatro zonas traseras.
- En pruebas anteriores se observó un valor delantero 175 → 50 al aproximar un objeto. **No está probado que signifique centímetros**.
- Valores traseros 62 (apagada) / 335 (encendida) aparecieron iguales en cuatro zonas; podrían representar centinelas o estados de reposo.
- Un pitido en D por un vehículo próximo detrás debe investigarse **sin perder detección delantera ni aviso trasero en R**.

### Prueba segura de lectura

Auto inmóvil, en P, freno aplicado, zona despejada y radar OEM habilitado:

- Captura A sin obstáculo.
- Captura B con **objeto inerte**, colocado con el vehículo completamente inmovilizado; nadie delante ni detrás de la T1 al realizar la lectura.
- No desplazar el vehículo ni usar personas como blanco.
- Registrar por separado: ID, A, B, distancia física medida en el suelo por personal fuera del trayecto, estado del botón OEM y presencia/ausencia de pitido.
- Si en P los sensores no entregan valores, reportar SIN SEÑAL en lugar de probar con desplazamientos.

La app solo muestra valores RAW. No asume centímetros, ni modifica umbral, tono, volumen o módulo PDC.

### Requisito para una futura corrección

Encontrar en el HMI/servicio OEM oficial un ajuste específicamente autorizado que discrimine radar trasero en D frente a delantero y radar trasero en R; validar estado, permiso, rango y lectura posterior. Si solo existe mando global, **no aplicarlo** como solución al pitido.

## 2. Limpiaparabrisas

- `2126` sensibilidad AUTO: en pruebas previas devolvió `-1`.
- `557843863` modo de limpia delantero (HAL, lectura candidata).
- `557843559` limpia trasero (HAL, lectura candidata).
- `557843861/862` y `557843558` figuran como setters candidatos en catálogo, **excluidos** del lector.
- Cambiar únicamente un paso original de sensibilidad AUTO entre A y B, con auto parado.
- Diferenciar sensibilidad de sensor de lluvia, intermitencia y velocidad de motor. No inferir que un ajuste pueda alterar todos ellos.

Un valor diferente demuestra capacidad observacional; no demuestra permiso de escritura ni calibración de motor. Si todo queda constante, investigar Activity del HMI OEM y permisos o BCM.

## 3. Plafón trasero / luz del portón

Prueba `lights_tailgate`: captura A con portón cerrado y luz original apagada si es posible; captura B después de abrir **solo el portón**. Comparar sin escritura: señales candidatas de CABIN/READING LIGHTS, estado de puertas y portón. La lámpara y el sensor de apertura son salidas diferentes.

La prueba por asiento `lights_seats` sigue disponible (áreas 0, 16, 32, 64). Las 14 lecturas globales de la prueba 0.2.36 anterior no cambiaron al accionar la luz; no debe reportarse como solución ya encontrada.

## Limitaciones y validación antes de publicar APK

- Solo el asistente y los accesos directos cambian; `WiperParkingProbe` sigue con las lecturas permitidas de VehicleService/IALCar y **sin setters**.
- Ninguna función nueva manipula marcha, radar, limpiaparabrisas, luces, alarmas ni datos CAN.
- Contrastar A/B con los mandos físicos originales y guardar/compartir el informe de cada prueba.
- Compilar y verificar Android CI antes de publicar un Receiver firmado.
- No actualizar el manifiesto OTA ni versionCode hasta pasar CI y pruebas.
