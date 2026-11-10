# Jetour T1 PHEV — segundo ciclo de tanque cerrado (10/11/2026)

**Estado:** datos de surtidor y CSV contrastados; consumo por repostaje para el segundo ciclo **estimado bajo método tanque a tanque**. No es medición de caudal de inyección ni cifra de consumo energético total (PHEV). Continuidad obligatoria con [tablero de telemetría](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md) y [auditoría anterior](CALIBRACION_CSV_T1_2026-11-10.md).

## Evidencia del surtidor (foto entregada por propietario, 10/11)

- **66,230 L de nafta SUPER**, primer corte automático de pistola; **SIN sobrellenado manual** según el propietario. Misma estación de servicio que el 03/11, **distinto surtidor/pistola**. El usuario sospecha diferente sensibilidad de corte; no atribuirlo a un hecho confirmado.
- Precio unitario **$2.129 ARS/L**; importe visible **$141.004 ARS**, coherente con 66,230 × 2.129 = 141.003,67 redondeado al peso. No confundir el importe de compra con costo histórico exacto del combustible consumido en el ciclo.
- Primer repostaje comparable **03/11: 66,865 L al primer corte**, luego **~67,0 L** al completar según el usuario, lo cual **NO es la misma condición** que el primer corte sin sobrellenar del segundo: para comparar estrictamente primeras detenciones, usar **66,865 vs 66,230 L**. Diferencia **−0,635 L** (≈−0,95% de la primera). La primera carga a ~67 al completar es una segunda referencia, no sustituye el primer corte.

## Dos ciclos y tres contadores

| Ciclo | Litros del primer corte de surtidor | Recorrido del Jetour | L/100 km de repostaje | km/L de repostaje | Calidad de evidencia |
|---|---:|---:|---:|---:|---|
| Ciclo previo hasta 03/11 | **66,865 L** | **~950 km** | **~7,04** | **~14,21** | Volumen foto/observación; distancia redondeada |
| 03/11–10/11, segundo corte térmico | **66,230 L** | **887 km** | **7,47** | **13,39** | Surtidor fotografiado y recorrido Jetour comunicado; mismo punto operativo de corte térmico (5,3 L OEM) |

**Fórmula:** `66,230 ÷ 887 × 100 = 7,46674 L/100 km`. `887 ÷ 66,230 = 13,3927 km/L`. Equivale al consumo **estimado por volumen de repostaje** si los niveles físicos del tanque al primer corte fueran comparables y no hubo entradas/salidas adicionales. Variaciones de pistola, inclinación, espuma, temperatura, SOC inicial/final y recorridos EV aportan incertidumbre. **No es consumo instantáneo del motor**, y no deducir litros inyectados por cada tramo GPS sin otra fuente.

Contadores del segundo ciclo suministrados por el usuario:
- **887 km** Jetour (preferido para la comparación histórica de tanque; usuario reportó 885 y después 887).
- **867 km** aplicación de T1Launcher/tercero (−20 km, −2,25% contra Jetour).
- **895,089 km** DriveLab GPS (≈+8,089 km, +0,91% contra Jetour). Captura de pantalla con encabezado `Viaje 03/11 17:59 · TERMINADO`, **21 h 00 min** en movimiento, **76 h 58 min** de duración, **42,6 km/h** de velocidad media en movimiento, **181,1 km/h** máxima registrada (no validada como real), **47 h 09 min sin clasificar**, ciudad y autopista *marcadas manualmente* en 0. Esos contadores pueden tener ventanas de registro distintas; **no calibrar el odómetro ni derivar proporciones ciudad/autopista a partir de una comparación no sincronizada**.
- La captura de DriveLab identifica `ciudad marcada=0` y `autopista marcada=0`: es **ausencia de etiquetas**, NO demostración de que no se transitó por esas vías.

## CSV de cierre/repostaje del 10/11: evidencia nueva

**Archivo original privado:** `T1testigo_2026-11-10 (1).csv`, **5821 filas**, 27 columnas, 5821 timestamps únicos, del **05:41:25 al 11:35:50**. **SHA-256: `db7008e6a38ed09f991420157166347207a64cb56b64842a0e21d7d704e3879f`**.

Archivo original anterior, contenido en el nuevo: `T1testigo_2026-11-10.csv`, 4938 filas del 05:41:25 al 10:51:09, SHA-256 `27a768b01e08c25de1463406aac2b0d63e63a636fd47e79393b7c45bde726066`. **No sumar ambas exportaciones**: el más reciente extiende el mismo registro. Para integrar varios días, deduplicar por `fecha_hora` y verificar coincidencia de columnas comunes. Hay **una pausa de 3539 s (~58 min 59 s)** dentro del CSV, por lo que no se debe interpolar velocidades ni consumos atravesándola.

### Eventos verificables en el CSV

| Timestamp CSV | Litros OEM de pantalla | Litros «crudo» CSV | Resistencia variable | RPM térmico | Interpretación |
|---|---:|---:|---:|---:|---|
| 10:35:57 | 5,4 | 5,0 | 328 Ω | 1580 | Últimas muestras antes del piso |
| **10:36:01** | **5,3** | 5,0 | 328 Ω | **1356** | Primer punto en piso; **térmico todavía gira** |
| **10:37:11** | **5,3** | 5,0 | 308 Ω | **648** | Última muestra con RPM positivas antes de cargar |
| **10:37:14** | **5,3** | 5,0 | 328 Ω | **0** | Desde aquí sin nuevas RPM positivas hasta repostar |
| **10:51:09** | **5,3** | 5,0 | **329 Ω** | **0** | SOC interno 14,4%; tablero 9,8% |
| **11:29:16** | **5,3** | **7,8** | **273 Ω** | **0** | Última muestra antes de evento de carga; SOC interno 10,2%. Valores «crudos» pueden derivar aun si la pantalla OEM queda en 5,3 |
| **11:29:19** | **8,4** | 8,4 | 272 Ω | **0** | Primera muestra de carga `evento_carga=1` |
| **11:30:53** | **60,5** | 60,5 | **56 Ω** | **0** | Saturación del sensor alto nivel |
| **11:31:11** | **66,5** | 66,7 | **56 Ω** | **0** | La lógica de exportación activa **`sobrellenado=1`**, aunque usuario confirma no sobrellenar |
| **11:33:33** | **67,0** | 66,7 | **56 Ω** | **0** | Tablero de combustible alcanza 67 L |
| **11:35:50** | **67,0** | 66,7 | **56 Ω** | **1352** | Último registro, térmico vuelve a operar tras llenado, SOC interno 11,9% |

**Clasificación del corte:** **CONFIRMADO por observación del usuario** que el térmico se apagó en el segundo ciclo al llegar a **5,3 L OEM**, coincidente con el corte previo. El inicio del piso a las 10:36:01 NO equivale al apagado definitivo: aún hay 9 muestras con RPM >0 mientras el OEM marca 5,3, y la última es 10:37:11. El intervalo posterior 10:37:14→11:29:16 muestra RPM 0. No afirmar que el vehículo jamás podría volver a encender térmico en condiciones distintas.

### Descubrimiento para la calibración: no restar litros OEM como litros físicos

Ambos repostajes tuvieron `litros_display≈5,3` en piso y `litros_display=67,0` arriba. Por lo tanto, el aumento **OEM** es `67,0-5,3 = 61,7 L`.

- Segundo surtidor REAL: **66,230 L**, **4,530 L más** que ese cambio OEM.
- Primer surtidor primer corte: **66,865 L**, **5,165 L más** que el mismo cambio OEM.
- **El OEM no da un volumen físico exacto por simple diferencia de extremos.** Hay offset/saturación/filtrado y el primer corte de pistola es variable. No corregir la curva sumando de manera universal 4,530 o 5,165 L: la saturación superior impide hacerlo linealmente.
- El campo exportado `sobrellenado=1` **NO prueba sobrellenado**: es una bandera de algoritmo que se activa tras alta lectura; no mostrarlo en tablero como hecho. Confrontar explícitamente con la declaración del propietario **SIN sobrellenado**.
- `resist_auxiliar` se mantiene cerca de **56 Ω** en la zona de llenado alto y cerca de **328–329 Ω** al corte bajo, con lectura alterada/rebote al reposo. `resist_principal` 500 Ω no demuestra sensor independiente.

## Política del nuevo tablero en Receiver

El primer tablero debe ser **agregado, solo lectura, separado de navegación y controles**, y mostrar:
1. Dos tarjetas históricas de tanque (volúmenes efectivamente ingresados, kilometrajes Jetour, L/100 km, km/L, nivel de confianza).
2. Lectura OEM actual CI186 etiquetada **indicador filtrado, NO litros físicos exactos**, SOC / energía cruda disponibles, con valor/fecha/estado, sin mapear automáticamente 67→capacidad física ni 5,3→litros reales restantes.
3. Corte térmico **observado dos veces en 5,3 L OEM**; señal de alerta **sin clasificar combustible agotado como inseguridad mecánica ni mandar orden de prueba**.
4. Comparativo de los tres contadores **con advertencia de períodos de medición diferentes**. La captura de viajes permite comprobar medias y tiempos, pero **no** segmentación ciudad/autopista porque faltan etiquetas.
5. Nuevo **TRIP A tanque** desde la carga actual y **TRIP B parcial** como futuro proyecto de registro propio; no simular kilómetros continuos sin fuente de distancia/timestamps confiables. El GPS de DriveLab clásico sigue siendo testigo independiente y nunca se sobreescribe.
6. Si hay lectura futura validada de `559964335 PHEV SUM FUEL` y `559964336 PHEV SUM CHRG EGY`, agregar un segundo modo de cálculo **sin mezclarlo con el viejo algoritmo T1Launcher**. EV, HEV, carga térmica, regeneración y kWh reales deben tener observaciones independientes y unidades verificadas.

**Regla irreversible:** el cálculo **VIEJO** de consumo T1Launcher está descartado. No sustituir mediciones de surtidor por proyecciones OEM sin etiqueta; no publicar CSV originales privados, ya que contienen horarios/datos de uso.
