# DriveLab Auto · análisis de los dos registros del TRIP 11/11/2026 16:40:21

**Análisis:** 11/11/2026 (Argentina). **Lectura, sin cambios de código ni calibración automática.**
**Fuente:** tabla privada Supabase `drivelab_auto_diagnostic_csv_chunks` (`trip_oem_20261111_164021.csv`) y `drivelab_auto_trip_batches` (TRIP `20261111_164021`, lotes saneados sin coordenadas). **No publicar GPS/VIN.**

## Integridad / resumen confirmado

| Indicador | Resultado |
| --- | --- |
| CSV OEM | 1/1 fragmento recibido; 53 eventos después del encabezado |
| TRIP | 1.213 muestras; 16:40:21–17:42:25; `tripKm` final **10,412 km** |
| Eventos de 500 m | **19 pares exactos** nafta+electricidad, separados por milisegundos; contadores finales del TRIP 19/19 |
| Callbacks compactados de promedio 50 km | 5 nafta, 10 electricidad |
| Nafta OEM CI186 | 56,7 → 55,9 L (**−0,8 L de lectura OEM**, NO medición física independiente) |
| SOC tablero | 14,1 → 13,8 % (−0,3 puntos) |
| SOC interno | 18,4 → 18,2 % (−0,2 puntos) |
| Máxima velocidad guardada | ≈60,3 (según señal OEM; contrastar unidad) |
| Consumo HV acumulado `hvIn/hvOut` | 0 en todas las muestras; voltaje HV no disponible en lote saneado. No calcular kWh integrados desde corriente sola |

**Sincronización:** primero se subieron viajes anteriores (`20261110_144559` y `20261110_232521`). `T1TripSync.sendPending` ordena archivos alfabéticamente y limita a 12 lotes de 40 filas por pasada; así el viaje actual aparece más tarde. Finalmente llegaron los 1.213 registros del TRIP 16:40. No confundir envío tardío con viaje distinto.

## Señales OEM 500 metros / promedio 50 km

| Canal | Eventos | Mínimo | Máximo | Media cruda |
| --- | ---: | ---: | ---: | ---: |
| `NAFTA_500_m` | 19 | 0 | 20 | 7,8947 |
| `ELÉCTRICO_500_m` | 19 | −12 | +31 | +3,1579 |
| `NAFTA_50_km` | 5 | 5,8 | 6,0 | 5,88 |
| `ELÉCTRICO_50_km` | 10 | −1,1 | −0,7 | −0,93 |

- Correlación Pearson **r≈−0,882** entre los 19 pares fuel/electric. Muestras: `(nafta, eléctrico)=(0,+31)` dos veces, `(20,−12)`, `(17,−12)`, `(1,+21)`. Una señal negativa **no demuestra frenada regenerativa**; puede reflejar generación térmica neta a batería.
- Cerca de 16:59 el par `(20,−12)` coincide con corriente HV ≈−92 A y SOC tablero ≈14 %, después de ≈13 % a las 16:56. Compatible con batería cargándose mientras el térmico consume combustible, **no prueba por sí solo la fuente exacta de carga**.
- Los números 50 km son **promedios rodantes**, que incluyen kilometraje previo al inicio de este TRIP; no tienen por qué coincidir con el promedio de 9,5 km de eventos.

### Hipótesis de escala muy prometedora (NO validada físicamente)

**Si** cada entero crudo de combustible representa L/100 km del intervalo de 500 m, el volumen de 19 tramos sería:

`SUM(NAFTA_500_m) × (0,5 km/100 km) = 150 × 0,005 = 0,750 litros`.

El tanque OEM CI186 cayó **≈0,8 L** en el viaje (56,7 → 55,9), una diferencia de **0,05 L**. Los extremos de 500 m y del TRIP no coinciden perfectamente, y CI186 está cuantizado/estimado; **es una concordancia interna entre señales OEM, no calibración independiente por surtidor**.

**Si** los enteros eléctricos representan kWh/100 km y positivo significa descarga neta, el balance sería `SUM(ELÉCTRICO_500_m) × 0,005 = 60 × 0,005 = +0,300 kWh` en los 19 tramos. Esto es una **simulación de hipótesis, NO kWh confirmados**. Contrastar contra voltaje/corriente y energía de BMS antes de mostrar valores físicos al usuario.

### Discrepancia sistemática de distancia (~10,6 %)

El primer evento registrado cae cerca de **0,050 km** del TRIP y el 19.º cerca de **10,000 km**. Hay 18 intervalos entre ellos:
- El OEM debería abarcar **18 × 0,5 = 9,000 km** si el evento corresponde exactamente a 500 m.
- El `tripKm` de Receiver creció **9,950 km** entre dichos eventos.
- Relación **1,1056** ⇒ el contador TRIP indica aproximadamente **10,6 % más distancia** que el paso nominal del callback OEM (18 diferencias individuales ≈0,524–0,589 km).

Hipótesis: `tripKm` integra velocidad OEM `2148` / fallback `1089` en vez del odómetro físico, o utiliza GPS con distinta metodología. La fuente exacta `distance_source` figura en el CSV local pero **no se envía en el JSON saneado**. **No aplicar factor de corrección todavía**, contrastar el odómetro físico/T1 Testigo o el campo del CSV local y repetir medición en ruta estable.

## Siguientes pasos útiles

1. **Validación física**: comparar `SUM(rawFuel) × 0,005` contra un trayecto suficientemente largo (y repostaje), no contra CI186 únicamente.
2. **Fijar origen de distancia**: incluir `distance_source` como valor enumerado no sensible en sync o revisar el CSV principal local. Comparar odómetro Jetour vs `tripKm` vs cantidad de callbacks 500 m.
3. **Energía**: lograr lectura fiable de voltaje HV junto con corriente y determinar convención de signos/flujo de batería. No traducir eléctrico crudo a kWh reales ni restar regeneración sin esa calibración.
4. **Experiencia de sincronización**: considerar una cola por viaje más reciente o round-robin y mostrar progreso; el orden actual antiguo-primero oculta temporalmente el último TRIP.
5. **Tablero**: provisionalmente mostrar segmentos, valor promedio crudo, suma hipotética y calidad de medición; reservar litros/kWh reales hasta prueba independiente. Para reconstruir ventana real 50 km hacen falta 100 tramos contiguos y escala validada; los 19 actuales no bastan.

**Referencias de implementación:** `T1OemConsumptionCallbacks.java` (registradores OEM 135/133/137/139), `T1TripRecorderService.java`, `T1TripSync.java`; [versión de origen](RECEIVER_0_2_32_SENDER_0_2_15_TRIP_500M_UBER_OCULTO.md).
