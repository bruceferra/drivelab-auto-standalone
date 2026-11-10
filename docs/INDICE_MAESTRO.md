# DriveLab Auto + Jetour T1 — índice maestro de continuidad

**Actualizado:** 2026-11-10 (Argentina). **Propósito:** referencia común para todas las conversaciones/solapas; no reemplaza las pruebas físicas ni los registros originales.

## Decisión de arquitectura vigente

**Unificar gradualmente en DriveLab Auto Receiver (Jetour T1) la navegación, el control manual del vehículo, el diagnóstico OEM y un futuro tablero de combustible/energía.** Mantener DriveLab Auto Sender en Xiaomi solo para compartir destinos y administrar la vinculación. Conservar DriveLab estable para registrar viajes, GPS e historial.

La primera versión integrada conjunta fue **Sender + Receiver v0.2.10**, publicada y firmada el 2026-11-09:
https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.10

**Estado correcto:** compilación y pruebas automáticas del código v0.2.10 aprobadas; **no** confundir con validación física en la Jetour. El uso real de los nuevos controles OEM desde el paquete Receiver todavía debe probarse. No se han fusionado las apps antiguas ni eliminado datos.

## Documentos: orden de lectura

1. [Auditoría de apps, versiones y funciones](AUDITORIA_APPS_Y_CONSOLIDACION.md): qué aplicaciones existen, diferencias y qué se migra.
2. [Arquitectura Sender / Receiver](ARQUITECTURA_SENDER_RECEIVER.md): flujo de mapas, puertos, seguridad, controles, arranque, actualizaciones y evolución prevista.
3. [Tablero de combustible, consumo y telemetría](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md): reglas de cálculo, señales y diseño del tablero.
   - [Auditoría y calibración de CSV 03–10/11](CALIBRACION_CSV_T1_2026-11-10.md): 52.880 muestras únicas de CSV (03–09/11), corrección de saturación, distancias comparadas y nueva observación del 10/11, con hashes de originales privados.
   - [Extracción APK T1N134 — espejos, reversa y contadores PHEV](EXTRACCION_T1LAUNCHER_T1N134_2026-11-10.md): inspección estática del archivo original recibido, IDs 1033/2069 y posibles acumuladores 559964335/336; **no habilitados ni validados físicamente**.
4. [Matriz de pruebas, problemas y limpieza](PRUEBAS_PENDIENTES_Y_DEPURACION.md): pruebas en vehículo, Spotify, control de cambios, criterios de retiro.
5. [Handoff para otra conversación](HANDOFF_ENTRE_SOLAPAS.md): contexto breve y protocolo para incorporar nuevos descubrimientos del análisis de consumo.

## Repositorios y dueño de cada pieza

- **bruceferra/drivelab-auto-standalone (este repositorio, público):** fuente de verdad para Sender/Receiver y diseño de la unificación. Paquetes Android independientes, releases firmadas.
- **bruceferra/drivelab (privado):** código y datos históricos de DriveLab GPS, DriveLab Uber Beta, DriveLab T1 de laboratorio y primer DriveLab Auto 0.1.x. Preservar su historial y los viajes.
- **T1Launcher (tercero, MainthorCar) y T1 Testigo:** investigación de interoperabilidad; no son componentes nuestros ni deben copiarse como código sin revisar licencia y derechos.

Las notas antiguas en README y en las carpetas de continuidad son **históricas**. En caso de conflicto, prevalecen: (1) evidencia medida en el vehículo y surtidor, con fecha, (2) código y release publicados verificables, (3) documentación de la versión actual, (4) hipótesis/maquetas.

## Decisiones cerradas

- La navegación existente es la base; no romper emparejamiento, firmas, actualizador ni flujo de Waze por incorporar telemetría.
- Mantener Sender liviano. Los controles de ventanas, AVAS, CODE y combustible viven solo en Receiver/T1.
- No hay rol HOME obligatorio: reemplazar Android Auto o T1Launcher sigue siendo objetivo futuro, no capacidad de v0.2.10.
- **Consumo de nafta:** se descarta por completo el cálculo anterior basado en estimaciones del launcher. Solo considerar el cálculo nuevo de nafta usada / medida en tanque, lecturas independientes OEM/CSV y surtidor, con calidad y límites declarados.
- Nunca ejecutar comandos de ventanillas ni AVAS al arrancar. No bloquear ni eludir los sistemas de seguridad OEM.
- No sumar automatización Spotify hasta aislar por qué pierde la fuente y muestra NOT PROVIDED.
- No borrar, actualizar a ciegas ni limpiar la app estable mientras existan viajes de tanque/historial sin respaldo comprobado.

## Actualización Receiver v0.2.11 desde la app (10/11/2026)

**CONFIRMADO (publicación, no prueba física):** Receiver `0.2.11 / versionCode 12` está en la [release oficial v0.2.11](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11) marcada como **latest**, con `receiver.json` y APK firmada con la **misma identidad de certificado de v0.2.10**. [GitHub Actions OTA 42819862706](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42819862706) **PASÓ**. Fue integrada mediante [PR 15](https://github.com/bruceferra/drivelab-auto-standalone/pull/15). Se preservó **exactamente** el archivo `sender.json` de v0.2.10 (mismo SHA-256), apuntando al Sender 0.2.10: el usuario NO actualiza Sender para este ensayo.

**Flujo requerido:** en la propia Jetour, `DriveLab Auto Receiver → ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA`. El actualizador en `common/Updater.java` consulta `releases/latest/download/receiver.json`, compara versionCode, descarga desde la URL canónica y verifica SHA-256, paquete y certificado antes de solicitar la instalación nativa Android. **No** se requiere bajar APK a mano, desinstalar ni cambiar el Sender. Requiere confirmación de Android y prueba física del flujo en la T1.

**Funcionalidad agregada, físicamente NO PROBADA:** sección de espejo con `PULSO 1` / `PULSO 2` del ID 2069, liberación 0, confirmación y prelectura de marcha P/velocidad cero. Sin HOLD ni auto-inclinación en reversa. Probar exclusivamente con vehículo estacionado, mandos de fábrica accesibles; no confiar en topes mecánicos. [Protocolo de QA](RECEIVER_ESPEJOS_QA_0_2_11.md).

La antigua [pre-release v0.2.11-mirror-qa](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11-mirror-qa) fue un artefacto previo de laboratorio: **NO es el canal de actualización** ni el enlace para el usuario. El canal correcto es v0.2.11 latest.

## Nuevo ensayo previsto: Receiver 0.2.12 · espejos 500 ms y lectura HAL (10/11/2026)

**Evidencia física en 0.2.11:** usuario confirma `2069=1` **SUBE** y `2069=2` **BAJA** el espejo **derecho**; hizo cuatro pulsos de ida y cuatro de retorno. Para movimientos perceptibles solicita **500 ms**, sin secuencias rápidas de 10 pasos. El izquierdo **2068** sigue como candidato no validado. El launcher externo se descalibra repetidamente al guardar `pos_actual` por pulsos, incluso sin ajustes manuales posteriores.

**Receiver 0.2.12 (QA inicialmente):** agrega órdenes manuales de hasta 500 ms por lado, lectura de marcha/velocidad y un **diagnóstico HAL estrictamente de lectura** para `MIRROR_Z_POS`, `MIRROR_Y_POS` y propiedades vendor candidatas, mostrando área/rango/valor o error de acceso. Referencias D/R son **instantáneas crudas**, no memorias para auto-inclinación. No se implementan `IR VALOR INICIAL/FINAL` ni auto-R/D sin comprobar un getter de posición real. [Plan de pruebas](RECEIVER_ESPEJOS_0_2_12_CALIBRACION.md). Publicación por **actualizador incorporado de Receiver**, nunca APK aparte; Sender permanece en 0.2.10.

## Estado de publicación de Receiver 0.2.12 (10/11/2026)

**PUBLICADO Y FIRMADO, FUNCIONES NUEVAS SIN PRUEBA FÍSICA:** [release OTA oficial v0.2.12](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.12) es ahora `latest` y contiene `receiver.json` con `versionName=0.2.12`, `versionCode=13` y APK canónica; [GitHub Actions 42824306707](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42824306707) **PASÓ** incluyendo comparación de firma contra 0.2.11. **El SHA-256 de `sender.json` en v0.2.12 es idéntico al de v0.2.11**, que apunta a Sender v0.2.10: no hay actualización de Sender.

**Instalación:** única vía recomendada al usuario: `DriveLab Auto Receiver → ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA`, confirmación nativa de Android. **No entregar APK por separado** y no desinstalar Receiver. Esta publicación no equivale a que el usuario ya la haya instalado ni validado físicamente. Funciones: derecho 2069 con órdenes de hasta 500 ms, izquierdo 2068 candidato, consulta de marcha/velocidad, lectura de posición/rangos HAL sin escritura, instantáneas D/R (no auto-reversa). [Procedimiento](RECEIVER_ESPEJOS_0_2_12_CALIBRACION.md).

## Actualización de experiencia Sender/Receiver v0.2.13 (10/11/2026)

Se implementó comprobación automática del manifiesto al abrir Sender o Receiver (solo consulta; **ofrece** instalar con consentimiento), aviso visible al tocar actualización manual cuando es la última versión, y conservación de scroll tras cada interacción en ambas pantallas y panel VEHÍCULO. El usuario informó en Receiver 0.2.12 **P/velocidad 0** correctos y **ninguna de las propiedades HAL de posición de espejo en la lista accesible**; eso no demuestra permiso denegado ni ausencia de sensor. Véase [especificación y pruebas 0.2.13](ACTUALIZADOR_Y_SCROLL_0_2_13_2026-11-10.md).

## Canal OTA confirmado: Sender y Receiver 0.2.13

La [release oficial v0.2.13](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.13) está marcada como `latest` y contiene `sender.json`, `receiver.json` y las dos APK firmadas. [GitHub Actions 42826440626](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42826440626) **PASÓ** con continuidad de certificados. **No asumir instalación completada en los dispositivos**: usuario debe pulsar actualizador incorporado en Sender/Receiver una sola vez para incorporar 0.2.13. Posteriormente las apps comprobarán actualizaciones automáticamente al abrir, mostrarán estado explícito si no hay nueva versión y preservarán el scroll. [Especificación](ACTUALIZADOR_Y_SCROLL_0_2_13_2026-11-10.md).

## Próxima prueba de espejos: Receiver 0.2.14 (10/11/2026)

La observación nueva sobre `2068` (izquierdo) / `2069` (derecho) y lecturas posibles `2` arriba, `1` abajo, `0` intermedio es **HIPÓTESIS DE ESTADO**, distinta de las **órdenes verificadas** `2069=1` subir/`2069=2` bajar/`2069=0` liberar. Todavía no se ha observado de forma reproducible el getter con el mando suelto. No inferir ángulo ni tiempo total de carrera.

El Receiver experimental 0.2.14 incluye lectura directa RAW `getInt(2068/2069)`, pruebas manuales de **1.000 y 2.000 ms** en P/velocidad cero, y botones de plegar/desplegar basados solo en Android Automotive `MIRROR_FOLD` con permiso privilegiado y getter validado; si faltan, **no envía comando**. No hay automatismo R/D ni homing contra topes. [Procedimiento de calibración temporal y estado](RECEIVER_ESPEJOS_CALIBRACION_TEMPORAL_2026-11-10.md). Sender 0.2.13, mapas, puente, viajes y consumo permanecen intactos.

## Próxima integración

El módulo hoy llamado **ENERGÍA / CODE** en Receiver crecerá hasta **TABLERO T1** con litros reales estimados, autonomía contrastada, SOC, corriente, energía y consumo. Su modelo deberá almacenar observaciones originales y cálculos reproducibles por separado. El tratamiento del consumo continúa en el documento de tablero; no inventar señales ni convertir mediciones de otro proyecto en valores OEM verificados.

**Política de documentación:** cada hallazgo nuevo debe agregar fecha, dispositivo/versión, fuente (surtidor, menú OEM, CSV, Binder, captura, código), valor/unidad y clasificación: CONFIRMADO / OBSERVADO / HIPÓTESIS / PENDIENTE. No subir CSV con rutas privadas, IP, VIN, claves, secretos ni datos de ubicación personal a este repositorio público.
