# Receiver 0.2.77 (code78) — un único 3/3 para vía y perfil OEM

## Observación física (13/11/2026)
El usuario entró en CIUDAD con el optimizador activo. El motor completó 1/3 → 2/3 → 3/3, cambió correctamente desde HEV FORZADO a EV, mostró SOC/REGEN PENDIENTE en gris y **volvió a contar 1/3 → 2/3 → 3/3** antes de indicar MODO CIUDAD en verde. La transición automática **HEV FORZADO → SMART → EV** quedó probada en el vehículo, pero el doble conteo causa demora y UI confusa.

## Causa en código 0.2.76
Dos filtros independientes:
- T1OptimizedAutoGate: tres GPS válidos de una misma vía y lectura OEM 1043.
- T1SocRegenTransitionGate: otros tres votos tras la lectura 1043 correcta, antes de solicitar ajustes SOC y regeneración.

El segundo no era una nueva vía ni un requisito de seguridad OEM. Se utilizaba la misma información GPS ya aprobada.

## Corrección
- Solo T1OptimizedAutoGate mantiene los tres votos GPS para cada **nueva vía**. Si GPS es incierto se bloquea el cambio de 1043, y las condiciones SOC se siguen comprobando.
- Una vez que 1043 ya refleja el modo deseado y el gate principal devuelve SAME_MODE, la sincronización SOC/regeneración comienza en ese mismo ciclo, sin contadores de vía adicionales.
- T1SocRegenTransitionGate se reduce a comparar la configuración auxiliar deseada con la última confirmada. Si coincide, no reescribe; si los getters OEM ya coinciden, la considera confirmada; si cambió, envía al piloto el perfil permitido **sin nuevas votaciones GPS**.
- El piloto de 1040/1041/1046 **mantiene** doble preflight de marcha/velocidad, escrituras permitidas, readback OEM, exclusión de operaciones simultáneas, cooldown y prohibición de reintentos/rollback ciegos.
- Mientras el cambio auxiliar no esté confirmado, el encabezado permanece gris **SOC/REGEN PENDIENTE**. El cartel verde MODO CIUDAD/ CARRETERA/ AUTOPISTA aparece solo con condición SOC, 3/3 de vía, 1043 confirmado y objetivos auxiliares confirmados.
- Deshabilitar un bloque sigue anulando su perfil y bloquea toda escritura de ese bloque; transición a otra vía exige otros tres GPS, **una sola vez**.

## Prueba integrada
T1SocRegenTransitionGateTest modela la secuencia física: CIUDAD 1/3 y 2/3 sin habilitación auxiliar; CIUDAD 3/3 solicita EV; mientras 1043 no confirma el piloto SOC/regen está no elegible; luego 1043=EV confirmado y el primer llamado al gate auxiliar autoriza sincronización inmediatamente (sin 1/3 secundario); una vez confirmada no repite órdenes, incluso con otras 50 muestras. También verifica nueva AUTOPISTA con sus tres GPS, objetivos 1041 nuevos sin GPS adicional y reactivación de bloque tras reset.

El código se compila y pasa pruebas automáticas; el resultado físico en Jetour debe comprobarse al instalar la versión.
