# Jetour T1 PHEV — combustible, consumo y tablero futuro

**Estado:** expediente de integración, actualizado 2026-11-10. **TODAVÍA NO ES UNA FUNCIÓN DEL TABLERO DE DRIVELAB AUTO 0.2.10.** Esa versión solo incorpora lecturas OEM puntuales CI186, 1042, 1062 y 1064 y CODE.

## Regla principal e irreversible del proyecto

**DESCARTAR EL CÁLCULO VIEJO DE NAFTA DEL T1LAUNCHER.** No utilizar el estimador antiguo de consumo en comparaciones, promedios, calibraciones, validaciones ni indicadores. Se prioriza:
1. Volumen físico cargado por surtidor y distancia del período correspondiente.
2. Lecturas independientes de tanque, menú OEM, CSV y sensores crudos con hora y procedencia.
3. **Cálculo nuevo de nafta usada / medida del tanque** del launcher, como dato independiente por contrastar, nunca confundido con surtidor ni con litros OEM.
4. Estimaciones derivadas, solo cuando indiquen fuente, fórmula, límites de incertidumbre y estado de calibración.

El nuevo cálculo de T1Launcher T1N134 se describe como medición por **variación del nivel de combustible**. El análisis estático no demuestra pulsos de inyección ni cuánto combustible generó electricidad; **no asumir medición de inyectores**. La versión medida puede filtrar, cuantizar o compensar nivel del flotante y debe contrastarse.

## Mediciones reportadas / bitácora de trabajo

| Hecho / momento | Valor | Procedencia y cautela |
|---|---|---|
| Tanque nominal del manual | 70 L | Referencia nominal de fabricante/usuario; no garantiza volumen utilizable. |
| Repostaje tras corte térmico por nivel | 66,865 L al primer corte; ~67,0 L completando | Surtidor y CSV de carga; volumen físico desde el estado de corte de la térmica. |
| Diferencia 70 − 67 | Aproximadamente 3 L | Inferencia de residuo/no utilizables u offset de capacidad; **no medir como reserva exacta**. |
| Nivel OEM en menú con tablero al 0% | ~7,4 L, térmico todavía operativo | Observación in situ; el 0% visual no representa tanque seco. |
| Corte del motor térmico por combustible | ~5,0–5,3 L OEM | Observación de la T1 pasando a EV puro; requiere repetibilidad en otros ciclos. |
| Sensor variable lleno | ~56 Ω | Muestra reportada; saturación de extremo alto posible. |
| Sensor variable a 41,3 L | ~148 Ω | Par de calibración reportado. |
| Sensor variable a 7,4 L | ~288 Ω | Par de calibración reportado. |
| Segundo canal/sensor | ~500 Ω, aparentemente fijo | Observación; su función como reserva está **sin probar**. |
| Cero de tablero | Aproximadamente 8 L según serie; 7,4 L en una captura real | Indicador adelantado frente a reserva operativa; hay dispersión/calibración pendiente. |
| Muestra posterior de nivel | Tablero ~21,6 L, CSV/muestreo ~22,7 L, ~231 Ω | Registros mencionados en conversación de 2026-11-09. No fusionar como una sola lectura. |
| Distancia desde carga en una muestra | 659 km; otra captura del launcher ~672,3 km | Distintas horas/contadores; no mezclar en una fórmula sin alinear timestamp. |
| Kilómetros EV en muestra del launcher | ~160,3 km | Separar distancia EV de la total y validar contador; no inferir litros térmicos por diferencia. |
| Nafta usada, cálculo NUEVO del launcher | ~40,93 L en una muestra | Dato independiente de variación de nivel; puede diferir del descuento neto del tablero. |
| Tanque anterior sin enchufar | ~950 km con ~67 L de surtidor | Observación acumulada de período separado; al relacionar consumo distinguir desplazamiento real y batería/energía inicial. |
| Consumo mostrado T1 de últimos 50 km | ~6,2 L/100 km en un momento | Promedio de ventana corta, no consumo acumulado del tanque. |
| Autonomía visual de tablero en muestra | ~383 km restantes cerca de 659 km | Proyección OEM, no medición física de combustible. |

**Nunca** comparar dos lecturas tomadas en distinta hora o distinto ciclo sin declarar esa diferencia. **Ya se auditaron 13 CSV originales del 03–09/11 (52.880 muestras únicas, con duplicados de exportaciones removidos)**; véase [auditoría de CSV y modelo de calibración del 10/11](CALIBRACION_CSV_T1_2026-11-10.md). Aún faltan el CSV del 10/11, el segundo corte térmico confirmado y el segundo volumen de surtidor antes de ajustar una curva operativa definitiva.

## Cómo interpretar la reserva

La T1 muestra 0% antes de agotar todo el líquido. La térmica aún podía encender cerca de 7,4 L OEM y se detuvo aproximadamente en 5,3 L, según observaciones. Los ~3 L resultantes de 70 L nominal menos ~67 L repostados desde el corte son **otra magnitud**: residuo geométrico/capacidad nominal frente a volumen físico recargado. No son intercambiables automáticamente con 5,3 L OEM ni con el porcentaje 0%.

Modelo preliminar de tres niveles:
- **Nivel OEM reportado:** lectura directa del vehículo (con ID, hora, unidad, canal).
- **Litros utilizables a partir del estado actual:** estimación calibrada a partir de lo que el térmico efectivamente puede consumir, con offset según punto de corte y surtidor.
- **Litros totales físicos/residuales:** hipótesis de capacidad nominal y contenido no accesible al térmico; no confundir con autonomía operativa.

Al crear el tablero, presentar aparte “nivel informado”, “utilizables estimados” y “reserva de operación/corte” con etiqueta ESTIMADO hasta validar.

## Flotante y curva de calibración

El sensor variable **no parece lineal en todo el recorrido**: satura cerca de 56 Ω antes de completar el repostaje y el 0% de la T1 ocurre con varios litros todavía informados. Conservar pares (Ω, litros, fuente, timestamp, temperatura, repostaje, estado P/EV/HEV) y construir tabla monótona a partir de puntos reales. No extrapolar linealmente a 0/70 desde solo 2 o 3 puntos y no tratar 500 Ω constante como “reserva” sin prueba física.

Validación mínima: al menos varias cargas y descensos de nivel, con datos de surtidor, CI186/menú OEM, lectura resistiva cuando esté disponible, nuevo contador de nafta usada y distancia por mismo tramo temporal; comparar error y saturaciones. El aforo podría variar por inclinación o filtrado del BMS/ECU.

## Híbrido enchufable: cómo no distorsionar el cálculo

Cuando funciona el térmico, una parte de su energía puede mover la camioneta y otra generar electricidad para la batería o alimentar consumos eléctricos. Regeneración y energía desde enchufe son fuentes distintas. Por ello:
- **Litros reales de nafta:** por surtidor/descenso de tanque calibrado, no por multiplicar horas de motor por una tasa supuesta.
- **Distancia total / distancia EV / distancia HEV:** contadores diferenciados, con método documentado.
- **Energía cargada de red (kWh), batería descargada, regeneración, potencia HV:** no sumarlas sin definición de signo y origen; evitar doble conteo.
- **Consumo del período** L/100 km y km/L calculado con volumen y distancia del mismo período; estimaciones sin repostaje etiquetadas.
- La batería BMS ha mostrado referencias de ~81 Ah y cargas reportadas ~20–22 kWh; no asumir que todo el nominal es energía utilizable ni capacidad fija.
- En modo EV azul se observó que la batería puede llegar a 0% mostrado, con arranque térmico y un retardo para avanzar en D hasta recuperar ~2–2,5% de SOC; es una observación operativa, **no una especificación técnica oficial**.

## Identificadores de telemetría (estado de confianza)

Los IDs se encontraron en el catálogo del APK T1Launcher T1N134 y/o en estudios de DriveLab T1; **el nombre del catálogo no verifica que todos funcionen en la versión argentina**.

| ID y canal | Posible significado | Situación en DriveLab Auto 0.2.10 |
|---|---|---|
| CiLink transaction 186 | Litros de combustible OEM | **Lector puntual implementado**; CI186 contrastado anteriormente con menú oculto en DriveLab T1, validar en Receiver de nuevo. |
| CiLink 222 | Trip A | Candidato de análisis estático, **no implementado** en nuevo Receiver. |
| Vehicle 1035 | RPM térmico | Candidato, pendiente. |
| Vehicle 1041 | SOC protegido | Candidato, pendiente. |
| Vehicle 1042 | SOC interno | Lector puntual implementado; escala/validez a contrastar. |
| Vehicle 1044 | Flujo energía | Candidato, pendiente. |
| Vehicle 1048/1049 | Contadores cargada/descargada | Candidatos, pendiente. |
| Vehicle 1050/1052 | Temperaturas extrema BMS | Candidatos, pendiente. |
| Vehicle 1054/1055/1056/1057 | Tensiones/números de celdas extremas | Candidatos, pendiente. |
| Vehicle 1058 | Aislamiento HV | Candidato, solo diagnóstico. |
| Vehicle 1062 | SOC mostrado | Lector puntual implementado; contrastar con tablero. |
| Vehicle 1063 | Voltaje HV | Candidato; intentos anteriores de readFloat fallaron. |
| Vehicle 1064 | Corriente HV | Lector puntual implementado; signo y escala pendientes. |
| Vehicle 1079 | % combustible | Candidato, pendiente. |
| Vehicle 1083 | Potencia HV | Candidato, pendiente. |
| Vehicle 1084/1085 | Consumo kWh/100 km y L/100 km | Candidatos; no usarlos para sustituir consumo real de tanque. |
| Vehicle 2148 | Velocidad | Candidato, unidades pendientes. |
| Vehicle 2004–2007 | Estado y mandos ventanillas | Lecturas y mandos acotados en VEHÍCULO; validación física del nuevo Receiver pendiente. |
| Vehicle 2054 | Estado general ventanas | Señal de estudio (lectura); no atribuir significado sin prueba. |
| Vehicle 2074 | Alarma de peatones | Toggle manual integrado; nunca desactivar al arrancar. |
| Vehicle 2008 | Techo corredizo | Observados valores 10/20 y 60/80 en pruebas previas; no escribir ni incluir botón todavía. |
| Vehicle 2505 | Señal auxiliar de maniobra sin identificar | En detector de cambios, no asignar etiqueta definitiva. |

En el detector v0.2.10 se consultan 2004, 2054, 2505, 1042, 1062 y 1064 durante 45 s, solo lectura. En CODE el lector de 25 IDs usa getters conocidos, devuelve errores/sin valor si corresponde y permite CSV. No interpretar un ID porque cambia una vez sin repetibilidad.

## Fuentes y diseño de datos del futuro tablero

Guardar cada muestra con: identificador, canal OEM/CSV/menú/surtidor, fuente concreta, timestamp, valor crudo, tipo int/float, unidad solo si validada, versión app/firmware, estado calidad, modo EV/HEV y notas de calibración. Separar las tablas conceptuales **MUESTRAS CRUDAS**, **EVENTOS DE CARGA**, **CALIBRACIONES VERSIONADAS**, **TRAMOS/VIAJES** y **RESULTADOS DERIVADOS**. No reescribir datos crudos tras mejorar una fórmula.

Vista propuesta:
- Litros OEM / porcentaje OEM / litros reales estimados / reserva operativa estimada, cada uno con su etiqueta.
- Última carga y litros de surtidor; nafta usada nueva desde el inicio de ese ciclo, con correcciones por repostajes parciales.
- Autonomía conservadora, normal y optimista, calculadas a partir de consumo **real del período** y litros **utilizables** con rango de confianza.
- Distancia total, EV, HEV, L/100 km, km/L, SOC interno/visible, corriente/tensión/potencia HV si están validados, energías cargada/recuperada y modo de operación.

Se generaron dos maquetas de tablero con ~21,6 L, ~22,7 L, 659 km, 15,3 km/L y proyecciones de autonomía; **no concuerdan** en “reserva”/“total”: una usa ~5,3 L de reserva y ~26,9 L total, otra ~3 L y ~25 L. Son **diseños ilustrativos e hipótesis**, no calibración final ni especificación de pantalla.

## Próxima tarea concreta en la conversación de consumo

1. Consolidar datos originales CSV/surtidor con timestamp y columnas.
2. Fijar definiciones operativas de litros OEM, litros de surtidor, litros de combustible usados **nuevo cálculo**, litros recuperables, reserva y distancia.
3. Calibrar función de flotante con tolerancia, saturación y error; ensayar al menos un nuevo ciclo antes de publicar autonomía.
4. Diseñar módulo de lectura local y estado persistente sin tocar el puente LAN.
5. Incorporar al Receiver mediante un módulo TABLERO experimental, sin borrar la app antigua ni alterar viajes existentes.

## Fuentes para profundizar

- Informe privado de análisis estático T1Launcher T1N134, 2026-11-04 (muestra SHA-256 6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480).
- Repositorio privado: https://github.com/bruceferra/drivelab/tree/main/drivelab-t1
- Fuentes del nuevo Receiver: VehicleReadOnly.java, EnergySignalPolicy.java, FuelReadingPolicy.java, VehicleBatchReader.java y VehicleChangeScanner.java.
- Conversación de consumo Jetour T1, muestras reportadas entre 2026-11-03 y 2026-11-09.
