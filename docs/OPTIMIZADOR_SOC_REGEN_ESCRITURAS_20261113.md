# Receiver 0.2.65 — Auto SOC/regen + Eco Score + autonomía dinámica

**13 noviembre 2026.** Comportamiento nuevo, experimental, solicitado por propietario Jetour T1.

## 1. Optimizador en movimiento (D)

El perfil por tipo de vía en `T1OptimizedModeStore` incluye EV/HEV/EV MAX (1043), SMART o FORZADO (1040), SOC objetivo cuando es FORZADO (1041) y recuperación baja/media/alta (1046). Todo ello se considera automáticamente cuando MODO OPTIMIZADO está ON, tras clasificación OSM estable, muestras OEM de marcha/velocidad válidas, repetición de clasificación y espera anti-oscilación.

- **No se exige detenerse** al pasar de colectora a autopista. Admite D con velocidad OEM finita 0–160 km/h; P únicamente inmóvil. No escribe en R, N ni con velocidad desconocida.
- Se reutiliza el Binder OEM `IVehicleControl`, getter 5, setter 15, float 2148 getter 6, comprobaciones dobles de marcha/velocidad. Se manda **una** escritura por propiedad distinta al perfil y se verifica readback. Ante error, no reintenta en bucle, intenta restaurar cambios del grupo y desactiva AUTO.
- Se retiraron los botones de desbloqueo de escrituras uno a uno, por petición explícita. El usuario confirma manualmente si el vehículo responde. **Advertencia:** que el OEM cambie un ajuste en marcha no prueba que la escritura vía DriveLab esté autorizada. Esta ruta está marcada como EXPERIMENTAL hasta comprobarla en un viaje real.
- Valores admitidos: 1040=2 SMART,4 FORZADO; 1041=20/30/40/48/50/56/60/64/70/72; 1046=1/2/3. El valor 1040=3 NO se escribe.
- El modo 1043 anterior sigue siendo el mismo controlador probado. Ninguno de los cambios usa root ni modifica servicios del sistema.

## 2. Telemetría y análisis económico/ecológico por viaje

Referencias del repo Uber `bruceferra/drivelab`: `RoadMixMath.java` distingue ciudad/carretera/autopista/sin clasificar sin inferir autopista por velocidad; `SpeedMath.java` usa rangos de 5 km/h.

Se crean estas clases nuevas:
- `T1EcoTripMetrics`: acumula km y segundos por las cuatro categorías OSM y por bandas 0–5, 5–10, ... 195–200 y 200+ km/h. Calcula cobertura y puntuación **Eco v1**, con aceleraciones y frenadas estimadas de la señal de velocidad OEM y corrección de contexto por proporción urbana. **No equivale a eficiencia energética medida.**
- `T1EcoTripMonitor`: se ejecuta dentro del grabador de TRIP OEM. Cada intervalo utiliza **exactamente la distancia del grabador** (calibración de velocidad OEM y respaldo GPS existente) para evitar dobles odómetros. Un OSM offline PBA/CABA clasifica la vía y se conserva un `eco_<tripid>.csv` sin coordenadas ni VIN; es un sidecar separado del CSV existente. Publica el resumen en vivo de Total, Viaje 1 y Viaje 2, respetando pausas.
- `T1EcoTripReport`: lee el sidecar del TRIP y permite filtrar el periodo de Viaje 1 o Viaje 2, incluso si comparten un mismo CSV principal. Para viajes históricos sin sidecar, analiza en segundo plano columnas de GPS/velocidad del CSV original con mapa offline local. No transmite rutas GPS a un servidor.
- El Cockpit muestra los porcentajes y Eco Score en vivo; en el historial se muestran % y km de cada vía, bandas de 5 km/h con km y duración, y el detalle del puntaje por cada viaje. La lectura pesada ocurre en background y no en la interfaz.
- **Calidad**: si falta el mapa, el GPS es ambiguo o hay demasiadas muestras inválidas, la vía es `SIN CLASIFICAR` y el rating puede ser `ECO N/D`. No inferir automáticamente categoría por ir a 130 km/h. Historial reconstruido indica cobertura parcial.

### Desglose vial en SEIS estados: normal / congestión probable

Se amplía la clasificación en vivo, Viaje Total, Viaje 1, Viaje 2 y los detalles de históricos a:

1. CIUDAD NORMAL vs CIUDAD CONGESTIONADA.
2. CARRETERA NORMAL vs CARRETERA CONGESTIONADA.
3. AUTOPISTA NORMAL vs AUTOPISTA CONGESTIONADA.

La **base del tipo de vía** sigue siendo 100% geometría OSM (no se deduce por velocidad), y usa `maxspeed` publicado por OSM cuando existe, o una velocidad de referencia heurística por clase de vía cuando no existe. Cada intervalo de velocidad OEM queda clasificado en flujo normal o *congestión probable*, solo después de una secuencia de circulación lenta en la misma categoría: **35 s ciudad, 25 s carretera, 20 s autopista**. Las detenciones más cortas en semáforos o rampas se mantienen en NORMAL provisional. Se conserva el porcentaje SIN CLASIFICAR cuando el GPS o la vía no son concluyentes; nunca se convierte en ciudad por defecto.

Los porcentajes normal/congestionada se calculan sobre **todos los km válidos del viaje** (sumar las seis categorías más sin clasificar da 100% aproximadamente, salvo redondeo); cada fila contiene también km y minutos. Se incluye la fracción de **tiempo** lento dentro de cada vía: un atasco parado puede consumir mucho tiempo aunque sume pocos kilómetros.

Los límites OSM y el tráfico real no son lo mismo: la app **infiere congestión probable**, no recibe reportes Waze ni un feed de tráfico. No confundir baja velocidad sostenida por obras, rampas, clima o conducción prudente con congestión verificada. Es una clasificación experimental/comparable de los recorridos, no información vial oficial.

Se agregó `T1TrafficFlowPolicy.java` y `T1TrafficFlowPolicyTest.java`. El sidecar ahora guarda `osm_limit_kmh`, con lector compatible con el formato anterior de 6 columnas; nunca almacena lat/lon.

### Eco Score v1 (0–10)

Se calcula únicamente cuando existen al menos 2 km, 120 s y 12 intervalos de calidad, cobertura OSM mínima 50% y aceleraciones válidas en 55% del tiempo. Penaliza la duración y frecuencia de aceleraciones/deceleraciones bruscas (de velocidad OEM), ajustadas por fracción urbana. Conducción en autopista **no recibe penalización fija**: es una métrica de suavidad de conducción contextualizada. El coste real nafta+red o eficiencia de combustible NO se incorpora hasta disponer de medición de combustible calibrada; así se evita asignar un supuesto rating energético a partir de datos experimentales.

## 3. Consumo reciente cada 500 m y autonomía adaptativa

- `ClusterInteraction` provee callbacks NAFTA 500 m (registrar 135) y ELECTRICIDAD 500 m (133), además de promedios NAFTA/ELÉCTRICO 50 km (137/139). Los callbacks 500 m son `float` crudos; su escala L/100 no está contrastada aún con surtidor ni pantalla Jetour. 1086/1087 getter pueden devolver -1, por ello se usa el listener independiente.
- `T1OemConsumptionCallbacks` guarda último valor y timestamp de callback y **media de los últimos cinco segmentos OEM 500 m**. Filtro: valores finitos de 0 a 35 y frescura de hasta 180 s, sin descontar intervalos faltantes.
- `T1LiveConsumption` expone último valor 500 m **sin unidad probada** y proyecta L/100 **candidato/experimental** con mínimo tres segmentos y promedio >2.5 L/100; si no, recurre al promedio OEM 1085 (50 km) si es plausible o a la calibración de surtidor/manual, dejando la fuente visible. Un tramo EV con 0 de nafta NO produce autonomía infinita.
- `T1ExperimentalRange.estimate` mantiene la fórmula experimental original para gasolina: `max(0, litros_ci186 − 5.3) × 100 / l100`; y EV: `SOC × km_ev_full / 100`. No duplica kWh ni trata el SOC como combustible. CI186 saturado 60.5+ da **sin estimación confiable**; nunca finge kilómetros precisos con depósito lleno. El estimador cambia cuando la media de 500 m cambia y se actualizan las muestras, **no predice lo que ocurrirá a una velocidad futura sin haberla recorrido**.
- En el Cockpit se muestran simultáneamente valor 500 m bruto, promedio reciente experimental, fuente activa, autonomía nafta y EV y total. Siempre son estimaciones, no rango Jetour ni medidor homologado.

## 4. Conservación de datos y compatibilidad

- No se cambia cabecera ni filas del histórico `trip_*.csv`, tampoco se sobreescriben `client_trips.csv`, `client2_trips.csv`, viajes anteriores ni Sender.
- Sidecars `eco_*.csv` son locales y contienen tiempo, intervalo de distancia, segundos, clase vial, velocidad y aceleración estimada, **sin GPS lat/lon**.
- Se respeta el mapa SQLite PBA+CABA de `T1OfflineRoadMap` existente. Viajes fuera de cobertura pueden quedar sin clasificación.
- Las preferencias de carburante se guardan aparte (`t1_live_consumption`); no se envían datos de trayecto a servidores externos.

## 5. Prueba en la camioneta

1. Con Receiver actualizado, encender Optimizado y recorrer con prudencia ciudad, carretera y autopista dentro de límites legales. Ver en original Jetour si FORZADO, SOC objetivo y regeneración corresponden al perfil. Si la app informa rechazo OEM o desactiva AUTO, compartir informe; **no asumir que una escritura cambió algo físicamente**.
2. Abrir Cockpit: verificar % de autopista/ciudad en vivo y si la diferencia coincide con OSM. Si map matching ambiguo, observar `SIN CLASIFICAR`.
3. Con viaje activo, comprobar que aparece un evento OEM de 500 m y su valor cambia tras varios segmentos de conducción; comparar también con el consumo del tablero Jetour. No confundir el 1085 promedio de 50 km con un valor instantáneo.
4. Observar si autonomía estimada responde a cambio sostenido de ritmo. Los primeros 500 m pueden ser insuficientes; después de tres eventos válidos, la app usa una media de hasta cinco tramos. Si el dato falta, la fuente de fallback aparece claramente.
5. Finalizar Viaje 1/2, abrir Historial → elegir viaje: verificar ECO, desglose vial y tabla de intervalos de velocidad. La app puede reconstruir viajes viejos sin modificar sus archivos originales, usando solo el mapa local.

**Seguridad:** No conducir mirando los reportes detallados, ni probar control de regeneración en tráfico exigente. Si la electrónica no acepta la escritura experimental o devuelve lectura discrepante, dejar optimizador OFF hasta analizar el log OEM. El usuario sigue controlando los modos físicos del vehículo.
