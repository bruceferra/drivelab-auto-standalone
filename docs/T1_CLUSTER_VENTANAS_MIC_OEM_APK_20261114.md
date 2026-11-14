# Jetour T1 · Diseño ajustable del clúster y micrófono OEM

Fecha: 2026-11-14 · **protocolo extraído del APK real del propietario**.

## Procedencia y límites

Se ha inspeccionado *directamente* `T1Launcher-T1N134.apk` de la biblioteca del propietario (21.652.547 bytes, SHA-256 `6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480`). Los hechos se extrajeron de las instrucciones DEX de `classes2.dex` con un lector estructurado de métodos y opcodes, no se adivinaron IDs ni se copió el código Kotlin de terceros al repositorio.

La release pública de MainthorCar hoy es **T1N144** (14/11/2026). No se inspeccionaron sus bytes: confirmar que esa versión mantiene el protocolo después de probar en la camioneta. Este análisis solo demuestra lo presente en la versión T1N134.

## Micrófono físico

Método `com.t1launcher.cluster.VozVolante$callback$1.onTransact`:

- Se recibe `IHardKeyCallback` mediante transacción **3** con tres enteros: `key`, `action`, `extra`.
- Cuando `key == 1010` y `action == 100`, se publica el runnable `VozVolante.escuchar()` en el Handler principal.
- Solo para los códigos 1000–1003, el mismo callback se deriva a `MandoMapa.tecla` (ruedita/OK/Atrás).
- `VozVolante.iniciar$lambda$1` vincula el componente OEM `com.autolink.hardkeyservice/.core.ALHardKeyService`. `VozVolante.llamar` pasa el Binder callback a `IHardKeyService` mediante transacción **1** para registrar (2 para baja).
- `VozVolante.escuchar()` usa `SpeechRecognizer.createSpeechRecognizer()`, `RecognitionListener`, idioma `es-AR`, modelo `free_form` y `startListening`.

Implementación independiente de DriveLab: `T1OemHardKeyBridge` reconoce **1010** además de 1000–1003 y el nuevo `T1OemMicrophoneService` (opt-in) procesa exclusivamente **1010/100**, con antirrebote. Solicita abrir la **pantalla de voz existente**; no captura audio de fondo. Si Android bloquea una actividad en segundo plano, muestra notificación/diagnóstico y no afirma que hubo escucha. Al iniciarse, comprueba que el puente opcional OEM Waze no esté habilitado: dos registros en el mismo Binder podrían ser exclusivos. Se restaura al encender solo si fue habilitado por el usuario.

## Ventanas y división

En `com.t1launcher.cluster.LanzarApp.lanzar` se comprobó:
- `ActivityOptions.makeBasic()` + `setLaunchDisplayId`.
- `setLaunchWindowingMode(5)` vía reflexión (modo **freeform** de Android).
- `setLaunchBounds(Rect)` + `Context.startActivity(Intent, options.toBundle())`.

También `com.t1launcher.ventanas.Ventanas.pedirVentanaLibre` usa reflexión con valor **5**, y `abrirEnCaja` aplica los límites `Rect`. Es una vía técnica concreta, no garantiza que el UID de DriveLab tenga los mismos permisos de la ROM OEM.

Implementación propia: tres modos (FULL, WINDOW, SPLIT), con cuatro sliders para la ventana (ancho, alto, X, Y) y un slider de proporción **30–70 %** para dos apps. Vista previa local y configuración persistente. Todas las pruebas en el clúster exigen P/0 km/h verificados por OEM. El modo freeform comienza apagado; solo se aplica a navegación tras una prueba reciente y confirmación visual del conductor. Si la reflexión/ROM no permite freeform, se indica error sin abrir un sustituto fullscreen sobre la pantalla central. La ruta anterior Waze fullscreen hacia Display 1 continúa como opción segura.

Para dividir se envían dos aperturas de apps independientes con `setLaunchBounds`; Android puede rechazar, superponer o redimensionar alguna. **No afirmar simultaneidad real hasta verificarla físicamente.** El slider controla geometría solicitada, no una garantía de layout o de instrumentos OEM.

## Plan de comprobación

1. Con T1 detenida, conservar seleccionado **Display 1**, que ya mostró Waze fullscreen; Display 2 no mostró.
2. Probar MODO VENTANA con 70 % ancho, 75 % alto, X=0, Y=0. Verificar imagen, tamaño y foco; restaurar FULL si falla.
3. Probar DIVIDIR 60/40 con Waze a izquierda y Google Maps derecha, siempre en P. Confirmar que ambos están realmente presentes antes de activar.
4. Con módulo Cluster de T1Launcher desactivado y puente OEM de ruedita DriveLab desactivado, activar receptor de micrófono 1010. Pulsar el botón físico y comprobar el texto «MICRÓFONO 1010/100». Si no llega: anotar estado Binder; no probar otros códigos a ciegas.
5. Conservar desactivación reversible. Si se pierde la escucha, el volante o alguna indicación importante del tablero, restaurar el Cluster anterior y desactivar los puentes experimentales.

**Seguridad:** no reclamar HOME, no escribir CAN/OEM, no pedir root, no grabar audio en segundo plano, no usar MediaProjection, ni asumir que una llamada `startActivity` sin error es una prueba visual.
