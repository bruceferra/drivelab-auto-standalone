# Receiver 0.2.75 · Lectura 1041 SOC: entero crudo versus porcentaje

**Evidencia:** cuatro fotografías del propietario de la Jetour T1 argentina (2026-11-13), fila de T1Launcher «1041 SOC protegido». Una quinta foto muestra diagnóstico DriveLab: etapa 2 Binder transact(15) ID 1041 nuevo 50 recibido; etapa 3 todavía sin confirmar.

## Pares observados (solo lectura)

| 1041 intRaw (T1Launcher) | SOC mostrado en pantalla |
| --: | --: |
| 30 | 20% |
| 40 | 30% |
| 48 | 40% |
| 56 | 50% |

**Antes estaba mal:** comparar el entero crudo con el preset directo rechazaba 56 cuando solicitábamos 50% y podía aceptar falsamente raw40 al solicitar 40% (aunque la UI en realidad mostraba 30%). El Cockpit mostraba raw56 como 56%, también incorrecto.

**Otra observación:** 1041 floatRaw puede ser 1. No es 1% ni una prueba del objetivo: mantener como diagnóstico sin darlo por confirmado.

### Qué está corregido
- T1SocTargetCodec es mapeo de SOLO LECTURA usando exclusivamente los cuatro pares fotografiados. El verificador SOC 1041 lo usa para confirmar los porcentajes. El optimizador usa el mismo mapeo al cotejar telemetría. Cockpit muestra 50% cuando raw56.
- El getter anterior se consulta OPCIONALMENTE como diagnóstico, sin bloquear la escritura y sin servir jamás de rollback. Así el CSV informa si ya estaba seleccionado el valor antes del comando.
- El valor 56 NO es un porcentaje habilitado para el usuario: 56 es un dato crudo de lectura. Nunca se manda como porcentaje de SOC.
- **NO se ha modificado el argumento del setter OEM para 1041:** las fotos muestran la codificación de lectura, no prueban que el setter deba recibir códigos crudos. Binder OK significa recepción, no demuestra cambio físico.
- Los preset 60% y 70% aún NO tienen una pareja readback comprobada. No extrapolar raw64/raw72 sin observación física. Nunca marcar estos valores confirmados por la mera coincidencia numérica del entero o el floatRaw1.

### Para confirmar físicamente y completar el mapeo
En P y con el auto detenido, comparar una transición única: SOC del menú Jetour previo, diagnóstico 1041_RAW_ANTES_OPCIONAL, comando 1041 solicitado, 1041 entero posterior y SOC OEM final. Usar «SUBIR AL SERVIDOR» tras la lectura, no el menú «COMPARTIR» de Android. Los ajustes 60/70 requieren fotos adicionales de lectura T1Launcher. No repetir comandos cuando el readback es incierto.

**Confirmado por el usuario:** regeneración 1046 pasó físicamente de 3 ALTA a 1 BAJA; SOC objetivo sigue pendiente de comprobación física. Las pruebas de integración no sustituyen la prueba en la Jetour.
