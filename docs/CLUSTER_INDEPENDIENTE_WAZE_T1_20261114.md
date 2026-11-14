# DriveLab Auto Receiver — clúster propio Waze Jetour T1

Fecha: 2026-11-14. Estado: experimental y DESACTIVADO POR DEFECTO.

## Origen
MainthorCar/t1launcher expone en GitHub web, README, licencia y APK release T1N134, pero NO su código Android completo. El análisis estático del APK que ya consta en docs/EXTRACCION_T1LAUNCHER_T1N134_2026-11-10.md identifica cluster.ClusterService, cluster.ClusterPantalla como Android Presentation, cluster.ClusterLink y cluster.T1MapaServicio. El módulo espejo.EspejoService usa MediaProjection; no está probado que sea el mecanismo del Cluster actual ni se ha inspeccionado la APK recién modificada por su autor.

## Implementación independiente
T1ClusterWazeRouter permite seleccionar únicamente un display público secundario detectado por DisplayManager. Registra ID, nombre y dimensiones; reidentifica tras reinicio solo si hay exactamente un display con esa huella. Launch de com.waze mediante ActivityOptions.setLaunchDisplayId, sin añadir ventana flotante, HOME, root, ADB, MediaProjection, inyección CAN ni controles OEM.

Ruta centralizada: ReceiverService para destinos Sender Xiaomi; ShareToClusterActivity para Google Maps compartido; T1VoiceRouteActivity para voz; ReceiverActivity para pruebas directas. Si el modo clúster está activo y Android rechaza la apertura, NO se abre Waze en la pantalla central como alternativa oculta: muestra diagnóstico. Modo clúster apagado mantiene Waze original.

Pantalla de configuración: Receiver > WAZE EN CLÚSTER · AUTÓNOMO. Selección explícita de display, prueba manual solo si marcha P y 0 km/h leídos del OEM, activación de destinos y autoabrir Waze al arrancar. Al activar arranque se habilita ReceiverService existente; BootReceiver inicia el servicio y espera 20 segundos para que la ROM exponga el segundo display. No reemplaza T1Launcher como launcher HOME ni desactiva su Cluster.

## Validación en la T1
1. Mantener el Cluster del tercero ACTIVO durante la primera prueba.
2. En P, abrir configuración DriveLab, detectar pantallas y elegir la del tablero (2, 5 u otra, nunca inferir por número).
3. Probar Waze y verificar visualmente que aparece en el clúster, no en pantalla principal. La API solo confirma que Android aceptó una solicitud, no que Waze se dibuje.
4. Activar destinos a clúster; probar uno enviado por Xiaomi y una indicación por voz, más rueda y botón de micrófono.
5. Activar inicio automático, apagar/encender T1, comprobar que Waze aparece en clúster sin abrir ventana en pantalla principal.
6. SOLO si todas las pruebas pasan, desactivar reversiblemente el módulo Cluster de T1Launcher; NO desinstalar T1Launcher ni modificar servicios del vehículo.
7. Si falla o se pierde voz/volante, reactivar el Cluster anterior y registrar mensaje de estado en DriveLab.

## Límites
Android restringe abrir actividades de terceros en displays privados o no autorizados. Si el display del cuadro es privado, esta vía normal NO puede reemplazar la integración de MainthorCar sin permisos/contrato OEM adicionales. Una Presentation propia no equivale a incrustar Waze, y una MediaProjection requiere permiso explícito del sistema. No publicar como reemplazo garantizado antes del ensayo físico.
