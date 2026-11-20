# DriveLab Bridge — inspección binaria real T1Launcher T1N145 (19/11/2026)

## Identidad y método
El propietario entregó un ZIP que contiene **T1Launcher-T1N145.apk**. APK: **21.773.219 bytes**; SHA-256 **f43c46b6e5a044482559373b6f705f6fca5dbcb465c848dc06f0cc18452bb11b**, igual al hash registrado en el documento anterior. Dos DEX: clases1 13.002.520 bytes y clases2 7.370.412 bytes. Se inspeccionaron metadatos, métodos y referencias de llamadas DEX **sin instalar ni ejecutar** el APK. No copiar la APK ni bytecode al repositorio. La inspección estática no demuestra visibilidad ni permisos en la T1 real.

## Mecanismos confirmados por métodos de T1N145
- **Mapa en pantalla virtual:** `cluster/MapaTransparente.asegurarVd` llama a `DisplayManager.createVirtualDisplay` (nombre `T1Mapa`); `marcoListo` fabrica `Surface` desde `SurfaceTexture`; `redimensionar`/ `reubicar` administran tamaños y ubicación.
- **Presentación visible:** `cluster/MarcoMapa` crea `TextureView` con `SurfaceTextureListener`. `MapaTransparente.armarCapa` utiliza `ClusterPantalla.alojarMapa` o `WindowManager.addView`. `ClusterPantalla` **hereda de `android.app.Presentation`**. No equivale a lanzar Waze directamente en Display 1.
- **APP 2 independiente:** `cluster/SegundaApp.asegurar` crea **otro** `VirtualDisplay` llamado `T1App2`; `ajustarVd` usa `VirtualDisplay.resize` y `SurfaceTexture.setDefaultBufferSize`. `meterAppEnTurno`/`mover` referencian comandos `am start --display`, `am display move-stack`, `am task resize`. La APP 2 estrecha no consiste únicamente en Freeform.
- **Freeform distinto de lo anterior:** `ventanas/Ventanas.abrirEnCaja` usa `ActivityOptions.setLaunchBounds`; `pedirVentanaLibre` referencia `setLaunchWindowingMode`. Es una ruta adicional, no la implementación completa del mapa.
- **Volante y accesibilidad:** `cluster/T1MapaServicio` hereda `AccessibilityService` y consulta `getWindowsOnAllDisplays`; implementa acciones y gestos. `cluster/VozVolante` referencia `com.autolink.adapterinterface.IHardKeyService`; `musica` usa `AudioManager.dispatchMediaKeyEvent` para controles multimedia. **Nuestro Bridge seguirá sin zoom**, conforme a lo pedido por el propietario.

## Brechas específicas del Bridge actual
1. `BridgeStandaloneWindows.tablero()` inicia una tarea Freeform en Display 1, **sin** `VirtualDisplay -> SurfaceTexture -> Presentation`. No da paridad de superficie ni asegura que el firmware Jetour deje el mapa visible.
2. `BridgeStandaloneWindows.center()` abre Maps y Spotify en dos tareas Freeform 65/35, pero no mantiene sesiones virtuales del tipo APP 2. No asegurar todavía vista móvil persistente ni recuperación de ruta.
3. `BootLinkService.openMap()` envía los destinos confirmados a Display 1 mediante `setLaunchDisplayId(1)` sin correlación robusta con la sesión de mapa ya presentada: el ACK de red no acredita que la tarea/bounds continúen correctos ni que el mapa sea visible.
4. `BridgeWheelAccessibilityService.visibleMap()` exige `TYPE_APPLICATION` de Waze/Maps en Display 1. Si el mapa se ejecuta en la pantalla **virtual** y sólo se presenta su superficie en Display 1, su ventana de accesibilidad puede pertenecer al ID virtual. No relajar las barreras contra control OEM: primero hay que asociar ventana de mapa, display virtual, superficie visible y autorización del usuario.
5. La T1N145 contiene rutas ADB para mover tareas, pero **no** autoriza adoptar escrituras ADB no verificadas ni elevar permisos en Bridge; comprobar primero las capacidades reales de Android/firmware y fallar sin efectos si son insuficientes.

## Próximo desarrollo original y validación
- Diseñar **sesión de mapa persistente propia**, `VirtualDisplay`/superficie privada y anfitrión para el tablero, con ajustes Rectángulo/Relojes sin cubrir alertas críticas. Evitar relanzamientos/limpieza de tareas cuando se modifica el marco.
- Diseñar sesión APP 2 independiente para Spotify, conservando audio y estado al alternar central/tablero. Probar la geometría 65/35 con el vehículo detenido.
- Sender debe entregar destino **a la sesión real** y distinguir estados `SOLICITUD`, `TAREA`, `SUPERFICIE`, `CAPA`, `VISIBILIDAD_CONFIRMADA`, sin declarar éxito sólo por `startActivity`.
- Volante opcional: rueda anterior/siguiente opción, OK confirmar, Atrás cancelar, **sin zoom**. Único propietario Binder OEM; no interactuar con la UI del fabricante.
- Pruebas físicas estacionadas: enumerar displays, confirmar tarea virtual/superficie/visibilidad, comprobar capa OEM y avisos, conservar mapa+Spotify+audio durante cambios; QR Xiaomi en puertos 48391/48392 sin detener Receiver anterior.
- Nada de `pm clear`, `force-stop`, `CLEAR_TASK`, simular MODE, root, copiar binarios de T1Launcher o interferir con alertas OEM.

**Estado: análisis de ingeniería confirmado; implementación equivalente y QA físico todavía pendientes. NO MERGE/RELEASE/OTA de Bridge por este hallazgo solamente.**
