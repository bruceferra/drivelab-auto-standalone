# Bridge LAB 0.1.18 — resultado físico en Jetour T1 (20/11/2026)

## Observación aportada por el propietario
En el vehículo, con `BridgeClusterSurfaceProbeActivity` y la nueva superficie de ancho completo en el 65 % inferior de Display 1, el propietario confirmó: **«Tapó todoooo»**. Contexto: la prueba se diseñó específicamente para alcanzar y comprobar la esquina inferior derecha, donde estaba el panel Radio/Bluetooth de Jetour.

**Conclusión acotada:** la `Presentation` de Bridge sobre el Display 1 físico puede quedar en primer plano de la interfaz Radio/Bluetooth en esa zona. Se valida el orden visual de la capa de prueba, no la ejecución, persistencia ni control de Waze. No extrapolar a seguridad, alertas OEM, todas las ventanas, cambios de marcha ni diferentes arranques.

## Siguiente experimento: sin modificar el firmware
1. Mantener la sesión propia `VirtualDisplay → SurfaceTexture → TextureView → Presentation` sobre Display 1; mantener la zona superior transparente y cierre manual/temporizado hasta evaluar seguridad.
2. Validar **primero** el lanzamiento de una actividad propia y desechable de Bridge en el Display virtual privado, con confirmación física de imagen, paquetes, tarea, ID de display y liberación limpia.
3. **Sólo después**, evaluar con el usuario estacionado si Android Jetour autoriza ejecutar Waze en ese display virtual. No inferir que `ActivityOptions.setLaunchDisplayId` basta, ni mover/forzar detener/reiniciar su tarea existente cuando tiene ruta activa. Preservar destinos duales y QR del Sender.
4. El volante OEM permanece deshabilitado automáticamente: Bridge actualmente identifica ventanas de mapas **en Display 1 físico** y tendría que reconciliar explícitamente el ID virtual, propiedad real de la ventana y superficie visible antes de registrar el único callback OEM; nunca dirigir eventos a Jetour.
5. No declarar **Waze estable** hasta repetir apertura, ocultamiento/aparición de Radio/Bluetooth, navegación real, cambios de foco y permisos en la unidad física. Nunca cubrir de forma persistente advertencias críticas de seguridad.

## Estado
Prueba de Z-order positiva en zona inferior. Pendiente implementación y validación de app real dentro de pantalla virtual; pendiente prueba de rueda en esa arquitectura. Las versiones estables y Receiver no se modifican.
