# Bridge LAB 0.1.19 — actividad Android propia dentro del display virtual

## Evidencia precedente
El propietario comprobó que el panel de prueba de Bridge LAB 0.1.18 cubrió totalmente Radio/Bluetooth en la zona inferior derecha del Display 1 físico. Se validó el orden de esa **Presentation**, no una actividad Android real.

## Nuevo experimento manual (20 segundos)
Botón en Bridge: **LAB · PROBAR APP REAL EN CAPA VIRTUAL · 20 S**.
- Bridge crea el mismo anfitrión `Presentation` no interactivo en Display 1 que ya se vio correctamente: ancho completo, banda inferior 65 %, zona superior transparente.
- Produce un `VirtualDisplay` **privado**, `SurfaceTexture → TextureView` sobre la superficie.
- Mediante `ActivityOptions.setLaunchDisplayId(idVirtual)` intenta lanzar una sola Activity **propia de Bridge**, `BridgeVirtualSceneActivity`, en tarea separada, sin flags de limpieza de tareas y sin involucrar Waze, Google Maps ni Spotify.
- La Activity inspecciona `getDisplay()`, tipo virtual y flag privado **antes de dibujar**. Si Android la abrió en otro display, se retira sin mostrarla. Un contador cambia cada 700 ms y alterna el fondo para distinguir contenido vivo de un patrón estático.
- Si Bridge no confirma el id en 8 segundos, falla cerrado. La prueba finaliza a los 20 segundos, al salir o al perder la superficie. Retira Activity propia, Presentation, VirtualDisplay y Surface sin limpiar datos ajenos.
- No se inicia en arranque, por llegada de rutas ni en segundo plano. No hay `force-stop`, `pm clear`, modificaciones OEM ni registro de volante.

## Resultado físico que se necesita
1. Sólo estacionado: abrir Radio/Bluetooth, luego el botón LAB de Activity.
2. Confirmar si aparece el texto **ACTIVITY REAL / CONTADOR EN VIVO** abajo y si el número aumenta. Verificar que siga delante de Radio/Bluetooth.
3. Si no aparece, tomar nota del resultado que muestra la app: display real, timeout o error de Android.
4. Verificar que se retire todo al llegar a 20 segundos. No probar mientras conduce.

## Límites
La política de Android 10+ permite a una aplicación lanzar **sus propias** actividades en un display virtual privado. Eso **no** concede automáticamente que Waze, de otro UID, pueda arrancar en ese display. No se afirmará que Waze funciona en la capa hasta ensayar una arquitectura apropiada y validar la ruta con el propietario. Mantener el área de alertas libre y no extender overlay a todo el clúster.

Prueba basada en arquitectura propia, no se copia bytecode del T1Launcher.
