# Receiver v0.2.67 — Cockpit: botones superiores bloqueados y scroll hacia historial

**Reporte reproducido por descripción de usuario:** al desplazarse hacia abajo hasta los viajes históricos, el Cockpit empieza a saltar automáticamente al análisis ECO; al intentar tocar `MODO OPTIMIZADO` y otros controles en el extremo superior no se abre ninguna Activity. El historial muestra `Analizando OSM, velocidades por 5 km/h y conducción del viaje seleccionado…`.

## Causa probable identificada en el código de v0.2.66

1. `updateStats()` ejecutaba `updateHistory()` **cada 5 s en el hilo de UI**, incluso cuando el panel de historial estaba cerrado.
2. `updateHistory()` consultaba archivos locales, `T1JourneyStore.totals()`, `history()` y `T1HistoricalDistanceRepair.load()` **en el hilo de UI**, después eliminaba y reconstruía hasta **60 elementos interactivos** con `removeAllViews()`. Esto podía bloquear la respuesta táctil durante un recorrido o un escaneo.
3. `showHistoryDetails()` destruía y reconstruía las métricas seleccionadas periódicamente, y `historyEcoStatus.setTextIsSelectable(true)` convertía el largo análisis ECO en un TextView enfocable. En Android 11, los cambios de vista/foco dentro de un ScrollView pueden pedir `requestChildRectangleOnScreen`, desplazando la pantalla hasta el histórico.
4. Toda la barra de navegación (Micrófono, PENDIENTES, PANTALLA, SIMULADOR, AUTO, MODO OPTIMIZADO e INICIO) estaba **dentro del mismo ScrollView**: el redibujado y movimiento competía con los toques del usuario.

## Corrección

- **Barra de controles fija**: ahora es un `LinearLayout` colocado encima del scroll, como hermano del ScrollView dentro del contenedor principal, no como hijo desplazable. Los botones mantienen sus listeners originales y la navegación directa.
- El popup decorativo de cambio de vía no es interactivo (`enabled=false`, `clickable=false`, `focusable=false`, `longClickable=false`) y se posiciona debajo de la barra de botones para no interceptar nada.
- El texto ECO del histórico **no toma foco ni selección táctil**. El análisis OSM se conserva, se muestra y queda en caché para el viaje seleccionado.
- **Carga asíncrona del historial** en hilo dedicado: totales y CSV se procesan fuera de UI; un `AtomicBoolean` evita cargas simultáneas. La vista solo recibe una instantánea inmutable con los textos y los registros listos.
- La lista de viajes se reconstruye **solo si cambia la firma** de los datos, nunca cada 5 segundos. Seleccionar otro viaje actualiza el resaltado sin eliminar/recrear toda la lista, y el detalle ECO se recalcula solo al cambiar viaje o sus datos corregidos.
- Con el panel cerrado **no se realiza ningún escaneo periódico del historial**. Cuando se abre, se carga una vez; mientras permanece visible se comprueban cambios aproximadamente cada 30 s. El histórico OEM y los CSV no se modifican.
- El análisis retroactivo de Eco Score, seis clases viales y bandas de 5 km/h sigue en su hilo de trabajo y no afecta los botones superiores.

## Prueba manual con el vehículo estacionado

1. Actualizar desde Receiver (si Cockpit no responde, volver mediante botón o gesto Android sistema; no hace falta tocar un control superior de Cockpit).
2. Entrar en **COCKPIT**. Pulsar sucesivamente `MODO OPTIMIZADO`, `SIMULADOR` y `PENDIENTES`. Deben abrirse inmediatamente.
3. Volver, abrir `DETALLES / HISTORIAL` y seleccionar un viaje antiguo. Durante `Analizando OSM…`, tocar cualquier botón fijo superior: **debe responder inmediatamente**, sin scroll automático.
4. Dejar la ventana del historial abierta más de 30 s. Verificar que no cambia la posición del scroll ni desaparece la selección.
5. Verificar el detalle ECO una vez terminado el análisis y que un viaje anterior se abre instantáneamente al seleccionarlo de nuevo.
6. Asegurar que los viajes originales, el botón de AUTO, consumos OEM sin grabación y Sender no cambian.

**Alcance de la comprobación:** CI Android valida que las rutas, el enfoque y la separación de layouts estén presentes y que compile el APK, pero la entrega real del gesto táctil requiere prueba física en la ROM Jetour T1.
