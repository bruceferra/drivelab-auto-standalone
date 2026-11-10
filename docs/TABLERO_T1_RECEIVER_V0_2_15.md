# Receiver v0.2.15 — TABLERO T1 (primera integración aislada)

**Fecha:** 10/11/2026. **Estado previo al merge/publicación:** código de piloto, solo lectura, pendiente de QA Android y uso real en T1. Fuentes: [cierre del segundo tanque](CIERRE_SEGUNDO_TANQUE_T1_2026-11-10.md), [calibración original](CALIBRACION_CSV_T1_2026-11-10.md), [reglas de telemetría](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md).

## Propósito

Mostrar desde DriveLab Auto Receiver un **tablero comprobable** de consumo PHEV que no confunda diferencia OEM de nivel con litros realmente repostados.

### Interfaz independiente

- Botón principal de Receiver: **TABLERO T1 · TANQUES / CONSUMO / TELEMETRÍA**. También disponible desde `VEHÍCULO → ENERGÍA`.
- Se implementa como **`T1TankDashboardActivity`** sin modificación de `ReceiverService`, `BootReceiver`, `ShareToClusterActivity`, `Sender`, `MirrorPulsePilot`, sensores de ventanas/AVAS ni servicios del puente LAN. Nunca se abre automáticamente al arrancar el coche.
- Históricos inmutables definidos en **`T1TankEvidence`** con fuentes: ciclo 1 (66,865 L primer corte y ~950 km Jetour ⇒ ~7,04 L/100 km); ciclo 2 (66,230 L de foto y 887 km Jetour ⇒ 7,47 L/100 km). Se muestra también km/L, y se distingue explícitamente el primer ciclo como distancia redondeada.
- Comparativo **887 km Jetour / 867 km de otra app / 895,089 km DriveLab GPS**, sin promediar ni suponer mismos instantes de inicio/fin. La captura GPS tiene viajes sin etiquetar ciudad/autopista, por lo que no se atribuye distribución vial.
- Evidencia de **5,3 L OEM dos veces al corte térmico** y 67,0 L OEM tras ambos repostajes, mientras el surtidor físico del segundo ciclo cargó 66,230 L: **4,530 L de diferencia** respecto del incremento OEM 61,7 L. Se explica que no es un offset universal.
- Comprobante segundo surtidor: **$141.004, $2.129/L y 66,230 L**, fechado 10/11/2026. El importe de compra **no se confunde con costo real de los 887 km anteriores**.
- **Lectura OEM CI186 (on-demand)** mediante `VehicleReadOnly.readFuelOnce`, etiquetada como nivel filtrado/indicador; y **SOC 1042/1062, corriente 1064** crudas por `readEnergyOnce`, sin declarar kWh ni velocidad de carga real hasta validar escala y signo. No se solicitan setters ni códigos no permitidos.
- En cada lectura CI186 válida se guarda una **muestra cruda con timestamp** en `getFilesDir()/t1_tank_ci186_readings.csv`; puede exportarse por el selector nativo de documentos `ACTION_CREATE_DOCUMENT`. No se envía ningún dato a GitHub. Se conserva el archivo local al exportar.
- La lectura en primer plano es **única por entrada/tap**, sin monitoreo del vehículo en segundo plano. La pantalla se construye una sola vez para que las respuestas asíncronas actualicen únicamente los textos y **no hagan saltar el scroll**.

### Funciones NO implementadas deliberadamente

- **TRIP A tanque / TRIP B parcial:** queda explícitamente indicado que faltan distancia continua y evento de carga/sistema comprobados; no se inventa un contador.
- **Autonomía, litros físicos restantes, curva Ω→litros real, kWh cargados/descargados, regeneración y carga de térmico:** no se presentan como mediciones hasta confirmarlos físicamente con fuente independiente.
- No se importa el algoritmo **VIEJO** de T1Launcher; no se usa `consumo_nafta_100` del CSV como caudal real ni se asume linealidad en el flotante.
- No se introducen comandos a espejos ni cambios en ReceiverService, mapas, Waze, Bluetooth/Spotify, vinculación, viajes o Sender.
- No se cargan los CSV crudos privados ni fotos originales a un repositorio público; solo las cifras, métodos, SHA-256 de los archivos y las discrepancias.

### Tests y seguridad de publicación

`receiver/tests/T1TankEvidenceTest.java` comprueba **14 condiciones**: los dos cálculos L/100 km, km/L, delta OEM 61,7, diferencias de surtidor 4,530 y 5,165 L, diferencia entre pistolas 0,635 L, relación de precio y los dos odómetros, rotulación de precisión y entradas inválidas. El workflow `android-ci.yml` compila esa política como Java puro y también compila ambas aplicaciones, verificando que no aparecen permisos y controles indebidos.

**Canal OTA requerido:** `publish-receiver-ota.yml` debe producir **Receiver v0.2.15 versionCode 16** con su firma anterior, publicar `receiver.json` en la release latest y **mantener `sender.json` de Sender 0.2.13 bit a bit sin cambios**. Usuario actualiza solamente mediante el botón incorporado u oferta automática al abrir Receiver; **nunca descargar APK externa ni desinstalar**. No declarar versión publicada antes de que GitHub Actions termine y se verifiquen los assets.

## Publicación verificada en GitHub

- **Receiver v0.2.15 / versionCode 16** publicado como release [v0.2.15/latest](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.15).
- **GitHub Actions OTA firmado [42838320184](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42838320184) PASÓ**, incluida la comparación de certificado contra Receiver v0.2.14 y la validación del paquete/versión; QA de compilación de la PR [19](https://github.com/bruceferra/drivelab-auto-standalone/pull/19) también pasó.
- Release contiene `DriveLab-Auto-Receiver-v0.2.15.apk`, `receiver.json`, `receiver.sha256` y `sender.json`. **Digest SHA-256 APK Receiver:** `c95a25adec4aea42d8425b7ee18c5597b06e9477f1b295b1f5def2125a2a3eed`.
- **Sender v0.2.13 sigue SIN cambios**: el digest de `sender.json` coincide exactamente con la release v0.2.14 (SHA-256 `9c6a0a084a21d8c5ab98b183764c906fba89e3688fba1f7003b6799b7d11ee9d`). No se publica APK Sender nueva.
- **Forma de actualización única:** desde la aplicación Receiver instalada, mediante `ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA` u oferta automática de su actualizador. No entregar APK manual. **Todavía no está confirmada instalación física ni lectura CI186 en esta versión del auto.**

## Pruebas de aceptación en T1

1. Actualizar Receiver con el botón interno. Abrir **TABLERO T1** desde inicio o **VEHÍCULO → ENERGÍA**.
2. Verificar tarjetas de dos tanques (7,04 aproximado; 7,47 calculado), comprobante 66,230 L, diferencias de contadores y advertencia del indicador OEM.
3. Pulsar **ACTUALIZAR TELEMETRÍA OEM**: comprobar CI186 y el estado de las lecturas de SOC sin asumir kWh; **revisar que la pantalla no salte arriba**.
4. Exportar CSV al almacenamiento de Android y confirmar timestamp y dato CI186. Verificar que no borra datos previos.
5. Comprobar espejos, Waze/puente, controles de ventanas/AVAS y Sender tal como estaban. No hubo modificaciones intencionales de estos componentes.

**Próxima iteración:** especificar persistencia real de TRIP A/B, detectar eventos de carga comprobables y explorar si `PHEV SUM FUEL` HAL 559964335 permite una segunda estimación físicamente independiente; mantener datos sin extrapolación si faltan señales.
