# DriveLab Auto — Sender + Receiver (independiente)

**Proyecto separado de `bruceferra/drivelab`. No contiene telemetría, controles de ventanillas, CODE, Uber Beta, OEM, CAN ni T1Launcher.**

Dos APK autónomas:

| App | Dispositivo | Paquete Android | Función |
|---|---|---|---|
| **DriveLab Auto Sender** | Xiaomi | `ar.com.rango5gps.drivelabauto.sender` | Recibir enlaces de Uber / Maps / Waze, enviarlos cifrados y mostrar el estado; botón **Solo clúster** |
| **DriveLab Auto Receiver** | Jetour T1 (Android 11) | `ar.com.rango5gps.drivelabauto.receiver` | Receptor TCP local cifrado, Waze en T1, inicio automático opcional, selector de app de arranque |

**Nuevo identificador de paquete para cada aplicación.** Las dos se instalan al lado de las anteriores y no sobrescriben DriveLab T1 (telemetría), DriveLab Puente T1 ni la actual DriveLab Auto del Xiaomi. Así preservamos la configuración que ya funciona hasta probar esta migración. Los dos dispositivos se vinculan una sola vez con **seis dígitos** y una nueva clave fuerte generada mediante ECDH P-256. El código de seis dígitos es únicamente un número de comparación, nunca una contraseña de red.

## Qué se logró en la versión de origen

El proyecto original ha sido probado físicamente: Uber Driver y Google Maps → DriveLab Auto Xiaomi → Waze en clúster de Jetour T1. Este proyecto es una **separación de código, todavía sin prueba física en la camioneta**. No afirmar que el nuevo Receiver abre Waze en el clúster hasta probarlo con la ROM de T1.

## Instalación inicial (cuando existan las APK release firmadas)

1. Instalar `DriveLab-Auto-Receiver-v0.2.0.apk` en Jetour T1 desde la pantalla de instalación de Android (una sola vez, por WebADB u otro instalador habilitado). Abrir **Receiver → INICIAR RECEPTOR**; si se quiere que se inicie al encender, activar **INICIO AL ENCENDER T1**.
2. Abrir el permiso especial **Mostrar sobre otras apps** para **Receiver** desde Ajustes Android 11. El Receiver no dibuja ventanas ni registra pantalla: es el permiso con el que Android 11 puede permitir lanzar Waze desde segundo plano. El firmware puede bloquearlo igualmente.
3. En Receiver tocar **VINCULAR XIAOMI · 6 DÍGITOS**. Leer la IP que se muestra arriba. En Xiaomi instalar `DriveLab-Auto-Sender-v0.2.0.apk`; abrir y escribir esa IP en **Vincular Receiver · código de 6 dígitos**.
4. Comparar los números de ambas pantallas. **Primero autorizar en T1**, después tocar **COINCIDEN · CONFIRMAR** en Xiaomi. Si no coinciden, rechazar. Ambos conservarán la clave fuerte de AES y la IP.
5. Probar **Probar envío real a La Plata** desde Sender. Si funciona, probar desde Google Maps → Compartir → DriveLab Auto Sender. Después, cuando sea seguro hacerlo, en Uber Driver elegir DriveLab Auto Sender como opción para los enlaces de navegación. Dado que el paquete es nuevo, habrá que elegir **Siempre** nuevamente.
6. Solo cuando Sender / Receiver superen las pruebas físicas de navegación con el Receiver oculto y de reinicio, desinstalar los puentes anteriores que ya no se necesiten.

**Modo SOLO CLÚSTER** activado por defecto en Sender: no abre Waze en el Xiaomi tras recibir confirmación autenticada de la T1. Si la T1 no se alcanza o no solicita Waze, Sender ofrece abrirlo manualmente en el teléfono. **CLÚSTER + XIAOMI** conserva la navegación en los dos dispositivos.

## Arranque automático y Wi-Fi

Receiver es un **Foreground Service** con una notificación persistente. Puede quedar ejecutándose al volver al launcher, incluso si se cierra su pantalla. Con la opción **INICIO AL ENCENDER**, el broadcast `BOOT_COMPLETED` intenta iniciar el receptor sin abrir la UI. Si Android mata el servicio, `START_STICKY` solicita restauración. El permiso de segundo plano permite intentar abrir Waze en la T1 al llegar un destino. **El ACK de red no demuestra por sí solo que la ROM haya abierto Waze en el clúster**; se verifica físicamente.

También podés elegir **una aplicación instalada que tenga pantalla de inicio** para que Receiver intente abrirla una vez después del boot (opcional, demora ~12 segundos, requiere permiso especial). No cambia el launcher ni intenta controlar el sistema OEM.

El receptor mantiene el puerto abierto mientras Wi-Fi se conecta o desconecta, pero **no puede encender Wi-Fi por fuerza** desde una app normal en Android 11. Si la camioneta se queda sin dirección IP o cambia la IP, puede requerir volver a conectar a la red del Xiaomi y actualizar la IP guardada en Sender. Todavía no hay descubrimiento automático de IP. Si Android Auto inalámbrico interfiere, diagnosticarlo aparte.

## Actualizadores independientes

Ambas apps tienen **COMPROBAR ACTUALIZACIÓN**. Consultan manifiestos HTTPS propios:

- Sender: `https://github.com/bruceferra/drivelab-auto-standalone/releases/latest/download/sender.json`
- Receiver: `https://github.com/bruceferra/drivelab-auto-standalone/releases/latest/download/receiver.json`

Un manifest contiene `packageName`, `versionName`, `versionCode`, `sha256`, `apkUrl`. El actualizador valida dominio HTTPS, paquete Android, versión ascendente y SHA-256 antes de solicitar al instalador de Android la actualización. La firma del paquete debe coincidir; Android no deja actualizar con un certificado distinto. **No hay instalación silenciosa** ni permiso root; la primera instalación requiere autorización para "Instalar apps desconocidas" y confirmación del usuario. La actualización es por app, no depende del VPS de telemetría.

Los manifiestos están diseñados para **GitHub Releases de un repositorio público sin secretos en el código**. En un repo privado los teléfonos no podrán descargar los assets sin autenticación; también se puede adaptar el host HTTPS de updates a otro servidor público. No usar estas URLs antes de crear el repositorio y publicar una release firmada.

## Compilar y distribuir APK definitivas

Este ZIP es **código fuente completo**, no APK firmadas. El entorno donde se generó no tiene Android SDK/Gradle, por eso no se pueden compilar APK para instalar sin un runner configurado.

En un nuevo repositorio de GitHub llamado **`bruceferra/drivelab-auto-standalone`** (sin mezclar nada en `bruceferra/drivelab`):

**Configuración más corta en Windows:** después de descomprimir, instalar Git, GitHub CLI y JDK 17 y ejecutar `gh auth login`, correr `./tools/bootstrap.ps1`. Este script crea un nuevo repositorio **público**, genera ambas firmas localmente, carga ocho secretos cifrados de GitHub Actions y solicita el primer build firmado de las dos APK. Necesitás configurar `git user.name` y `git user.email` si todavía no lo hiciste. Respaldar fuera de GitHub la carpeta `signing/`. Si la ejecución se interrumpe después de crear claves, ejecutar `tools/upload-signing-secrets.ps1` para reanudar sin rotarlas. El repo anterior no se toca.


1. Subir el contenido de este ZIP a la rama `main` (en Windows, opción automatizada `tools/create-github-repo.ps1`, que requiere `gh auth login` y Git).
2. `.github/workflows/android-ci.yml` compila ambas APK debug para QA y prueba cifrado. Los artefactos debug **no** deben instalarse sobre releases ya firmadas.
3. Crear una **clave PKCS12 permanente distinta por app** (ayuda local: `tools/setup-signing.ps1`; ejecutarlo una sola vez después de crear el repo; el parámetro `-UploadSecrets` permite enviarlas usando `gh auth login`, y las claves nunca se incluyen en la APK ni en el código). Guardar de manera privada y hacer backup fuera de GitHub. Subir a GitHub Actions Secrets los valores `SENDER_KEYSTORE_B64`, `SENDER_STORE_PASSWORD`, `SENDER_KEY_ALIAS`, `SENDER_KEY_PASSWORD`, y las cuatro variables equivalentes `RECEIVER_...`. No versionar claves ni contraseñas.
4. En Actions correr **DriveLab Auto · APK release firmadas y actualizador** con `tag=v0.2.0`. El workflow verifica protocolo, construye las dos APK firmadas, valida los paquetes, publica un único GitHub Release con **ambas APK y ambos manifests de actualización**. El repositorio debe ser público para descarga directa.
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

- Fuente separada: **incluida en este ZIP**.
- Prueba de protocolo Java (ECDH, SAS, GCM, integridad): **validada localmente**.
- Compilación de ambas APK Android, firmas productivas, publicación remota, actualización real y prueba física en T1: **pendientes**.
- Repositorio remoto independiente: **todavía no creado**, porque el conector GitHub disponible no ofrece crear repositorios; no se ha tocado el repositorio original.
