# DriveLab Bridge — separación efectiva y limpieza segura (19/11/2026)

## Instrucción del propietario

El vehículo debe encender SIN abrir la aplicación grande DriveLab Auto, sin duplicar mosaicos, sin detener Waze/Maps, sin robar el foco del tablero OEM. Quiere Bridge independiente para (1) QR cifrado con Xiaomi Sender, (2) composición Display 0 Maps+Spotify en panel vertical, (3) presentación Display 1 rectangular/relojes/transparencia y (4) rueda, con autoinicio opt-in y actualización propia. DriveLab Auto retiene, *sin arrancar pantallas*, telemetría/jornadas/cargas. No tocar ni borrar bases, cuentas o datos de terceros.

## Errores hallados y corregidos en ESTA rama

| Área | Antes | Estado rama |
| --- | --- | --- |
| Arranque Bridge | `BootAutoService` llamaba `BootConfig.send(this,true)`, que pedía al **Receiver DriveLab Auto** lanzar Display 0 y Display 1 | Ahora arranca exclusivamente `BootLinkService.START` de la APK Bridge, sin órdenes de mosaico a la app grande |
| Configuración principal | Pantalla "DriveLab Boot", dos pestañas 50/50 con el cockpit de DriveLab por defecto | Cabecera "**DriveLab Bridge**", Maps+Spotify como defaults para pruebas, layout inicial 65/35. La configuración de mosaicos legacy queda **oculta** detrás de advertencia y no interviene en el autoinicio |
| Receptor QR | `BootLinkService` independiente existía, pero no estaba accesible desde la home | Botones independientes INICIAR, QR (imagen local), DETENER, estado y número de mensajes ACK; puertos 48391/48392; el usuario debe confirmar enlace y prueba física |
| Actualizador | `BootUpdater` verificaba SHA-256/firma pero no se abría desde home | Botón propio en la app Bridge; sólo releases estables verificadas y aprobación manual de Android |
| Diseño | Tema base blanco Material Light | Material oscuro, botones turquesa/grafito tipo Jetour |
| Persistencia de enlace | `BootLinkService` devolvía `START_STICKY` incluso tras arranque manual con auto OFF | `START_NOT_STICKY` si no hubo opt-in; un intento del sistema con Intent null se descarta si auto OFF |
| Permiso de intent desde fondo | Bridge comprobaba `Settings.canDrawOverlays` pero no declaraba permiso ni ofrecía abrir configuración | Permiso opcional declarado + botón que abre configuración de Android; NO concede permisos silenciosamente ni garantiza visibilidad en el firmware |

## Volante (corrección explícita del propietario)

**NO HAY ZOOM.** La rueda no altera escala ni gestiona mapa pinch.
Se ha establecido por pedido expreso el perfil de DriveLab Bridge:

- Jetour OEM HardKey Binder `ALHardKeyService`, interfaz `IHardKeyService` con registro/unregistro 1/2 y callback 3, tomado del análisis DEX ya realizado sobre el APK T1Launcher **T1N134** (no se han inspeccionado los DEX del APK más nuevo T1N145).
- Códigos físicos 1000/1001 (pulsación action 100) = **siguiente/anterior opción seleccionable**; 1002 = **cancelar/volver dentro del mapa**, 1003 = **OK/confirmar opción**; liberación action 101 ignorada.
- Solo se enumeran controles de accesibilidad **visibles y clicables del paquete Waze/Maps en Display 1**; las acciones `zoom/acercar/alejar` se excluyen; no hay `dispatchGesture`, pinzas ni **BACK global**.
- Movimiento usa `ACTION_ACCESSIBILITY_FOCUS` sobre el botón del mapa, si Android permite resaltarlo; no hace clic hasta OK.
- Cancelar opera un botón accesible inequívoco de Waze/Maps o cancela la selección, sin afectar la interfaz OEM.
- Servicio y configuración pertenecen a la **APK Bridge**, sin dependencia de Receiver; opt-in DESACTIVADO por defecto y autorización Android manual. Si T1Launcher usa el mismo Binder debe desactivarse antes de habilitar Bridge (suscriptores pueden ser exclusivos).
- Los tests cubren mapeo y filtrado de acciones/teclas prohibidas, sin garantizar el foco/resaltado físico en la T1 hasta prueba estacionado.

## No resuelto — bloquea release

- **Ventanas realmente independientes**: Bridge aún NO implementa la superficie de mapa persistente y la presentación visible con geometría/foco a lo T1Launcher. `BootLinkService.openMap` hoy sólo lanza intent a Maps/Waze; **no equivale a dos mapas Freeform**. El autoplay de ventanas se deshabilitó deliberadamente hasta paridad física.
- **Display 1 por encima de la capa Jetour**: prioridad analizar secuencia que en DriveLab Auto ocasionalmente ponía el mapa por encima (no MODE). No ocultar alertas, no modificar ventanas del fabricante.
- **Rueda**: módulo OEM + accesibilidad migrado a Bridge para **navegar opciones/OK/cancelar sin zoom**. No hay prueba física del foco ni del Binder con T1Launcher simultáneo; validación pendiente antes de release.
- **QR en T1 real**: listener está implementado, no comprobado con el Sender instalado. ACK de protocolo significa intención aceptada, NO que se ve mapa. Si la app grande sigue ocupando 48391/48392, Bridge debe reportar puerto ocupado y no forzar detener.
- **DriveLab Auto legado**: sus pestañas y servicios no se borran hasta migrar y validar; pueden contener SQLite de cargas y viajes críticos. Su pantalla de inicio automático sigue desactivada por el propietario; NO modificar flags ni borrar. Revisión aparte #95 para mejorar diario/semana y datos HV/combustible.
- **Mosaicos viejos**: para conservar diagnóstico pueden abrirse sólo con aceptación explícita en sección avanzada, usando aún el Receiver; no están en el flujo Bridge independiente.

## Contrato de autoinicio

- BootReceiver Bridge sólo responde a BOOT_COMPLETED si `BootConfig.enabled(context)` es true; false por defecto.
- BootAutoService espera una vez 15s y abre **sólo** BootLinkService, no activity; no `BootConfig.send`, no `startActivity`, sin toques OEM.
- BootLinkService manual sin opt-in no es sticky; botón DETENER baja el servicio y apaga autoinicio futuro.
- El nombre de botón `AUTOINICIO` se refiere **sólo al receptor Xiaomi** hasta disponer de geometría de ventanas independiente probada.

## QA / publicación

Rama experimental: `refactor/boot-receiver-unificado-seguro`, PR #93, NO merge, NO release, NO OTA. Pruebas estáticas `BridgeIndependentBootContractTest`, `BridgeModeLayerDiffTest` y Android Gradle CI. La compilación del código no certifica visibilidad en Display 1 ni comportamiento de la rueda. No efectuar actualizaciones o pruebas en marcha.
