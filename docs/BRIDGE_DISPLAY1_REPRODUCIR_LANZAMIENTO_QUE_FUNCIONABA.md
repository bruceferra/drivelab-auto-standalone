# DriveLab Bridge — reproducir la aparición real de mapas y rueda (19/11/2026)

## Corrección factual del usuario (prioridad máxima)

- T1Launcher apagado; módulo anterior de clúster apagado.
- La capa que tapa los mapas es la pantalla audiovisual/MODE del firmware Jetour.
- **El botón MODE no hace que los mapas se vean.** La experiencia histórica es que al ejecutar diversas acciones de envío/apertura desde **DriveLab T1 Auto (Receiver)**, en ocasiones los mapas de Display 1 terminaban POR ENCIMA de la interfaz del firmware y simultáneamente funcionaba la rueda del volante.
- NO pedir dos fotos TAPADO/VISIBLE obtenidas alternando MODE; el escenario es incorrecto.
- No repetir acciones peligrosas de “reparación mapas”: `pm clear --cache-only` borró datos completos en Android 11.

## Diferencia COMPROBADA EN CÓDIGO entre Receiver anterior y Bridge WIP

**DriveLab T1 Auto, launch de mapas sobre Display 1:**
- `T1ClusterLayoutPolicy.start(...)`: `ActivityOptions.setLaunchDisplayId(displayId)`, `setLaunchWindowingMode(5)` mediante reflexión y `setLaunchBounds(Rect)`, flags `NEW_TASK|SINGLE_TOP|CLEAR_TOP`.
- `T1ClusterLayoutPolicy.routeWazeIfActive(...)` y `T1ClusterWazeRouter.routeGoogleMaps(...)` **conservan el rectángulo Freeform y panel seleccionado al enviar un destino del celular**, y llaman `start(...)`.
- `ReceiverService.scheduleNativeClusterWaze()`: abre paneles guardados tras iniciar el sistema, restaura último destino y llama `T1WazeWheelService.requestClusterReady(...)` después de solicitar apertura. La rutina histórica `T1ClusterBootFocus.afterClusterBoot()` movía tareas con `ActivityManager.moveTaskToFront`, pero el código actual suspendió sus reintentos tras problemas: NO afirmar que eso solucionaba la capa por sí solo.
- `T1WazeWheelService` usa accesibilidad y `T1OemHardKeyBridge` con evento OEM para rueda (1000–1003), requiere autorización del usuario y no debe competir con otro callback.

**Bridge rama de prueba antes de migrar:**
- `BootLinkService.openMap(...)` crea Intent Waze/Google Maps y sólo configura `setLaunchDisplayId(1)`, NO aplica modo FREEFORM ni límites de ventana, NO enumera/reutiliza tareas existentes ni inicializa la ruta OEM/Accessibility de la rueda.
- **Por lo tanto, Bridge aún NO reproduce el mecanismo que históricamente podía funcionar**. No anunciarlo como resuelto ni publicar esa compilación.

## Trabajo técnico concreto (en este orden)

1. Llevar al Bridge independiente la **apertura Freeform con bounds por panel** y reuse de tareas verificadas, manteniendo Display 0 y Display 1 separados, sin introducir callbacks duplicados.
2. Entregar destino desde Sender a la ventana ya configurada del mapa (no abrir otra instancia full screen ni cambiar al Display 0).
3. Migrar el control de rueda: un único callback OEM con opt-in y servicio de accesibilidad de Bridge autorizado por el usuario, condicionado a que el mapa de Display 1 sea realmente identificable.
4. Registrar sin mutaciones **qué ocurre antes y después del envío de destino que eventualmente hace que los mapas aparezcan**, en lugar de pedir al usuario alternar MODE. Analizar ActivityManager, WindowManager y SurfaceFlinger de Display 1; datos de prueba sin rutas, coordenadas, secretos ni nombres de apps de terceros.
5. No dar por visible una app porque Android aceptó `startActivity` o `moveTaskToFront`. Verificar tareas y pantalla, sin force-stop ni borrar mapas.
6. Sólo después de validación física de una/ dos ventanas y de rueda, habilitar autoinicio y actualizador del nuevo Bridge. Mantener rama draft sin release hasta entonces.

## Seguridad

Nunca simular MODE, deshabilitar servicios críticos OEM, tocar el panel del auto mientras conduce, modificar tareas del fabricante ni generar overlays encima de alertas. No instalar nada en el Jetour hasta que la migración reproduzca apertura + rueda de forma comprobable.
