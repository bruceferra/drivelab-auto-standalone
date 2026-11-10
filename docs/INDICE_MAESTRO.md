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

## Receiver 0.2.14 · ensayo de carrera temporal disponible por OTA

**Publicado y firmado:** [v0.2.14/latest](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.14), [Action 42832582917 exitosa](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42832582917). `receiver.json` apunta a v0.2.14/code15; `sender.json` es idéntico al de v0.2.13. Solo actualiza Receiver por su **actualizador interno**. Pantalla experimental incluye comandos manuales 1.000/2.000ms, lectura RAW 2068/2069 con mandos sueltos y plegado Android Automotive **condicionado a permiso OEM**. Sin prueba física aún, sin auto-R/D ni homing. [Procedimiento de calibración](RECEIVER_ESPEJOS_CALIBRACION_TEMPORAL_2026-11-10.md).

## Diseño de calibración de espejos por tiempo (10/11/2026)

El propietario propuso una alternativa más simple que las ocho posiciones virtuales: **calibrar duración total real en milisegundos y asignar marcas ¼, ½ y ¾ por tiempo**. Si como *ejemplo* una carrera tarda 20.000 ms, los objetivos serían 5.000, 10.000 y 15.000 ms. **No se ha medido que la carrera de la T1 dure 20 segundos.** Registrar dos tiempos por espejo, uno para subir y otro para bajar, y guardar D/R como tiempos respecto de un extremo verificado. Solo es autocalibración si los getters 2068/2069 distinguen de modo persistente extremos físicos; si no, la referencia depende de una calibración guiada. No forzar el motor contra topes para adivinar posiciones. [Diseño y cautelas](RECEIVER_ESPEJOS_CALIBRACION_TEMPORAL_2026-11-10.md). Receiver 0.2.14 sigue siendo experimental y **no** contiene automatismo de D/R ni carrera autocalibrada.

## Segundo tanque cerrado y TABLERO T1 experimental (10/11/2026)

**Dato físico nuevo:** el 10/11 se cargaron **66,230 L de nafta SUPER al primer corte de surtidor**, sin sobrellenar, por **$141.004** a **$2.129/L**, después de **887 km Jetour** y segundo corte del térmico a **5,3 L OEM**. Esto representa **7,47 L/100 km de reposición** (no caudal de motor); el ciclo anterior fue **66,865 L al primer corte / ~950 km / ~7,04 L/100 km**. El CSV nuevo documenta la carga desde las 11:29:19 y la subida OEM de **5,3 a 67,0 L**, que es **61,7 L OEM contra 66,230 L reales**, discrepancia **4,530 L**. El exportador marcó `sobrellenado=1` aunque el propietario confirmó que NO completó: no interpretarlo como hecho. [Evidencia y SHA-256](CIERRE_SEGUNDO_TANQUE_T1_2026-11-10.md).

**Primera integración propuesta:** Receiver v0.2.15 agrega una pantalla independiente **TABLERO T1** con ciclos históricos, triple contador, lectura puntual CI186 y SOC crudos, CSV local/exportable; no calcula litros físicos restantes ni kWh sin validar ni usa el cálculo viejo de T1Launcher. Sender, mapas, servicios y controles existentes no se modifican. **El estado de publicación debe verificarse en GitHub Actions**. [Especificación y aceptación](TABLERO_T1_RECEIVER_V0_2_15.md).

## Release oficial Receiver 0.2.15 (10/11/2026)

**CONFIRMADO en GitHub:** [Receiver 0.2.15/latest](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.15), [workflow firmado 42838320184 EXITOSO](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42838320184). El botón/aviso del actualizador interno ofrece v0.2.15 / code16. `sender.json` conserva **idéntico SHA-256** a v0.2.14, por lo cual **Sender queda v0.2.13**. No descargar APK suelta ni desinstalar Receiver. La [pantalla nueva TABLERO T1](TABLERO_T1_RECEIVER_V0_2_15.md) contiene dos históricos de repostaje, CI186 y energía cruda en lectura puntual y exportación CSV privada; no calcula autonomía física, kWh, TRIP A/B sin sensores validados. Instalación en T1 **pendiente de prueba del usuario**.

## Corrección de usabilidad de espejos Receiver 0.2.16

Prueba visual con una marca fija: **500, 1.000 y 2.000 ms producen el mismo desplazamiento** del espejo en las órdenes OEM actuales. No se puede calibrar el recorrido acumulando esos milisegundos como desplazamiento proporcional; es probable que cada transición envíe un paso discreto. La UI nueva deja solo movimiento de **500 ms**, quita las confirmaciones modales para controles de espejo/plegado, y muestra la lectura **RAW 2068/2069 justo debajo del botón** correspondiente, no al final. Las condiciones OEM de **P, velocidad cero, liberación 0 y cooldown** permanecen en código. El plegado vía API Android Automotive está denegado por permiso OEM, sin bypass. [Evidencia y restricciones](RECEIVER_ESPEJOS_CALIBRACION_TEMPORAL_2026-11-10.md). Los cambios no afectan Sender, viajes, mapas, tablero de consumo ni ventanas.

## Corrección publicada Receiver 0.2.16 (10/11/2026)

La [release v0.2.16/latest](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.16) contiene Receiver code17 firmado con el certificado anterior, `receiver.json` para actualizador incorporado y `sender.json` idéntico al previo (Sender sigue 0.2.13). [QA run 42842438931 EXITOSO](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42842438931) y [OTA run 42842439213](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42842439213). Corrige resultado RAW inmediatamente bajo su botón, sin popups de confirmación de espejos/plegado y sin 1.000/2.000 ms redundantes. Sigue protegida la exigencia OEM P/velocidad cero y liberación 0. La observación del usuario con marca fija confirmó igual recorrido para 500/1.000/2.000 ms, de modo que la calibración temporal continua **no está validada**. [Detalle](RECEIVER_ESPEJOS_CALIBRACION_TEMPORAL_2026-11-10.md).

## Espejos: avance UX y retorno al salir de R >10 km/h (10/11/2026)

**Validación física nueva:** el espejo **izquierdo 2068** también sube y baja con un solo toque, como el **derecho 2069**. El usuario observó que los toques rápidos se ignoran a veces: el piloto tenía un **cooldown de 1.800 ms impuesto por la app**, que se elimina de la próxima Receiver **0.2.17** para admitir el siguiente toque en cuanto finalice el anterior, conservando verificación de marcha P, velocidad cero, un comando en curso y liberación `0`. El plegado/desplegado estándar fue denegado por permiso OEM, por lo cual sus botones no operativos se retiran; también se retiran botones `GUARDAR D/R` que guardaban **texto HAL, no posición física**. La alarma peatonal 2074 pasa a un toque, con guardia OEM P/velocidad cero; ventilación y cierre de ventanas están **CONFIRMADOS** y no se modifican.

**Cambio de lógica pedido:** al entrar en R, bajar una sola vez N pasos por espejo, sin repetir durante R↔otras marchas a baja velocidad. Recuperar solo con **marcha conocida distinta de R + velocidad >10 km/h mantenida 800 ms**, incluso N si el coche estuviera rodando. **El motor automático aún NO está conectado:** se añadió `MirrorParkingPolicy` Java puro con pruebas, mientras se valida 1033 en R y velocidad OEM. Documentación: [espejos R/no-R](ESPEJOS_AUTO_R_NO_R_10KMH_2026-11-10.md). Los pasos 10 son ejemplo, no ajuste medido. Sin cambios a Sender, mapas, viajes ni cálculo de tanque.

## Marcha R con cámara y posible plegado por 2003 (10/11/2026)

El propietario confirmó **P=1, N=3, D=4** en `1033`; todavía no puede leer **R** porque aparece la cámara OEM. La Receiver experimental **0.2.18** agrega grabación puntual **35s** de cambios de `1033` mediante servicio de lectura independiente, para consultar el historial al regresar a P. La cámara no se desactiva.

También observó **2003: desplegar 0→1, plegar 1→0**. Es una **transición de lectura**, no una prueba de escritura. Nuevos controles experimentales de lectura y **ensayo individual** `2003=0` plegar / `2003=1` desplegar, con prelectura P/velocidad 0 y una sola orden; **no liberar con 0** después de desplegar, porque 0 podría plegar. **No se habilita auto-R** hasta verificar `1033` y velocidad. [Pasos de ensayo y evidencia](PRUEBA_MARCHA_CAMARA_Y_PLEGADO_2003_2026-11-10.md).

El propietario confirmó capacidad de **proyección a pantalla completa e importación ZIP de skins** desde T1Launcher. El objetivo del tablero es una vista dinámica propia, independiente de mapas, con agujas animadas basadas en señales OEM verificadas y autonomía estimada usando **solo surtidor/CSV/Ω/CI186**; no usar cálculo viejo T1Launcher. La proyección arbitraria está observada, pero la integración técnica del ZIP y sus datos **sigue por validar**.

## Receiver 0.2.18 publicada (10/11/2026)

Confirmado: [release Receiver 0.2.18/latest](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.18), [QA 42854161716 exitoso](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42854161716) y [publicación OTA firmada 42854161957 exitosa](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42854161957). Se conserva Sender v0.2.13, mismo `sender.json`. Receiver code19 se instala por su actualizador incorporado. Incluye grabación de 35 s para leer el código R pese a la cámara y prueba manual `2003=0` plegar / `2003=1` desplegar con lectura previa, P/0 y resultado inline. Ninguna escritura 2003 ni detección automática de R está **validada físicamente aún**. [Expediente](PRUEBA_MARCHA_CAMARA_Y_PLEGADO_2003_2026-11-10.md).

## Próxima integración## Próxima integración## Próxima integración## Próxima integración## Próxima integración

El módulo hoy llamado **ENERGÍA / CODE** en Receiver crecerá hasta **TABLERO T1** con litros reales estimados, autonomía contrastada, SOC, corriente, energía y consumo. Su modelo deberá almacenar observaciones originales y cálculos reproducibles por separado. El tratamiento del consumo continúa en el documento de tablero; no inventar señales ni convertir mediciones de otro proyecto en valores OEM verificados.

**Política de documentación:** cada hallazgo nuevo debe agregar fecha, dispositivo/versión, fuente (surtidor, menú OEM, CSV, Binder, captura, código), valor/unidad y clasificación: CONFIRMADO / OBSERVADO / HIPÓTESIS / PENDIENTE. No subir CSV con rutas privadas, IP, VIN, claves, secretos ni datos de ubicación personal a este repositorio público.
