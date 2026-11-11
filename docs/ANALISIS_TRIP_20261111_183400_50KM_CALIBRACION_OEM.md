# Prueba real de 50 km — Receiver 0.2.33 — 11/11/2026

## Contexto y procedencia

* **Jetour T1 PHEV argentina**; usuario pone en cero el TRIP original tras apagar/encender el vehículo y reinicia la sesión de T1Launcher. Odómetro inicial **31.292 km**; final **31.342 km**.
* Fotos finales (19:37 ART) muestran TRIP Jetour **50,0 km**, T1Launcher **47,9 km**, DriveLab **52,70 km**.
* TRIP DriveLab `20261111_183400`, hora 18:34:00–19:37:41 ART; 1.242 muestras. Recibido **completo**: 61 lotes en `public.drivelab_auto_trip_batches`, offset 0..1242, sin huecos ni solapamientos.
* CSV OEM `trip_oem_20261111_183400.csv`, recibido completo en **2 fragmentos** en `public.drivelab_auto_diagnostic_csv_chunks`; reconstruir concatenando fragmentos según `chunk_index` (el corte ocurre dentro de una línea).
* Datos observados, no medidas certificadas de combustible ni energía.

## Comparación de distancias

| Referencia | Recorrido km | Diferencia contra Jetour |
| --- | ---: | ---: |
| Trip original Jetour | **50,0** | referencia 0 % |
| Odómetro total | 31.292→31.342 (**50 km de incremento indicado**) | resolución entera |
| T1Launcher automático | **47,9** | **−2,1 km (−4,2 %)** |
| DriveLab 0.2.33 | **52,69891** | **+2,69891 km (+5,40 %)** |
| Integración independiente de `speed` OEM en las 1.242 muestras de nube | **52,69792** | coincide con la acumulación de DriveLab a ~1 m |

* El TRIP exportado figura como `VELOCIDAD_OEM_ESTIMADA`, no GPS.
* El dato del odómetro total tiene resolución de 1 km y el TRIP original de 0,1 km; **no** interpretar 50,0 como precisión centimétrica.
* **99 callbacks NAFTA_500_m y 99 ELÉCTRICO_500_m**, pareados 1:1, diferencia máxima de 6 ms entre canales, media 0,8 ms.
* Los 99 segmentos × 0,5 km supondrían 49,5 km acumulados; hay 98 intervalos entre el primer y último callback, que sumarían nominalmente 49,0 km.
* Distancia DriveLab al primer callback OEM: **0,13500 km**; al último: **52,62487 km**; entre ambos **52,48987 km** de DriveLab frente a **49,0 km nominales**; **0,53561 km por intervalo**; relación **1,07122×**.
* La discrepancia global **5,4 %** y la discrepancia entre callbacks **7,1 %** no son idénticas; no sustituir la distancia por un factor fijo sin entender frecuencia real de callback, escalas y fuentes.
* En dos viajes más cortos anteriores la relación distancia DriveLab por intervalo de callback fue ~1,106 y ~1,094. Variabilidad impide asumir un único factor universal.
* Investigar `speed` OEM vs Trip A/odómetro/velocidad GPS en condiciones controladas; **no modificar** kilometraje histórico ni UI OEM.

## Nafta — fuentes realmente comparadas

| Método | Valor | Cómo se obtuvo | Estado |
| --- | ---: | --- | --- |
| DriveLab PROXY | **3,935 L** | 52,69891 km × 7,47 L/100 km | **estimación fija**, no medición |
| Eventos OEM `NAFTA_500_m` | **495,0 crudo**; estimación **2,475 L** | 99 eventos no solapados × `raw × 0,005`, **si** unidades L/100 km y tramos exactamente 500 m | **hipótesis** |
| CI186 | **55,1→52,8 L indicados** | Diferencia de extremos **2,3 L** | Nivel de tanque, no litros inyectados |
| OEM 1085 | **4,8 L/100 km** al final | Promedio móvil últimos 50 km | 4,8×50/100 = **2,40 L equivalentes de esa ventana**, no el volumen exacto de este TRIP |
| T1Launcher | **1,97 L** | Señala que la nafta usada fue medida en tanque con algoritmo propio | No sabemos todos sus filtros/algoritmo |
| T1Launcher cálculo antiguo | **~2,58 L** | La propia pantalla lo identifica como cálculo anterior | Estimación antigua del launcher |

* La estimación OEM por 500 m **2,475 L** difiere **+0,175 L (+7,6 %)** de la caída CI186 2,30 L y **+0,075 L (+3,1 %)** de la equivalencia 2,40 L por el 1085 final (ventana móvil).
* Contra el cálculo 1,97 L de T1Launcher la diferencia es +0,505 L (~25,6 %), razón suficiente para NO declarar la fórmula como validada.
* El valor **4,1 L/100 km**, visible como «Consumo medio de energía», NO debe confundirse con el valor de nafta **4,8 L/100 km** junto al ícono de gota. Puede ser una magnitud energética equivalente, sin confirmar la ecuación.

## Electricidad y SOC

* Suma `ELÉCTRICO_500_m` **−158 crudo** en 99 eventos, **−0,790 kWh netos** únicamente **si** unidad fuera kWh/100 km y se cumple segmentación por 0,5 km.
* Signos de las 99 muestras: **58 negativas, 34 positivas, 7 ceros**. Negativo neto es compatible con recarga neta de batería, pero NO identifica por sí solo si la fuente es frenado regenerativo o motor térmico.
* T1Launcher: batería usada **2,88 kWh**, regenerado **3,89 kWh**; balance **−1,01 kWh** (usado − regenerado), asumiendo que las categorías cubren la misma sesión.
* OEM 1084 final **−2,4 kWh/100 km**, su equivalente de 50 km sería **−1,20 kWh** de la ventana móvil, no energía exacta del viaje.
* SOC display Receiver **13,7→18,4 % (+4,7 pp)**; SOC interno **18,1→22,5 % (+4,4 pp)**. T1Launcher muestra aprox. **18,0→22,5 %**. Dirección coherente con balance eléctrico neto negativo en la convención del vehículo.
* **0 de 1.242 muestras** contiene voltaje HV válido; valores `HV descarga estimada = 0` y `carga HV = 0` son **ausencia de medición**, no cero físico.
* T1Launcher: **6,4 km EV (13 %)** y 90/100 conducción (según su escala propia).

## Integridad y eventos

* `NAFTA_500_m`: 99 eventos; suma 495, valores min 0, max 11, **96 positivos y 3 ceros**.
* `ELÉCTRICO_500_m`: 99 eventos; suma −158, valores min −12, max 31.
* 30 callbacks `NAFTA_50_km` y 36 `ELÉCTRICO_50_km` (promedios móviles que cambian). **No se suman** como consumo de segmentos.
* Muestras Receiver: max velocidad OEM 103,13 km/h; 742 de 1242 muestran RPM>0; distancias basadas en integraciones temporales, no GPS.

## Etiqueta del tablero original

La foto verifica que el sistema OEM muestra literalmente **«Consumo medio de energía en los últimos 50 m»**. Los códigos 1084/1085 y el contexto prueban que la ventana tratada es **50 kilómetros**. Es un fallo de texto/unidad de la interfaz, independiente del cálculo: **no se modificó** el APK OEM ni el firmware.

## Conclusiones y siguientes pasos

1. **Muy buena consistencia experimental:** 99 pares de eventos para 50,0 km y nafta estimada 2,475 L vs 2,30 L CI186 y 2,40 L equivalente OEM 1085. No declarar litros físicamente confirmados hasta contrastar con un repostaje real.
2. **Error/diferencia en distancia:** DriveLab 52,70 km, Jetour 50,0 km y T1Launcher 47,9 km. Es principalmente diferencia de escala/origen de velocidad integrada, no pérdida de lotes; preservar números crudos y verificar la velocidad/odómetro antes de corregir.
3. **Electricidad neta:** −0,79 kWh experimental vs −1,01 kWh de T1Launcher y signo compatible con SOC creciente. Unidades y polaridad de la señal siguen por validar.
4. **No usar todavía** los datos hipotéticos para litros reales, dinero, autonomía o corrección automática del odómetro; conservar PROXY explícitamente separado.
5. Próximo enfoque: mapear origen y escala OEM de velocidad, añadir contraste de Trip A/odómetro físico al inicio/fin, y calibrar litros con surtidor. Para la interfaz, corrección de «50 m» a «50 km» requiere localizar primero el APK/recursos OEM responsables.
