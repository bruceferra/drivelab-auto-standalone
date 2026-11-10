# DriveLab Auto 0.2.13 — actualización automática al abrir y scroll estable

**2026-11-10.** Cambio solicitado en campo por el usuario para Sender y Receiver. Versiones nuevas: **Sender v0.2.13 code14** (desde v0.2.10 code11) y **Receiver v0.2.13 code14** (desde v0.2.12 code13). Los saltos de número son intencionales para publicar las dos aplicaciones en una sola release con manifiestos por app independientes. Las firmas anteriores siguen siendo obligatorias.

## Pedido funcional

1. **Al abrir Sender/Receiver**: comprobar automáticamente el manifiesto de su canal `releases/latest/download/sender.json` o `receiver.json`. Si hay actualización, **ofrecer** descargar e instalar. Nunca descargar/instalar automáticamente sin consentimiento. Requiere confirmación Android normal.
2. **Botón manual ACTUALIZAR**: mostrar `Buscando actualización...` inmediatamente, y ante ausencia de nueva versión el texto visible exacto `Esta es la última versión de DriveLab Auto Sender/Receiver.` más Toast. Un fallo de Internet/permiso debe decir `No se pudo comprobar...`, no fingir que está al día.
3. **Scroll estable**: en las pantallas principales de Sender y Receiver y el panel Vehículo/CODE/Energía, cualquier cambio de estado o botón que reconstruya la vista restaura la posición vertical anterior. No volver arriba tras pulsar botones ni al actualizar el estado del instalador.
4. **Sin regresiones**: ninguna modificación del protocolo LAN, SenderClient, ReceiverService, mapas, Waze, viajes, guardado de combustible, comandos espejo/ventana/AVAS ni cifras OEM.

## Implementación

- `Updater.checkOnOpen()`: comprueba **solo el JSON** en un hilo, y si hay nueva versión enseña `Actualización disponible · Sender/Receiver` con `DESCARGAR E INSTALAR` y `AHORA NO`. La acción positiva utiliza el flujo existente `Updater.check` de descarga, hash, paquete, certificado y solicitud de instalador nativo; no hay instalación silenciosa.
- Se invoca desde `onResume()` de `SenderActivity` y `ReceiverActivity`. Reentrada a la **misma actividad** con menos de 120 segundos no repite la comprobación, evitando bucle al volver del instalador; una apertura nueva sí la realiza. La comprobación que no encuentra versión informa en línea; la manual además emite Toast.
- Las tres actividades desplazables conservan `ScrollView.getScrollY()` antes de recrear contenido y lo restauran tras `setContentView()`; mensajes del actualizador aparecen junto al botón para verlos sin volver arriba.
- `MirrorPositionProbe`: si un ID no figura en `CarPropertyManager.getPropertyList()`, informa **`NO EN LISTA ACCESIBLE (soporte o permiso indeterminado)`**, en lugar de confundir falta de soporte con denegación. El usuario observó en v0.2.12 marcha **P**, velocidad **0** y todas las propiedades consultadas ausentes de la lista accesible. No se ha obtenido posición absoluta ni límites físicos de los espejos.

## Publicación OTA

- El workflow `.github/workflows/publish-release.yml` se activa en el merge que modifica el workflow en `main`; publica **v0.2.13** con ambos APK y `sender.json` y `receiver.json`, manteniendo URLs exactas que exige el `Updater.latest`.
- Antes de publicar, compara el **certificado SHA-256** de Sender nuevo contra Sender v0.2.10 y el de Receiver nuevo contra Receiver v0.2.12; también comprueba versión/paquete Android y ejecuta pruebas de seguridad del puente.
- El usuario instala exclusivamente desde el **actualizador incorporado** de cada app. No enviar APK manual. No afirmar que la app instalada se actualizó sin confirmación del dispositivo.

## Validación real pendiente

1. En la T1, abrir Receiver 0.2.12; pulsar botón interno **ACTUALIZAR RECEIVER** para pasar a 0.2.13.
2. Abrir la versión nueva, confirmar que muestra el mensaje de búsqueda y luego **Esta es la última versión** si está al día, sin saltar de scroll.
3. Desde Xiaomi, abrir Sender 0.2.10; botón interno para pasar a 0.2.13. En las aperturas posteriores debe verificar y no mostrar ofertas inexistentes.
4. Verificar que al poner la app en segundo plano y volver durante un instante no se abran diálogos repetidos, que el historial y preferencias sobrevivan y que mapa/vinculación sigan correctos.
5. Si se publica v0.2.14 posteriormente, abrir ambas apps y comprobar que **ofrecen** la actualización sin descargar ni instalar hasta aprobación.

**Estado:** implementación de código y pruebas automáticas deben verificarse mediante GitHub Actions; uso real en vehículos y teléfono se registra aparte.
