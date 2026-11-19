# DriveLab Auto — handoff 19/11/2026: Receiver 0.3.94 + Boot 0.1.1

## Estado real verificado

- Receiver **0.3.94 / versionCode 195**, paquete `ar.com.rango5gps.drivelabauto.receiver`.
- Boot **0.1.1 / versionCode 2**, paquete `ar.com.rango5gps.drivelabauto.boot`.
- Release firmado: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.3.94
- Receiver: https://github.com/bruceferra/drivelab-auto-standalone/releases/download/v0.3.94/DriveLab-Auto-Receiver-v0.3.94.apk
- Boot: https://github.com/bruceferra/drivelab-auto-standalone/releases/download/v0.3.94/DriveLab-Boot-v0.1.1.apk
- Workflow publicación **SUCCESS**: https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44611955265
- QA independiente **SUCCESS**: https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44611955299
- Certificados de firma de Boot y Receiver comparados entre sí y con versión antigua en CI. Build del Sender preservado.
- **El usuario confirmó físicamente que Boot v0.1.0 consiguió crear mosaico real de Display 0**, pero hay barra gris de maximizar sobre botones, franja vacía superior y barra inferior. **No se ha probado físicamente la corrección de 0.1.1/0.3.94 ni el control conjunto de Display 1.**

## Cambios funcionales

### Barra gris / espacio superior / barra inferior
- `T1ImmersiveActivity`: cuando su propia ventana está en Freeform, agrega un espacio de seguridad **interior a la app** antes del toolbar fijo. El margen se recibe desde Boot y se guarda en `drivelab_freeform_ui_v1.caption_safe_dp`. Valor inicial 44dp, rango 0–88dp, 0 desactiva. Se recalcula al cambiar el modo de ventana y recuperar foco.
- `T1BootMosaicBridgeService`: persiste ese valor antes del lanzamiento del mosaico, y reporta al diagnóstico.
- El mosaico ya calibraba el inset Jetour de ~101px y ajustaba la altura útil. Las barras arriba, abajo y el inset **pertenecen a Android/firmware Jetour**, no se pueden prometer eliminados por `setSystemUiVisibility`. No se cambian ajustes globales, HOME, SystemUI u overlays OEM.
- En aplicaciones externas como Spotify/YouTube no se puede inyectar el margen interno de DriveLab; las barras OEM pueden permanecer.

### DriveLab Boot como único panel de configuración de ventanas
- `BootActivity`: Display 0 mantiene mosaicos 1–3 apps, 1–99 %. Sección nueva Display 1 para Waze/Google Maps (1 app completa o dos 30–70 %). Sección margen de cabecera Freeform.
- Display 1 **opt-in desactivado por defecto**, test manual y confirmación visual explícita en Boot antes del autoarranque. Cambiar distribución, aplicaciones, porcentaje o apagar/encender la opción invalida confirmación anterior.
- `BootConfig.send(context,true)`: peticiones firmadas con `from_boot`, selección y aprobación de Display 1. `BootAutoService` espera 15s una vez.
- `T1BootMosaicBridgeReceiver`: al activar Boot, desactiva el autoinicio anterior de apertura de interfaz Receiver y, **solo si Display 1 está incluido**, desactiva `T1ClusterWazeRouter.AUTO_BOOT` para evitar doble arranque; mantiene ReceiverService, registros de viaje, carga, AVAS, espejos y clima.
- `T1BootMosaicBridgeService`: crea mosaico Display 0 primero, en segunda etapa independiente solicita Display 1; el bloqueo BUSY se libera al terminar ambas. No exporta shell a Boot.
- `T1BootClusterLayout`: usa motor ya existente `T1ClusterWazeRouter` + `T1ClusterLayoutPolicy`, exige Display ID=1 público y seleccionado, velocidad OEM entre 0 y 0,5 km/h, paquete exclusivamente Waze/Maps, sin cerrar procesos y sin tocar OEM. **No afirma visibilidad real**: pide inspección de capas/foco. Si hay desvanecido propio de DriveLab `T1ClusterEdgeFade` configurado, lo informa sin modificarlo.
- Display 1 con 3 apps arbitrarias **no está habilitado**: se limita a los 1–2 mapas del motor existente hasta corroborar compatibilidad, privacidad y seguridad física.

### Boot actualización sin USB/WebADB
- `T1BootInstaller` ahora ofrece Boot 0.1.1 a quien tiene instalada 0.1.0, vía Receiver 0.3.94 → Inicio → INSTALAR DRIVELAB BOOT · SIN WEBADB.
- Consulta exclusivamente la release oficial exacta `v0.3.94`, valida asset, URL de GitHub, SHA-256 oficial, tamaño, paquete, código 2 y **certificado igual al Receiver** antes de la sesión PackageInstaller (consentimiento Android). Alternativa de lectura exclusiva por FileProvider y reintento.
- En el auto primero actualizar Receiver a 0.3.94 usando ⟳. Después actualizar Boot a 0.1.1 desde Inicio. **No desinstalar ninguno**.

## Prueba física recomendada, únicamente vehículo detenido

1. Actualizar Receiver con ⟳ y luego DriveLab Boot desde Inicio. Confirmar ambas versiones en ajustes/ícono.
2. Abrir Boot y dejar Display 1 desactivado durante la primera prueba. Elegir Cockpit + Spotify en 50/50, aplicar, confirmar geometría. Ajustar «Separación superior» entre 0 y 88dp (44dp predeterminados) hasta que la barra gris deje de tapar **botones propios de DriveLab**.
3. Fotografiar o capturar la franja superior, barra gris y barra inferior. Si son capa Jetour, no afirmar que son ocultables.
4. En Receiver → CLÚSTER, confirmar que el display secundario elegido es **Display 1** y que navegación manual original funciona.
5. En Boot habilitar «Incluir Display 1»; elegir Waze / Google Maps (o una app), aplicar con velocidad OEM 0. Observar si ambas aparecen, sin tapar indicadores ni avisos originales.
6. Solo **después** de comprobar visualmente seleccionar «Confirmar visualmente Display 1» y habilitar el único autoinicio de Boot.
7. Apagar/encender Android y observar si Display 0 reaparece, Display 1 muestra ventanas en orden, el foco/rueda y T1 Launcher funcionan. Guardar el texto del «Último diagnóstico» para fallos de capas o permisos.
8. Si Display 1 no está identificado, si la velocidad OEM no se conoce o Android bloquea una ventana, el módulo debe **omitir esa etapa** y explicar por qué. Nunca intentar `force-stop` de Jetour/SystemUI/Spotify u ocultar overlays OEM.

## Pendientes para resolver el origen de las «capas Jetour»

- El hueco libre superior de ~101px y la barra inferior del sistema aún dependen del WindowManager ROM. Obtener capturas/logs reales para distinguir caption Freeform, barra navegación, overlays propios de DriveLab, T1Launcher ClusterService y overlays OEM.
- Comprobar si `ActivityRecord.visible` representa realmente el foco visible; no significa que una tarea no esté tapada.
- Diagnóstico no invasivo de las ventanas por display y stack del sistema, sólo con consentimiento, sin modificar procesos.
- Test de reinicio en frío y apertura manual de Boot (ID Display 1 puede cambiar según la ROM; **no seleccionar otro display automáticamente**).
- Validar las capas con Spotify, Waze y Google Maps, si la recuperación de foco anterior vuelve a competir, y si un overlay privado del fabricante cubre Freeform.
- Confirmar que la barra de maximizar no tape controles con 44dp de margen en distintos anchos. Si queda insuficiente, ajustar hasta 88dp o revisar insets reportados.

## Alcance y seguridad

Esta publicación no cambia datos históricos de viajes/cargas, controles OEM, botones de volante, Home/Launcher, SystemUI, Android settings ni mapas del sender. La función de quitar la barra Freeform del propio sistema **no está implementada**, sólo el margen interior; la barra inferior y franja superior OEM requieren investigación física. No anunciar «problema de capas resuelto» sin pruebas reales.
