# Bridge LAB 0.1.23: ruta Google Maps y rueda copiadas del Receiver estable

**Estado de implementación, 20-11-2026.** PR #93 aislado, LAB 0.1.23-lab.20, tag `bridge-lab-v20`. No se han editado archivos `receiver/` ni `sender/` en esta intervención.

## Hallazgo exacto: DriveLab Auto ya solucionaba el resumen
El **Receiver estable** manda `google.navigation:q=<destino>&mode=d` con paquete `com.google.android.apps.maps` (`receiver/.../T1NavigationTargets.java`). `BootLinkService` de Bridge ya tenía el mismo enlace; **ése no era el bug**.

Lo que Bridge omitía:
- `T1NavigationTargets.send()` llama a `T1GoogleMapsReplacement.arm()` solamente en una petición de ruta nueva de Sender. Abre una autorización de 12 segundos para reemplazar ruta previa y auto-ejecutar un único botón Iniciar de Maps.
- `T1WazeWheelService.maybeConfirmMapsReplacement()` detecta el texto de salida/reemplazo y limita el clic a un botón exacto. `maybeStartMapsNavigation()` detecta un único botón de inicio visible y permite un clic sin tocar OEM ni otras ventanas. Ambas corren por el heartbeat de accesibilidad.
- Bridge enviaba sólo el intent y no armaba el flujo automático, por eso el propietario veía el resumen en lugar de instrucciones.

## Paridad instalada en Bridge
- `BridgeGoogleMapsReplacement.java` porta la lógica de la clase de Receiver (TTL 12 s, `startAction`, `stopQuestion`, `exitQuestion`, `stopAction` y confirmaciones), adaptando exclusivamente el paquete, preferencias y el ID real de display destino.
- `BootLinkService.openMap`: arma el flujo **sólo al recibir una ruta autenticada para Google Maps**, no al abrir la app, y cancela si Android rechaza el intent.
- `BridgeWheelAccessibilityService`: porta reconocimiento de STOP/START de `T1WazeWheelService`; verifica Maps en la pantalla realmente seleccionada por la ruta, y ejecuta como máximo un clic en la ventana de Maps dentro de los 12 segundos. El heartbeat sigue también con volante desactivado para no acoplar la navegación a la rueda. Se eliminó botón manual «Iniciar ruta del resumen».
- OEM hardkey: receptor manual ya no depende de que Jetour publique la ventana visible del mapa; 20 segundos de recaptura sin acciones; reintento de Binder desconectado; lectura de estado real de accesibilidad y ADB local 127.0.0.1:5556→:5555. Waze/Maps OK/Atrás y selección sólo tras verificar ventana real. **No zoom**.
- QA: `BridgeMapsReceiverAutoRouteSourceTest` compara los reconocedores críticos de reemplazo e inicio con las fuentes del Receiver, y contract tests impiden zoom, gestos, BACK global, task wipes.

## IMPORTANTE: no mezclar modo seguimiento con navegación
El lanzamiento `google.navigation:q=<destino>&mode=d` de Android solicita navegación giro a giro a un destino. El **MODO MANEJO / seguimiento sin destino** de T1Launcher es una función distinta. En la nota pública oficial T1N145 figura que Google Maps, cuando abre desde cero en el marco del módulo, arranca en modo seguimiento. Las notas T1N144 indican solicitudes de seguimiento tras ~4 s y 10 s sólo si no hay ruta. **No está documentado en el código propio el intent propietario exacto de T1Launcher ni se ha reproducido aquí ese modo de seguimiento.** La nueva LAB resuelve el circuito equivalente al DriveLab Auto para rutas nuevas, no asegura modo libre ni persistencia de capa.

## Prueba mínima (estacionado)
1. Si Bridge no recibe teclas, comprobar «ACCESIBILIDAD REAL»; con permiso conectado, «RECAPTURAR TECLAS OEM · 20 S» debe contar 1000–1003 sin ejecutar acciones; T1Launcher volante debe estar desactivado para evitar doble dueño. Si falla ADB: «RECONECTAR ACCESIBILIDAD · ADB LOCAL» (5556/5555 autorizado).
2. Dejar GOOGLE MAPS como mapa del volante, elegir Display 1 **si Maps está realmente en Display 1**. No asumir que una copia MediaProjection en tablero cambia el display de la ventana; si Maps vive en central, Display 0.
3. Enviar una ruta nueva desde Sender. Verificar si Google Maps pasa automáticamente de resumen a navegación y qué registra «GOOGLE MAPS · NAVEGACIÓN AUTOMÁTICA».
4. No activar zoom. La superposición ocasional de Jetour/orden de capas todavía no está resuelta por esta entrega.

## Referencias
- `receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1NavigationTargets.java`
- `receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1GoogleMapsReplacement.java`
- `receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1WazeWheelService.java`
- https://developer.android.com/guide/components/google-maps-intents
- https://github.com/MainthorCar/t1launcher/releases/tag/T1N145

**No afirmar éxito físico antes de repetir en la Jetour. No mover tareas ni interferir con avisos de seguridad.**
