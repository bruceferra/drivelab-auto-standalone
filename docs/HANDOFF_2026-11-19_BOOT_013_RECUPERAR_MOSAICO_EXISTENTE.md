# DriveLab T1 — incidente de mosaico oculto y recuperación sin destruir datos (19/11/2026)

## Problema observado físicamente
- En Jetour, Receiver y Boot se instalaron, y Cockpit/Spotify llegaron a abrir Freeform, pero las tareas quedaron ocultas tras otras capas.
- El botón de Boot «MOSTRAR MOSAICO» de 0.1.2 solo ejecutaba `moveTaskToBack(true)` en la Activity de Boot: **no traía ninguna tarea de usuario al primer plano**.
- La preparación Freeform es asíncrona. Su cuadro anterior decía «se configuró solamente la pantalla central» ANTES de recibir los resultados de Receiver; era engañoso.
- El usuario comprobó que solo veía las ventanas tras Forzar detención: **no repetir** ese mecanismo. La recuperación debe trabajar con los IDs de tareas guardadas sin matar procesos.
- La sincronización Xiaomi Sender/Receiver es independiente del mosaico. Boot no puede asegurar que esté conectada.

## Corrección publicada
- Receiver **0.3.97, código 198**, Boot **0.1.3, código 4**.
- Release oficial firmada: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.3.97
- Build: https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44634479459 (**SUCCESS**).
- Boot UI → CENTRAL · 0:
  - **▶ MOSTRAR MOSAICO · RECUPERAR VENTANAS**: pide al Receiver que recupere tareas existentes, sin abrir instancias nuevas.
  - **PREPARAR / CAMBIAR DISTRIBUCIÓN**: para cambios de apps/posiciones; envía operación asíncrona sin afirmar éxito antes del diagnóstico.
  - El área de estado distingue «preparando», «recuperación solicitada» y «no recuperado».
  - Sólo si Receiver reporta `MOSAICO_RECUPERADO_SOLICITADO:` y el usuario inició Mostrar, Boot envía SU PROPIA tarea al fondo. NO se cierra Waze/Maps/Spotify/Cockpit.
  - Mensaje explícito de que la conexión Sender/Receiver no se diagnostica en Boot.
- `T1CenterBootReveal`: recupera los ID de `drivelab_center_managed_freeform_v1` para los paquetes solicitados; valida ADB por cada ID, paquete autorizado, Display 0, modo Freeform y bounds disjuntos; llama a `ActivityManager.moveTaskToFront` para los IDs EXACTOS. No ejecuta `pm clear`, `am force-stop`, `startActivity` ni `T1CenterSavedFreeformMosaic.apply`. No cambia HOME, OEM, Displays 1 ni configuración original.
- El nuevo IPC `REVEAL_BOOT_CENTER` está limitado al package firmando idéntico certificado Boot+Receiver.
- `receiver/tests/T1BootMosaicRevealContractTest.java` protege el flujo; se actualizaron `T1BootStandaloneSourceContractTest`, `T1BootStandaloneUiIsolationSourceTest` y controles de release/installer.
- Compilación y prueba de publicación pasan. La prueba general de Fade requería la antigua reparación destructiva (Android 11 pm clear --cache-only) que hoy está desactivada intencionalmente; se corrigió su expectativa sin reactivar ese código. Verificar estado de QA independiente en el último workflow posterior a esa modificación.

## Seguridad y límites
- Android puede aceptar la solicitud de ordenar tareas pero **el firmware Jetour puede mantener alguna capa por encima**: no se afirma visibilidad visual sin comprobarla en el coche.
- Si un ID gestionado ya no existe o perdió modo Freeform, informa causa sin modificar ninguna app. Antes de preparar un layout nuevo, confirmar qué pasó con el anterior.
- El botón MAPAS sigue **MAPAS · OFF** desde Receiver 0.3.96 y ya NO ejecuta `pm clear --cache-only` (en Android 11 ese comando borra los datos, no solo caché). NO reactivar el REBOOT técnico destructivo. Los datos de usuario de Waze/Google Maps perdidos no se restauran con esta APK.
- **No actualizar ni configurar mientras el vehículo está en movimiento**; usar navegación del teléfono Xiaomi hasta terminar el viaje.
- Sender v0.2.22 permanece sin cambio en esta publicación. La falta de sincronización del Xiaomi **sigue pendiente de diagnóstico independiente**; no mezclarse con el éxito del mosaico.

## Prueba física estacionada, sin forzar detenciones
1. Actualizar Receiver a 0.3.97 usando OTA ⟳, después Boot a 0.1.3 desde el instalador nativo de Receiver (sin WebADB). NO desinstalar aplicaciones, datos persistidos ni backups.
2. Boot → CENTRAL · 0 → MOSTRAR MOSAICO. Si los IDs todavía son válidos, aparecen delante por `ActivityManager.moveTaskToFront`. En el diagnóstico figura solicitud de recuperación. Confirmar visualmente que Cockpit y demás apps están al frente, sin capas Jetour ocultándolas.
3. Si falla, fotografiar/capturar el mensaje **MOSAICO NO RECUPERADO**; no usar Forzar detención. No intentar reparaciones peligrosas.
4. Sólo si realmente cambiaron las apps o el reparto, pulsar PREPARAR / CAMBIAR DISTRIBUCIÓN y esperar diagnóstico. Después pulsar MOSTRAR.
5. Display 1 se prueba aparte desde CLÚSTER · 1. El mapa principal en teléfono y la conexión Sender/Receiver siguen independientes; no afirmar sincronización basándose en el mosaico.
