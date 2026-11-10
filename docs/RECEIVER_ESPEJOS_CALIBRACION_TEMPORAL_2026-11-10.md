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

## Publicación OTA verificable — Receiver 0.2.14

- [Release v0.2.14](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.14) **latest** con `receiver.json`, APK Receiver firmada y `sender.json` preservado desde la 0.2.13 (digest idéntico).
- [Workflow de firma OTA 42832582917](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42832582917) **PASÓ**. Code de Receiver `15`, versión `0.2.14`. Sender sigue `0.2.13`.
- Nuevos botones: **LEER ESTADO RAW 2068/2069**, pruebas manuales del derecho **1.000/2.000 ms** y del izquierdo **1.000 ms**; botones **PLEGAR / DESPLEGAR** que verifican autorización y propiedad estándar `MIRROR_FOLD`, si falta se niegan sin actuación. No hay búsqueda automática de topes ni automatismo R/D.
- Instalación **solo desde Receiver → ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA** o la oferta automática al abrir si está disponible. **No entregar APK separada**. Ninguna prueba en vehículo de las funciones 0.2.14 está confirmada todavía.

## Modelo propuesto: ocho posiciones virtuales con reanclaje a extremos (10/11/2026)

**Propuesta del propietario / HIPÓTESIS a verificar.** En vez de almacenar solamente número de impulsos sin referencia, construir un **codificador virtual temporal** para cada espejo independiente, que se reancle siempre que una lectura **real y estable** confirme un extremo físico:

- **Lectura extrema inferior** `getInt(2068/2069)==1` → índice virtual **0/8** (abajo).
- **Lectura extrema superior** `getInt(2068/2069)==2` → índice virtual **8/8** (arriba).
- **Lectura intermedia** `==0` → **zona entre extremos**, NO índice 0 ni ángulo cero y NO indica un octavo particular.

**Advertencia decisiva:** estas tres interpretaciones de **GETTER** provienen de la observación del usuario y **aún no están validadas mediante lectura persistente con el mando físico liberado**. Las **órdenes de SETTER** se interpretan separadamente: para 2069 derecho, `setIntData(2069,1)` sube; `setIntData(2069,2)` baja; `setIntData(2069,0)` libera. **NUNCA** confundir el 2 del GETTER (supuesto «arriba») con el 2 del SETTER (orden de bajar).

### Escala de posiciones virtuales

`0/8 (inferior)` · `1/8` · `2/8=1/4` · `3/8` · `4/8=1/2` · `5/8` · `6/8=3/4` · `7/8` · `8/8 (superior)`

Son **nueve marcas, ocho intervalos iguales estimados**. **No se garantiza distancia angular equidistante** con motores cuyo desplazamiento por tiempo varía; validar visualmente y añadir correcciones.

Cada espejo lleva por separado:
- `T_down_ms`: tiempo comandado **medido** entre extremo superior confirmado y extremo inferior confirmado;
- `T_up_ms`: tiempo comandado **medido** en sentido contrario;
- `virtual_index` o fracción [0,1], `reference_valid`, `reference_timestamp`, estado de marcha, y posición objetivo `D`/`R` elegida por usuario;
- secuencia cruda de **orden/duración/lectura previa/posterior/estado de liberación**, sin sustituirla por una posición supuesta.

Para una prueba **hipotética** donde `T_down = 10.000 ms` y `T_up = 9.000 ms`:
- paso hacia abajo ≈ **1.250 ms** por octavo;
- paso hacia arriba ≈ **1.125 ms** por octavo;
- ir de 8/8 a 4/8 serían ~5 s de comandos hacia abajo, **solo estimación** hasta verificar visualmente;
- si se usan comandos de 1.000 o 2.000 ms, ajustar el último movimiento al tiempo restante **solo luego de verificar que el mando puede detenerse con seguridad**.

### Alternativa equivalente propuesta: calibración directamente en milisegundos (10/11/2026)

**Aclaración del propietario:** no hace falta mostrar posiciones numeradas 0/8 a 8/8. La UI puede presentar **duración de movimiento acumulada respecto de un extremo comprobado** y marcas de 25%, 50%, 75%. Ejemplo exclusivamente aritmético: **si** la carrera completa medida fuera de **20.000 ms**, desde arriba hacia abajo los objetivos serían **5.000 ms (¼), 10.000 ms (½), 15.000 ms (¾)**, y abajo 20.000 ms. **NO está medido que la T1 tarde 20 segundos** y no debe mostrarse como calibración real.

El algoritmo guardará `T_down_ms` y `T_up_ms` **por separado y por espejo**, así como el tiempo de movimiento realmente comandado (excluyendo pausas y respuesta de la app). Para ir de una referencia a otra: `duracion_objetivo = fraccion_objetivo * T_direccion_ms`, con compensaciones de ensayo si las posiciones resultan no lineales. Para una posición normal D o retroceso R elegida por el usuario, guardar su **duración equivalente desde un extremo** (además de versión y validez de la calibración); las duraciones de dirección opuesta deben calibrarse individualmente.

**Importante:** el programa NO puede «detectar cuántos milisegundos dura toda la carrera» solo por leer 2068/2069 mientras envía órdenes. Primero debe verificarse que un getter indique de forma estable el **extremo alcanzado y no la última orden de motor**. Sin esa señal, la duración de carrera se obtiene únicamente mediante calibración **guiada y observación humana**, no por homing automático contra topes. Una duración de 2.000 ms es el máximo del ensayo manual actual, no evidencia del recorrido total ni un permiso para enviar diez comandos consecutivos automáticamente.

Las marcas de cuartos y octavos son estimaciones de tiempo; motor, tensión, rozamiento y diferencias subir/bajar pueden producir posiciones angulares distintas a igual duración. Comparar visualmente ida y vuelta y corregir deriva antes de habilitar memorias o automatismo R/D.

### Reanclaje automático por extremo — solo si hay confirmación real

Al observar `getInt == 2` **establemente con el mando suelto** y contrastarlo con el espejo físicamente arriba, fijar la referencia a **8/8**. Al observar `getInt == 1` en las mismas condiciones y espejo abajo, fijarla a **0/8**. Un `getInt == 0` intermedio **no corrige ninguna deriva**: la app puede seguir estimando la fracción por tiempo, pero no sabe el ángulo real.

**No ejecutar «buscar extremo» enviando órdenes hasta que choque al tope.** Si el sensor de extremo no está confirmado, si el motor deja de responder, si un movimiento falla o si se detecta acción manual de un control externo, marcar `reference_valid=false`. Pedir una recalibración **guiada con control OEM**, registrar fin de carrera observable y evitar sostener corriente contra el motor. No usar la limitación mecánica como mecanismo de detección.

### Requisitos previos a inclinación automática en R

1. Confirmar lecturas `getInt(2068/2069)` y que **1 y 2 permanezcan** con mando liberado en sus extremos durante varias tomas (no meramente el estado transitorio de una orden).
2. Medir recorridos completos **de subida y bajada por separado**, sin tiempos inventados, con repetibilidad y parada segura. En ausencia de ángulo real, fracción es estimada.
3. Guardar D/R dentro del rango calibrado; validar regreso real tras varias idas/vueltas y tras apagar/reabrir la app.
4. Confirmar 1033 para las marchas R y D en la T1; automatizar solo cuando haya referencia válida y parada/restauración comprobadas. Si se pierde la referencia en tránsito **no realizar homing automático** ni mover espejos inesperadamente.

**Estado en producción:** Receiver 0.2.14 ofrece **lectura RAW y movimientos de ensayo**, pero **NO** implementa posiciones 0/8–8/8, calibración automática, ni D/R automáticos. Este apartado es **diseño**, no una funcionalidad entregada. Sender y mapa/viajes/consumo no se tocan.

## Próximo resultado que necesitamos del usuario

Para el espejo derecho, registrar lectura **antes, mientras está a mitad y después de soltar**; comparar si `getInt(2069)` continúa reportando 2/0/1 en extremos una vez liberado. Luego ensayo único de 1.000 ms y de 2.000 ms. Esto decide si se puede calibrar con límites detectados o solo medir tiempos aproximados.

**Regla:** no confundir `setIntData(2069,2)` (orden bajar) con `getInt(2069)==2` (posible estado superior observado): son operaciones distintas y requieren validación independiente.
