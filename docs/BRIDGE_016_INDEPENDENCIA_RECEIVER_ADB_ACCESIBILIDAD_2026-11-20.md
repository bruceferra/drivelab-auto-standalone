# DriveLab Bridge LAB 0.1.6-lab.3 — independencia de Receiver y autorización accesibilidad propia
Fecha: 20/11/2026. Rama: refactor/boot-receiver-unificado-seguro, PR #93 (draft).

## Hecho y publicado
- OTA firmada en canal aislado LAB, tag **bridge-lab-v3**, versionCode 7, applicationId `ar.com.rango5gps.drivelabauto.boot.lab`.
- Publicación: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/bridge-lab-v3
- Manifest OTA aislado: `bridge-lab-ota/bridge-lab.json`. El último release **estable** no cambia.
- SHA256 APK: `8feea79a13d9ca7b1217aa26a3dd031225b22f67ef896e3339cd2c132905687d`. Verificado por firma Android de release, paquete y hash en GitHub CI.
- `BootConfig`: ahora sólo mantiene preferencias privadas y opt-in de autoinicio; **eliminados** `assertReceiver`, IPC de mosaicos, takeOver, sendCenterOnly, sendClusterOnly, firmas del Receiver y broadcast de resultados.
- `BootActivity`: eliminados paneles y opciones legado con dependencia de Receiver. Módulos presentes: dos ventanas propias **Display 0**, mapa propio **Display 1**, QR cifrado Xiaomi, destino enviado desde Sender a app/Display confirmados, volante, LAB de VirtualDisplay y OTA.
- `BootLinkService`: listener autónomo puertos 48391/48392, secreto propio de Bridge y `BridgeCrypto` embebido en el APK por módulo common; no precisa proceso Receiver.
- `BootAutoService`/`BootReceiver`: sólo encienden receptor de red propio por opt-in; no relanzan ventanas/maps ni toman HOME OEM.
- `BridgeWheelAccessibilityService` y `BridgeWheelOemBinder`: servicio de Bridge, sin dependencia del Receiver; Binder OEM sólo se registra con opt-in y mapa verificado, sin zoom ni teclas globales.
- `BridgeWheelAccessRecovery`/ `BridgeLocalAdb`: botón con confirmación para modificar exclusivamente permisos de accesibilidad de Bridge LAB, si **ADB local** 127.0.0.1:5555/5556 está activo y la clave de la instalación ya fue autorizada. Reutiliza la identidad ADB privada de diagnóstico de **esta LAB**, nunca importa claves del Receiver. Conserva la lista completa de T1Launcher/Receiver/OEM, verifica escritura/lectura y usa registro de restauración ante rebind interrumpido. No necesita Termux si ADB local ya está disponible, pero no puede activar adbd ni omitir la autorización Android cuando faltan.
- El código de mapas prohíbe explícitamente que apps DriveLab sean tareas de panel.
- Ningún componente exige `android.permission.WRITE_SECURE_SETTINGS`, root, `pm clear`, force-stop, teclas OEM sintéticas, navegación global BACK o cambio de HOME.

## Validación ejecutada
- Signed LAB OTA v3: https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44730533586 — success.
- QA Android Sender/Receiver/Bridge: https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44730540450 — success.
- Bridge seguridad y volante: https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44730540549 — success.
- Voz y mapas offline: CI success.
- **Compilar no prueba el comportamiento de Android Jetour instalado ni la visibilidad del mapa en Display 1**.

## Comprobación física pendiente, con vehículo estacionado
1. Desde LAB v0.1.5 tocar Buscar actualización firmada LAB OTA → v0.1.6; confirmación Android. Mantiene paquete/datos, no requiere cable.
2. Confirmar Xiaomi QR y envío de destino desde Sender **sin abrir Receiver**. No volver a emparejar si la clave persiste.
3. Central: probar las dos aplicaciones mediante Bridge; tablero: una aplicación de mapa mediante Bridge, y verificar contenido, tamaño y alertas OEM. Estas ventanas aún son *solicitudes* Freeform; **no** hay aún paridad completa con superficies virtuales T1N145 ni diseño persistente de dos mapas.
4. Con T1Launcher Mapa volante desactivado, usar manualmente **RECONECTAR ACCESIBILIDAD · ADB LOCAL · SIN TERMUX**; verificar servicio conectado. Si adbd local no escucha o Android no confía en su clave, informará error, no promete conexión.
5. Activar volante sólo después de identificar mapa real visible y no competir con Binder OEM T1Launcher. Sin zoom.
6. **No desinstalar Receiver todavía en el vehículo habitual** hasta completar pruebas estacionadas de estas funciones. La arquitectura de Bridge ya no lo requiere en código, pero la independencia total en esa ROM concreta sigue pendiente de verificación.

## Límite de soporte actual
Bridge gestiona dos ventanas Freeform en Display 0 y una solicitud de mapa en Display 1; no reemplaza aún las capas virtuales persistentes de T1Launcher ni puede afirmar que Waze/Maps esté correctamente escalado o visible por encima del firmware. El modo LAB es experimental, PR #93 sigue draft y no se mezcla con la release estable.
