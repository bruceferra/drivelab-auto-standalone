# Receiver 0.2.79 (code80) — ruedita Zoom de T1Launcher: diagnóstico sin salir de DriveLab

## Informe de la Jetour T1 (13/11/2026)
El propietario habilitó mediante WebADB el servicio de accesibilidad `T1WazeWheelService`, conservando otro servicio T1Launcher. Con Waze fuera del clúster, el botón de aprendizaje NO capturó códigos; al girar/pulsar ruedita aparece el cartel "Zoom" personalizado por su amigo y se cambia la ventanita izquierda OEM de Waze en la T1. No hay prueba de que la ruedita emita un Android KeyEvent para apps de terceros.

## Falso negativo de versión anterior
`T1WazeWheelService.onKeyEvent` examinaba `com.waze` en primer plano ANTES de mirar el estado de captura pendiente. Si el volante activa el overlay OEM o T1Launcher, Waze ya no es la ventana activa y la captura explícita se omitía. Además el usuario tenía que ir y volver entre Waze/DriveLab: diagnóstico incómodo e incierto.

## Cambio seguro
- `onKeyEvent` procesa 45s de aprendizaje explícito *antes* de comparar foreground `com.waze`; captura localmente el primer KeyEvent real visible para accesibilidad (con clase de pantalla y paquete activo). Retorna **false**, no roba el botón al sistema OEM. Fuera de aprendizaje no registra ni controla teclas de otras apps.
- `T1WheelSettingsActivity.dispatchKeyEvent` añade lectura directa del KeyEvent enviado a LA PANTALLA de DriveLab. Guarda durante aprendizaje y devuelve siempre `super.dispatchKeyEvent`; la app no intercepta Zoom.
- Pantalla RUTAS · VOLANTE mueve códigos, estado del servicio y contador al encabezado, actualizados cada 750ms. La prueba es **sin cambiar a Waze**: tocar APRENDER, girar la rueda ahí mismo, mirar el estado. También informa foco actual de la pantalla. Un botón abre configuración Android solo cuando existe resolución; en la ROM sin Settings explica usar WebADB.
- Si no llega nada a ninguno de los canales NO inventa keycode ni simula Waze. La fuente puede ser CAN, servicio OEM, Binder de cluster, entrada propietaria, o un servicio con filtro de teclas exclusivo. La documentación AOSP aclara que el filtro Accessibility FLAG_REQUEST_FILTER_KEY_EVENTS puede ser usado por una sola AccessibilityService a la vez (si otra ya la usa, DriveLab no recibe esos eventos). Por eso conservar T1Launcher es importante; no se desactiva ni reemplaza ningún servicio de fábrica sin evidencia.

## Si continúa sin KeyEvent
1. Auto estacionado, WebADB conectado, ejecutar `getevent -lt` y girar rueda: si recibe eventos, anotar `/dev/input/eventN` y código; si informa Permission denied o no hay eventos, la prueba NO prueba que el CAN OEM no los emita.
2. Conservar configuración original. Consultar en el APK / código privado de T1Launcher **qué servicio/Intent/Callback usa** para que aparezca "Zoom" y cambie ventanita. El repositorio público `MainthorCar/t1launcher` a 13/11/2026 expone web/README pero no Java/Kotlin de esa captura.
3. Con esa documentación y permiso del autor, usar un puente local explícito (p.ej. broadcast con destino definido a DriveLab) para acciones SIGUIENTE, ANTERIOR, OK y ATRÁS. No hace falta root SI el amigo puede emitir eventos permitidos por Android; es diferente a intentar suscribirse sin permisos a CustomInputEvent OEM.
4. Verificar recién después si la UI de Waze expone rutas alternativas; no prometer selección automática A/B/C sin accesibilidad.

**Prueba CI**: `T1WheelCalibrationSourceContractTest` impide regresar al bloqueo previo de Waze, exige captura limitada a 45s, pantalla visible, sin consumir pulsaciones OEM y sin Settings crash. Queda pendiente verificación física.
