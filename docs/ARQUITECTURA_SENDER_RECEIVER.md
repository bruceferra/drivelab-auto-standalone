# Arquitectura DriveLab Auto: Sender + Receiver

**Base verificable en código:** v0.2.10, 2026-11-09. **Principio:** no interrumpir la navegación ya implementada para incorporar el tablero.

## Diagrama lógico (descripción)

1. **Xiaomi / Sender** recibe un destino por Android Compartir, enlace de Maps/WhatsApp o navegación de Uber si Android entrega la intención; valida URL/host/posición y normaliza sin inventar coordenadas.
2. El **puente LAN cifrado** envía un mensaje al Receiver en la IP local de la Jetour. Sender decide SOLO CLÚSTER (predeterminado) o CLÚSTER + XIAOMI. Si falla el vehículo, no debe bloquear el uso manual del teléfono.
3. **Jetour / Receiver** comprueba autenticidad/antirrepetición y solicita Waze con coordenadas o consulta válida, según lo recibido. Puede mantenerse como servicio de primer plano tras cerrar la pantalla.
4. **Google Maps corriendo directamente en la Jetour:** Android Compartir → DriveLab · Navegar en clúster → Waze en la T1, sin pasar por Xiaomi (incorporado v0.2.9).
5. **Módulo VEHÍCULO / ENERGÍA / CODE** existe solo en la interfaz del Receiver. Sus consultas Binder van a servicios Autolink locales; **no viajan por Sender** y no cambian mensajes ni claves del puente.
6. **Tablero T1 futuro:** consumidor de muestras OEM propias y registros de combustible/energía; no modifica datos de viajes de DriveLab estable ni interfiere con Waze.

## Identidades, Android y compatibilidad

| Dato | Sender | Receiver |
|---|---|---|
| APK | DriveLab-Auto-Sender-v0.2.10.apk | DriveLab-Auto-Receiver-v0.2.10.apk |
| Package | ar.com.rango5gps.drivelabauto.sender | ar.com.rango5gps.drivelabauto.receiver |
| versionName/code | 0.2.10 / 11 | 0.2.10 / 11 |
| Plataforma | Xiaomi (mínimo Android 8) | Jetour T1 Android 11 (mínimo Android 11) |
| Tarea | Compartir y enviar destinos | Waze, arranque, UI OEM, observaciones y futuras gráficas |
| Actualización | Manifiesto sender.json + firma permanente | Manifiesto receiver.json + firma permanente |

El puente **nuevo** escucha destinos en TCP **48391** y vinculación en **48392**. ECDH P-256 con comparación visual de seis dígitos, AES-GCM y protección antirrepetición. Los seis dígitos **no** son una contraseña de red. No transmitir claves ni destinos completos en logs.

El puente **original** de drivelab/drivelab-t1 y drivelab/drivelab-auto trabajaba en **47391/47392** y con protocolo/vinculación distintos. **No mezclar sender viejo con receiver nuevo**; ambos pares coexisten como aplicaciones diferentes solo durante la migración. La fuente de verdad del puente nuevo son los módulos common/BridgeCrypto.java, common/MapLinks.java, sender/ y receiver/.

## Apertura de mapas

- Preferir coordenadas de destino explícitas; rechazar centro del mapa y coordenadas ambiguas.
- Enlaces cortos maps.app.goo.gl: resolución HTTPS limitada; si falla y existe nombre compartido explícito, se puede solicitar búsqueda por nombre. Waze puede escoger el primer resultado: verificar.
- Waze usa deep links de navegación (coordenadas ll o consulta q, con navigate=yes); **solicitud de apertura no garantiza ejecución visible en clúster** en todas las ROM de Jetour.
- Un ACK de red verifica recepción/procesamiento autenticado, no que el tablero haya dibujado Waze.
- Modo SOLO CLÚSTER no debe abrir Waze en Xiaomi cuando hay éxito auténtico; modo CLÚSTER + XIAOMI sí puede abrir ambos.
- La navegación y el audio pertenecen a rutas funcionales diferentes: evitar que un cambio de controles del vehículo altere el bridge.

## Ciclo de vida

- ReceiverService: Foreground Service, notificación persistente y START_STICKY, con opción de iniciar al arrancar mediante BootReceiver.
- Arranque automático del **receptor** solo si el usuario lo habilitó. Abrir una app elegida al encender es otra opción explícita.
- El firmware Android 11 puede restringir apertura Waze desde segundo plano; permiso Mostrar sobre otras apps no supone acceso OEM ni garantiza lanzamiento.
- Receiver no enciende Wi-Fi por la fuerza. Cambios de IP/hotspot pueden exigir corregir IP de la T1 en Sender.
- Ninguna operación de ventanillas, AVAS, detector CODE ni lectura masiva debe ejecutarse con BOOT_COMPLETED ni en segundo plano por decisión del diseño.

## Controles OEM integrados en Receiver 0.2.10

Origen de la investigación: APIs Binder locales usadas por la app T1 de laboratorio y referencias técnicas verificadas en código. Servicio conocido com.autolink.app.vehicleservice.VehicleService; descriptor com.autolink.app.vehicleservice.IVehicleControl. Lectura int transaction 5, lectura float transaction 6, setInt transaction 15 (solo IDs permitidos). **Las transacciones son detalles de interoperabilidad, no una API pública garantizada.**

- Ventanillas: **2004 LF, 2005 RF, 2006 LR, 2007 RR**. Los comandos acotados implementados son **1 cerrar** y **3 ventilar a la banda del primer cuarto**. La lectura del estado puede ser **1 cerrada, 2 abierta, 3 primer cuarto, 4 banda hacia 3/4, 0 intervalo**; son bandas reportadas, no medición continua de 0 a 100%. No emitir comando 0.
- Para ventilación en grupo, se inspeccionan las cuatro ventanas antes de actuar; si alguna está más abierta de lo permitido se cancela. Comandos secuenciales y verificación OEM de lectura después de cada paso. El botón cancelar impide futuras órdenes, **no detiene un motor que ya se mueve**.
- Controles individuales por ID incorporados en 0.2.10; también se usa la política de prelectura y timeout.
- AVAS/alarma peatonal **2074, 1 encendido / 0 apagado**, alternancia únicamente manual con confirmación y enfriamiento. **Debe permanecer habilitada al circular**; no desactivación en arranque.
- Estos permisos de lectura/escritura **pueden estar limitados por UID/firma/permisos OEM**, aunque los mismos IDs funcionen desde otra APK: la aplicación Receiver es un paquete nuevo y requiere prueba física. Tampoco valida electrónicamente posición P o velocidad; el operador debe realizar la prueba en P y sin movimiento.

Las lecturas de energía, lote y detector diferencial se detallan en [tablero y telemetría](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md). En caso de fallo Binder: mostrar SIN LECTURA y registrar la causa, no sustituir por cifras inventadas.

## Actualizaciones y release

Releases firmadas Sender/Receiver en este repo público; manifiestos HTTPS latest/download/sender.json y receiver.json con paquete, versionCode, SHA-256 y URL. Cada app comprueba identidad del APK, hash y certificado instalado antes de solicitar el instalador nativo de Android. El usuario confirma: **no** instalar silenciosamente ni desactivar Play Protect. Las firmas son distintas pero permanentes por app; preservarlas para actualizaciones en sitio.

En 0.2.10 pasaron compilación Android y tests automáticos del puente; no equivale a prueba de firmware. Release: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.10.

## Diseño objetivo después de 0.2.10 (NO implementado todavía)

Una única interfaz coherente en la T1, que comience por mapas/puente, seguida por **NAVEGACIÓN / MÚSICA / VEHÍCULO / TABLERO (combustible y energía) / AJUSTES**. El diseño de pestañas es intención de producto, **no** algo ya completado en la APK. El panel del vehículo debería leer valores como funciones separadas de los mandos (por seguridad) y actualizarse sin robar foco a Waze.

- Capa de lecturas: fuentes con sello temporal, unidad, estado, calidad, identificador y firmware.
- Capa de calibración: conserva valores crudos y parámetros versionados.
- Capa de vistas: tablero sin cálculos ocultos ni confusión entre directos/estimados.
- Capa de navegación: independiente de lectura OEM y de fallos de la UI de telemetría.
- Capa de viajes: sigue en DriveLab estable y se integra por exportación/contrato documentado cuando corresponda, sin compartir SQLite privado por defecto.

No reclamar rol HOME ni reemplazar Android Auto/T1Launcher hasta disponer de retorno seguro, navegación, apps y controles verificados. Tampoco confundir Android Auto completo (proyección/protocolo multimedia) con tener una app Android que abre Waze.

## Spotify: un problema independiente

v0.2.7 probó un listener de notificaciones y reconexión Bluetooth; v0.2.8 **lo retiró** por permisos y Play Protect. **v0.2.10 no automatiza Spotify.** Síntomas reportados en T1: reproducción inicia sola tras encender y luego se corta; desde Xiaomi o T1 empieza y se corta; tablero muestra NOT PROVIDED; aun con Bluetooth del Xiaomi apagado, Spotify puede seguir mostrando Xiaomi como dispositivo y el volante queda en RADIO. Posibles ejes para aislar: Spotify Connect por red, Bluetooth/AVRCP, fuente multimedia OEM, sesión/metadatos y control del volante. Ninguna causa está probada. No atribuirlo a DriveLab sin prueba; no añadir más automatización hasta obtener el origen.

## Referencias a código

- receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/ReceiverActivity.java
- receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/ReceiverVehicleActivity.java
- receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/ReceiverService.java
- receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/VehicleWindowGroup.java
- receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/PedestrianAlertControl.java
- receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/VehicleReadOnly.java
- common/src/main/java/ar/com/rango5gps/drivelabauto/common/BridgeCrypto.java
- common/src/main/java/ar/com/rango5gps/drivelabauto/common/MapLinks.java
