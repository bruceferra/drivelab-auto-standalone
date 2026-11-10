# DriveLab Receiver: carrera de espejos por tiempo, estado y plegado

**2026-11-10.** Hallazgo observado por propietario. **No validado como medición absoluta de ángulo ni de fin de carrera.** Continuación de [extracción T1N134](EXTRACCION_T1LAUNCHER_T1N134_2026-11-10.md) y [espejos 0.2.12](RECEIVER_ESPEJOS_0_2_12_CALIBRACION.md).

## Evidencia de usuario y lecturas

- Mirror Right **2069**: el usuario confirmó que órdenes **1 suben**, **2 bajan**; cuatro accionamientos cortos en ambos sentidos funcionaron. **2068** identifica el izquierdo según las observaciones del usuario, pero sentidos y paro de cada lado requieren prueba aparte.
- En una lectura que el usuario está interpretando como *estado de posición*, afirma **2 = extremo superior**, **1 = extremo inferior**, **0 = intermedio**. **No confundir esta lectura con las órdenes de escritura (1 = subir, 2 = bajar, 0 = liberar).** Un mismo ID puede tener interpretaciones distintas para getInt y setIntData.
- **PENDIENTE:** verificar con lecturas `VehicleLink.getInt(2068/2069)` en momentos separados y sin tocar botones: una vez en extremo superior, una vez en el medio, una vez en extremo inferior y otra vez tras ajustar manualmente con el mando OEM. Debe mantenerse estable con mando suelto para clasificarlo como sensor de estado grueso. Si vuelve siempre a 0 al soltar, es un estado de comando, **no de posición**.
- Las propiedades Android Automotive `MIRROR_Z_POS` y vendors consultadas desde `CarPropertyManager` **no aparecen en la lista accesible** en Receiver 0.2.12; se desconoce si faltan o están protegidas. Sin sensor de ángulo accesible, 0 solo categoriza una región, **no permite conocer porcentajes dentro de la carrera**.

## Modelo de calibración temporal — NO asumir 10 segundos medidos

Supuesto de cálculo del usuario: si el espejo tardara **10 s** de tope a tope de forma continua, serían **10 órdenes de 1.000 ms** o **5 órdenes de 2.000 ms**; la media carrera geométrica **no queda demostrada** por cinco segundos porque pueden existir retardo, aceleración, fricción, tensión variable, atascos y saturación contra límite. *Estos números son exclusivamente un ejemplo aritmético.*

Medir dos duraciones **independientes** (pueden diferir):
- `T↓`: duración **efectiva validada** de extremo superior al inferior.
- `T↑`: duración **efectiva validada** de extremo inferior al superior.

Una vez confirmados el significado y persistencia de estados de extremo:
1. **Lectura inicial sin motor:** obtener estado de espejo derecho/izquierdo por `getInt(2069/2068)` antes del pulso y guardar timestamp. No suponer fin de carrera por un código de mando.
2. **Probar manualmente 1.000 ms** desde posición intermedia y comprobar que el espejo se mueva, pare al liberar y que la lectura de estado se corresponda con observación física. Si 1.000 ms es seguro, probar máximo **2.000 ms** solo bajo supervisión en P, sin movimiento automático ni ráfagas.
3. Para medir recorrido completo, tomar estado de extremo de forma **pasiva** y registrar sumas de duraciones de órdenes realmente completadas, separando ↑/↓. No buscar el fin de carrera manteniendo órdenes activas a ciegas; no usar topes mecánicos como sensor eléctrico.
4. La carrera estimada se convierte en una tabla de intervalos con **incertidumbre**: `fraction_estimada = tiempo_acumulado_en_sentido / T_sentido`. No asumir que implica el mismo ángulo físico. Solo actualizar al recibir una parada exitosa y con cambios esperados.
5. Guardar dos referencias **D** (normal) y **R** (retroceso) solo después de validarlas manualmente. Si hay desplazamientos físicos externos, interrupción, estado incompatible o falta de lectura, invalidar calibración. La descalibración del launcher de terceros demuestra que una memoria de pulsos no basta.
6. **Automatización de R/D desactivada** hasta validar marcha 1033 en P/R/D, comportamiento de los dos sentidos, readback y parada, y retorno reproducible tras múltiples ciclos.

## Plegado/desplegado

- Android Automotive define `MIRROR_FOLD` **287312709**, propiedad booleana global; requiere permiso privilegiado `CONTROL_CAR_MIRRORS`. **No constituye prueba de que la T1 la soporte o que el Receiver tenga permiso**. Referencia: https://developer.android.com/reference/android/car/VehiclePropertyIds .
- Se permite un botón que *compruebe disponibilidad*, y que rechace con mensaje claro si no figura o no tiene permiso. **Nunca** emitir escrituras arbitrarias a los candidatos vendor `VC.MIRROR FLIP`, `SETTINGS REAR MIRROR FOLD`, ni adivinar sus valores sin contrato OEM.
- Cualquier movimiento manual queda restringido a **P/velocidad 0**, confirmación explícita y un único accionamiento. No recurrir a Bluetooth, root ni al cierre de sesión del vehículo para forzarlo.

## Próximo resultado que necesitamos del usuario

Para el espejo derecho, registrar lectura **antes, mientras está a mitad y después de soltar**; comparar si `getInt(2069)` continúa reportando 2/0/1 en extremos una vez liberado. Luego ensayo único de 1.000 ms y de 2.000 ms. Esto decide si se puede calibrar con límites detectados o solo medir tiempos aproximados.

**Regla:** no confundir `setIntData(2069,2)` (orden bajar) con `getInt(2069)==2` (posible estado superior observado): son operaciones distintas y requieren validación independiente.
