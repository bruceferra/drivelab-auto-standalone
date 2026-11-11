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
