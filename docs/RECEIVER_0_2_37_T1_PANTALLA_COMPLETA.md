# Receiver 0.2.37 — pantalla completa T1 y diagnóstico de bandas OEM

Fecha: 11/11/2026. Sender 0.2.16, servicios y grabación OEM no modificados.

## Observación del propietario

T1Launcher ocupa el panel completo; el resto de apps instaladas, incluido DriveLab Receiver 0.2.36, aparece con franja superior «Pruebe a decir Hola, Jetour», barra inferior de iconos OEM y márgenes laterales. **La conducta afecta a muchas apps, no solo DriveLab**, por lo que el firmware puede gestionar ventanas de terceros de una manera distinta al launcher.

## Comparación estática del APK T1Launcher T1N134

En el **APK aportado por el propietario** (SHA-256 `6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480`), `classes2.dex` define `Lcom/t1launcher/MainActivity;->goFullScreen`:

* `onCreate` invoca directamente `goFullScreen()` y `watchStatusBar()`.
* `onWindowFocusChanged` vuelve a invocar `goFullScreen()`.
* `goFullScreen` invoca `androidx.core.view.WindowCompat.setDecorFitsSystemWindows`, crea `WindowInsetsControllerCompat` para la ventana y llama a `hide` / `show` con `statusBars` / `navigationBars`; también invoca `Window.addFlags`.
* Este es **un método específico de la propia Activity de T1Launcher**; no demuestra que exista una configuración global de fábrica para todas las aplicaciones. Tampoco demuestra privilegios de sistema que el Receiver no tenga.

**Importante:** el header de voz y el dock inferior que aparecen en las fotografías pueden ser ventanas propias de la ROM Jetour, independientes de las barras estándar Android. Los métodos inmersivos estándar no garantizan ocultar ventanas OEM superpuestas.

## Implementación reversible Receiver 0.2.37

* Nuevo `T1ImmersiveMode` aplica `Window.FLAG_FULLSCREEN` y `View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY`, `LAYOUT_FULLSCREEN`, `LAYOUT_HIDE_NAVIGATION`, `HIDE_NAVIGATION`, `FULLSCREEN` al decor. Para Android 11 (API30), también solicita `Window.setDecorFitsSystemWindows(false)` y `WindowInsetsController.hide(statusBars|navigationBars)` con reaparición temporal mediante swipe.
* Nuevo `T1ImmersiveActivity` re-aplica el modo al entrar, reanudar y recuperar foco; evita que cuadros de diálogo u otra pantalla deshagan permanentemente la presentación.
* Alcance: cuatro pantallas del **Receiver**: inicio, controles vehículo/CODE, cockpit premium y tablero técnico. No modifica Waze, Spotify, apps ajenas, configuración global de Android, ni T1Launcher.
* Por defecto habilitado solo dentro de Receiver y **desactivable** desde la pantalla principal (botón `PANTALLA COMPLETA · ACTIVADA · MOSTRAR BARRAS`) o desde la cabecera del cockpit (`MOSTRAR BARRAS`). La elección se conserva localmente y ambas pantallas permiten reactivarla. No requiere root ni firma OEM.

## Prueba física sugerida — detenido en P

1. Abrir Launcher original y fijarse en altura/anchura de su ventana. Abrir Receiver 0.2.37 y luego cockpit.
2. Confirmar si desaparecen únicamente las barras estándar o si las franjas «Hola Jetour» y de controles siguen visibles. Si la parte OEM persiste, **no intentar borrarla globalmente**: hay que identificar propietario y tipo de ventana.
3. Si afecta navegación o controles necesarios, pulsar `MOSTRAR BARRAS`, o regresar a la versión previa firmada sin desinstalar. La barra Android también puede aparecer momentáneamente mediante gesto de deslizamiento.
4. Para diagnóstico ADB **solo lectura** (cuando el vehículo esté inmóvil y ADB autorizado): `adb shell dumpsys window windows` con T1Launcher y con Receiver al frente; buscar ventana enfocada, propietarias de barra y `mSystemUiVisibility`, `mAttrs`, `DisplayFrames`. No ejecutar comandos `settings put global policy_control` ni `wm overscan` a ciegas.
5. Si el firmware expone en Ajustes una opción tipo **«aplicaciones a pantalla completa»**, verificar el texto exacto y la app afectada antes de usarla. En la versión argentina aún **NO se ha comprobado esa pantalla ni su ruta**.

## Límites

* No se ha verificado físicamente 0.2.37 en el auto; GitHub CI valida compilación y presencia de banderas, no comportamiento del firmware.
* El modo inmersivo no convierte a DriveLab en la aplicación HOME de fábrica ni concede privilegios.
* Si **todas** las apps de terceros permanecen dentro de una ventana de presentación controlada por la ROM, la solución requiere diagnosticar el host y su configuración, no solamente poner flags de Android.
