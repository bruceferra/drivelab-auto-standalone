# Receiver 0.2.72 (code73) — indicador del modo de vía en la primera fila

El usuario necesita ver la clasificación actual de vía junto a EV, HEV y EV MAX en la barra fija de todas las pantallas Receiver. Los tres botones de modo OEM siguen marcando en verde el valor confirmado por 1043.

## Nuevo botón de solo lectura

El encabezado muestra un único botón dinámico con cuatro posibles textos:
- MODO CIUDAD
- MODO CARRETERA
- MODO AUTOPISTA
- MODO DESCONOCIDO

Este botón no tiene onClick, foco, pulsación larga ni escritura OEM. Utiliza la clasificación de vía offline OSM actual del mismo almacén de lecturas que usa el optimizador (T1OptimizedModeStore.lastRoad).

El servicio T1OptimizedAutoService ahora publica en ese almacén cada clasificación GPS/OSM al examinar los perfiles. Si la clasificación no es confiable, publica DESCONOCIDO. Las lecturas caducan después de 45 segundos. El encabezado actualiza indicador y color cada 3 segundos sin salir de la pantalla.

**Verde** cuando MODO OPTIMIZADO está ON; **neutro** cuando está OFF. El botón OPTIMIZADO ON/OFF sigue visible, verde/rojo, en la segunda fila.

El chip informa la categoría de la vía, NO confirma que la ECU haya aplicado una orden de propulsión. EV, HEV y EV MAX sí se colorean según la lectura OEM 1043. No se modifica la escritura de los modos ni la automatización de SOC/regeneración.

## Validación
CI Android, chequeos de indicador sin acción, firma code73 idéntica a code72, OTA y Sender 0.2.16 sin cambios. Falta comprobación visual en la pantalla física Jetour T1.

## Otros ajustes incorporados tras prueba física del usuario
- **ESPEJOS**: el texto siempre indica la acción que sigue: getter 2003=0 (plegados) ⇒ DESPLEGAR ESPEJOS; 2003=1 (desplegados) ⇒ PLEGAR ESPEJOS. Se consulta al entrar a la pantalla, después de cada orden y periódicamente. El getter de solo lectura ahora funciona en marcha sin interlock de velocidad (los setters siguen sujetos a la misma restricción de velocidad que antes). Si 2003 no está disponible, aparece ESPEJOS · SIN ESTADO; no inventa estado inverso. Tras un setter único, relee durante ~1,4 s sin segundo comando.
- **VENTILACIÓN TOTAL y CIERRE TOTAL**: ambos grises; son acciones puntuales, nunca indicadores de selección continua.
- **PEATÓN 2074**: el control lee el getter OEM en entrada, después de alternar y periódicamente. Si 1, PEATÓN ON en verde; si 0, PEATÓN OFF en rojo; si no hay lectura confirmada, color neutro y PEATÓN · SIN LECTURA. Tras una sola escritura el getter se relee durante ~1,5 s por posible actualización tardía, sin reenvío.
- Las acciones AVAS pueden ser denegadas por VehicleService; no inferimos cambio físico de la recepción Binder y siempre mostramos la lectura real cuando está disponible.

Estos cambios conservan los límites del vehículo, no cambian la lista SOC de 20/30/40/50/60/70, ni desactivan escrituras 1040/1041/1046.
