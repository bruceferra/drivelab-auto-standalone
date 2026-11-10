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
3. [Tablero de combustible, consumo y telemetría](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md): datos empíricos del T1 PHEV, códigos y política del cálculo de nafta.
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

## Próxima integración

El módulo hoy llamado **ENERGÍA / CODE** en Receiver crecerá hasta **TABLERO T1** con litros reales estimados, autonomía contrastada, SOC, corriente, energía y consumo. Su modelo deberá almacenar observaciones originales y cálculos reproducibles por separado. El tratamiento del consumo continúa en el documento de tablero; no inventar señales ni convertir mediciones de otro proyecto en valores OEM verificados.

**Política de documentación:** cada hallazgo nuevo debe agregar fecha, dispositivo/versión, fuente (surtidor, menú OEM, CSV, Binder, captura, código), valor/unidad y clasificación: CONFIRMADO / OBSERVADO / HIPÓTESIS / PENDIENTE. No subir CSV con rutas privadas, IP, VIN, claves, secretos ni datos de ubicación personal a este repositorio público.
