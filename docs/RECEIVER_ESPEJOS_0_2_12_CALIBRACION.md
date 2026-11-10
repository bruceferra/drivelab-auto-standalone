# Receiver 0.2.12 — control de espejos 500 ms y diagnóstico real de posición

**Fecha:** 2026-11-10 · **Estado:** versión de ensayo en desarrollo/QA, publicación OTA solo tras compilar y validar firma. Sender v0.2.10 no se modifica.

## Evidencia de campo (CONFIRMADA por usuario, 10/11)

- En T1 argentina, desde Receiver 0.2.11 instalado **por el actualizador interno**, `2069=2` mueve **abajo** el espejo **derecho** y `2069=1` mueve **arriba**. Se hicieron **4 pulsos en cada sentido**. No se observó bloqueo del motor en esos ensayos.
- Los pulsos de 120 ms son muy pequeños; propietario solicita prueba de movimiento perceptible de **500 ms**.
- La app T1Launcher del tercero descalibra referencias con frecuencia, incluso si el usuario no toca los mandos. Su `pos_actual` es un contador propio; no está probado como posición real.

## Configuración experimental Receiver 0.2.12

| Función | ID / método | Estado |
|---|---|---|
| Derecho subir | `2069=1` durante hasta **500 ms**, después `2069=0` | Dirección/lado **VALIDADOS** con pulsos previos de 120 ms; 500 ms aún **PENDIENTE** de ensayo |
| Derecho bajar | `2069=2` durante hasta **500 ms**, después `2069=0` | Dirección/lado **VALIDADOS**; duración nueva pendiente |
| Derecho comparador corto | `2069=2` 120 ms, después `2069=0` | Referencia de ensayo corto anterior |
| Izquierdo (candidato) | `2068=1` o `2068=2`, hasta **500 ms**, después `2068=0` | **SOLO código de catálogo**, sin validación de lado/sentido/efecto |
| Marcha y velocidad | `1033` y `2148/1089` por getter OEM | Lectura puntual, permite anotar valores al cambiar P/R/D con vehículo detenido. El piloto exige P y velocidad <=0,5 para mandar pulsos |
| Leer posiciones/rangos | Android Automotive `CarPropertyManager` con reflexión y lista de propiedades/areas | **SOLO LECTURA**, sin usar setters HAL; consultar MIRROR_Z_POS 339741504, MIRROR_Y_POS 339741506 y vendors 557843649, 557867096, 557875203, 557879310. Puede fallar por permiso o por falta de soporte del fabricante |
| Guardar referencia D o R | Instantánea literal de resultados de lectura HAL con fecha/hora, en preferencias locales | **NO es calibración operativa ni memoria de ángulo**; para poder comparar valores antes/después |

La lectura de **mínimo y máximo** proviene de la configuración de la propiedad HAL, si existe. Esos rangos **no equivalen necesariamente a topes mecánicos comprobados**. El módulo no incorpora botones de `IR AL INICIO`/`IR AL FINAL` ni realiza desplazamientos automáticos a extremos: **exigen validar getters efectivos y movimientos bidireccionales antes de implementarlos**. Poder escribir una propiedad OEM **no garantiza que exista ni esté autorizada la lectura del ángulo físico**.

### Prueba en T1 (estacionada y en P)

1. Actualizar **únicamente Receiver** desde `ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA`; no descargar ni desinstalar nada. En el panel debe figurar versión 0.2.12. Mantener mando OEM de espejo a mano.
2. Pulsar `LEER MARCHA Y VELOCIDAD` en P. Verificar que lee 1033=1 y velocidad estacionaria.
3. Pulsar `LEER POSICIONES Y RANGOS HAL` y guardar/compartir **solo los números y errores**, sin VIN, localización ni información privada.
4. Mover el derecho **un poco** mediante el botón OEM físico, sin llegar a tope. Repetir lectura. Verificar si algún valor numérico cambia para un área. Repetir vuelta a posición anterior y comprobar si valor también vuelve.
5. Probar `DERECHO SUBIR ▲ 500 ms` **una sola vez**, observar lado, recorrido y parada. Solo si se detiene con normalidad, probar `DERECHO BAJAR ▼ 500 ms` una vez. **Si no detiene, usar mando OEM, suspender prueba y no circular hasta verificar**.
6. Probar izquierdo `2068=1` solo si espejo despejado y seguro, anotando si hace algo; `2068=2` después únicamente si el primero se detuvo. **No asumir sentidos**.
7. Leer posición HAL de ambos lados tras estas pruebas. Marcar instantáneas D y R solo como **registros de observación**, no memorias que actúen en R.
8. Leer marcha en R y D, **con vehículo detenido y freno**, para anotar valores de 1033; la app **nunca mueve espejos por este cambio**.

### Qué no debe probarse ni implementarse todavía

- No usar topes mecánicos para homing o recuperación de carrera.
- No pulsar ni dejar comandos mientras el vehículo circula, ni hacer una secuencia de 10 pasos o 3 segundos hasta verificar la parada.
- No convertir una instantánea de HAL en orden de posición absoluta.
- No guardar falsa posición al recibir éxito Binder (éxito de escritura no demuestra desplazamiento).
- No activar auto-inclinación de R ni restauración en D sin comprobar marcha, señal de ángulo (o referencia alternativa segura), respuesta de motor y fallo/reintento.
- No modificar Sender, mapas, Waze, puentes LAN, ventanas/AVAS, Spotify, tablero de consumo ni viajes GPS.

**Siguiente decisión:** si `MIRROR_Z_POS` u otra señal cambia en sincronía con movimientos reales, implementar control realimentado, memorias D/R y objetivo de valor inicial/final **limitados por config OEM validada**. Si no, la app no puede inferir posición absoluta solo a partir de cantidad de pulsos; estudiar APIs de memoria OEM distintas antes de construir un automatismo.
