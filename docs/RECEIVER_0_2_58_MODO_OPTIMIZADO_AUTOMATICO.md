# Receiver 0.2.58 (code59) — Modo optimizado automático

## Pedido
El usuario probó más de 150 km de sugerencias OSM y pidió que el Modo Optimizado finalmente cambie EV, HEV o EV MAX en la Jetour, y que tenga un interruptor general ON/OFF. La versión 0.2.57 solo sugería, sin escritura.

## Qué se activa
- SOLO la propulsión OEM 1043: EV=1; HEV=2; EV MAX=3. Reutiliza el piloto OEM de tres modos que exige dos lecturas directas de marcha 1033, velocidad 2148 y modo actual 1043, acepta solo P/0 o D/velocidad válida 0..160 y verifica el nuevo valor 1043 durante 3.5 s después de la única escritura.
- No escribe la regeneración 1046, gestión SMART/FORZADO 1040, objetivo SOC 1041 ni modo de conducción 2053. Esos setters no están validados; continúan como preferencias en los cuatro perfiles y la pantalla dice que NO están aplicados. No se afirma que una transición de 1043 implique una intervención energética específica.
- La condición de batería actual >= o <= porcentaje se evalúa contra SOC OEM 1062 (o 1042 cuando falta 1062). Si la condición falla, se solicita 1043=2 HEV de respaldo; NO se afirma que se haya seleccionado HEV Smart en 1040.
- Si la categoría es DESCONOCIDO o la lectura de SOC es inválida, se conserva el modo OEM efectivo SIN escrituras. El perfil Desconocido queda guardado como preferencia pero no se aplica automáticamente por seguridad.

## Interruptores y funcionamiento
- Cockpit: botón visible AUTO OFF / AUTO ON.
- Pantalla MODO OPTIMIZADO: interruptor general, guarda los cuatro perfiles y solicita confirmación antes del primer ON.
- El ON es voluntario, predeterminado OFF. Exige permiso de GPS preciso y mapa local PBA+CABA instalado; activa el indicador OSM. La app ejecuta un servicio foreground independiente con notificación persistente y botón DESACTIVAR AUTOMATICO. Se mantiene funcionando aunque el usuario cambie a Maps. Al elegir OFF, cancela la automatización y conserva perfiles.
- No se inicia desde BootReceiver ni escribe nada solo por instalar la APK. Una preferencia ON previamente confirmada puede restaurarse al volver a abrir el Cockpit.

## Filtrado de cambios y protección
- GPS nuevo <=15 s y precisión <=30 m, no reutiliza un fix anterior; vía OSM local con match <=35 m.
- La vía debe repetirse en al menos tres consultas de ~10 s; no reacciona a lecturas aisladas.
- Datos OEM <=10 s, SOC 0..100, marcha P/0 o D/velocidad 0..160 y modo actual 1043 válido.
- En cada cambio realiza dos lecturas OEM directas, un solo setter y confirmación real del resultado. Control único GLOBAL_BUSY entre cambio manual y automático.
- Espera 90 segundos entre cambios para evitar oscilación. No reenvía una orden fallida ni fuerza HEV tras un rechazo; desactiva AUTO y muestra el motivo. Las restricciones OEM originales permanecen operativas.
- Un conductor que quiera mantener otro modo manual debe apagar AUTO, porque ON reaplica la regla confirmada.

## Compatibilidad
- Mantiene Receiver 0.2.57: recirculación de aire interior por defecto en segundo plano, alarma 2074 solo manual, km por velocidad OEM, 3 viajes, histórico con 6 métricas y reparación auditada, simulador experimental, clasificación de avenidas 50/60 km/h como Ciudad, avisos cortos y mapa offline.
- Sender 0.2.16 sin cambios; actualización firmada conservando paquete y datos.

## Validación
Prueba pura T1OptimizedAutoGateTest verifica estabilidad de vía, pérdida GPS, UNKNOWN, batería, fallback HEV, cooldown 90 s y bloqueo de velocidad inválida. Se suman tests anteriores y compilación firmada. La CI no simula hardware Jetour; la aceptación real de 1043 circulando requiere prueba cautelosa en el vehículo.
