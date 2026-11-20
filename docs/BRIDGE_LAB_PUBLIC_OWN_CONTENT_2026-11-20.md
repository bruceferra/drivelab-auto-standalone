# Bridge LAB — comprobación física de actividad en display público (20/11/2026)

## Evidencia de usuario
- LAB 0.1.18: la banda inferior de `Presentation` en el Display 1 físico ocultó la capa Jetour Radio/Bluetooth.
- LAB 0.1.19: `SecurityException` al intentar abrir una actividad en el VirtualDisplay privado.
- LAB 0.1.20: el propietario vio **contador de colores + «actividad denegada por Android»**, confirmando que **sólo corrió el respaldo animado en Presentation**, NO una Activity Android real.

## Siguiente diagnóstico original, totalmente opt-in
Un botón independiente crea durante 20 segundos **VirtualDisplay PUBLIC + PRESENTATION + OWN_CONTENT_ONLY**, con tamaño de la banda inferior (65 % de la altura, ancho completo), y solicita exclusivamente la actividad **propia** `BridgeVirtualSceneActivity`. El flag `OWN_CONTENT_ONLY` evita el espejo automático del display central, pero mientras el display es público Android puede permitir que otras apps accedan. Se informa explícitamente al propietario antes de la prueba.

Preflight de `ActivityManager.isActivityStartAllowedOnDisplay` y llamada a `ActivityOptions.setLaunchDisplayId`. La Activity verifica ID esperado, flag privado/público esperado y token; contador animado solo si coincide. En la denegación, nunca se reintenta con ADB, permisos privilegiados ni Waze: `Presentation` muestra «ACTIVITY DENEGADA POR ANDROID». Registrar el modo y la categoría del rechazo en preferencias como `last_diagnostic`, evitando que el cierre automático borre el resultado.

La prueba existente PRIVADA y el patrón de Display 1 de 12 segundos permanecen disponibles sin cambios; Waze, Google Maps, rutas, QR, Receiver, el firmware OEM y el volante no se modifican.

## Próxima validación con la T1 estacionada
1. Actualizar canal LAB. Abrir Radio/Bluetooth y pulsar «LAB · DISPLAY VIRTUAL PÚBLICO · APP PROPIA · 20 S».
2. Observar **ACTIVITY REAL / CONTADOR EN VIVO** o **PRESENTATION ANIMADA / ACTIVITY DENEGADA**.
3. Leer **DIAGNÓSTICO PERSISTENTE** luego del cierre. Confirmar que todo desaparezca a los 20 s.
4. No habilitar tercera aplicación real, Waze, servicios permanentes ni volante hasta una validación real, especialmente de la zona de alertas OEM.

Referencia Android:
- https://developer.android.com/reference/android/hardware/display/DisplayManager
- https://source.android.com/docs/core/display/multi_display/activity-launch

Sin pruebas físicas no se afirma que Android habilite apps sobre un display público, ni que el panel de instrumentos sea seguro para una capa persistente.
