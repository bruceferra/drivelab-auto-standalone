# Receiver 0.2.34 — cockpit moderno, jornada, cliente y zonas GPS (11/11/2026)

## Implementación y límites de la versión

Receiver 0.2.34 introduce **COCKPIT T1 · JORNADA / CLIENTES / COSTOS** en la pantalla principal, accesible también desde el tablero técnico. Mantiene intactos el Receiver LAN y Sender v0.2.16, el actualizador por APK firmado, la vinculación QR, las grabaciones históricas, los callbacks OEM, el CSV del TRIP maestro y los controles existentes.

**No** modifica el tablero original de la Jetour ni lo sustituye como pantalla homologada. El velocímetro nuevo solo presenta la señal OEM que recibe DriveLab.

### Diseño

* Nuevo velocímetro circular grande con escala 0–200 y número central, fondo oscuro, transición de color, barras de litros OEM CI186 y SOC, modo OEM 1043 EV/HEV/EV MAX; los valores ausentes muestran SIN LECTURA.
* Un solo bloque de métricas prioritarias: distancia del TRIP maestro, nafta candidata en litros, balance eléctrico neto candidato en kWh, **costo parcial de la nafta**. No mostrar gráficos de diagnóstico ni señales de prueba en primer plano; el tablero técnico anterior sigue accesible mediante un botón.
* Información de costes que distingue **datos experimentales** de lecturas confirmadas. La distancia sigue siendo **VELOCIDAD_OEM_ESTIMADA**, pendiente de validar con código Trip A 222. **No se aplica una corrección arbitraria del 5,4 %.**

### Un día de trabajo / transporte

1. **INICIAR JORNADA** inicia el servicio existente `T1TripRecorderService`: muestras cada ~3 s, callbacks OEM y CSV central, incluso en segundo plano. Una jornada no se reinicia por cliente.
2. **INICIAR TRAYECTO** acepta un nombre opcional (p. ej., Cliente, Traslado, Vuelta). Guarda localmente un *checkpoint* del mismo `trip_id`: hora, distancia acumulada, conteos de callback y sumas crudas OEM; no guarda coordenadas ni datos identificatorios del pasajero.
3. **FINALIZAR TRAYECTO** hace la diferencia entre checkpoints, guarda un registro CSV append-only privado y muestra distancia, litros experimentales y costo parcial de nafta. No detiene la jornada.
4. **HOY** y **ESTE MES** suman exclusivamente trayectos cerrados, para **no duplicar** el TRIP maestro y sus subviajes. Muestra últimos 5 trayectos.
5. **CERRAR JORNADA** pide confirmación y no se permite mientras un trayecto cliente está abierto; el CSV maestro se conserva intacto.
6. Se añadió **CARGA ELÉCTRICA**: el usuario informa kWh *medidos externamente* en el cargador y ARS/kWh, crea registro privado `grid_charges.csv`. El total mensual de red queda separado del costo por cliente para evitar imputación doble.

### Fórmulas: todas provisionales hasta medir físicamente

* `nafta_litros_candidata = delta(suma_raw_NAFTA_500_m) × 0.005` **si** cada callback representa un tramo nuevo de 500 m y la unidad es L/100 km.
* `electricidad_neta_kwh_candidata = delta(suma_raw_ELÉCTRICO_500_m) × 0.005` **si** unidad y segmentación equivalentes. Puede ser negativa. **No equivale a energía comprada de red ni automáticamente a kWh recuperados por frenado**.
* `costo_nafta_experimental = litros_candidatos × precio_nafta`, **no** costo total del trayecto. Cuando no hay callbacks, se muestra «sin dato», no falso cero.
* El costo real eléctrico por viaje **NO** se calcula desde la electricidad neta ni desde los contadores HV vacíos; primero se necesitan medidas de carga externa, energía de batería/recuperación y un criterio de asignación al trayecto.
* Para la prueba real de 50 km `20261111_183400`: Jetour 50,0 km, DriveLab 52,699 km; 99 pares OEM; 2,475 L candidatos, −0,790 kWh netos candidatos. No modificar históricos.

### Modos EV/HEV según GPS — Fase segura

* Se pueden guardar hasta cinco zonas GPS locales con un nombre y destino sugerido EV o HEV, radio 300 m, solo con GPS reciente (≤120 s), precisión ≤75 m y coche confirmado en **P a 0 km/h**.
* Al abrir el panel avanzado, sugiere el modo según la zona, comparándolo con el **1043 leído**. Esta versión no ejecuta cambios de propulsión, no mantiene geofencing en segundo plano y no sube ubicaciones al servidor.
* **ENSAYO EV/HEV · SOLO LECTURA** exige vehículo detenido en P. Inicia la captura OEM de 3 minutos existente; el conductor prueba los cambios en los controles Jetour originales y puede exportar CSV de 1043. No existe `setInt`/transacción de escritura PHEV desde el cockpit nuevo.
* Los DEX de ALVehicleSettings contienen `KeyPHEVPowerMode` y propiedades HAL de escritura, pero **no** se validaron permisos, condiciones, escalas ni readback de un setter desde app de tercero. No usar automatización GPS para tren motriz en marcha.

## Archivos y compatibilidad

* Nuevo: `T1PremiumCockpitActivity.java`, `T1PremiumInstrumentView.java`, `T1JourneyStore.java`, `T1JourneyMetrics.java`, `T1ZonePolicy.java`, `T1ModeZoneStore.java`.
* Modificado: `T1TripRecorderService` persiste sumas RAW de 500m adicionales en sus SharedPreferences; `T1OemConsumptionCallbacks` suma RAW mientras conserva cada evento completo; `T1TripTelemetry` consulta readInt(1043) sin setter.
* No se altera el esquema/filas del CSV maestro ni los archivos anteriores. El histórico de clientes se guarda como `client_trips.csv` en directorio privado del Receiver; cargas en `grid_charges.csv`. No se sincronizan con Supabase para evitar enviar nombres o ubicaciones.
* Tests Java JVM de deltas cliente y geocercas; QA Android y firma con la misma clave anterior antes de instalar.

## Prueba física requerida (vehículo detenido para controles)

1. Actualizar Receiver 0.2.34 sobre el anterior, sin desinstalar. Comprobar que están los viajes históricos y Sender 0.2.16 intacto.
2. Iniciar jornada, esperar 1–2 muestras, iniciar trayecto «Cliente prueba» y recorrer normalmente SIN operar el panel al conducir; al detenerse, finalizar cliente y verificar registro de hoy/mes.
3. Sin cliente abierto, cerrar jornada y comprobar que se conserva el CSV del TRIP maestro y el CSV de eventos OEM.
4. Para zona GPS EV/HEV, con vehículo en P y GPS confiable, guardar zona, verificar que solo recomienda modo. Ensayo OEM 1043 de 3 min cambiar solo en controles de fábrica, volver y exportar CSV. No escribir 1043.
5. Cargar un valor real de kWh medidos y precio, verificar total separado del costo cliente. Ningún costo eléctrico por cliente se declara real hasta calibración.

## Siguientes prioridades

1. Leer y comparar **Trip A OEM 222** en un ensayo autorizado de solo lectura, validar unidades frente a tablero original; usarlo en vez de integrar velocidad si coincide.
2. Validar consumo de nafta con surtidor y segmentos OEM, y electricidad con kWh de red medidos y SOC; luego incorporar total ARS/100 km y equivalente monetario L eq./100 km con unidades confirmadas.
3. Identificar interfaz OEM oficial para cambio EV/HEV, condiciones en P, firma y autorización del fabricante; solo después considerar un mando manual y más adelante recomendaciones automáticas con consentimiento explícito. Nunca activar cambios silenciosos en marcha.
