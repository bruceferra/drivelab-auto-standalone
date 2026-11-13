# Receiver v0.2.84 (code85) · entrega integrada

## Cambios incluidos sin romper el recorrido ya validado
1. Optimización vial **continúa exclusivamente con OSM offline PBA+CABA**. Los 75 km exitosos, perfiles Ciudad A/B, Autopista A/B, umbrales SOC, lectura OEM de vuelta, tres votos y fallback «SIN PERFIL» no se modifican.
2. **Overture e IGN PBA**: `T1RoadShadowMonitor` analiza en 2 workers independientes el mismo GPS que OSM. Nunca aplica control OEM. `T1OfflineRoadMap.roadAtDataset` reutiliza la geometría v3 en modo SOLO LECTURA, por origen. Guarda muestras, % desconocido (kilómetros cuando hay continuidad), desacuerdos con OSM, ms por consulta y retraso relativo en cambios de categoría; no graba coordenadas ni VIN. No presenta el desacuerdo como error de una de las fuentes.
3. **Comparador** en Configurar optimizador: DESCARGAR OVERTURE + IGN (del VPS), ACTUALIZAR MÉTRICAS y SUBIR DIAGNÓSTICO 3 MAPAS mediante el QR/PIN de reportes ya existente. OSM no se vuelve a descargar ni cambia. Mapas ausentes: «BASE NO INSTALADA» (no se maquillan los porcentajes).
4. **Barra superior permanente**: REGEN BAJA/MEDIA/ALTA (1046), BATERÍA SMART/FORZADO (1040) y SOC OBJETIVO (1041). Se actualizan mediante getters OEM cada 15 segundos junto al indicador EV/HEV/EV MAX. Si 1041 no tiene codificación verificada, «SOC OBJ. ?», no inventa 60/70%. Estos tres botones son SOLO lectura.
5. **Mismo botón de voz existente**: órdenes explícitas de navegación siguen a Waze/clúster con confirmación. «Doscientos más ochenta y ocho» usa calculadora offline y respuesta TextToSpeech. «Cómo es el clima hoy» solicita un pronóstico de Open-Meteo basado en última posición GPS fresca y lo lee. «Qué onda con Trump» o «noticias» reproducen titulares RSS de Google News atribuidos y no mayores a 48 h; no los transforma en información confirmada arbitrariamente. «Cómo salió San Lorenzo» consulta último partido FINALIZADO de Liga Profesional en ESPN (endpoint público NO oficial, cobertura parcial; no presenta resultados de otras copas como si estuvieran cubiertos). Preguntas generales fuera de estas rutas continúan pasando a Gemini/ChatGPT mediante compartir texto, que puede requerir un toque adicional: NO es un agente conversacional completo.
6. Autonomía MIXTA ESTABLE sin doble conteo EV, espejos REVERSA AUTO y aire INTERIOR AUTO al arrancar, AVAS solo manual, registros de viajes y Sender v0.2.16 permanecen como en 0.2.83.

## ESTADO REAL DE LAS NUEVAS BASES
- El VPS existente ofrece el mapa OSM en `https://promanager.com.ar/drivelab/maps/pba-caba-roads-manifest.json` y `pba-caba-roads.sqlite.gz`, de donde ya lo descarga DriveLab.
- La app nueva está preparada para leer:
  - `https://promanager.com.ar/drivelab/maps/shadow/overture/manifest.json`
  - `https://promanager.com.ar/drivelab/maps/shadow/overture/roads.sqlite.gz`
  - `https://promanager.com.ar/drivelab/maps/shadow/ign/manifest.json`
  - `https://promanager.com.ar/drivelab/maps/shadow/ign/roads.sqlite.gz`
- **No se ha verificado que esos cuatro archivos nuevos estén publicados en Hostinger**. El APK incluye el descargador y el comparador, **no** incluye los datos cartográficos Overture/IGN ni pretende que ya existan.
- No hay conexión SSH/SFTP a Hostinger desde las herramientas de esta sesión. El PIN/QR de DriveLab es para subir diagnósticos, no da automáticamente privilegios para publicar archivos del servidor.
- Los paquetes son HTTPS gzip + SHA-256 y SQLite v3 de solo lectura. Instalar desde un VPS con manifiesto y suma invalidada no sobrescribe ningún mapa anterior.

## Preproducción de mapas Overture/IGN en VPS
Fuente Overture: datos abiertos transporte `segment`, release ejemplo 2026-10-21.0, recortar `bbox=-64.9,-42.1,-56.0,-33.0` (verificar y ajustar límites administrativos PBA+CABA para no incluir regiones ajenas). Overture integra fuentes que pueden compartir geometría con OSM: NO son observadores estadísticamente independientes. Citar Overture Maps Foundation y contribuyentes según licencia.

Fuente IGN: descargar oficialmente «Red vial» de Provincia de Buenos Aires del catálogo GBA, recurso con actualización publicada 05/08/2026, y utilizar el shapefile vectorial. IGN es fuente de rutas provinciales, **no** un mapa urbano completo. Dejar constancia de atribución «Instituto Geográfico Nacional, República Argentina, CC BY 4.0» conforme al dataset.

Preparación de entradas de GeoJSONSeq (usar GDAL):
```bash
# A partir de overture.geojson (descargado/filtrado de transportation segments)
ogr2ogr -f GeoJSONSeq overture.geojsonseq overture.geojson -t_srs EPSG:4326
# A partir de SHP oficial extraído desde el ZIP IGN
ogr2ogr -f GeoJSONSeq ign.geojsonseq /ruta/al/ign/red_vial_provincial.shp -t_srs EPSG:4326

python3 tools/build_shadow_maps.py --source OVERTURE --input overture.geojsonseq --out /var/www/html/drivelab/maps/shadow/overture --license-note 'Overture Maps Foundation y fuentes contribuyentes'
python3 tools/build_shadow_maps.py --source IGN --input ign.geojsonseq --out /var/www/html/drivelab/maps/shadow/ign --license-note 'Instituto Geográfico Nacional, República Argentina, CC BY 4.0'
```
Verificar ruta web real del VPS Hostinger y servir ambos `manifest.json` y `roads.sqlite.gz` sin autenticación de coordenadas. No introducir secretos de Hostinger en el APK ni inventar PIN. El proceso de generación y el test de artefactos están en `tools/build_shadow_maps.py` y `.github/workflows/shadow-map-ci.yml`.

Para IGN la capa provincial demuestra existencia de ruta; si sus atributos no identifican autopista, se la considera genéricamente CARRETERA en el comparador, no se le inventa velocidad máxima. NUNCA se usa este supuesto para cambiar EV/HEV. El indicador de «desconocido» de OSM mantiene su significado original, con porcentaje y distancia.

## Calidad y estado de prueba
- `shadow-map-ci.yml`: crea SQLite independientes Overture/IGN de un fixture, verifica esquema v3, índices, número de segmentos y SHA-256.
- `T1ShadowMapsSourceContractTest`: garantiza que las dos bases nuevas nunca cambian comandos OEM y que voz y barra superior están integradas.
- `T1TopRowEnergyStatusTest`: valida visualización OEM 1040/1041/1046, incluidos estados sin lectura.
- `T1VoiceMathTest`, `T1VoiceNewsTopicTest`, `T1VoiceIntentWeatherTest` y `T1VoiceIntentPolicyTest`: protegen el enrutamiento aritmética/actualidad/clima vs Waze.
- Gradle Android CI compila Sender y Receiver. La prueba física en Jetour, red real para fuentes de noticias y descarga efectiva de Overture/IGN seguirán pendientes hasta instalar la APK y publicar los mapas en Hostinger. Ninguna prueba de CI implica validación física.
