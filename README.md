> **VERSIÓN ACTUAL · 12/11/2026:** **Receiver 0.2.41 (Jetour T1, code 42)** + **Sender 0.2.16 (Xiaomi, code 17)**. [Release APK firmado](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.41) · [Diagnóstico de capturas EV→HEV, HAL 557867211 y diferencia HV/12 V](docs/RECEIVER_0_2_41_FOTOS_HEV_12V_TEMPERATURA_Y_UPDATER.md). Corrige el mensaje al pulsar EV→HEV con 1043=2 (ya HEV, ninguna escritura enviada), compara solo lectura HAL 557867211 ↔ Vehicle 1043, mantiene sin utilizar el candidato write 557843642, distingue EXT.BATT 315 crudo como posible HV (no 12 V), muestra versión instalada real y actualizador legible. Conserva temperatura 559940105 experimental, LED P/N, pantalla completa y viajes. Sender sin cambios.

> **CONTEXTO HISTÓRICO RECUPERADO 11/11/2026 (previo a 0.2.33):** [Sender 0.2.16 Xiaomi + Receiver 0.2.32 T1, compilaciones, APK firmadas, OTA y pruebas pendientes](docs/RECUPERACION_ESTADO_2026-11-11_SENDER_0216_RECEIVER_0232.md). Los apartados con versiones anteriores son históricos.

> **DOCUMENTACIÓN MAESTRA / UNIFICACIÓN (10/11/2026):** [Índice maestro DriveLab Auto + Jetour T1](docs/INDICE_MAESTRO.md) — [Auditoría de versiones](docs/AUDITORIA_APPS_Y_CONSOLIDACION.md) — [Arquitectura Sender/Receiver](docs/ARQUITECTURA_SENDER_RECEIVER.md) — [Tablero consumo y telemetría](docs/TABLERO_CONSUMO_Y_TELEMETRIA_T1.md) — [Pruebas y limpieza](docs/PRUEBAS_PENDIENTES_Y_DEPURACION.md) — [Handoff a otra solapa](docs/HANDOFF_ENTRE_SOLAPAS.md).
>
> **ALERTA DE HISTÓRICO:** los apartados antiguos que dicen “Receiver no tiene OEM/telemetría” corresponden a versiones anteriores a 0.2.10. En 0.2.10 existen lectores y controles de prueba compilados, **pendientes de validación física en Jetour**. La función TABLERO de consumo continúa en diseño, sin incorporarse aún a la APK.

# DriveLab Auto v0.2.10 — puente + controles OEM T1

## Cambios preparados

- Conserva la navegación y Compartir de Google Maps en la T1 de v0.2.9, con el mismo protocolo, paquetes y firmas.
- Receiver agrega VEHÍCULO / ENERGÍA / CODE como módulo de la misma APK: controles manuales grupales e individuales de ventilación 1/4 y cierre para IDs 2004–2007, alarma peatonal 2074 solo manual, combustible OEM CI186, SOC 1042/1062, corriente 1064, detector diferencial 45 segundos, lector de tandas de 25 IDs y exportación CSV.
- Los controles OEM se activan solo desde pantalla visible y un toque deliberado; se leen estados y no se ejecutan al encender. No se fuerzan comandos 0 ni apertura total, ni se pretende anular seguridad OEM.
- Sender conserva las funciones de v0.2.9, con versión 0.2.10 para mantener ambas APK alineadas.
- Spotify no se automatiza: primero hay que estudiar conflicto de fuente Bluetooth/Spotify Connect, interrupciones y metadatos NOT PROVIDED.
- Sin migración o borrado de DriveLab GPS/Uber Beta/T1. El firmware puede rechazar accesos OEM de una aplicación con nuevo package: verificar en vehículo estacionado, no considerarlos confirmados antes de prueba.

## Secuencia de pruebas v0.2.10

1. Actualizar Sender y Receiver sobre 0.2.9 sin desinstalar ni desvincular.
2. Comprobar Xiaomi → clúster, Google Maps → Compartir → Receiver → Waze en T1, y arranque del receptor después de reiniciar.
3. En T1 estacionada y en P: abrir VEHÍCULO, consultar estados OEM, probar cierre y ventilación con ventanas libres; cada orden debe mostrar lectura posterior.
4. Alarma 2074: solo probar manualmente si es necesario y dejar activa antes de circular.
5. ENERGÍA: CI186, SOC 1042/1062 y 1064, comparar valores crudos con tablero/menú OEM. No extrapolar automáticamente litros utilizables.
6. CODE: detector 45 segundos con cambios físicos aislados, +25/-25 y CSV; no escribir códigos no identificados.
7. Spotify: probar por separado reproducción local y fuente del volante, sin automatizaciones en DriveLab.

## Alcance y límites

No HOME launcher. No modificación de T1Launcher, DriveLab original ni Uber Beta. Los controles del vehículo pueden depender de permisos OEM no concedidos a Receiver, y requieren prueba en la T1. No instalar versiones QA encima de firmas release.

---

# DriveLab Auto — Sender + Receiver (independiente)

> **v0.2.9 · Google Maps Compartir:** Sender acepta destinos compartidos que tengan nombre de lugar o un enlace `/maps/place/...`; si Google Maps envía un enlace corto y no se logra obtener coordenadas, usa **únicamente el nombre explícitamente compartido**, no la posición del mapa. Receiver en la propia Jetour ahora figura como **«DriveLab · Navegar en clúster»** en el menú **Compartir** de Google Maps (acción texto o enlace compatible). Al seleccionarlo, solicita a Waze de la T1 `waze://?ll=...&navigate=yes` o `waze://?q=...&navigate=yes`, sin utilizar el teléfono ni requerir tocar el clúster. Si solo tenemos el nombre, Waze puede elegir el primer resultado y debe verificarse; si no hay un destino identificable no se envía uno inventado. No se modifican el puente cifrado, el autoarranque, T1Launcher, ni los permisos. Se requiere actualización de **Sender y Receiver** para tener ambas funciones; quedan con versión y firma continuas.


> **v0.2.8 · corrección de seguridad:** Se elimina del Sender el control de Spotify por acceso general a notificaciones y los receptores Bluetooth experimentales añadidos en v0.2.7. Se conserva la navegación Sender→Receiver, el emparejamiento y la actualización directa. El inicio automático de Spotify queda **sin implementar** mientras se investiga una alternativa de permisos mínimos (Spotify App Remote requiere autenticación y configuración propia). Esta corrección no garantiza eliminar advertencias de Play Protect: confirmar el texto exacto si persisten.
**Proyecto separado de `bruceferra/drivelab`. No contiene telemetría, controles de ventanillas, CODE, Uber Beta, OEM, CAN ni T1Launcher.**

Dos APK autónomas:

| App | Dispositivo | Paquete Android | Función |
|---|---|---|---|
| **DriveLab Auto Sender** | Xiaomi | `ar.com.rango5gps.drivelabauto.sender` | Recibir enlaces de Uber / Maps / Waze, enviarlos cifrados y mostrar el estado; botón **Solo clúster** |
| **DriveLab Auto Receiver** | Jetour T1 (Android 11) | `ar.com.rango5gps.drivelabauto.receiver` | Receptor TCP local cifrado, Waze en T1, inicio automático opcional, selector de app de arranque |

**Nuevo identificador de paquete para cada aplicación.** Las dos se instalan al lado de las anteriores y no sobrescriben DriveLab T1 (telemetría), DriveLab Puente T1 ni la actual DriveLab Auto del Xiaomi. Así preservamos la configuración que ya funciona hasta probar esta migración. Los dos dispositivos se vinculan una sola vez con **seis dígitos** y una nueva clave fuerte generada mediante ECDH P-256. El código de seis dígitos es únicamente un número de comparación, nunca una contraseña de red.

## Qué se logró en la versión de origen

El proyecto original ha sido probado físicamente: Uber Driver y Google Maps → DriveLab Auto Xiaomi → Waze en clúster de Jetour T1. Este proyecto es una **separación de código, todavía sin prueba física en la camioneta**. No afirmar que el nuevo Receiver abre Waze en el clúster hasta probarlo con la ROM de T1.

## Instalación de las APK firmadas (v0.2.1, luego de confirmar publicación)

1. Instalar `DriveLab-Auto-Receiver-v0.2.1.apk` en Jetour T1 desde la pantalla de instalación de Android (una sola vez, por WebADB u otro instalador habilitado). Abrir **Receiver → INICIAR RECEPTOR**; si se quiere que se inicie al encender, activar **INICIO AL ENCENDER T1**.
2. Abrir el permiso especial **Mostrar sobre otras apps** para **Receiver** desde Ajustes Android 11. El Receiver no dibuja ventanas ni registra pantalla: es el permiso con el que Android 11 puede permitir lanzar Waze desde segundo plano. El firmware puede bloquearlo igualmente.
3. En Receiver tocar **VINCULAR XIAOMI · 6 DÍGITOS**. Leer la IP que se muestra arriba. En Xiaomi instalar `DriveLab-Auto-Sender-v0.2.1.apk`; abrir y escribir esa IP en **Vincular Receiver · código de 6 dígitos**.
4. Comparar los números de ambas pantallas. **Primero autorizar en T1**, después tocar **COINCIDEN · CONFIRMAR** en Xiaomi. Si no coinciden, rechazar. Ambos conservarán la clave fuerte de AES y la IP.
5. Probar **Probar envío real a La Plata** desde Sender. Si funciona, probar desde Google Maps → Compartir → DriveLab Auto Sender. Después, cuando sea seguro hacerlo, en Uber Driver elegir DriveLab Auto Sender como opción para los enlaces de navegación. Dado que el paquete es nuevo, habrá que elegir **Siempre** nuevamente.
6. Solo cuando Sender / Receiver superen las pruebas físicas de navegación con el Receiver oculto y de reinicio, desinstalar los puentes anteriores que ya no se necesiten.

**Modo SOLO CLÚSTER** activado por defecto en Sender: no abre Waze en el Xiaomi tras recibir confirmación autenticada de la T1. Si la T1 no se alcanza o no solicita Waze, Sender ofrece abrirlo manualmente en el teléfono. **CLÚSTER + XIAOMI** conserva la navegación en los dos dispositivos.

## Arranque automático y Wi-Fi

Receiver es un **Foreground Service** con una notificación persistente. Puede quedar ejecutándose al volver al launcher, incluso si se cierra su pantalla. Con la opción **INICIO AL ENCENDER**, el broadcast `BOOT_COMPLETED` intenta iniciar el receptor sin abrir la UI. Si Android mata el servicio, `START_STICKY` solicita restauración. El permiso de segundo plano permite intentar abrir Waze en la T1 al llegar un destino. **El ACK de red no demuestra por sí solo que la ROM haya abierto Waze en el clúster**; se verifica físicamente.

También podés elegir **una aplicación instalada que tenga pantalla de inicio** para que Receiver intente abrirla una vez después del boot (opcional, demora ~12 segundos, requiere permiso especial). No cambia el launcher ni intenta controlar el sistema OEM.

El receptor mantiene el puerto abierto mientras Wi-Fi se conecta o desconecta, pero **no puede encender Wi-Fi por fuerza** desde una app normal en Android 11. Si la camioneta se queda sin dirección IP o cambia la IP, puede requerir volver a conectar a la red del Xiaomi y actualizar la IP guardada en Sender. Todavía no hay descubrimiento automático de IP. Si Android Auto inalámbrico interfiere, diagnosticarlo aparte.

## Actualizadores independientes

Desde **v0.2.3**, ambas apps tienen **ACTUALIZAR SENDER / RECEIVER · INSTALACIÓN DIRECTA**. No abren navegador ni GitHub: consultan manifiestos HTTPS propios dentro de la app:

- Sender: `https://github.com/bruceferra/drivelab-auto-standalone/releases/latest/download/sender.json`
- Receiver: `https://github.com/bruceferra/drivelab-auto-standalone/releases/latest/download/receiver.json`

El manifiesto tiene `packageName`, `versionName`, `versionCode`, `sha256` y `apkUrl`. Al tocar Actualizar se consulta el manifiesto, se descarga **en caché privada dentro de la misma aplicación** la APK oficial por HTTPS (la URL nunca se abre en pantalla), se verifica SHA-256 del archivo y se cotejan **paquete, versión y certificado digital de firma** con los instalados. Si todos coinciden se abre el **instalador nativo de Android** con un proveedor privado de un único archivo en solo lectura. No se instala silenciosamente, no se usa root y Android exige aceptar la instalación. Por única vez Android puede solicitar autorizar «Instalar apps desconocidas» desde esta aplicación; al volver se retoma el instalador sin descargar de nuevo. El permiso `REQUEST_INSTALL_PACKAGES` es requerido para ofrecer esta función desde el botón, y puede causar revisiones de Play Protect. Si aparece una detección real de malware, no desactivar Play Protect: investigar el mensaje y presentar apelación si corresponde. La actualización es por app y no depende del VPS de telemetría.

Los manifiestos están diseñados para **GitHub Releases de un repositorio público sin secretos en el código**. En un repo privado los teléfonos no podrán descargar los assets sin autenticación; también se puede adaptar el host HTTPS de updates a otro servidor público. El repositorio y la versión inicial v0.2.0 se publicaron correctamente en GitHub.

## Compilar y distribuir APK definitivas

Este ZIP es **código fuente completo**, no APK firmadas. El entorno donde se generó no tiene Android SDK/Gradle, por eso no se pueden compilar APK para instalar sin un runner configurado.

En un nuevo repositorio de GitHub llamado **`bruceferra/drivelab-auto-standalone`** (sin mezclar nada en `bruceferra/drivelab`):

**Configuración más corta en Windows:** después de descomprimir, instalar Git, GitHub CLI y JDK 17 y ejecutar `gh auth login`, correr `./tools/bootstrap.ps1`. Este script crea un nuevo repositorio **público**, genera ambas firmas localmente, carga ocho secretos cifrados de GitHub Actions y solicita el primer build firmado de las dos APK. Necesitás configurar `git user.name` y `git user.email` si todavía no lo hiciste. Respaldar fuera de GitHub la carpeta `signing/`. Si la ejecución se interrumpe después de crear claves, ejecutar `tools/upload-signing-secrets.ps1` para reanudar sin rotarlas. El repo anterior no se toca.


1. Subir el contenido de este ZIP a la rama `main` (en Windows, opción automatizada `tools/create-github-repo.ps1`, que requiere `gh auth login` y Git).
2. `.github/workflows/android-ci.yml` compila ambas APK debug para QA y prueba cifrado. Los artefactos debug **no** deben instalarse sobre releases ya firmadas.
3. Crear una **clave PKCS12 permanente distinta por app** (ayuda local: `tools/setup-signing.ps1`; ejecutarlo una sola vez después de crear el repo; el parámetro `-UploadSecrets` permite enviarlas usando `gh auth login`, y las claves nunca se incluyen en la APK ni en el código). Guardar de manera privada y hacer backup fuera de GitHub. Subir a GitHub Actions Secrets los valores `SENDER_KEYSTORE_B64`, `SENDER_STORE_PASSWORD`, `SENDER_KEY_ALIAS`, `SENDER_KEY_PASSWORD`, y las cuatro variables equivalentes `RECEIVER_...`. No versionar claves ni contraseñas.
4. En Actions correr **DriveLab Auto · APK release firmadas y actualizador** con el nuevo tag de versión (por ejemplo, `tag=v0.2.1`). El workflow verifica protocolo, construye las dos APK firmadas, valida los paquetes, publica un único GitHub Release con **ambas APK y ambos manifests de actualización**. El repositorio debe ser público para descarga directa.
5. Las actualizaciones posteriores solo requieren aumentar el número de versión y `versionCode` en los dos Gradle según corresponda, y publicar una nueva release con los mismos certificados de firma originales.

Requisitos locales: JDK 17, Android SDK 35, Gradle 8.7. En Windows puede ejecutarse `tools/build-local.ps1 -Variant Debug` con Gradle instalado. Las APK se instalan normalmente usando Android; ni este proyecto ni el updater necesitan acceso a los APIs del vehículo.

## Seguridad y diagnóstico

- Solo red LAN, puertos **48391 (destinos)** y **48392 (vinculación)**, sin servidor relay ni transmisión en Internet de posiciones. AES-128-GCM con nonce nuevo en cada paquete y anti-replay; las consultas de actualizaciones se hacen por HTTPS.
- El código de seis dígitos se muestra simultáneamente en ambos extremos. La clave criptográfica de 128 bits se deriva del acuerdo ECDH, no de los dígitos.
- Sin permisos de Accesibilidad, GPS, cámara, micrófono ni captura; no guarda coordenadas, URL completas ni historial de viajes.
- Los enlaces de Google Maps cortos se resuelven mediante redirecciones HTTPS limitadas a hosts conocidos. Un enlace ambiguo no se convierte en coordenadas aproximadas.
- La opción de abrir Waze en segundo plano puede ser bloqueada por el firmware aun si Android permite el intento; la notificación de Receiver ofrece un respaldo manual.
- Nunca habilitar funciones de navegación o probar el clúster mientras se conduce.

## Estado de entrega

- Fuente separada: **repositorio público** `bruceferra/drivelab-auto-standalone`.
- Prueba de protocolo Java (ECDH, SAS, GCM, integridad): **validada en GitHub Actions**.
- Las APK iniciales v0.2.0 se compilaron, firmaron y publicaron. v0.2.1 reduce los permisos de instalación. La validación de Play Protect y las pruebas físicas en la T1 están pendientes.
- Repositorio independiente: `https://github.com/bruceferra/drivelab-auto-standalone`; no se modificó el repositorio original.

## v0.2.1: reducción de permisos y advertencias de Play Protect

- **Sender** solicita únicamente permiso de Internet. No usa overlay, arranque, permisos de instalación, accesibilidad ni acceso a datos personales.
- **Receiver** solicita Internet, inicio tras el arranque, servicio de primer plano y el permiso especial opcional `SYSTEM_ALERT_WINDOW` para intentar abrir Waze cuando la UI no está visible. Este permiso requiere autorización explícita en Android 11.
- Ninguna APK puede instalar paquetes directamente ni se autoactualiza en silencio. El botón COMPROBAR ACTUALIZACIÓN consulta el manifiesto oficial y ofrece abrir GitHub con el navegador. El instalador del sistema conserva las comprobaciones de firma al actualizar.
- Quitar permisos innecesarios puede reducir alertas, pero **NO garantiza** que Play Protect no marque una APK externa como sospechosa. Si Play Protect muestra «aplicación dañina» o bloquea la instalación, no desactivar la protección: identificar el mensaje concreto y presentar apelación por falso positivo a Google si corresponde.
- Se mantienen los identificadores de paquete y las claves de firma originales; **no ejecutar bootstrap ni regenerar claves**. Los usuarios con v0.2.0 podrán actualizar a v0.2.1 encima, si Android permite la instalación.

## v0.2.2: vinculación Xiaomi ↔ T1 recuperada

El asistente de Receiver ahora **actualiza automáticamente** el estado y los seis dígitos mientras está visible (sin tocar «ACTUALIZAR ESTADO»). La confirmación sigue siendo **obligatoriamente presencial**: con Receiver activo, tocar VINCULAR XIAOMI; en Sender iniciar vinculación con la IP privada mostrada en Receiver; esperar a que ambos muestren el mismo código; tocar **AUTORIZAR CÓDIGO COINCIDENTE → SÍ, COINCIDEN** en T1 y **COINCIDEN · CONFIRMAR** en Xiaomi. Si los códigos no coinciden, no aprobar; repetir el proceso con un par nuevo.

En v0.2.1 la prueba criptográfica final y el ACK aplicaban además una ventana de reloj absoluto de cinco minutos. En un coche que arranca sin Internet, el reloj de Android 11 puede estar desfasado con respecto al Xiaomi. v0.2.2 elimina esa dependencia **solo en mensajes de emparejamiento `ping/ack`**, dentro de la sesión ECDH de un solo uso y dos minutos autorizada físicamente en la T1. Los paquetes de navegación conservan caducidad y protección anti-replay.

Antes de enviar cada destino, el Sender solicita un **ping AES-GCM autenticado** al Receiver, comprueba que el nonce corresponda a esa solicitud y usa el reloj remoto autenticado para ajustar la marca de tiempo de la ruta y del recibo, sin debilitar la ventana temporal de cinco minutos ni almacenar coordenadas. La T1 admite el ping de sincronización aunque su reloj local no coincida, pero nunca acepta destinos caducados.

El Receiver presenta el motivo general cuando el emparejamiento caduca o le falta la confirmación física. Actualizar **ambas** APK a 0.2.2 con las claves originales de firma; no desinstalar ni borrar la vinculación anterior. Si todavía falla, registrar el mensaje exacto que aparece en las dos pantallas (sin compartir claves ni contraseñas).

## v0.2.3 · Actualizador nativo integrado, sin navegador

Tanto el Receiver Android 11 de la T1 como el Sender del Xiaomi replican el mecanismo ya utilizado en DriveLab Uber/ProManager. El botón **ACTUALIZAR** consulta, descarga y verifica la APK firmada y abre el instalador Android dentro del flujo de la aplicación. GitHub sigue siendo únicamente el servidor remoto donde se publican las versiones: no se abre una página ni un navegador en la T1.

**Primer salto:** la versión anterior 0.2.2 todavía contiene el actualizador por navegador. Para pasar a 0.2.3 es necesaria **una última actualización manual** del Receiver por un instalador ya habilitado en la T1 (p. ej. WebADB o administrador de archivos) sin desinstalarlo. Las siguientes versiones se podrán instalar directamente desde el botón 0.2.3. El Sender puede actualizar 0.2.3 desde el Xiaomi de la misma manera.

El actualizador verifica también el certificado de la APK contra el certificado ya instalado. Las firmas de Sender y Receiver permanecen separadas y estables. Android 11 puede solicitar, una vez, la autorización de instalación desde Receiver y siempre mantiene la confirmación del instalador del sistema: no es una actualización silenciosa. El firmware de Jetour puede limitar este tipo de instalaciones y debe comprobarse **estacionado**.

No se cambia el receptor de navegación, sus puertos (48391/48392), las llaves de vinculación, el modo clúster, el launcher, ni la app DriveLab T1 de telemetría.

## v0.2.4 · Release de prueba para comprobar la actualización nativa real

La v0.2.4 conserva el código de actualización introducido en v0.2.3. Se publica con `versionCode 5` y las mismas firmas para permitir la prueba real: instalar primero v0.2.3 por el método ya utilizado para la instalación inicial (en la T1, WebADB o instalador de archivos), abrir **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA** y observar que v0.2.3 descarga, verifica e instala v0.2.4 sin abrir navegador ni página de GitHub. El mismo proceso sirve para Sender.

Android debe confirmar la instalación y puede pedir autorización de fuente desconocida por primera vez. Si Play Protect detecta una amenaza, detener la prueba y revisar el diagnóstico: no desactivar protecciones. No desinstalar, no volver a generar certificados, no desvincular.

## v0.2.5 · Instalador de Receiver adaptado a Jetour Android 11

En la T1, la página `ACTION_MANAGE_UNKNOWN_APP_SOURCES` puede no abrir ninguna interfaz. La v0.2.3/0.2.4 ya descargaban y verificaban la APK, pero quedaban detenidas en el cuadro «AUTORIZAR». Esta versión **no detiene Receiver en esa pantalla**: tras verificar SHA-256, paquete, versión y certificado, solicita una sesión Android estándar de `PackageInstaller` para que el sistema decida la confirmación necesaria.

Se agregó un `ReceiverInstallResultReceiver` privado para informar `STATUS_PENDING_USER_ACTION`, `STATUS_SUCCESS` o el código/error de rechazo; intenta abrir el diálogo oficial de consentimiento, con notificación accionable cuando Android restringe las aperturas desde segundo plano. El Receiver actualiza automáticamente ese estado en pantalla y ofrece «REINTENTAR INSTALACIÓN YA DESCARGADA», «INSTALADOR NATIVO ALTERNATIVO» y «ABRIR AJUSTES DE RECEIVER (PERMISO)». Ninguna opción instala en silencio ni elude las restricciones del firmware.

**Advertencia:** si la ROM restringe deliberadamente las instalaciones desde otras aplicaciones, Android podría rechazar también las sesiones de `PackageInstaller`. En ese caso la app no puede prometer una actualización sin intervención del sistema; mostrará el fallo y seguirá estando disponible instalar una APK firmada mediante WebADB. No desactivar Play Protect. Solo hay un Receiver; no modificar la app de telemetría ni desinstalar el paquete existente. Las claves de firma siguen siendo las originales.

Prueba recomendada: instalar una vez Receiver 0.2.5 encima de 0.2.4 mediante WebADB (o instalador Android habilitado) y usar su botón de actualización para instalar la siguiente versión de prueba. El Sender mantiene exactamente el mismo flujo ya probado en el Xiaomi.
