# Receiver 0.2.51 code52 — mapa offline compartido, 6 bloques, códigos OEM

## Mapa Buenos Aires
- Reutiliza **las mismas URLs de datos y esquema v3** que DriveLab Uber Beta: `https://promanager.com.ar/drivelab/maps/pba-caba-roads-manifest.json` y `pba-caba-roads.sqlite.gz`. El manifiesto publicado define `region=AR-B+AR-C`, `schema=3`, `roads_bounds`, cantidad de vías, bytes comprimidos y SHA-256 del paquete.
- La implementación `T1OfflineRoadMap.java` deriva de `drivelab/android/app/src/main/java/ar/com/rango5gps/OfflineProvinceRoads.java`, pero tiene espacio privado de Receiver. **No es posible leer directamente el archivo privado de Uber**: cada app conserva su copia para no alterar datos de la otra. Una única fuente de actualizaciones *en el servidor* y una descarga local por app.
- Instalación manual desde **ZONAS/DIAGNÓSTICOS → DESCARGAR / ACTUALIZAR MAPA PBA+CABA**. Archivo temporal, checksum SHA-256 y bytes exactos, validación SQLite (metadatos región, índice portable BTree y conteo), reemplazo atómico con copia de seguridad; si falla la actualización se conserva el mapa anterior. 200 MB libres recomendados.
- Al entrar al cockpit, si Receiver ya tiene un mapa, consulta el mismo manifiesto aproximadamente cada **24 horas y solo por Wi-Fi no medida**, y descarga la nueva versión si cambió el SHA (no importa si el mapa creció o se redujo, si la revisión tiene formato válido). No hay actualizaciones push instantáneas con la app cerrada. Descarga manual por cualquier red solo tras confirmación explícita.
- Las recomendaciones OSM ahora salen de la base SQLite local con índice de `roads_bounds` y geometría zlib, evaluando distancia perpendicular a líneas de carretera y margen de precisión GPS. **No usa Overpass ni envía las coordenadas** a terceros. `motorway`/links sugieren HEV; `residential/service/tertiary` sugieren EV. Carreteras de clasificación incierta, vías paralelas y localización imprecisa => *sin sugerencia*.
- El asesor conserva la condición **solo lectura**. Ni motor, SOC ni modo EV/HEV cambian automáticamente. Las zonas EV/HEV manuales no se alteran.

## Cockpit — seis bloques
Seis tarjetas en una misma fila (tres lecturas seleccionables arriba: VIAJE 1 / VIAJE TOTAL / VIAJE 2):
1. **CONSUMO · EXP.** = `litros*100/km` L/100 km + `km/litros` km/L; si no hay muestra válida, distancia es cero o litros son cero, se informa SIN DATOS.
2. **KM ELÉCTRICOS PUROS**, distancias con RPM térmicas 1035 = 0 (observación, no medición directa de par eléctrico).
3. **COMBUSTIBLE CONSUMIDO**, litros estimados de eventos 500 m.
4. **ELECTRICIDAD CONSUMIDA · EXP.**, kWh netos experimentales.
5. **COSTO COMBUSTIBLE · EXP.**, costo de litros estimados.
6. **AUTONOMÍA TOTAL · EXP.**, alcance restante estimado del vehículo, compartido entre los viajes.

## Nuevas señales OEM de solo lectura
El propietario identificó las siguientes claves en el vehículo. `T1TripTelemetry` las consulta como int sin escrituras:
- **1079** porcentaje de combustible, indicador adicional de la primera pantalla;
- **2053** modo de conducción: el APK `ALVehicleService` contiene clases de Eco/Sport/Snow, pero **sin correspondencia numérica probada**; se muestra RAW hasta confirmar seleccionando modos OEM;
- **2204** freno de mano, tipo/valores sin mapear;
- **1031** sistema de alta tensión habilitado (1 informado); **estado, NO voltaje en V**;
- **2073** `KeyVehiclePropertyEngineSts`: reportado encendido aunque RPM 1035=0; no se usa para decidir si el térmico consume gasolina;
- **1088** velocidad válida: informó sin dato; no se sustituye por 2148 sin pruebas;
- **2014** `KeyVehiclePropertyAtmosphereLightSwitch`: puede no corresponder a luminaria visible, sin asociar al interior;
- **2170** `KeyVehiclePropertyPassengerDoorCem2`: propiedad relacionada con puerta, NO asumir sensor de cinturón.
- **1063** es candidato de voltaje pack; float puede devolver NaN/ausente. **1064** es amperios, no voltios. **559940910** devolvió 310.4 V candidato del circuito HV en diagnósticos previos, no de batería 12 V. `1035` = RPM motor térmico, no motor eléctrico.
- Un toque en el texto de 1079/2053 abre diagnóstico; COMPARTIR sube informe privado sin GPS/VIN a Supabase. Sin escritura de estos nuevos códigos.

## RPM del motor eléctrico
Los APK recibidos (`T1Launcher-T1N134.apk`, `ALVehicleService.apk`, `ALCarService.apk`, `ALNaviService.apk`, `ClusterInteraction.apk` y `t1testigo.apk`) contienen inventarios OEM. T1Launcher ya mostraba una *estimación matemática* de RPM por velocidad, que no se declara RPM reales del inversor. Hasta encontrar y probar una propiedad inequívoca se presenta SIN LECTURA (sin inventar datos).

## Otros
- Receiver **0.2.51 code52** instalable sobre versión anterior con misma firma; Sender **0.2.16 code17** sin cambios.
- No se ha validado en el vehículo la descarga, la recomendación de tipo de vía, el orden numérico de Eco/Sport/Snow ni una lectura 1063 válida. Las afirmaciones anteriores son de implementación y de lecturas observadas, no de prueba física de esta actualización.
