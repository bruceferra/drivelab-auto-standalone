# Receiver v0.2.11 QA — prueba manual de retrovisor, sin automatización

**Estado:** Receiver 0.2.11 (versionCode 12) firmado y publicado en [release latest OTA](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11); [workflow de publicación exitoso](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42819862706) comparó el certificado con 0.2.10. **NO VALIDADA FÍSICAMENTE EN VEHÍCULO**. Sender permanece 0.2.10. **No usar controles experimentales en circulación**.

## Objetivo del ensayo

Validar el ID OEM `2069` extraído estáticamente del `EspejoRService` del APK T1Launcher T1N134 (SHA-256 `6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480`):
- `2069=1` y `2069=2` representan **dos posibles sentidos**, no se conocen aún lado ni orientación física exacta.
- `2069=0` aparece en el código original como **liberación**, pero no hay prueba física de su función de parada.
- Candidato `1033=1` indica P según T1N134 (2 se compara con R). `2148` (float) / `1089` (int) se consultan para comprobar velocidad de 0 km/h. Si esas señales están inaccesibles o fuera de rango, la prueba **no mueve** el espejo.

## Implementación limitada

- En `ReceiverVehicleActivity`, pestaña VEHÍCULO, sección **ESPEJO EN REVERSA · PRUEBA MANUAL EXPERIMENTAL**: `PULSO 1`, `PULSO 2` y **LIBERAR MANDO**. Cada pulso requiere **confirmación explícita**.
- Cada toque, si se permite, emite **una única orden** durante **120 ms**, luego intenta `2069=0` en bloque `finally`. No se implementa `HOLD`, repetición automática, retroceso automático, memoria de posiciones ni ajustes de ambos espejos; primero verificar el comportamiento del motor.
- Permisos mínimos y escritura allowlist SOLO `2069` con valores `0,1,2`; no habilitar UI de setter arbitrario. El módulo hace consultas de marcha/velocidad antes de cada movimiento. Intervalo mínimo 1.800 ms entre pulsos y sin solapamiento de pruebas.
- `onPause` y `onDestroy` solicitan cancelar la operación pendiente; la liberación se envía mediante el mismo `finally` cuando está disponible el Binder. **Una app Android no garantiza parada al ser finalizada abruptamente**: no es un dispositivo de seguridad. Si el comando falla o continúa moviendo, utilizar el mando OEM del espejo y detener el ensayo; no circular hasta comprobarlo.
- **Ninguna modificación** de `ReceiverService`, `BootReceiver`, `ShareToClusterActivity`, `BridgeCrypto`, `Sender`, controles de ventanas, AVAS, viajes, Waze ni actualización estable. No hay comandos al iniciar/reiniciar la T1.

## Instrucciones de prueba física

1. Con la T1 estacionada, abrir **Receiver 0.2.10 → ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA**. La app detecta 0.2.11 mediante `releases/latest/download/receiver.json`, descarga y comprueba hash/firma antes de pedir la instalación Android. **No descargar APK a mano, no desinstalar y no actualizar Sender 0.2.10.**
2. Estacionar en superficie plana, marcha **P**, velocidad 0, entorno despejado. Dejar el espejo en una posición intermedia alejada de los topes mecánicos; no usar el extremo para calibrar.
3. Abrir **Receiver → VEHÍCULO → ESPEJO**. Dejar el mando físico accesible y observar el espejo desde un punto seguro.
4. Elegir **PULSO 1**. Anotar si Receiver rechaza por marcha/velocidad/permiso, si se mueve algún espejo, cuál, en qué dirección, cuánto y si se detiene al concluir. **Si sigue moviéndose, no mandar PULSO 2; intentar liberar y usar el control OEM.**
5. Solo si la primera prueba se detiene correctamente, probar **PULSO 2** y anotar mismo resultado. No exceder un ensayo por vez. No probar mientras hay ocupantes delante del espejo ni elementos que puedan interferir.
6. Exportar/compartir únicamente estado de ensayo y la respuesta de la app **sin VIN, ubicación ni datos de la vinculación**. No publicar datos privados en un issue público.

### Criterios de aprobación

- **PASÓ (control básico):** Receiver lee P y 0 km/h, puede enviar un pulso, se observa movimiento acotado y el espejo **se detiene inmediatamente**; el valor `0` se acepta. Se conoce el sentido y lado para cada valor.
- **FALLÓ:** cualquier condición no satisfecha o movimiento inesperado; volver a usar controles de fábrica y no seguir.
- **NO PROBADO:** la app compiló pero no se hizo la prueba real. **Una respuesta Binder exitosa no equivale a movimiento ni a parada**.

## Próximos pasos luego de prueba

Si los pulsos controlan físicamente el motor y la detención es confiable, diseñar pulsación sostenida con límite temporal, parada en `ACTION_UP/CANCEL` y guardia de seguridad, siempre sin basarse en topes físicos. No anunciar como funcional antes del registro de aceptación del usuario.

**Compilación/publicación actual:** `.github/workflows/publish-receiver-ota.yml` publica exclusivamente **Receiver firmado 0.2.11**, `receiver.json` y copia **sender.json de v0.2.10** sin cambios en el canal latest. La publicación es oficial para el mecanismo OTA; la **función de espejos sigue experimental** hasta prueba física. La pre-release separada anterior es histórica y no debe utilizarse para actualizar.
