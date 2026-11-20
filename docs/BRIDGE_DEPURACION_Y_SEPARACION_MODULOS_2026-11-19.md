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

## No resuelto — bloquea release

- **Ventanas realmente independientes**: Bridge aún NO implementa la superficie de mapa persistente y la presentación visible con geometría/foco a lo T1Launcher. `BootLinkService.openMap` hoy sólo lanza intent a Maps/Waze; **no equivale a dos mapas Freeform**. El autoplay de ventanas se deshabilitó deliberadamente hasta paridad física.
- **Display 1 por encima de la capa Jetour**: prioridad analizar secuencia que en DriveLab Auto ocasionalmente ponía el mapa por encima (no MODE). No ocultar alertas, no modificar ventanas del fabricante.
- **Rueda**: todavía depende del servicio OEM + accesibilidad de Receiver, no migrado a Bridge; evitar listeners duplicados.
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
