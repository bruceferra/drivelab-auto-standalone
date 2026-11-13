# Receiver 0.2.71 · SOC por decenas, escritura OEM activa

## Observación física del 13 de noviembre de 2026
El comando 1041=20 produjo timeout de getter y error de restauración con entero RAW 56. El usuario comprobó en pantalla original Jetour estrategia SMART y objetivo forzado 20 %. **No se puede afirmar que el comando no se haya aplicado.** El entero 56 no es un porcentaje válido de objetivo SOC, sino lectura no interpretada; no debe reutilizarse como objetivo de rollback.

## Comportamiento corregido
- Menú SOC FORZADO: exclusivamente 20, 30, 40, 50, 60, 70 (múltiplos de diez); fuera 48, 56, 64 y 72. Los perfiles anteriores no se sobreescriben hasta guardar de nuevo.
- Siguen activas las escrituras automáticas controladas 1040 SMART/FORZADO, 1041 SOC, 1046 regeneración y 1043 EV/HEV/EV MAX. El usuario pidió expresamente NO suspenderlas.
- La lectura de 1041 se hace por entero (Binder transact 5) y por flotante (transact 6), y se registran ambos valores. Un valor flotante válido que coincide exactamente con uno de los seis porcentajes se acepta como referencia/confirmación aun cuando el entero sea RAW no interpretable.
- Ningún getter que no confirme 1041 autoriza a asumir que el setter fue rechazado; se informa ESTADO INCIERTO, se detiene ese ciclo y se conserva el fail-stop original del optimizador. No hay reintentos ciegos ni restauración de 1041 a RAW 56 u otros porcentajes inferidos.
- Se preservan la doble comprobación OEM de marcha/velocidad, la limitación de porcentajes, los controles del vehículo y la firma/OTA de Receiver.

## Prueba física pendiente
Con el vehículo detenido, verificar los valores iniciales del menú OEM, comprobar el desplegable de seis porcentajes, ejecutar una sola orden segura y contrastar pantalla original con getter 1041 entero/flotante, 1040 y 1046. Si no confirma, registrar el resultado y no insistir hasta aclararlo.

La validación CI no constituye una prueba del vehículo real.
