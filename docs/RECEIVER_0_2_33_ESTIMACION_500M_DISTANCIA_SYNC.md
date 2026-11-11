# DriveLab Auto Receiver 0.2.33 — estimación 500 m, auditoría de distancia y sincronización

## Por qué existe esta versión

Análisis de los CSV privados del TRIP **11/11/2026 16:40:21**:
- 1.213 muestras sincronizadas, recorrido Receiver **10,412 km** y 19 pares `NAFTA_500_m` + `ELÉCTRICO_500_m`.
- Suma de valores crudos: nafta **150**, electricidad **+60**.
- Interpretación **hipotética** si cada valor fuera consumo por 100 km para el intervalo de 0,5 km: **0,750 L** y **+0,300 kWh netos**. No son magnitudes calibradas.
- El nivel OEM CI186 pasó de **56,7 a 55,9 L**; concordancia interna aproximada, no validación independiente por surtidor.
- Entre 19 eventos, 18 intervalos de 500 m deberían medir 9,000 km; el TRIP registró 9,950 km entre el primero y el último, factor **1,106**. No corregir kilómetros automáticamente sin confirmar odómetro.

## Qué implementa la 0.2.33

1. **TABLERO T1 / TRIP**: sección adicional de lectura **experimental**, sin reemplazar datos PROXY existentes. Suma exclusivamente pares completos de callbacks OEM de 500 m (`NAFTA_500_m`, `ELÉCTRICO_500_m`), sin deduplicar valores iguales ni sumar notificaciones móviles de 50 km. Muestra conteo, suma cruda y equivalencias solo **SI** la unidad fuera L/100 km o kWh/100 km. Excluye eventos huérfanos.
2. **Auditoría de distancia sin escritura**: cruza timestamps de primer y último callback con el CSV principal del mismo TRIP (hasta 10 s de separación) y muestra km por intervalo OEM, relación frente a 0,500 km y origen `GPS` o `VELOCIDAD OEM (SIN CALIBRAR)` leído de la columna original `notes`. No aplica factor sobre distancia, costos, consumos históricos ni al vehículo.
3. **No bloquear UI**: lee los CSV locales en hilo de fondo y actualiza la pantalla aproximadamente cada 12 s. Son ficheros de solo lectura; las grabaciones originales no se pisan, no se publican GPS ni VIN.
4. **Sincronización**: conserva todos los cursores por viaje y prioriza los archivos `trip_YYYYMMDD_HHmmss.csv` **más recientes primero**, con límite de **4 lotes de 40 muestras por archivo por pasada** (12 lotes máximo). Así el viaje actual comienza a llegar a la nube aunque existan históricos grandes, y los antiguos terminan de sincronizarse en pasadas siguientes.
5. **Pruebas automáticas**: fixture sintético de los 19 pares reales de 11/11 (incluye negativos, cero, pares invertidos y callbacks repetidos), conversión hipotética 0,750 / 0,300, relación 9,95/9,00, desecho de eventos inválidos/incompletos. QA compila las dos APK independientes.

## Invariantes

- Solo lecturas OEM; sin comandos sobre ventanillas, AVAS, espejos, tren motriz ni firmware.
- **Sender no cambia**: permanece **0.2.16/code17** con la vinculación actual y navegación Uber oculta.
- **Receiver**: **0.2.33/code34**, mismo paquete y certificado de firmado que la 0.2.32. Release independiente `v0.2.33` con APK y `receiver.json`; `sender.json` idéntico al de la release anterior, ofreciendo Sender 0.2.16.
- Actualización desde **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA**, sin desinstalar ni volver a vincular, solo después de verificar release exitosa.
- Los valores negativos pueden reflejar generación, recuperación o balance neto; **no clasificarlos automáticamente como frenada regenerativa**.
- `hvIn/hvOut` permanecen en cero si voltaje HV no está disponible; **no sustituirlos** por la hipótesis OEM.
- Para confirmar litros y kWh: pruebas en trayectos suficientes con odómetro físico, registros BMS y cargas reales en surtidor/enchufe; 100 segmentos contiguos permiten comprobar la ventana móvil de 50 km.

## Estado de pruebas de hardware

Sin validación física de esta versión en Jetour. La firma, compilación y automatizaciones deben pasar CI y publicación antes de ofrecerla como actualización disponible.

Ver [análisis de los dos registros del 11/11](ANALISIS_TRIP_20261111_164021_CSV_OEM_500M.md).
