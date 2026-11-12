# Receiver 0.2.55 (code56) — OSM offline siempre DESCONOCIDO

## Qué ocurrió
El propietario reportó «GPS siempre dice desconocido», aun con permiso de ubicación y después de activar el asesor. Se identificaron errores reales de implementación del primer mapa offline:

1. `T1OfflineRoadMap.checkDue()` devolvía false **si el mapa todavía no existía en Receiver**, por lo que nunca iniciaba la primera descarga automática. Tener el mapa en Uber Driver **no da acceso** al archivo privado a Receiver; cada APK necesita su propia copia local, usando el mismo manifiesto y paquete del servidor.
2. La búsqueda de la carretera más cercana incluía `footway`, `cycleway`, `pedestrian` y otros tipos no transitables clasificados como UNKNOWN. El más cercano podía ser un sendero sobre la vía vehicular y dejar todo el recorrido en DESCONOCIDO. Ahora se ignoran estos candidatos para elegir una calle categorizada, aunque la ambigüedad entre calles vehiculares de clases diferentes sigue provocando UNKNOWN.
3. Una restricción del GPS heredada de Overpass (`precisión <=35 m`) impedía incluso usar una ubicación suficiente para una **recomendación no crítica** del mapa local. Se admite posición reciente con precisión <=65 m y se adapta el radio de búsqueda, evitando adjudicar una vía si hay categorías competidoras cercanas.
4. Un banner que solo decía DESCONOCIDO no distinguía falta de mapa, GPS, red, índice o falta de calle próxima.

## Corrección
- Al abrir Cockpit y existir Wi-Fi o red no medida, intenta primera descarga de mapa desde **el mismo servidor de DriveLab Uber**, con verificación de manifiesto, tamaño, checksum SHA-256 y SQLite v3. Si no hay mapa y no hay Wi-Fi, se permite descarga manual desde Zonas/Diagnósticos o tocando OSM en la cabecera. El intento automático se limita a uno por cada 30 min cuando no tiene éxito; si el mapa ya está instalado, comprueba actualizaciones con cadencia diaria.
- Si el mapa está ausente, OSM lo informa **de inmediato**, sin esperar 16 segundos a un fix GPS. Durante la descarga, el motivo dice MAPA DESCARGÁNDOSE. Una vez instalado, vuelve a consultar el GPS y el mapa local; no realiza una llamada a Overpass.
- Mientras OSM está activado, el aviso grande para UNKNOWN indica **FALTA DESCARGAR MAPA, REVISAR GPS / PRECISIÓN, SIN CALLE CERCA, VÍAS AMBIGUAS o ERROR EN MAPA LOCAL**, según el caso. No se muestra automáticamente cada 10 segundos si no hubo cambio.
- Un toque en la banda OSM abre un diagnóstico **grande y legible**, con estado de instalación y motivo concreto. Desde ahí se puede **DESCARGAR MAPA** o **COMPARTIR** el reporte QR en Supabase, sin copiar/pegar. El reporte no incluye coordenadas GPS, nombres, VIN ni etiquetas de viaje.
- Si la pantalla Android carece de posición suficientemente precisa, sigue mostrando Desconocido y **no inventa calle**. Tampoco cambiará automáticamente EV/HEV, SOC ni regeneración.

## Compatibilidad
- Receiver **0.2.55 / versionCode 56**, firmado sobre el certificado anterior, con OTA, sin borrar viajes.
- Preserva de Receiver 0.2.54: simulador histórico experimental, nuevos campos OEM de SOC/HEV/regen/aceleración, nombres automáticos de Viaje1/Viaje2, botoneras alineadas 1|Total|2, decodificación 2053 ECO/NORMAL/SPORT/SNOW.
- Sender **0.2.16 / code17** inalterado.
- El resultado de una descarga física desde el servidor y la clasificación en carretera deben validarse con el GPS real del Jetour. Compilar y probar los modelos Java no sustituye ese ensayo.

## Cómo probar
1. Abrir Cockpit con permiso de Ubicación y OSM activado; con Wi-Fi debería intentar instalar el mapa si falta. Puede tardar según el tamaño.
2. Tocar la banda OSM y observar **MAPA PBA+CABA INSTALADO** o **MAPA NO INSTALADO**. Si falta, usar DESCARGAR MAPA.
3. Tras instalación, observar los avisos grandes **ENTRANDO A CIUDAD / CARRETERA / AUTOPISTA** al cambiar la vía. No forzar cambios de modo por un aviso.
4. Si continúa DESCONOCIDO, tocar banda OSM → **COMPARTIR**, y revisar el `code_diag` resultante en Supabase para identificar la causa. Evitar escribir textos o copiar en la camioneta.
