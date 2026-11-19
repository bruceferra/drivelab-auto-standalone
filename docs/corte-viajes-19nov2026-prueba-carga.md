# Corte de viaje y carga de prueba — 19/11/2026 08:00 ART

## Alcance autorizado por el conductor
- Fecha de referencia de la jornada y del informe: **19 de noviembre de 2026 a las 08:00, Argentina** (11:00 UTC).
- Mantener el registro automático de carga operativo para la próxima recarga nocturna.
- Evitar mezclar la primera carga importada MANUALMENTE desde T1 Launcher con cargas automáticas futuras.
- Mantener la jornada/VIAJE TOTAL que esté grabando; no interrumpir ni retrofechar muestras OEM.
- No modificar T1 Launcher, HOME, clúster ni DriveLab Uber.

## Acciones implementadas en Receiver 0.3.91
1. En **Cargas automáticas · historial**, cada fila **cerrada** cuya fuente incluya «IMPORTACIÓN MANUAL» ofrece **BORRAR SOLO ESTA CARGA MANUAL #ID**. Confirmación explícita, comparación del ID y fuente en SQLite antes de borrar, actualización de CSV de cargos. Jamás borra registros automáticos ni una carga abierta.
2. En **Viajes AUTO · encendidos / última carga**, botón **DESDE 19/11/2026 08:00 · SIN INVENTAR KM**. Genera un reporte virtual a partir de muestras OEM existentes, recortadas a esa fecha/hora. No modifica sesiones ni inventa los km anteriores a la primera muestra.
3. El **Cockpit** conserva **VIAJE TOTAL · INICIAR/PAUSAR/TERMINAR** y **Historial → BORRAR SELECCIONADO** para un registro cerrado. Este borrado ya exige que no haya viaje/jornada activo, pausado o pendiente. La eliminación de todos los historiales no forma parte de esta actualización.

## Lo que la app no puede hacer honestamente sin datos en el dispositivo
No puede iniciar retroactivamente un recorder real a las 08:00 si no estaba grabando: el nuevo TRIP obtiene `System.currentTimeMillis()` al pulsar INICIAR. El reporte desde las 08:00 muestra solamente observaciones ya registradas; no se inventa odómetro, consumo ni kWh.

## Prueba de carga esta noche
Activar **AUTO** en **Cargas automáticas**, verificar señal de cable OEM y lectura SOC/corriente. Conservar el historial automático. No borrar la base de cargas completa.
