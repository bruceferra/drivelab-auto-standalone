# Bridge LAB 0.1.22 — MediaProjection en la Jetour T1: prueba física positiva

Fecha: 20/11/2026. Rama de investigación PR #93. Reporte directo del propietario.

## Resultado confirmado por el propietario
En Bridge LAB **0.1.22-lab.19**, tras la prueba manual de **LAB · COPIAR CENTRAL A TABLERO · PERMISO · 20 S**, el propietario informó: **«Funciona perfecto»**.

Secuencia contextual comprobada por el equipo propietario:
1. En versiones anteriores, la `Presentation` sobre Display 1 físico cubrió la capa Radio/Bluetooth de Jetour.
2. Crear `VirtualDisplay` privado o público y lanzar allí una Activity propia produjo **«ACTIVITY DENEGADA POR ANDROID»**, aunque la animación de respaldo sí se veía sobre la capa OEM.
3. En **0.1.22-lab.19**, se implementó `MediaProjection` consentida + FGS `mediaProjection` + `VirtualDisplay` capturador hacia una `SurfaceTexture` anfitriona, mostrada en una `Presentation` no enfocada/no táctil en la franja inferior de Display 1.
4. El propietario reporta que **esa prueba funciona perfectamente en el vehículo**. El contexto de la prueba implica que la imagen de la pantalla central (con Waze abierto durante la prueba) se mostró en el tablero, pero el mensaje no describe por separado latencia, FPS, duración exacta, apagado automático ni avisos OEM: NO dar esos puntos por verificados físicamente.

## Qué demuestra y qué NO
**Sí:** se dispone de una ruta de transporte visual de pantalla central → clúster Display 1 usando la autorización explícita de Android, **sin tener que lanzar Waze en un VirtualDisplay**. La superficie llega por delante de la región OEM Radio/Bluetooth comprobada previamente.

**No:** esto NO crea una instancia de Waze independiente; refleja **todo lo visible** en Display 0, incluidas potencialmente notificaciones u otra app. Si Waze no está delante en la central, deja de verse en la copia. El volante/OK/atrás no controla Waze por medio de esta copia ni hay correlación de ventanas de accesibilidad. No confirma que avisos importantes sigan visibles en toda condición.

## Criterios antes de considerar algo persistente
- Conservar la prueba de 20 segundos y su consentimiento por sesión como modo diagnóstico, NO activar automáticamente al encender ni durante la marcha.
- Auditar corrección de aspecto, orientación, escalado y latencia visual en la unidad física.
- Verificar limpieza de `MediaProjection`/FGS/VirtualDisplay/Surface en timeout, cierre, rotación, apagado y revocación.
- Delimitar región del mapa con restricciones, preservando instrumentos y avisos críticos; evitar superposiciones persistentes en instrumentos si no se puede garantizar seguridad.
- Para Waze independiente de la app central, investigar **otra arquitectura** distinta del mero mirroring; la prueba actual NO resuelve esa independencia.
- Evitar root, escrituras OEM, `pm clear`, `force-stop`, mover o destruir tareas, y cambios del volante sin autorización y validación.

**Estado:** Hito físico positivo de transporte visual. Bridge LAB 0.1.22 sigue experimental. Receiver y versión estable sin cambios.
