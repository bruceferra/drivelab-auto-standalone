# Handoff entre chats: Jetour T1 consumo ⇄ DriveLab Auto

**Fecha:** 2026-11-10. **Este documento es el puente de contexto cuando se cambia de solapa.**

## Estado que cualquier nueva conversación debe asumir

El objetivo ya acordado es consolidar gradualmente:
- **DriveLab Auto Sender 0.2.10 en Xiaomi**: envíos de destinos/Maps/Uber y vinculación por LAN.
- **DriveLab Auto Receiver 0.2.10 en Jetour T1**: navegación a Waze/clúster, arranque opcional, controles manuales de ventanillas/AVAS, lecturas OEM y explorador CODE.
- **Módulo TABLERO T1 futuro en Receiver**: combustible real/estimado, autonomía, SOC y energía a partir del análisis de consumo aún en progreso.
- **DriveLab GPS estable**: conservar registro de viajes, kilómetros, mapas de recorridos e historial. **No modificar ni borrar datos de tanque en curso.**
- **DriveLab T1 separado y puente antiguo**: mantener como diagnóstico/respaldo hasta que Receiver 0.2.10 pase pruebas físicas.

Código/release principal: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.10

## Regla de combustible que no se puede perder

**El cálculo viejo de nafta del launcher fue descartado; no usarlo, aunque aparezca en capturas o CSV históricos.** Solo el nuevo de “nafta usada / medida en tanque” y las lecturas físicas de surtidor, vehículo, CSV y flotante, contrastadas. La T1 tiene tanque nominal 70 L, ~67 L cargados desde corte térmico, muestra 0% con varios litros restantes, térmico corta cerca de 5,3 L OEM, sensor variable 56 Ω lleno / 148 Ω a 41,3 L / 288 Ω a 7,4 L; un segundo canal ~500 Ω parece fijo (función desconocida). Son observaciones, no curva final.

En una muestra: 21,6 L de tablero, 22,7 L de CSV, ~231 Ω; el cálculo NUEVO informó ~40,93 L usados. Capturas de distinto momento no deben mezclarse. El otro tanque reportó ~950 km con ~67 L y sin enchufar. El híbrido usa parte de energía térmica para tracción y parte para generación; no imputar litros solo a kilómetros HEV ni sumar energía regenerada dos veces.

La guía detallada para implementar el tablero está en [Tablero consumo y telemetría T1](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md).

## Qué aportar al expediente desde la solapa de consumo

Registrar cada ensayo nuevo como:
1. Fecha/hora y condición (EV/HEV, conectado o no, carga/repostaje, nivel, km).
2. Fuente exacta: menú OEM, CI186, CSV, flotante Ω, cálculo nuevo launcher, surtidor.
3. Dato crudo, unidad, rango/método y captura/archivo.
4. Contraste con la lectura anterior del **mismo ciclo**; cambios y desfases.
5. CONFIRMADO / OBSERVADO / HIPÓTESIS / PENDIENTE; si corrige un valor anterior, registrar motivo.
6. Qué campo nuevo necesita el TABLERO y si se resuelve por lectura OEM, fuente local exportada o derivación matemática.

El inventario de los códigos (1042,1062,1064, CI186, 2004-2008, 2074, etc.) y sus estados se encuentra en el documento de telemetría. No escribir códigos experimentales.

## Qué NO hacer en otro chat sin revisión de este estado

- No modificar Sender, puente cifrado ni protocolo 48391/48392 para una prueba de combustible.
- No habilitar automáticamente AVAS OFF, ventanas o espejo de retroceso al iniciar.
- No reintroducir automatización Spotify por Bluetooth/NotificationListener (el problema NOT PROVIDED está sin resolver).
- No declarar que la app ya reemplaza Android Auto o T1Launcher. Todavía no es HOME ni implementa todos los servicios del sistema.
- No borrar DriveLab de viajes, Uber Beta, DriveLab T1 o archivos CSV antes de una migración verificada.
- No afirmar compatibilidad OEM del Receiver v0.2.10 hasta una prueba real.

## Plan de acción conjunto

1. Verificar navegación, autoinicio, ventanas, AVAS y lectores en la 0.2.10.
2. Refinar algoritmo de tanque con surtidor y pares de flotante; fijar contrato de datos sin descartar observaciones originales.
3. Crear TABLERO T1 como módulo desacoplado dentro de Receiver; validar consumos antes de mostrar autonomía.
4. Reorganizar UI, Spotify y quizá espejos; más adelante evaluar launcher HOME y clúster.
5. Solo al final simplificar DriveLab tradicional para dejar viajes/registro y retirar puentes/componentes redundantes.

**Documentos canónicos:** [Índice maestro](INDICE_MAESTRO.md), [Auditoría](AUDITORIA_APPS_Y_CONSOLIDACION.md), [Arquitectura](ARQUITECTURA_SENDER_RECEIVER.md), [Tablero](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md), [Pruebas](PRUEBAS_PENDIENTES_Y_DEPURACION.md).
