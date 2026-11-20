# Bridge — primer prototipo original de VirtualDisplay/Presentation (19/11/2026)

## Qué implementamos en PR #93

La inspección de T1Launcher T1N145 aportada por el propietario confirmó un motor de sesiones virtuales y superficies que el Bridge Freeform antiguo no tenía. Se agregó **código original de diagnóstico**, independiente del binario T1Launcher:

- **BridgeVirtualProbeActivity.java**: crea una Surface a partir de TextureView.SurfaceTexture, usa DisplayManager.createVirtualDisplay con los flags OWN_CONTENT_ONLY y PRESENTATION, instancia una android.app.Presentation privada con un patrón propio y recibe cuadros de esa sesión en la pantalla central de Bridge.
- Comprueba que el ID virtual no coincida con un display existente y que sea privado. No asume que el ID 1 pertenezca siempre al cuadro de instrumentos: Android puede asignarlo a un display virtual si no hay segunda pantalla física.
- El botón **LAB · PROBAR PANTALLA VIRTUAL · SOLO CENTRAL** se encuentra en Bridge, bajo pantalla central. Requiere confirmación del propietario y vehículo detenido. Se libera al salir o tras **dos minutos**.
- **BridgeVirtualProbeContractTest.java** protege creación real de la superficie, privacidad, liberación de recursos, consentimiento, ausencia de ADB, comandos OEM y tareas de terceros.
- Los flujos de GitHub CI verifican el contrato y ahora también compilan Boot/Bridge en la tarea de Android QA. Se mantiene el PR como **draft**, sin merge/OTA/release.

## Qué NO hace

- **NO ejecuta Waze, Maps ni Spotify en la pantalla virtual.** La Presentation muestra sólo texto y fondo de prueba de Bridge.
- **NO crea una capa ni ventana en el tablero físico Display 1.** No controla, cubre ni ordena ventanas del firmware Jetour.
- **NO demuestra todavía que el mapa de T1N145 fue reproducido.** Un cuadro recibido sólo prueba el pipeline local VirtualDisplay → Surface → TextureView en esa ejecución, no la visibilidad de Waze en el tablero ni el uso de la rueda.
- No utiliza root, ADB de escritura, am task resize, force-stop, pm clear, CLEAR_TASK, acceso a bases de DriveLab Auto, Bluetooth ni claves de otros servicios.
- Un CI verde demuestra compilación y contratos estáticos, no prueba física en la T1.

## Siguiente puerta de validación

1. Compilar Bridge con GitHub Actions y cerrar todas las pruebas.
2. En Jetour detenida, abrir manualmente LAB y comprobar si el patrón se muestra y si se liberan los recursos sin interferir con el firmware. Estado local: bridge_virtual_probe_diagnostic_v1.last_result; no guarda rutas ni secretos.
3. Sólo con resultado positivo, prototipar **una sesión de mapa aislada en display virtual**, validando permisos Android y tarea/ID real antes de mover Waze. Nunca usar fallback automático al Display 0.
4. Separar después la superficie del mapa del marco visible físico (Rectángulo/Relojes), preservando alertas OEM; cualquier presentación sobre el tablero necesitará aislamiento, permisos, comprobación visual y cortafuegos propios.
5. Mantener volante SIN ZOOM y sin callback OEM hasta correlacionar mapa real, display virtual y visibilidad. No habilitar autoinicio de ventanas ni routing Sender al display virtual sin verificación.

## Política de publicación

**NO instalar ni publicar esta APK experimental; no hacer merge a main.** Avance de código para revisión en PR #93. Las releases estables existentes de Receiver, Sender y Boot no se modifican.
