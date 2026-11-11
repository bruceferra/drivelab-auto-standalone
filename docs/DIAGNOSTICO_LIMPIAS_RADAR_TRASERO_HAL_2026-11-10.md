# Jetour T1: barrido de limpiaparabrisas y radar trasero

**Estado:** implementación experimental de **lectura OEM exclusivamente**, sin cambios de configuración ni movimiento de actuadores. El usuario solicita (a) ajustar velocidad de barrido, (b) modificar distancia de aviso del sensor trasero y (c) mostrar distancia real en metros si se puede.

## Evidencia extraída de los APK originales de la T1

En `ALVehicleService.apk`, enum `VehiclePropertyKey.<clinit>`:

| ID VehicleService | Etiqueta OEM original | Alcance verificado por análisis |
|---:|---|---|
| **2126** | `INT雨刮速度灵敏度` | sensibilidad/velocidad de limpiaparabrisas en modo AUTO; no demuestra modificación de velocidad manual/intermitencia |
| **2125** | `后雨刮联动` | interconexión/activación automática de limpia trasero |
| **2107** | `驻车雷达报警距离分段` | segmentación de distancia de aviso del radar; no se prueba que afecte solo los traseros |
| **2049** | `驻车雷达开关` | interruptor de radar de estacionamiento |

El enum del APK contiene `CarWiperSensitivityModeNone/Low/Middle/High/VeryHigh`; la correspondencia exacta y la autorización de escritura **en esta T1** se deben contrastar con cambios realizados desde el sistema OEM antes de emitir ajustes desde DriveLab.

En `ALCarService.apk`, las **constantes HAL son enteros**:

| Propiedad HAL | ID decimal | ID hexadecimal |
|---|---:|---|
| `CLUSTER_REAR_PDC_OBJECT_DISTANCE` | **557862927** | `0x2140500f` |
| `CLUSTER_FRONT_PDC_OBJECT_DISTANCE` | 557862926 | `0x2140500e` |
| `CLUSTER_LHR_RADAR_DISTANCE` | 557843296 | `0x21400360` |
| `CLUSTER_RHMR_RADAR_DIATANCE` | 557843297 | `0x21400361` |
| `CLUSTER_LHMR_RADAR_DISTANCE` | 557843298 | `0x21400362` |
| `CLUSTER_RHR_RADAR_DISTANCE` | 557843299 | `0x21400363` |

El DEX del proxy `IALCar$Stub$Proxy.getIntProperty(id,area)` confirma `Binder.transact(2)`. Se lee solo mediante un binder exacto validado, con `area=0`, sin llamada a `setIntProperty`. El que las constantes estén en el APK **no garantiza** que respondan en tiempo real en la T1.

## Código de diagnóstico en Receiver

`WiperParkingProbe` vincula los dos servicios por separado, valida el descriptor del binder y realiza únicamente:

- `VehicleService.readInt` (transacción 5): los IDs 2126, 2125, 2107 y 2049.
- `ALCarService.getIntProperty` (transacción 2) sobre la propiedad HAL trasera y cuatro sectores.
- Muestra valores crudos, errores explícitos y `DISTANCIA EN METROS — No calibrada`. **No aplicar factor de conversión arbitrario**.
- Control accesible en pestaña VEHÍCULO, sección «LIMPIAPARABRISAS Y RADAR TRASERO», con botón de lectura solo cuando está visible. UI 3D del Receiver se conserva.
- **No hay botones de ajuste ni escritura** en esta versión de diagnóstico: todavía faltan tabla de valores válidos, distinguir sensibilidad vs velocidad de barrido real, alcance delantero/trasero de 2107 y unidad de 557862927.

## Validación física necesaria

1. Con vehículo inmóvil, probar las distintas posiciones de sensibilidad AUTO desde el **mando original**, leer 2126 y anotar cambios 0,1,2... Nunca bloquear la visión o ejecutar pruebas con lluvia/tránsito.
2. Cambiar la distancia del aviso desde la **pantalla OEM original**, si existe tal opción. Leer 2107 antes/después y verificar si afecta también a sensores delanteros; no usar prueba de reversa en circulación.
3. Para escala PDC: ubicar un objeto fijo y no móvil detrás en área despejada; con otra persona verificando y vehículo frenado e inmóvil, comparar lecturas crudas en varias distancias conocidas con cinta métrica. Evitar acercarse a personas/animales; no usar DriveLab como único elemento de estacionamiento.
4. Si valores realmente representan distancia y se determina escala, presentar metros con 2 decimales y etiqueta de confianza, no al revés.
5. Cuando las escalas y modos permitidos estén demostrados, evaluar controles OEM con verificación posterior de lectura, bloqueo por marcha/velocidad y retorno inmediato a configuración original ante fallas.

**Sin root, sin modificaciones de APK OEM, sin escribir valores de limpiaparabrisas, radar ni sensores.**

## Primera prueba real con Receiver 0.2.23: vehículo apagado

**Fuente:** foto de la pantalla VEHÍCULO del Receiver enviada por el usuario, con confirmación explícita posterior de que la Jetour T1 estaba **sin encender**. Todos los números son valores crudos, no unidades físicas validadas.

| ID | Valor observado | Comentario |
|---|---:|---|
| 2126, sensibilidad de barrido AUTO | -1 | Posible no inicializado / no disponible; no asumir escala |
| 2125, limpia trasero | 0 | Estado inactivo posible; falta confirmar encendida |
| 2107, banda aviso radar | 0 | Sin interpretación del umbral |
| 2049, radar estacionamiento | 0 | Puede ser inactivo con vehículo apagado |
| 557862927, PDC trasero | 0 | No expresa 0 metros necesariamente |
| 557843296, zona trasera izquierda | 62 | Mismo valor en cuatro zonas: posible centinela/reposo |
| 557843297, zona trasera intermedia derecha | 62 | Idem |
| 557843298, zona trasera intermedia izquierda | 62 | Idem |
| 557843299, zona trasera derecha | 62 | Idem |

**Hipótesis:** `62` podría ser un valor por defecto cuando el radar no está activo, o un código/estado empaquetado y no una distancia. No representar como 62 cm ni 62 m sin ensayo.

**Comparación siguiente:** camioneta encendida, estacionada en zona despejada, con freno aplicado y un ayudante observando el indicador OEM original. Tomar lectura inicialmente en P; cuando corresponda y resulte seguro, con pie en el freno pasar a R sin desplazarse y repetir. Comparar si el 2049 y las cuatro zonas abandonan sus valores iniciales. No mover el vehículo hacia personas u obstáculos ni usar una lectura experimental para maniobrar. Anotar también si la opción OEM del radar está activada. El 2126 se ensaya solo con el mando original en modo AUTO y vehículo inmóvil.

## Segunda prueba real con Receiver 0.2.23: vehículo encendido / radar OFF-ON

**Fuente:** tres capturas del usuario 10/11/2026 con la Jetour T1 **encendida y sin desplazarse**, y una primera captura previa con vehículo apagado. El usuario activó el botón OEM de radar trasero, observando que también activa el delantero. Necesidad concreta: reducir la alerta acústica trasera en circulación cuando otro vehículo se aproxima a ~1 m, **sin perder el sensor delantero para estacionar**.

| Escenario | 2126 AUTO | 2125 limpia trasero | 2107 tramo aviso | 2049 radar | PDC trasero 557862927 | Cuatro sectores traseros 557843296-299 |
|---|---:|---:|---:|---:|---:|---|
| T1 apagada | −1 | 0 | 0 | 0 | 0 | 62,62,62,62 |
| T1 encendida, radar OFF | −1 | 0 | 0 | 0 | 0 | 335,335,335,335 |
| T1 encendida, radar ON | −1 | 0 | 0 | **1** | 0 | 335,335,335,335 |

Hallazgos:
- **2049 confirmado como indicador de activación general del radar** con el botón OEM en ese ensayo; el cambio 0 → 1 no implica habilitar solo el radar trasero.
- `2107` permaneció en 0 incluso al conmutar el botón; su rol de ajuste de distancia **no está demostrado** en este vehículo. No editarlo para intentar cambiar el aviso.
- Todos los sectores traseros cambian simultáneamente 62 → 335 **con el encendido**, sin cambios cuando el radar se activa por el botón. Es probable que sean centinelas/valores por defecto o una codificación; no afirmar que 335 = 3,35 m o que 62 = 0,62 m.
- La señal HAL de distancia trasera 557862927 sigue 0 en todos los escenarios. El getter consultado puede estar retornando un dato no poblado, o el área/servicio no ser adecuado. **No convertir a metros**.
- `2126=-1` de sensibilidad del barrido se mantiene; no se ha probado variación desde el control OEM.

### Comportamiento del radar documentado en el manual de la T1

El manual público T1 (edición china, susceptible de variantes de equipamiento y país) detalla que para versiones de **8 y 12 sensores** se activa con el botón o al seleccionar R, y que **al salir de R puede permanecer encendido** en otra marcha. Indica que en algunos modos se desactiva cuando la velocidad supera 15 km/h, y que con P/freno de estacionamiento las advertencias acústicas pueden silenciarse aunque siga la indicación visual. Manual: https://t1d.cherymanuals.com/views/om004007.html (sección «泊车雷达系统»).

Esto **podría explicar** el pitido en tráfico cuando otro vehículo se acerca por detrás: el sistema general sigue activo tras salir de reversa. NO demuestra que el radar se esté activando en el mismo modo en la versión argentina, ni que el umbral acústico sea modificable por separados.

### Objetivo de ingeniería (sin pérdida de asistencia)

- Mantener **alertas delanteras de estacionamiento** y **alertas traseras cuando efectivamente se da marcha atrás**.
- Verificar con OEM si el sonido trasero en D se puede silenciar temporalmente sin cortar detección y/o avisos delanteros; identificar un ajuste *autorizado* específico si existe, antes de tocar la ECU.
- No forzar escritura `2049=0` desde DriveLab: activa/desactiva el radar general y afectaría el delantero.
- No forzar escritura `2107`: valor 0 persistente sin tabla de niveles validados.
- Próxima evidencia: observar el símbolo/botón OEM y si su piloto queda encendido en D tras salir de R, junto al pitido; comparar una vez con vehículo detenido y con control OEM original. Sin maniobras peligrosas ni usar a personas como obstáculos.

