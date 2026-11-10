# Receiver 0.2.18 — grabación de marcha con cámara y ensayo de plegado OEM 2003

**Fecha:** 10/11/2026. **Estado:** implementación experimental, sin validación física nueva hasta prueba del propietario.

## Evidencia física recibida

- El propietario leyó códigos de marcha: **P=1, N=3, D=4**. Al introducir marcha atrás, la cámara OEM toma la pantalla y le impide consultar a tiempo el lector; **R=2 es hipótesis del APK T1N134, NO confirmación física aún**.
- El propietario observó que **ID 2003 transiciona 0→1 al desplegar espejos y 1→0 al plegar**. Clasificación **ESTADO OBSERVADO**: `0=plegado`, `1=desplegado`. Todavía **no se ha demostrado** que `VehicleLink.setIntData(2003,0/1)` efectúe físicamente el plegado/desplegado en esta versión argentina. No confundir con `2068/2069`, cuyas órdenes de movimiento se liberan con 0; ¡`2003=0` es el candidato de PLEGAR, no una liberación!
- Previo: método estándar Android Automotive `MIRROR_FOLD 287312709` devolvió permiso OEM privilegiado denegado. El nuevo ensayo **no** solicita ese permiso: usa únicamente el Binder `VehicleService` utilizado para controles físicos ya comprobados, con ID 2003 y sin root. Si OEM rechaza escritura, informar, no forzar.

## Cambios en el código v0.2.18

1. `GearCaptureService`: servicio temporal de primer plano **solo lectura de 1033**, iniciado manualmente por botón **GRABAR MARCHAS 35 S**. Lee 4 veces por segundo, guarda un evento solo cuando cambia el valor crudo y termina automáticamente tras 35 segundos. Mientras aparece la cámara de reversa continúa intentando capturar; al volver a P se puede reabrir VEHÍCULO → **VER RESULTADO GRABADO**. También hay **DETENER REGISTRO**. No registra audio, GPS ni video y **no manda órdenes de espejos**.
2. `MirrorPulsePilot.readFold2003`: lectura puntual de `getInt(2003)`, tras verificar OEM **P/velocidad cero**. Resultado junto al botón, sin desplazar scroll.
3. `MirrorPulsePilot.testFold2003`: botón separado **PROBAR PLEGAR 2003=0** y **PROBAR DESPLEGAR 2003=1**, sin ventana de confirmación repetitiva. Verifica primero estado 0/1, velocidad cero y P, y omite escritura si ya está en objetivo; si no, envía **una sola** `setIntData(2003,0/1)`, sin mandar 0 al finalizar una orden de despliegue. Lee de nuevo luego y compara; **lectura coherente NO equivale a desplazamiento real confirmado**.
4. No se conecta la lógica futura `MirrorParkingPolicy` de R/no-R a motores. El bloqueo de movimiento manual de espejo 2068/2069 fuera de P sigue activo. Sin cambios de Sender, Waze, puente de navegación, viajes, controles de ventanas/AVAS, tablero de combustible y prueba de segundo tanque.
5. Release 0.2.18/code19 prevista para actualizarse mediante **actualizador incorporado** de Receiver, firmada con el mismo certificado de 0.2.17. `sender.json` debe continuar en versión 0.2.13.

## Publicación OTA comprobada

La release [Receiver v0.2.18/latest](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.18) se publicó el 10/11/2026: **code19**, `receiver.json`, APK firmada y `sender.json` conservado. [QA Android, run 42854161716](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42854161716) y [publicación OTA, run 42854161957](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42854161957) terminaron con **éxito**. La firma se verificó contra Receiver v0.2.17.

- **APK Receiver SHA-256:** `a191c28290724cfee7136c87293eed374bf32ad52a9aebbafa3e60fb5f8a50bb`.
- **Sender SIN cambios**: `sender.json` tiene digest idéntico al de la release 0.2.17, Sender v0.2.13.
- **Instalación:** solo por oferta automática o **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA**. No enviar APK aparte.
- **No hay pruebas físicas de 2003 ni confirmación de grabación R al momento de publicar**. QA y firma correctas no equivalen a funcionamiento validado en la T1.

## Pasos de prueba en vehículo

**Ensayo de marcha con cámara:**
- Estacionado en sitio seguro con freno mantenido, iniciar **GRABAR MARCHAS 35 S** en VEHÍCULO.
- Seleccionar R mientras la cámara desplaza la aplicación; luego regresar a P.
- Volver a Receiver y pulsar **VER RESULTADO GRABADO**. Esperamos capturar un cambio `1033=1` → `1033=2` → `1033=1`, pero **registrar el número real aunque no sea 2**. No conducir ni mirar la app durante la maniobra.
- Si la cámara detuviera el servicio o el firmware restringiera la lectura en segundo plano, marcar SIN CAPTURA, no inferir R=2.

**Ensayo plegado:**
- En P/0, con espejos visibles, pulsar **LEER ESTADO DE PLEGADO 2003** en posición desplegada; anotar entero.
- Usar botón OEM de plegado (sin Receiver), releer 2003. Confirmar cambio 1→0 y 0→1 al desplegar.
- Si se repite, con supervisión directa y sin obstáculos, hacer **una única prueba** de `PROBAR PLEGAR 2003=0` y luego una de `PROBAR DESPLEGAR 2003=1`. Si la respuesta del vehículo no coincide con el resultado de lectura, **parar y no repetir**. El estado Binder no confirma movimiento mecánico.
- Nunca escribir `2003=2,3,4` ni usarlo como acción de detener el motor.
 
## Clúster completo y ZIP de diseños

El propietario confirmó en su vehículo que **proyecta aplicaciones arbitrarias al clúster** y que **T1Launcher permite modo pantalla completa, esconder relojes OEM e importar diseños mediante ZIP**. Esto es **evidencia del comportamiento observado en su T1**, no prueba de un protocolo JavaScript específico ni de que Receiver pueda alojar el ZIP con la misma API sin investigación adicional.

Objetivo de la siguiente fase: **panel DriveLab visual de relojes y agujas animadas**, con señal de velocidad y SOC OEM realmente leída; combustible y autonomía propia estimada **basada exclusivamente en surtidores/CSV/nivel/Ω**, identificando saturación a **56 Ω** y corte térmico observado a **5,3 L OEM**. Nunca usar la fórmula vieja T1Launcher ni crear indicadores dinámicos con valores falsos. La pantalla de instrumentos OEM no debe perder sus advertencias de seguridad por sustituir una app de proyección; restaurar siempre la vista original y priorizar legibilidad y la cámara de reversa.
