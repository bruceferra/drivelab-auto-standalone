# Receiver v0.2.50 · tres viajes, botones, OSM y volante

## Solicitado en prueba física del 12/11/2026
La pantalla del usuario mostraba dos botoneras Jornada y Cliente. Al iniciar Viaje 1, PAUSAR y TERMINAR de Jornada se desactivaban por una condición equivocada (`&&(!client||clientPaused)&&!pending`). Al cerrar Viaje 1 volvían a estar disponibles. Se corrige este error.

## Tres espacios de viaje separados
- **VIAJE TOTAL:** es la jornada maestra de muestras OEM, un CSV `trip_YYYYMMDD_HHMMSS.csv`. Inicio, pausa/reanudación y fin están disponibles según **su propio** estado, no por la actividad de los subviajes.
- **VIAJE 1:** conserva el anterior almacenamiento y los registros de usuarios (`T1JourneyStore`, `client_trips.csv`). Conserva los viajes activos tras instalar una versión nueva.
- **VIAJE 2:** nuevo espacio persistente independiente (`T1JourneyStore2`, prefs `t1_passenger_journeys_slot2`, historial `client_trips_2.csv`, `client_drive_km_2.csv`). Tiene sus propios INICIAR, PAUSAR, REANUDAR y TERMINAR, sin reiniciar Viaje 1 ni la jornada. Se permiten ambos subviajes simultáneos porque son dos checkpoints separados de las **mismas** muestras OEM de la jornada. El CSV maestra nunca se duplica.
- Los botones incompatibles están **gris/inactivos**; al grabar, INICIAR permanece gris, PAUSAR amarillo y TERMINAR rojo. Cada botonera se actualiza según su propio estado. Al pausar VIAJE TOTAL se conservan ambos checkpoints individuales y se los pone en pausa antes de detener el CSV (no se suman kilómetros durante una pausa sin muestras). Al reanudar el total se pueden reanudar individualmente. Cerrar VIAJE TOTAL con Viaje 1 o Viaje 2 abiertos requiere confirmación explícita del cierre conjunto, guardando ambos antes de detener la jornada.
- El streaming de RPM 1035, kilómetros por motor apagado/encendido/desconocido, eventos de 500 m y demás telemetría se acumula **en los tres espacios** sin atribuir movimientos anteriores a la activación de cada viaje.
- Al cerrar Viaje 2 se envía igualmente un resumen `code_diag_*` privado y sin nombre ni GPS a Supabase/QR, con tipo `VIAJE_2_FINISHED_V1` para distinguirlo de Viaje 1.

## Diseño del primer cockpit
- Tres tarjetas de lectura **VIAJE 1 | VIAJE TOTAL | VIAJE 2**, una al lado de la otra. VIAJE TOTAL está **inmediatamente a la derecha** de VIAJE 1.
- Cada tarjeta indica sus propios kilómetros y si está grabando, pausada, preparando o inactiva. Al tocar una tarjeta, se selecciona (fondo distinto). La selección persiste al cerrar el Cockpit.
- Debajo hay cinco tarjetas: **km con motor térmico apagado (RPM 1035=0)**, **nafta experimental**, **electricidad neta experimental**, **costo nafta experimental** y **autonomía total experimental**. Los primeros cuatro leen el espacio seleccionado. La autonomía es **el alcance restante del vehículo**, no una autonomía diferente por cliente, por lo que es la misma independientemente del viaje seleccionado.
- El botón de calibrar autonomía y sus explicaciones se movieron a DETALLES, sin perder funcionalidad.
- Se conservan los cuatro instrumentos compactos OEM de combustible, SOC, motor térmico y batería HV; sin velocímetro gigante.

## OSM: se muestra la recomendación
- Antes la sugerencia estaba oculta en el panel ZONAS/DIAGNÓSTICOS y se consultaba una ubicación `getLastKnownLocation` que podía tener 120 s, pero `T1OsmRoadAdvisor` rechazaba ubicaciones de 25 s. Se mantiene el consentimiento explícito y se pide posición **nueva** a `LocationManager.requestSingleUpdate` al no tener un fix confiable.
- El resultado aparece ahora en la pantalla principal en una sola línea: **OSM · SUGERENCIA: EV/HEV** si identifica con confianza una calle residencial o autopista. Tocarla abre diagnósticos para ver el tipo de vía o el motivo del fallo (GPS sin permiso, sin señal, vías paralelas, servidor Overpass HTTP/error).
- Tolerancia relajada a 35 m y 35 s solo para una **sugerencia informativa**: la clasificación todavía evita inferir autopista de `primary/secondary/trunk` y mantiene la regla de vías paralelas ambiguas.
- Las zonas EV/HEV se guardan **manual y separadamente** usando GUARDAR ZONA EV/HEV en el panel diagnóstico. OSM no ofrece ni crea zonas automáticamente. **Ningún resultado OSM escribe códigos al vehículo.**

## Volante: captura de tecla solo lectura
- El diagnóstico anterior bloqueaba INICIAR CAPTURA si la clave 1033/2148 fallaba de manera momentánea, aunque el vehículo estuviera físicamente en P a velocidad cero. Es un lector `Android KeyEvent`, no manda comandos: ahora deja iniciar la captura de 90 s en la pantalla, mostrando los valores OEM que pudo comprobar o un aviso si no existen.
- Usuario debe estacionar en P y 0 km/h para hacer el ensayo. Si las teclas son reservadas por el sistema, puede capturar cero eventos; el informe nunca inventa keycodes. La opción COMPARTIR conserva subida de eventos reales a Supabase.

## Versionado
Receiver **v0.2.50 code 51**, firmado, OTA y APK sobre instalaciones anteriores; Sender **v0.2.16 code 17** sin modificaciones. Sin desinstalar ni borrar datos de jornadas/cliente. Los diagnósticos de OSM y los nuevos controles requieren confirmación física en el vehículo; la compilación CI solo verifica construcción y pruebas de lógica existentes.
