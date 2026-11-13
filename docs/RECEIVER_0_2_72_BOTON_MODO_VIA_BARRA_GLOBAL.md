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
