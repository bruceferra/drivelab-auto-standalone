# Receiver 0.2.76 (code77) — salir de HEV FORZADO antes de EV

## Observación física confirmada por propietario
En la Jetour T1 híbrida enchufable, si el SOC 1040 está en FORZADO (4), el vehículo puede rechazar pasar de HEV a EV 1043=1. En cambio **EV MAX 1043=3 desconecta FORZADO por sí mismo**; no necesita ni debe recibir la orden previa SMART por nuestra app.

## Secuencia corregida (mismo piloto para botones manuales y optimizador)
Cuando se solicita **EV 1043=1**:
1. Comprobaciones originales dobles de marcha 1033 y velocidad 2148: P/0 o D con velocidad OEM válida. El objetivo del perfil solo se solicita si las condiciones min/max de SOC se cumplen; si no, se mantiene fallback HEV según configuración.
2. Leer 1040. Si es **SMART=2**, no escribir 1040 y solicitar EV directamente.
3. Si es **FORZADO=4**, comprobar segunda lectura 1040 para detectar cambios inesperados y revalidar marcha/velocidad, luego enviar **UNA sola vez** 1040=2.
4. Verificar por getter OEM 1040=2 y revalidar otra vez la marcha/velocidad **antes** de enviar 1043=1.
5. Mandar 1043=1 por el piloto compartido existente y confirmar lectura de vuelta 1043=1.
6. Si SMART falla/no confirma/no hay seguridad, **NO enviar 1043 EV**, no repetir escrituras a ciegas ni intentar restaurar FORZADO. Registrar eventos EV_PRE_SMART_LECTURA, EV_SMART_WRITE_REQ, EV_SMART_BINDER_OK, EV_SMART_READBACK, EV_POST_SMART_SEGURIDAD, EV_SMART_CONFIRMADO para diagnóstico.

Cuando se solicita **EV MAX 1043=3**: no hay pre-SMART en el piloto. Se solicita directamente como antes. El optimizador espera el cambio autónomo del OEM a 1040=SMART antes de sincronizar regeneración. No manda SMART anticipadamente ni reactiva FORZADO después.

## Invariantes y configuración
- FORZADO solo es compatible con perfiles HEV. En el editor, al seleccionar EV o EV MAX, la opción SOC se fija en SMART y el selector FORZADO se inhabilita. Al regresar a HEV puede seleccionarse FORZADO normalmente.
- Para configuraciones antiguas donde EV/EV MAX quedó guardado con SOC FORZADO, el motor de perfiles normaliza en memoria el destino auxiliar a SMART y **no toca 1041** al ejecutar esos modos. No reescribe los cuatro perfiles anteriores silenciosamente hasta que el usuario los guarde.
- Se respetan condiciones min/max batería; ejemplo EV ciudad con batería >=20% nunca debe aplicarse por debajo del mínimo, aunque se libere FORZADO.
- No se modifica el mapeo del objetivo SOC 1041 ni el protocolo de regeneración 1046 que ya fue probado por el usuario. Se mantiene el servicio privado de subida de diagnósticos y la compatibilidad Sender 0.2.16.

## Pruebas
T1SocRegenWritePolicyTest: solo EV target1 con estrategia FORZADO4 exige desarmar; EV en SMART no añade escritura; EV MAX y HEV omiten orden pre-SMART; antiguos perfiles EV/EV MAX FORZADO se normalizan a SMART, HEV FORZADO se conserva, SOC mínimo impide entrar en EV por debajo del mínimo. Las verificaciones estáticas CI exigen que la preparación SMART ocurra antes del setter 1043 y que EV MAX evite la orden extra.

**Pendiente de validación física:** comparar HEV FORZADO -> EV desde el vehículo en P (detenido y seguro) tras instalar v0.2.76. El código puede enviar órdenes protegidas pero no garantiza que la ECU las acepte en todas las condiciones. Ante rechazo no insistir automáticamente.
