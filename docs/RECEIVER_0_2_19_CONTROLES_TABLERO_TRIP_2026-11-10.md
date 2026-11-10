# Jetour T1 — Receiver 0.2.19: barra de controles, AUTO espejos, aire interior, tablero y TRIP

**Fecha de investigación:** 10/11/2026. **Estado en código:** integración realizada en una rama y sujeta a compilación y prueba física; **NO confundir con validación funcional en el vehículo**. Fuente de verdad: [índice maestro](INDICE_MAESTRO.md), [dos repostajes](CIERRE_SEGUNDO_TANQUE_T1_2026-11-10.md), [calibración](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md), [espejos](ESPEJOS_AUTO_R_NO_R_10KMH_2026-11-10.md).

## Cambios físicos confirmados por propietario

| Señal | Valor / regla | Evidencia / interpretación |
|---|---|---|
| Marcha **1033** | **1 P, 2 R, 3 N, 4 D** | **CONFIRMADO en la T1 argentina**; la cámara original oculta UI en R, pero se pudo confirmar el valor |
| Retrovisor derecho **2069** | **1 subir / 2 bajar**, liberar con 0 | Movimiento físico probado |
| Retrovisor izquierdo **2068** | Ambas direcciones físicas probadas; se plantea la misma polaridad 1 subir/2 bajar | **SENTIDO 1/2 izquierdo pendiente de validación inequívoca por código**; activación AUTO requiere prueba supervisada |
| Espejos plegados **2003** | **0 plegar / 1 desplegar** | **Ambas escrituras funcionan** desde Receiver v0.2.18, confirmado por propietario; **no enviar 0 como liberación** |
| Recirculación HVAC **1004** | **1 recirculación interior ENCENDIDA / 2 apagada, puede entrar aire exterior** | Observación del propietario: 2→1 al activar, 1→2 al desactivar. **Es observación de transición, escritura desde Receiver aún pendiente de prueba** |
| Aviso de cambio HVAC **2101** | Durante transición 1→0 con aviso | Observado como evento; **SOLO LECTURA**, no escribir 2101 |
| Ventilación total/cierre total | 2004–2007 | Funciona físicamente, inalterado |
| Alarma peatonal | 2074 alternar | Funciona en UI existente con comprobación P/0, inalterado |

## Barra de controles en pantalla principal (sin entrar a submenú)

`ReceiverQuickControls` ocupa la **primera franja** de la Activity principal y usa una disposición apaisada de 4 botones por fila, color de fondo distinto por función, sin popups de confirmación ni desplazamiento del scroll. Tiene:
- **PLEGAR, DESPLEGAR:** por 2003, lectura previa y comprobación P/velocidad cero, una orden si estado distinto; bloqueados si los espejos están en modo AUTO para evitar plegar mientras el motor ajusta ángulo.
- **ESPEJOS AUTO / MANUAL**, **BAJAR ESPEJOS / SUBIR ESPEJOS**; pulsos por espejo editables IZQ ±, DER ±, **1–10, por defecto 2 por lado**. Cambiar valores requiere MANUAL; los movimientos manuales desarman AUTO y se aceptan con el vehículo inmóvil (marcha P/R/N/D conocida).
- **VENTILACIÓN TOTAL, CIERRE TOTAL**: secuencia ya validada en `VehicleWindowGroup`, sin mezclar con navegación.
- **ALARMA PEATÓN**: un toque, P/0 obligatorio.
- **SIEMPRE AIRE INTERIOR** y **AIRE AUTO**. **Sin aire exterior** implica **recirculación ENCENDIDA = 1004=1**; no confundir con «recirculación apagada» que significa aire exterior.
- **TABLERO / TRIP**: abre los instrumentos nativos dinámicos.

## Espejos automáticos — protocolo de maniobra

Implementación dedicada `MirrorAutomationService` con `MirrorAutomationOem` y FSM `MirrorParkingPolicy`:
1. Modo AUTO **OFF de fábrica**. Solo el propietario arma desde la pantalla, con su pose normal D físicamente ajustada. No se autoarma tras reinicio/boot porque desconocemos la posición física.
2. **Transición fresca conocida hacia R=2** desde otra marcha y estando casi parado (**≤0,5 km/h según getter OEM**) → **una sola secuencia de bajada**, que se interrumpe si deja R o supera 8 km/h.
3. Durante maniobras R ↔ D/N a ≤10 km/h **no se repite ni se restaura**.
4. Cuando marcha OEM **conocida !=R** y velocidad **>10 km/h 800 ms seguidos**, devuelve los pasos previamente contabilizados, una vez; corta si se reingresa R, baja la velocidad o supera umbral de seguridad. Se cuenta cada escritura solo después del `release=0`, **no se afirma ángulo físico equivalente**.
5. Una falla, desconexión o secuencia parcial **DESARMA AUTO** y marca pose `INCIERTA`; el usuario puede restaurar manualmente con mandos originales. Los manuales de la barra solo actúan detenidos, con una orden por vez.
6. Pulsos **500 ms**, cada uno + liberación 0, separados brevemente. El propietario comprobó que **500/1.000/2.000 ms producen el mismo desplazamiento**, así que no simular carrera continua. Un ciclo de muchos pasos demora varios segundos y exige un ensayo de 1 paso por lado antes de aumentar. **Código izquierdo 1/2 y unidad de velocidad en movimiento requieren validación en nueva app**.

**Importante:** el primer movimiento automático de espejo mientras se coloca R NO está validado físicamente en esta versión. Plegado sí está validado pero es otra propiedad. No usar auto sin supervisión inicial.

## Recirculación HVAC con preferencia persistente

`RecirculationGuardService` independiente de `ReceiverService` (Waze) y de espejos:
- **AIRE AUTO:** no escribe nada y detiene el monitor.
- **SIEMPRE AIRE INTERIOR:** opt-in, muestra notificación persistente y comprueba `1004` cada ~3 segundos. Si =1 no escribe; si =2 solicita **una** escritura OEM `1004=1`, relee y guarda estado; consulta `2101` únicamente como señal de aviso.
- **No escribe 2101 ni 1004=2**. Si el climatizador devuelve repetidamente 1004=2 (**3 veces/min**) o si hay 4 lecturas fallidas seguidas, el monitor **se pausa** y exige reactivación: no pelear con desempañado/ECU. No garantiza hermeticidad ni sustituye el control de desempañado del vehículo.
- Puede reanudarse tras reinicio del vehículo solo si el propietario dejó este modo activo y **no estaba pausado**. AUTO es comportamiento por defecto.

## TABLERO T1 animado y navegación sin interferencias

`T1InstrumentView` es una vista nativa 1200×490 escalable, con **3 agujas** y lectura OEM periódica al estar visible:
- Velocidad de `2148 float` / `1089 int` si se obtiene, como OEM; no se inventa velocidad cuando falta lectura.
- Nafta **CI186 OEM** (indicador) como aguja de 0–67 L, **NO afirmar litros físicos exactos**.
- SOC `1062` (tablero) o `1042` (interno) si disponible; **NO** etiquetar como kWh reales.

**Proyección al clúster:** el propietario confirma que su T1Launcher puede proyectar apps a pantalla completa y cargar ZIP de dibujos con index.html. Esta entrega implementa la **vista nativa dentro de Receiver**, **NO** aún un ZIP compatible con T1Launcher ni un takeover de la pantalla de instrumentos. La conexión de la vista al método de proyección de T1Launcher queda como una fase separada; nunca eliminar avisos OEM ni cámara de reversa.

## Autonomía propia con referencia de surtidor

**El cálculo viejo de consumo T1Launcher está DESCARTADO.** No usar su consumo anterior para ninguna estimación.

**Referencias físicas:**
- 03/11, primer corte surtidor: **66,865 L** y ~950 km Jetour → ~7,04 L/100km.
- 10/11, primer corte surtidor: **66,230 L** y **887 km** Jetour → **7,46674 L/100km**, alcance inicial condicionado de **~887 km**, comparado con autonomía OEM 1.022 km. Incluye kilómetros PHEV eléctricos, por lo que no mide tasa de inyección del térmico.
- 10/11 CSV: primera saturación del flotante a **56 Ω con 60,5 L OEM**; subió después hasta 67,0 L OEM **sin variación de Ω**, mientras surtidor real cargó 66,230 L. Cambio indicador 5,3→67,0 = 61,7 L, **4,530 L menos que la carga real**. Nunca ajustar linealmente toda la curva con este offset.
- Límite inferior: **dos cortes térmicos observados a 5,3 L OEM**, no equivalentes a tanque físicamente vacío. Cuando CI186≤5,3, autonomía de nafta **hasta corte térmico** =0.

**Estimador principal:** tras pulsar **MARCAR TANQUE LLENO** con lectura CI186≥60,5, se fija una referencia física del llenado y kilómetros desde ella. `restante≈(volumen último surtidor / consumo histórico)−km desde la carga`, expresado como **aproximación**, y se reduce al viajar en TRIP. En banda alta de flotante saturado, el cálculo por recorrido evita simular precisión del sensor. Fuera de esa banda, se presenta **una segunda referencia OEM lineal de BAJA confianza**, por separado (no reemplaza la principal ni se fusiona silenciosamente). El resto físico del tanque debajo de 5,3 OEM no se da por consumible. La función **AJUSTAR KM DESDE LLENADO** admite registrar recorrido previo a instalar esta versión.

**Recalibración futura:** cargar litros reales y km Jetour equivalentes en **REGISTRAR NUEVA CARGA REAL** guarda una línea append-only `t1_refills_user.csv` local y actualiza la referencia de L/100 km y volumen del último surtidor. Solo con evidencia nueva se mejorará la curva; ningún inventario OEM previo se pierde.

## TRIP, fuentes, tarifas y costos

`T1TripRecorderService` es un servicio foreground **separado, iniciado/detenido por botón**. Guarda cada ~3 s un CSV privado append-only:
- timestamp, marcha 1033, velocidad OEM, CI186, SOC 1042/1062, voltaje 1063 y corriente 1064, contadores 1048/1049 crudos sin suponer unidades, HVAC 1004/2101, plegado 2003;
- latitud/longitud/precisión solo si el usuario concede permiso GPS; GPS y distancia calculada a partir de GPS si disponible. Si no, distancia **ESTIMADA** por velocidad OEM, cuyo factor km/h aún requiere validación;
- distancia, litros proxy, kWh descargados/cargados HV brutos aproximados y sus costos proxy. Cada valor mantiene su etiqueta de calidad.

**Tarifas configurables:** pesos/L de nafta (default $2.129 de último ticket) y pesos/kWh de energía (default 0/pendiente). Nafta costo estimado = `km TRIP × L/100 km histórico × $/L`, **puede ser incorrecto en un viaje totalmente EV**. Electricidad provisional = `integral aproximada positiva de V×A HV × tarifa`; **NO** equivale a kWh comprados de la red, puede incluir energía generada por el térmico, regeneración y errores de signo/escala. No atribuir ahorro real ni costo de red hasta validar 1048/1049 o un contador externo. El modo real de tarifa eléctrica requiere fuente validada. En ambos casos UI usa **estimado / proxy**, nunca «precio exacto».

Los datos de TRIP se almacenan bajo el directorio privado `files/t1_trips/trip_YYYYMMDD_HHMMSS.csv`, nunca se suben al repo, y se exporta el último por selector Android. **No toca los viajes ni archivos de DriveLab clásico.** Los originales pueden recalcularse después de nuevos descubrimientos.

## QA y aceptación posterior a entrega

1. Instalar únicamente por **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA**, firma y versión Android verificadas por GitHub Actions; **NO** APK manual. Sender 0.2.13 queda intacto.
2. Confirmar barra rápida en inicio y botones visibles sin scroll; plegado/desplegado 2003 una vez cada uno, sin alarma ni confirmación modal.
3. Con espejo ya colocado en D, probar AUTO **con IZQ=1, DER=1** primero, detener y observar. Confirmar si 1/2 del izquierdo coincide con derecho. Evitar circulación hasta haber confirmado orientación.
4. R=2 debe bajar una vez; R/D alternados despacio no deben repetir; al pasar >10 km/h en marcha no-R regresar una vez. Confirmar unidad de velocidad y conteo, y que el sistema original recupera la vista al activar cámara de reversa.
5. Probar HVAC primero en AUTO sin escritura, luego INTERIOR: leer 1004 antes/después, y comprobar 2101 solo aviso. Ante desempañado activado, no insistir.
6. Probar tablero en pantalla principal, agujas con lectura, marca de lleno real, inicio/detención TRIP, edición de precios, exportación local CSV y cálculo de autonomía sin adopción de 1.022 OEM. Confirmar que ninguna lectura faltante se reemplaza por cero inventado.
7. Verificar mapas/puente LAN, Sender, Waze, ventanas, alarma peatonal y el registro GPS anterior sin regresiones.

**Todo resultado nuevo de pruebas reales se anexará al índice y a este documento, sin publicar CSV con rutas personales ni VIN.**
