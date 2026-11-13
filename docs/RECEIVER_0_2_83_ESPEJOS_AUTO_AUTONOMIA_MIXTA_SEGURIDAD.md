# Receiver 0.2.83 · Espejos R automáticos, autonomía mixta sin contar EV dos veces

## Solicitud del conductor
- Cada apertura NUEVA de DriveLab (proceso nuevo) vuelve a poner **ESPEJOS REVERSA AUTO** y **AIRE INTERIOR AUTO** como valores de arranque, sin tener que pulsar manualmente. Cambiar de pantalla no reactiva AUTO si el conductor lo desactivó en la sesión actual.
- La inclinación de R no se puede rearmar automáticamente si la última secuencia quedó con posición `INCIERTA`. No se manda un movimiento por abrir la app, sino únicamente tras la próxima transición válida a R con la marcha y velocidad verificadas. Conserva D estable 800 ms para retornar y los pasos independientes por lado.
- La recirculación en modo interior sigue respetando `PAUSED` del supervisor, por ejemplo si la ECU requiere aire externo para desempañar.
- La alarma acústica de peatones OEM **2074** continúa como un control **manual y explícito**. **No se agregó una escritura automática de desactivación al arrancar**. Se lee estado ON/OFF en barra fija; no se alteran restricciones ni sistemas de seguridad OEM.

## Autonomía PHEV: corregido doble conteo
La versión 0.2.82 ya evitó las oscilaciones por sustituir el consumo con 3–5 segmentos crudos de 500 metros. Aun así, el Cockpit sumaba `fuelKm + electricKm` aunque `fuelKm` se calculaba con `surtidor_l / km mixtos del tanque`, cuyo denominador ya incorpora trayectos EV: un duplicado implícito del beneficio de batería.

En 0.2.83:
1. `T1StableAutonomy` sigue tomando surtidor/manual como referencia y el 1085 OEM de 50 km solo si es plausible y en promedio ponderado. Los eventos crudos de 500 metros siguen disponibles para comparar, **no** para asumir litros certificados.
2. `T1PhevTotalRange` calcula referencia de tanque completo a partir de `67 L × 100 / L100 mixto estable`, con volumen de surtidor **observado aproximadamente**, no capacidad certificada de la ECU.
3. Con CI186 en zona no saturada, se toma fracción de recorrido restante `(CI186-5,3)/(67-5,3)` multiplicada por el rango mixto. Si el conductor marcó realmente un tanque lleno y el servicio contabilizó kilómetros desde él, se calcula además `max(0,rango_mixto_total-km_desde_lleno)`. Cuando ambas están disponibles, se muestra la **menor** como estimación prudente, con fuentes separadas para auditar.
4. Con CI186 superior saturado y ancla de llenado válida, el histórico restante usa únicamente kilómetros desde el llenado. Sin ancla y en nivel alto saturado, muestra **referencia histórica aproximada de tanque alto**, sin pretender lectura exacta de litros físicos.
5. El SOC sigue dando un `EV potencial` por separado en el detalle, pero **NO se suma** al rango principal. Advertencia cuando se cargó electricidad de la red durante el mismo ciclo, o cuando el surtidor no equivale a consumo puramente térmico.
6. La cifra principal queda marcada `MIXTA ESTABLE`, con explicación de fuentes, kilómetros referenciales del tanque, lectura CI186 y odómetro desde último llenado.

### Ejemplo explícito del conductor
Si el ciclo histórico que se usa como referencia fuera 950 km y el tanque actual lleva 606 km desde un llenado confirmado, el cálculo por odómetro indica **344 km restantes**. El indicador OEM puede mostrar 592 km restantes, pero eso es su propia proyección y no supera la evidencia del último ciclo. La app nunca inserta 950 km como regla fija sin consumo o llenado anclado.

## Eco Score y ahorro real
`T1EcoTripMetrics` v1 ya clasifica ciudad/carretera/autopista con OSM, congestión probable, bandas de 5 km/h, aceleraciones y frenadas. Da una nota de **suavidad contextual**, no de litros ni kWh reales. Para la futura comparación de ahorro real por trayecto, conservar volumen en surtidor y energía/carga de batería separada, y validar la unidad del callback 500m frente al OEM de 50 km. No declarar eficiencia energética medida hasta entonces.

## QA
- `MirrorControlsVisibilitySourceContractTest`: AUTO R e INTERIOR al iniciar proceso, no reactivación al navegar, guardas `INCIERTA` y `PAUSED`.
- `T1PhevTotalRangeTest`: 950 km histórico + 606 recorridos = 344 km de referencia; prueba que los 100 km eléctricos diagnósticos NO se agregan por segunda vez; nivel CI186 calibrado y saturación.
- `T1StableAutonomySourceContractTest`: Cockpit usa modelo mixto y deja EV como dato independiente.
- `T1StableAutonomyTest` y `T1Fuel500mWindowTest`: estabilidad, integridad/persistencia de últimos 100 eventos.
- Sender, historial CSV, software OEM y alertas de seguridad no se modifican.

**Pendiente prueba física:** comparar nuevo rango con tablero OEM, CI186, odómetro desde lleno y SOC en condiciones reales; ningún cálculo es autonomía certificada.
