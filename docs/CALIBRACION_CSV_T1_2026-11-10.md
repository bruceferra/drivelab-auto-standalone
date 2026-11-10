# Jetour T1 PHEV — auditoría CSV y calibración del tanque (10/11/2026)

**Estado:** investigación reproducible. **No es una calibración final ni una función instalada** en DriveLab Auto Receiver v0.2.10. Leer junto con [documento maestro del tablero](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md). Datos derivados de archivos proporcionados por el propietario y de observaciones suyas, sin publicación de CSV originales.

## Regla innegociable

**El cálculo VIEJO de consumo de T1Launcher está descartado y no debe usarse en ningún promedio, calibración, estimación ni interfaz.** El cálculo **NUEVO** de *nafta usada / medida en tanque* es un estimador externo basado en variación de nivel, sujeto a contraste. La fuente OEM CI186, el menú chino y la lectura equivalente del otro software **no son tres mediciones físicas independientes**: en condiciones observadas exponen la misma señal filtrada.

## Auditoría de los CSV del 3 al 9 de noviembre

- **13 archivos originales CSV**, incluyendo exportaciones parciales superpuestas: **69.061 filas de archivo**; después de deduplicar por `fecha_hora`, **52.880 muestras únicas**; también 52.880 filas únicas al comparar el resto de las columnas, sin discrepancias entre solapamientos. Cobertura: **2026-11-03 05:38:37** a **2026-11-09 21:47:32**, hora informada por el CSV.
- Método de deduplicación: concatenar los 13 CSV; convertir `fecha_hora` a marca temporal; conservar una muestra por marca. No agregar columnas de otros viajes ni extrapolar durante pausas sin muestras.
- Campos: `litros_display` (nivel filtrado mostrado por vehículo), `litros_crudo` (valor nombrado así por el exportador, **no validado como litros físicos**), `resist_auxiliar` (canal que cambia), `resist_principal` (500 Ω estable en las muestras válidas), `evento_carga`, `vel_veh_kmh`, `rpm_termico`, `soc_real`, `soc_tablero`, `dte_calculado`, `km_prox_service`. No confundir `trip_a_km`, que en muchos segmentos vale 0, con la distancia del tanque.
- El canal `resist_principal` vale **500 Ω en 52.872 registros con dato**. La finalidad del canal sigue **desconocida**: no etiquetarlo como sensor/reserva física.

### Puntos y comportamientos observados

| Instante / contexto | Nivel OEM | `litros_crudo` | Resistencia variable | Otra evidencia | Certeza |
|---|---:|---:|---:|---|---|
| 03/11 05:38:37, antes de cargar | 7,4 L | 7,9 L | 286 Ω | Tabla de instrumentos marcaba 0%; motor térmico todavía era utilizable en el período | CSV + observación usuario |
| 03/11 06:23:59, primera muestra en mínimo observado | **5,3 L** | 5,0 L | 328 Ω | **RPM térmico 2.480** en esa muestra; no equiparar primera lectura 5,3 con apagado instantáneo | CSV |
| 03/11, meseta de 5,3 L | 5,3 L | variable | mediana 329 Ω | 436 muestras con ese nivel; solo tres tienen RPM > 0 | CSV |
| 03/11 09:35:52, tras repostaje | 67,0 L | 66,7 L | 56 Ω | Surtidor 66,865 L al primer corte, luego ~67,0 L reportados | CSV + usuario |
| 03/11 19:18:44 | 67,0 L | 63,6 L | 56 Ω | `vel_veh_kmh` 26,46 | CSV |
| **03/11 19:18:47**, tres segundos después | **63,4 L** | **63,3 L** | **79 Ω** | **`vel_veh_kmh` 30,52**; caída de pantalla de 3,6 L en movimiento, no consumo instantáneo real | CSV |
| 04/11 cierre del registro | 55,3 L | 58,3 L | 84 Ω | Lecturas filtrada y «cruda» pueden diferir | CSV |
| 08/11 cierre del registro | 24,4 L | 24,6 L | 226 Ω | — | CSV |
| **09/11 21:47:32**, última muestra disponible | **11,8 L** | **9,9 L** | **278 Ω** | `dte_calculado` 120 km; no equivale necesariamente a autonomía visible del clúster | CSV |
| **10/11, usuario (sin CSV aún)** | **6,8 L** | sin muestra | sin muestra | odómetro parcial de tanque Jetour 868,5 km | OBSERVADO, pendiente archivo |

**Corrección de la narración anterior:** el salto **67,0 → 63,4 L NO ocurrió con el vehículo estacionado**. Ocurrió a 26–31 km/h, según dos registros a tres segundos de distancia. No reescribir las muestras ni atribuir el salto a consumo real súbito. La resistencia también saltó de 56 a 79 Ω, compatible con actualización tras saturación.

**Cautela sobre el 5,3 L:** es un piso OEM observado en el ciclo anterior y se mantuvo con el térmico apagado, pero la primera lectura de 5,3 L aún registró RPM positivas. La marca física relevante es la **indisponibilidad operativa del térmico** cuando el vehículo lo necesita y no consigue encenderlo; evitar pruebas de agotamiento deliberado durante la circulación. El segundo corte debe confirmarse con estado de vehículo, SOC, RPM, condiciones de prueba y litros reales de surtidor.

### Últimos puntos comunicados por el usuario

| Recorrido acumulado Jetour | Combustible OEM | Autonomía Jetour | Fuente |
|---:|---:|---:|---|
| 763 km | 14,0 L | 160 km | Observación 09/11; CI186 = menú chino en comparación inmediata |
| 790 km | 11,8 L | 119 km | Observación 09/11 (captura/CSV cercana, no misma muestra garantizada) |
| 868,5 km | 6,8 L | «--» luego de los últimos 50 km | Observación 10/11, pendiente CSV de ese día |
| **885,0 km** | **6,0 L** | «--» (permanece oculto) | **Observación del usuario 10/11, ~09:28 hora Argentina, pendiente CSV**; no se informó bloqueo definitivo del térmico |

Entre **790 km / 11,8 L** y **868,5 km / 6,8 L**: diferencia **78,5 km**, descenso OEM **5,0 L** ⇒ **6,37 L/100 km indicados**, **NO consumo físico exacto**. El usuario puso otro parcial a cero cuando el clúster mostraba los últimos 50 km y recorrió 33,3 km; cálculo aritmético 50−33,3=16,7 km y 868,5+16,7=**885,2 km**, únicamente **cero extrapolado de la indicación antigua**, no fecha/km de corte confirmado. El clúster recalcula autonomía y oculta la cifra al llegar a «--».

El usuario observó SOC pasar por **21% al salir de autopista y 18% en ciudad**, coherente con transferencia/consumo de energía en un PHEV; no convertir diferencias de SOC directamente en litros ni kWh exactos sin capacidad útil y energía medida.

### Referencia de ciclo anterior y surtidor

- Primer ciclo previo: **~950 km** entre referencias de corte operativo y **~67 L** cargados, sin recarga externa de batería reportada. Referencia de **7,05 L/100 km = 14,18 km/L** para ese ciclo, no para todos los trayectos.
- Repostaje documentado el 03/11: **66,865 L al primer corte** de pistola, **~67,0 L completados**. El próximo repostaje se hará idealmente mismo surtidor y primer corte, sin sobrellenar, para comparar condiciones. **No afirmar una segunda carga de 67 L hasta recibir el dato**.
- El tanque declarado es 70 L, pero **70−67≈3 L no demuestra por sí solo una reserva física de exactamente 3 L**. Tampoco implica restar siempre 5,3 L de `litros_display`. Se trata de cantidades diferentes.

### Avance del 10/11/2026 ~09:28 ART: 885 km y 6,0 L OEM

**OBSERVADO / relato del propietario:** después del punto de 868,5 km con 6,8 L OEM, se recorrieron **16,5 km adicionales** (total **885,0 km**). La lectura de combustible descendió a **6,0 L**; diferencia de **0,8 L indicados**. El usuario indicó que parte del avance fue eléctrico, y aún no reportó el bloqueo definitivo del térmico.

- **Diferencia calculada:** (0,8 / 16,5) × 100 = **4,85 L/100 km OEM indicados**; inversa **20,63 km/L** solo como relación aritmética entre descenso OEM y kilometraje del tramo. **No etiquetar como consumo de combustible físico medido** ni añadirlo al balance como caudal real de inyección.
- Hasta el piso de **5,3 L OEM** observado en el ciclo anterior restarían **0,7 L indicados**, NO un litro exacto; si *hipotéticamente* se repitiera la pendiente de los últimos 16,5 km, representarían ~**14,4 km**, equivalentes a **899,4 km acumulados**. Esta extrapolación es **HIPÓTESIS**, no autonomía garantizada. Los kilómetros EV y los retardos de actualización pueden cambiar el resultado.
- El cero de autonomía **extrapolado** desde el último «50 km» visible en el clúster era **885,2 km**; el vehículo alcanzó **885,0 km** sin evidencia de corte térmico definitivo, prueba adicional de que **ese cero no equivale al límite operativo del combustible**. No atribuir un «cero real» a un display que ya muestra rayitas.
- **PENDIENTES:** CSV 10/11; valores de resistencia variable, SOC y RPM al mismo timestamp; evento exacto de corte térmico; volumen ingresado al surtidor tras el corte. **No adelantar el final del ciclo ni incorporar ajustes de firmware/Receiver** en función de este único tramo.
- Mantener el historial del segundo tanque y seguir separando energía eléctrica de combustible. Aun si una relación local de litros OEM/km mejora en ciudad, no demuestra eficiencia instantánea de gasolina para el motor, porque el híbrido alterna energía de batería.

## Calidad de distancia: no promediar sensores incompatibles

Un ensayo en el mismo ciclo informó Jetour **868,5 km**, DriveLab GPS **873,66 km** y app externa **848,6 km**. Las fuentes tienen errores y metodologías diferentes y no deben promediarse. Diferencias contra Jetour: +5,16 km (+0,59%) y −19,9 km (−2,29%), respectivamente.

En un recorrido parcial con **ambos TRIP puestos a cero simultáneamente**: Jetour **73,3 km, 74 min, media 59 km/h**; app externa **68,6 km, 67 min, media 61 km/h**. La diferencia es **4,7 km (−6,41%)** y **7 min**. Es coherente con criterios diferentes de contabilizar movimiento/pausas o intervalos no incluidos, pero **no prueba** cuál contador es físicamente más exacto. Un error exclusivo por diámetro de rueda debería ser más estable, manteniendo las mismas condiciones.

La velocidad Waze GNSS se reporta **2,5 km/h menos** que el velocímetro Jetour, con diferencia aparentemente aditiva constante según prueba del usuario. **No corregir el odómetro del vehículo restando 2,5%**: velocímetro y odómetro tienen calibraciones distintas. Por ahora, **distancia oficial para L/100 km entre cargas = TRIP Jetour**, GPS DriveLab como contraste autónomo, app externa pendiente de validación. No recalibrar registros históricos silenciosamente.

## Especificación del estimador (sin vender precisión inexistente)

1. **Extremos operativos versionados**: «lleno después de corte» = litros realmente aceptados por surtidor; «corte térmico confirmado» = **0 L de combustible utilizable para el térmico**, independientemente de la lectura OEM residual. Asignar evento, timestamp, distancia, SOC y fuente a cada extremo.
2. **Zona alta saturada (≈56 Ω)**: durante el primer tramo el flotante no aporta caudal instantáneo. No afirmar que se descontó físicamente 0,1 L por cada paso de distancia. Solo puede presentarse un **estimador de consumo** por historial de ciclos y uso energético, con incertidumbre, o una tasa real de inyección si llegara a validarse una señal. Mantener el nivel OEM y el estimado en campos distintos.
3. **Zona intermedia**: estimador por curva monótona de resistencia + nivel OEM **sin asumir linealidad**; suavizado con retardo/confiabilidad, sin convertir `litros_crudo` en volumen físico automáticamente. Evitar restar «5,3 L» como constante de toda la curva.
4. **Zona inferior**: priorizar corte operativo observado; mantener un rango de confianza de litros utilizables. Nunca forzar el motor hasta vaciar estando en autopista ni condicionar seguridad a autonomía experimental.
5. **Autonomía**: iniciar con referencia histórica calibrada (~950 km en un ciclo) **y actualizar con evidencia nueva**, no mostrar siempre 1.022 km de fábrica. Distinguir nafta y la contribución energética de batería; considerar SOC inicial/final.
6. **Electricidad independiente**: validar signo/unidad de 1063 voltaje, 1064 corriente, 1083 potencia, 1048/1049 contadores de carga/descarga; integrar potencia HV para kWh si los valores representan efectivamente ese flujo. Separar carga de térmico de regeneración de frenado **solo si un código permite distinguir el origen**; variaciones de SOC por capacidad nominal **son estimaciones**, no medidor de kWh exacto.
7. **Doble TRIP**: TRIP A «tanque», persistente hasta carga confirmada; TRIP B «parcial», reinicio independiente. Distancia, tiempo transcurrido, tiempo de movimiento y parada, litros medidos/estimados, EV/HEV, energía entrante/saliente, SOC, versiones de calibración. Guardar muestras crudas y eventos sin sobrescribirlos.
8. **Integración sin regresiones**: TABLERO T1 como módulo aislado y experimental en **Receiver**; inicialmente lectura y almacenamiento local con tasa controlada, sin abrir páginas de Waze ni modificar el puente cifrado, Sender, mapas, Spotify, controles de ventanillas/AVAS/espejos, firmas o viajes preexistentes. No publicar autonomía calibrada en producción antes de comparar ciclos reales.

### Próximo registro necesario

Cuando suceda de manera natural el segundo **corte operativo definitivo del térmico**: hora, TRIP Jetour, SOC, CI186/menú chino, resistencia si la hay, RPM y estado térmico, archivo CSV del 10/11 y, tras repostaje, litros al primer corte de pistola. Si no está confirmado, dejar evento como **PENDIENTE**. No continuar agotando la batería/combustible deliberadamente para probar una cifra.

## Manifiesto de evidencia CSV privada (SHA-256)

Se registran aquí nombre/hash para verificar integridad. **Los CSV originales NO deben publicarse en este repositorio público**, ya que pueden contener ubicación, horarios y datos personales de uso.

| Archivo local original | SHA-256 |
|---|---|
| `09.csv` | `1a83132b41db6a31cf709800ba4f2e6f2dd82163bdda7f578696276eec89945d` |
| `T1testigo_2026-11-03 (1).csv` | `29cf31ef86d0e6f6c28a79ac6bd0a98798fcb10f1532d1fc42cf5b15d23eb753` |
| `T1testigo_2026-11-03 (2).csv` | `892adde10a815f071b119772ab71f7c82ab6d0fb76e2db5aac5b51b0f5a5172e` |
| `T1testigo_2026-11-03 (3).csv` | `3cbc05b96e3d8a5c45a2f40d4f0a1a5823897c922da868757fae29ede1ca1806` |
| `T1testigo_2026-11-03.csv` | `bd4ced115605a6a88b3c939303d02f88e30cc93732e03ea8b484d49d9bd733ae` |
| `T1testigo_2026-11-04 (1).csv` | `2fd797799925ad84cca6b15954d494a4e0f5e05f9e79b47c287cffc56ba0f76c` |
| `T1testigo_2026-11-04 (3).csv` | `3dcb09a7351d6b769190b3e0e101ce32d52ad1f1e60dd29b0d272ccc97b42c2a` |
| `T1testigo_2026-11-04.csv` | `e06f355d67c819b23bb48010dcfeed6fda6018c4c2c6c926afb1f620afc77678` |
| `T1testigo_2026-11-05.csv` | `c2d745f0446571cacbf24e457b68b980b74562d1985d433635f933abfb7fbe08` |
| `T1testigo_2026-11-06.csv` | `867f60bc11b9179a1b7cd554cf70c62831457e984f7523a802d262d8a34d2208` |
| `T1testigo_2026-11-07.csv` | `255e5af1bfc2d6a4a2d5b499c5a4cc9d484ebf7c05fd7e73e7c7e15c506a5378` |
| `T1testigo_2026-11-08.csv` | `cccc2b2277995f641d3e83552990481232981549034847a899390bfe7549cb43` |
| `T1testigo_2026-11-09.csv` | `8f0b21dcc22f88b46c5d788d49c30f1b13a86cb9de488cf3475539b8de79584b` |

**Convenciones:** CONFIRMADO = lectura/código repetible y contrastado en condiciones definidas; OBSERVADO = evidencia con fuente, pero sin validación independiente; HIPÓTESIS = inferencia/modelo; PENDIENTE = necesita nueva prueba. La deduplicación es una transformación reproducible; no altera archivos originales.
