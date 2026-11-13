# Receiver 0.2.82 — autonomía más estable con evidencia de surtidor y OEM

## Problema que se corrige
La cifra principal de autonomía del Cockpit escogía directamente un consumo de **solo 3–5 eventos OEM de 500 m**. Al entrar/salir de EV o vencer la frescura de una notificación, saltaba de esa fuente al OEM de 50 km o al promedio del surtidor. Un cambio pequeño en el denominador podía producir cambios de 280 a 580 km sin ganar combustible.

## Modelo nuevo
- Ancla: último par de surtidor **registrado en Receiver** mediante `T1FuelCalibration.latestL100()`, o valor **manual** `fuel_l100` si el conductor lo configuró explícitamente. Si no hay ninguno, referencia histórica 66,230 L / 887 km = 7,47 L/100 km. No reutilizar el cálculo viejo rechazado de T1Launcher.
- Lectura OEM `1085`: promedio **móvil de 50 km** del fabricante; se usa solamente si es físicamente plausible y compatible con el historial de surtidor. Objetivo de consumo = 65% referencia + 35% OEM 50 km. Si la lectura está fuera del intervalo, no se la convierte en dato de consumo certificado.
- Los callbacks `NAFTA_500_m` (`IClusterInteraction` 135) permanecen **crudos/no calibrados**. `T1Fuel500mWindow` conserva los 100 eventos más recientes en preferencias, incluso cambiando de pantalla o de TRIP; si no había estado previo, un lector **de fondo** recupera datos de los últimos registros `trip_oem_*.csv` originales, sin reescribirlos. 100 eventos representan **hasta** 50 km de notificaciones, pero **no** acreditan que no faltaron segmentos ni su escala física. Una media comparable a la lectura OEM 50 km (+/- 15% o 0,7 unidades) se muestra como coincidencia, **no** como calibración independiente. Nunca sustituye el denominador de la autonomía.
- Cambio gradual del consumo eficaz: `T1StableAutonomy` limita cada transición según tiempo y velocidad OEM desde el cálculo anterior. `stableFuelL100` y su ancla se conservan entre aperturas, con caducidad de 30 días. El modelo no usa el promedio inestable de 5 muestras como consumo principal.
- Gasolina estimada: `max(0, CI186-5,3 L)×100 / L100_estable`; si CI186 se satura a 60,5+, la parte gasolina aparece `NO FIABLE`. El estado EV se calcula como antes (`SOC×kmEV100/100`). **Cautela PHEV**: los valores del surtidor reflejan kilómetros mixtos con aportes eléctricos; sumar otra autonomía EV es una referencia orientativa, puede sobreestimar y no representa predicción física certificada.

## Pantalla
Autonomía principal `~ N km · ESTABLE`. En el detalle: litros utilizables, L/100 estable, ancla, 1085, ventana 100×500 m y grado de corroboración. La fila de 5 eventos OEM dice explícitamente `SOLO DIAGNÓSTICO`.

## QA
- `T1StableAutonomyTest`: arranque frío, falta de datos, EV 0, outliers, cambio urbano gradual y prohibición del salto de cientos de km por un evento de 500 m.
- `T1Fuel500mWindowTest`: persistencia, ventana 100, descarte de inválidos, restauración.
- `T1StableAutonomySourceContractTest`: integración real en Cockpit, ancla de último surtidor, carga de CSV en background, ausencia del selector viejo.
- Conserva Sender v0.2.16, firma original de Receiver, viajes y archivos `trip_*.csv` / `trip_oem_*.csv` sin alterar. No toca la ECU ni modifica el OEM.

## Prueba física pendiente
Comparar durante al menos 50 km, preferentemente con variedad de vía y niveles de SOC: 1085 OEM, media cruda de 100 notificaciones, L/100 estable, CI186 y autonomía OEM mostrada. Solo después de comprobar correspondencia de escala, continuidad y recargas del surtidor puede entrenarse un consumo propio **exclusivamente** desde eventos de 500 m. No presumir que los 100 eventos reproducen exactamente el filtro interno del fabricante.
