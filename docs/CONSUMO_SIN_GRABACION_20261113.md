# Receiver v0.2.66 — Consumo vivo aunque no se grabe viaje

**Cambio realizado:** la versión 0.2.65 leía el dato de consumo por tramo OEM de **500 metros** de un listener creado dentro de `T1TripRecorderService`. Cuando no había viaje grabándose, Cockpit obtenía únicamente valores antiguos de ese listener o su **promedio OEM 50 km (1085)**, que sí se lee en el muestreo normal del Cockpit. Esto no satisfacía la necesidad de un tablero vivo independiente de la grabación.

## Solución

`T1OemConsumptionCallbacks` incorpora **`startForCockpit()`**, observador de **solo lectura** que se conecta a `com.autolink.clusterinteraction.CiService` y registra callbacks públicos OEM 135 (consumo nafta 500 m), 133 (eléctrico 500 m), 137 (nafta promedio 50 km) y 139 (eléctrico promedio 50 km), igual que el grabador existente.

- La escucha permanece activa **mientras Cockpit está visible**, sin límite de la prueba de 3 minutos. Al salir del Cockpit, se libera la suscripción a los eventos.
- Si `T1TripRecorderService` ya está grabando y tiene datos recientes, Cockpit utiliza su listener existente y no inicia una suscripción paralela; si la grabación termina con Cockpit abierto, comienza el observador de Cockpit.
- El observador de Cockpit **no inicia ni cierra un viaje**, **no crea archivos CSV** y no toca `T1JourneyStore`, `T1JourneyStore2`, historial de kilometraje o servicios de optimización.
- Cada nuevo callback nafta 500 m actualiza `t1_live_consumption` (último crudo, timestamp y promedio hasta cinco eventos); el callback nafta promedio 50 km actualiza su dato con cadencia limitada a 8 s para evitar sobreescribir preferencias constantemente.
- Si el Binder OEM está inaccesible, no se fabrican consumos: la UI muestra **SIN CALLBACK FRESCO** y utiliza, si hay valores fiables, el 1085 de 50 km o la base histórica/manual; se reintenta el enlace como máximo cada 15 s mientras está visible.
- Los valores de consumo de 500 m son **experimentales y aún no calibrados en L/100 km**. Un tramo eléctrico con 0 L/100 no se transforma en autonomía de nafta infinita.
- El valor de autonomía dinámica se actualiza mediante `T1LiveConsumption` y `T1ExperimentalRange`, igual que antes, pero ahora recibe información aunque no esté grabándose un viaje.

## Comprobación en la Jetour

1. Sin iniciar **JORNADA** ni **VIAJE 1/2**, abrir **COCKPIT** y observar **NAFTA · ÚLTIMOS 500 m**.
2. Conducir normalmente hasta que llegue un evento de 500 m. Si la ROM lo publica a apps de terceros, la leyenda deja de decir `SIN CALLBACK FRESCO` y aparece el valor crudo y la tendencia.
3. Pasados tres segmentos válidos, comprobar si la autonomía cambia al variar el ritmo. Si no aparece el valor, observar si existe el promedio 50 km. **No atribuir L/100 definitivos al callback crudo sin validación.**
4. Iniciar y finalizar un viaje: comprobar que el consumo continúa visible en Cockpit, y que únicamente el grabador legítimo crea el CSV de viaje.
5. Cerrar Cockpit sin viaje grabándose y regresar: se reinicia la escucha, sin crear viajes ni eventos OEM duplicados.

## Compatibilidad

Receiver v0.2.66 versionCode67 firmado con el mismo certificado de v0.2.65, Sender v0.2.16 sin cambios. No altera optimizador de SOC/regeneración ni los informes Eco Score/OSM de la versión anterior.
