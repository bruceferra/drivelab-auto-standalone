# DriveLab Bridge — contrato visual y funcional basado en T1Launcher, 19/11/2026

## Fuentes verificadas
- Seis fotos reales del Jetour T1 aportadas por el conductor en esta conversación (central, tablero, ajustes, ajuste caja).
- Release oficial: https://github.com/MainthorCar/t1launcher/releases/tag/T1N145
- APK publicada: `T1Launcher-T1N145.apk`, 21.773.219 bytes, SHA-256 `f43c46b6e5a044482559373b6f705f6fca5dbcb465c848dc06f0cc18452bb11b`.
- Análisis estático anterior del APK entregado por el usuario `T1Launcher-T1N134.apk`: `cluster.ClusterLink`, `cluster.ClusterPantalla` hereda `android.app.Presentation`, `cluster.T1MapaServicio` usa Accessibility, `ventanas.Ventanas` y `adb.AdbPuente`. El código binario exacto de T1N145 **no se pudo descargar en este entorno**; no inventar conclusiones DEX de esa versión.
- Release T1N145: Google Maps abierto desde cero arranca en modo seguimiento; con segunda app activa, botones anterior/siguiente del volante permiten cambiar canción en apps compatibles; gestos tienen activaciones NADA/ATRÁS/T1LAUNCHER/CLUSTER/ELEGIR APP.

## Qué demuestran las fotos, sin confundir dos superficies

**Pantalla central / Display 0**
- Foto música sola: Spotify ocupa vista horizontal amplia, tipo tablet, NO corresponde al aspecto compacto solicitado.
- Foto mosaic: Maps izquierda amplio (~2/3) y Spotify derecha angosto, panel alto, interfaz visual tipo celular y bordes redondeados. Es una configuración **distinta** a la presentación sobre el tablero.
- La interfaz angosta probablemente fuerza un breakpoint responsive de Spotify; sólo es una hipótesis basada en tamaños, no evidencia de que T1Launcher altere APK, DPI o user agent.
- T1Launcher muestra barra lateral con Home, menú y acceso al módulo.
- La versión nueva de Bridge debe tener modo simple con Panel 1 Maps y Panel 2 Spotify, distribución predeterminada 2/3 + 1/3, sin imponer 50/50; comprobar tareas reales, no abrir duplicados.

**Pantalla de instrumentos / Display 1**
- Foto del tablero con mapa: mapa azul ocupa la zona extendida.
- Foto del tablero OEM: muestra instrumentos de seguridad (incluyendo aviso de combustible), una pantalla distinta de la vista de mapas. Nunca ocultar permanentemente avisos de seguridad ni mandar controles OEM sin autorización.
- La interfaz T1Launcher ofrece **PANTALLA 1, PANTALLA 2 (de fábrica), CLUSTER 1, CLUSTER 2**: son opciones de destino visual del módulo; no asumir que equivalen a IDs físicos Android 1/2 sin enumeración.
- La ventana `AJUSTAR` deja: `RECTÁNGULO` o `RELOJES`, posición con flechas y paso 1/10, ancho, alto, `ORIGINAL`, `DESVANECIDO`, `LISTO`. Ejemplo físico en foto: `x 0 y 0 ancho 1443 alto 541`, desvanecido 0, sin suponer que son unidades DP. Esto NO es el mismo repartidor de ventanas de Display 0.
- En el ajuste principal: Maps seleccionado, Spotify segunda app, `AL ENCENDER`, `VOLANTE → MAPA`, `MODO MANEJO`, `AJUSTAR CAJA`, `CERRAR EL MAPA`. El volante funciona según reporte del conductor: zoom, OK, aceptar, cancelar y retroceso, con prioridad Google Maps.
- El firmware Jetour oculta las ventanas del mapa en ocasiones. MODE **no** hace aparecer Maps. Lo que en ocasiones consigue levantar mapas y rueda es solicitar la navegación/apertura desde DriveLab T1 Auto. Registrar esa secuencia, NO simular MODE.

## Hallazgo de arquitectura: por qué DriveLab actual no se parece
- Receiver `T1ClusterLayoutPolicy.start`: `setLaunchDisplayId`, reflexión `setLaunchWindowingMode(5)`, `setLaunchBounds`, flags `NEW_TASK|SINGLE_TOP|CLEAR_TOP`. Los destinos de Sender se envían a la tarea con límites Freeform de la app guardada (si configurado).
- Bridge WIP `BootLinkService.openMap` actualmente sólo usa `setLaunchDisplayId` sin Bounds/Freeform. **No puede reproducir fielmente las posiciones/tamaños del launcher** y no gestiona rueda todavía. No publicar.
- El análisis T1N134 detectó un módulo de `Presentation` propio en tablero, NO debe tratarse la pantalla del tablero como una segunda copia de la disposición central.
- El layout de Bridge debe tener **dos configuraciones y un único motor**:
  1. Centro: Panel Maps + Panel Spotify (2/3 + 1/3), geometría de ventanas con apariencia compacta, redondeado en la UI contenedora donde Android lo permita, recuperar tareas existentes.
  2. Tablero: mapa, modo RECTÁNGULO/RELOJES, posición, tamaño, alpha; on/off independiente y autoboot opt-in. La capa del firmware no se modifica por fuerza y avisos OEM permanecen.
  3. Xiaomi: QR par único y envío cifrado de destino directo al motor Bridge (sin abrir la app grande).
  4. Volante: UN receptor OEM autorizado y accesibilidad habilitada por el usuario; no inyectar MODE ni duplicar callbacks.

## Aceptación física obligatoria antes de publicar
- Centro: Maps a la izquierda, Spotify a la derecha **en aspecto móvil** y sin recreaciones/flicker.
- Tablero: mapa con modo rectangular y modo relojes; transparencia y límites físicos se aplican sin cubrir avisos críticos.
- Estado: 1 app / 2 apps verificado por actividad, display, bounds y comprobación visual del conductor. No declarar éxito simplemente porque `startActivity` aceptó la petición.
- Rueda: zoom/aceptar/cancelar y música en el perfil correcto, sin interferir la funcionalidad OEM.
- Destinos Xiaomi: compartir desde Uber/Maps, ACK sólo si se solicitaron todos los destinos configurados; sin abrir la aplicación DriveLab Auto.
- Arranque: Bridge único como interfaz; app grande no abre; no forzar detener ni borrar datos ni reiniciar sistema/firmware.
- Para redistribución y código: implementación **original** de Bridge, sin copiar bytecode/licencia protegida del autor.

## Estado
La rama es un borrador sin APK publicada. No se ha validado físicamente el comportamiento anterior con la T1N145; la APK original **está identificada** pero no disponible para decompilación en la sesión actual.
