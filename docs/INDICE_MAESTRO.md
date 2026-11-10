# DriveLab Auto + Jetour T1 — índice maestro de continuidad

**Actualizado:** 2026-11-10 (Argentina). **Propósito:** referencia común para todas las conversaciones/solapas; no reemplaza las pruebas físicas ni los registros originales.

## Decisión de arquitectura vigente

**Unificar gradualmente en DriveLab Auto Receiver (Jetour T1) la navegación, el control manual del vehículo, el diagnóstico OEM y un futuro tablero de combustible/energía.** Mantener DriveLab Auto Sender en Xiaomi solo para compartir destinos y administrar la vinculación. Conservar DriveLab estable para registrar viajes, GPS e historial.

La primera versión integrada es **DriveLab Auto Sender + Receiver v0.2.10**, publicada y firmada el 2026-11-09:
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

## Prerelease experimental de espejos (10/11/2026)

**Receiver v0.2.11-MIRROR-QA** fue compilado y firmado correctamente desde la rama `feature/receiver-espejos-qa-20261110`, sin modificar Sender, el puente ni la release estable v0.2.10. **NO VALIDADO físicamente en la T1; NO es actualización estable.** Descarga/registro de compilación: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11-mirror-qa . Es el mismo package de Receiver con versionCode 12; se construye utilizando la clave de firma Receiver configurada en Actions. Se entrega únicamente como instalación manual supervisada.

La función muestra `PULSO 1`, `PULSO 2` (ID OEM 2069, dirección/lado **sin validar**) y liberación `2069=0`, con consentimiento por operación, ~120 ms de pulso, control previo de `1033=1` (P candidata) y velocidad OEM próxima a cero. **No hay HOLD continuo, auto-tilt al engranar R, memoria ni actuación durante el arranque.** Ninguna garantía de parada física ante fallo de Android/OEM; si ocurre movimiento anormal, detener ensayo con los mandos de fábrica y no circular. [Instrucciones de QA en rama de prueba](https://github.com/bruceferra/drivelab-auto-standalone/blob/feature/receiver-espejos-qa-20261110/docs/RECEIVER_ESPEJOS_QA_0_2_11.md). Conservamos la versión 0.2.10 estable en main; no se fusionó el prototipo hasta validar en vehículo.

## Próxima integración

El módulo hoy llamado **ENERGÍA / CODE** en Receiver crecerá hasta **TABLERO T1** con litros reales estimados, autonomía contrastada, SOC, corriente, energía y consumo. Su modelo deberá almacenar observaciones originales y cálculos reproducibles por separado. El tratamiento del consumo continúa en el documento de tablero; no inventar señales ni convertir mediciones de otro proyecto en valores OEM verificados.

**Política de documentación:** cada hallazgo nuevo debe agregar fecha, dispositivo/versión, fuente (surtidor, menú OEM, CSV, Binder, captura, código), valor/unidad y clasificación: CONFIRMADO / OBSERVADO / HIPÓTESIS / PENDIENTE. No subir CSV con rutas privadas, IP, VIN, claves, secretos ni datos de ubicación personal a este repositorio público.
