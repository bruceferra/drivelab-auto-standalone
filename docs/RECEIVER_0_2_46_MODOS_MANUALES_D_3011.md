# Receiver 0.2.46 — EV/HEV/EV MAX manual en P o D, código 3011

## Hallazgos de propietario, 12 de noviembre de 2026
- En `VehicleService` (códigos 4 dígitos), **3011 = pedal de freno de pie**. El usuario observó **0 suelto y 1 pisado**, en una prueba directa. Los candidatos HAL de 9 dígitos `557867210` y `557867238` quedaron ambos fijos en 0 durante 38 muestras y no sirven para inferir freno.
- En los CSV recibidos del Receiver 0.2.45, `1033=1` es P, `1033=4` es D y `2148=0.0` durante la secuencia P→D→P; se verificó lectura de marcha, no cambios de modo desde DriveLab en D.
- El usuario confirma que el selector original Jetour permite cambios EV/HEV/EV MAX circulando en D. La API usada por DriveLab (`IVehicleControl`, `transact(15)`, `property 1043`) sí se comprobó para los tres modos en P. **La aceptación de esa ruta API en D todavía es experimental y NO está confirmada en esta unidad**.

## Cambios de 0.2.46
- **Tres botones superiores de Cockpit:** selección MANUAL `1043=1 EV`, `2 HEV`, `3 EV MAX`. Admiten preflight por modo P (1033=1, velocidad = 0) o D (1033=4, velocidad OEM real y finita entre 0 y 160 km/h).
- Doble lectura directa de modo OEM, marcha y velocidad antes del único envío, se invalida si cambió la marcha entre ambas; requiere app visible y confirmación del usuario; registro y readback de respuesta. Fail closed para lectura inválida, otros cambios de marcha y velocidad negativa o fuera de rango.
- **NO exige pedal de freno en D**: 3011 se agrega **solo como diagnóstico** en CSV PHEV, y en los logs del preflight. No se usa para tomar decisiones de escritura.
- No hay cambio automático por GPS, arranque o segundo plano. El modo de prueba antiguo EV→HEV en el apartado avanzado continúa restringido a P.
- Cabecera de Cockpit muestra versión real instalada (ya no el texto fijo 0.2.43).
- Se mantienen experiencias/registro de viajes, clientes, electricidad y el Sender 0.2.16.
- `T1DriveModeGateTest` ejecuta pruebas de regresión puramente JVM de estados permitidos y rechazados. CI verifica versión real del paquete, manifiesto OTA y firma respecto a Receiver 0.2.45.

## Pruebas físicas ordenadas por riesgo
1. Tras instalar 0.2.46, vehículo detenido en lugar seguro y plano, probar botones del cockpit en **P** para confirmar que nada retrocedió.
2. En **D con velocidad cero**, freno pisado y sin tráfico, seleccionar una vez un modo diferente con la ayuda de un acompañante que toque la pantalla; verificar en la pantalla original que la Jetour realmente lo aceptó y comparar 1043. No repetir órdenes no confirmadas.
3. Un éxito en D detenido NO valida necesariamente cambios a velocidad distinta de cero: no realizar pruebas distractoras o en tráfico. Cualquier comportamiento inesperado implica no insistir, revisar log y usar los controles originales Jetour. Esto no desactiva controles OEM.

## Nota de seguridad
El modo de conducción es un control del tren motriz. La ruta Binder de DriveLab no es una interfaz certificada para permitir acciones en movimiento. Las comprobaciones previas solo detectan errores de datos, no prueban seguridad funcional. El vehículo puede rechazar una petición. Registrar resultados antes de considerar automatizar el cambio o usarlo de forma regular.
