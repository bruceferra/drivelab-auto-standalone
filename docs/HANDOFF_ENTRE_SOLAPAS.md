# Handoff entre chats: Jetour T1 consumo ⇄ DriveLab Auto

**Fecha:** 2026-11-10. **Este documento es el puente de contexto cuando se cambia de solapa.**

## Estado que cualquier nueva conversación debe asumir

El objetivo ya acordado es consolidar gradualmente:
- **DriveLab Auto Sender 0.2.10 en Xiaomi**: envíos de destinos/Maps/Uber y vinculación por LAN.
- **DriveLab Auto Receiver 0.2.11 OTA disponible (instalación real aún no confirmada) en Jetour T1**: mantiene Waze/clúster, arranque opcional, ventanas/AVAS, lecturas OEM y CODE; suma piloto **manual no validado** de espejos 2069 en P. 0.2.10 es versión anterior.
- **Módulo TABLERO T1 futuro en Receiver**: combustible real/estimado, autonomía, SOC y energía a partir del análisis de consumo aún en progreso.
- **DriveLab GPS estable**: conservar registro de viajes, kilómetros, mapas de recorridos e historial. **No modificar ni borrar datos de tanque en curso.**
- **DriveLab T1 separado y puente antiguo**: mantener como diagnóstico/respaldo hasta que Receiver 0.2.10 pase pruebas físicas.

Release de actualización Receiver: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11 (latest; Action 42819862706 PASÓ). Sender sigue 0.2.10, sin actualización. **Método de usuario: botón interno «ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA»**, que lee `releases/latest/download/receiver.json`, verifica hash y certificado y solicita confirmación de Android. No entregar APK aparte. El `sender.json` servido por latest es idéntico al de v0.2.10. Espejos sin prueba física.

## Regla de combustible que no se puede perder

**El cálculo viejo de nafta del launcher fue descartado; no usarlo, aunque aparezca en capturas o CSV históricos.** Solo el nuevo de “nafta usada / medida en tanque” y las lecturas físicas de surtidor, vehículo, CSV y flotante, contrastadas. La T1 tiene tanque nominal 70 L, ~67 L cargados desde corte térmico, muestra 0% con varios litros restantes, térmico corta cerca de 5,3 L OEM, sensor variable 56 Ω lleno / 148 Ω a 41,3 L / 288 Ω a 7,4 L; un segundo canal ~500 Ω parece fijo (función desconocida). Son observaciones, no curva final.

En una muestra: 21,6 L de tablero, 22,7 L de CSV, ~231 Ω; el cálculo NUEVO informó ~40,93 L usados. Capturas de distinto momento no deben mezclarse. El otro tanque reportó ~950 km con ~67 L y sin enchufar. El híbrido usa parte de energía térmica para tracción y parte para generación; no imputar litros solo a kilómetros HEV ni sumar energía regenerada dos veces.

La guía detallada está en [Tablero consumo y telemetría T1](TABLERO_CONSUMO_Y_TELEMETRIA_T1.md). **Actualización 10/11:** [auditoría de 13 CSV originales, 52.880 registros únicos y especificación de calibración](CALIBRACION_CSV_T1_2026-11-10.md), que además registra el último punto informado **868,5 km / 6,8 L OEM**, pendiente del CSV del día. El corte operativo definitivo y el segundo repostaje siguen sin confirmar.

**Corrección documentada:** el salto de nivel 67,0 → 63,4 L del 03/11 ocurrió **en movimiento**, entre 26,46 y 30,52 km/h, no parado. **CI186 / menú oculto / otra app comparten la misma fuente OEM**, no son tres mediciones independientes. La lectura de 5,3 L aparece primero con RPM positivas, así que la etiqueta de corte requiere confirmar el evento completo. **Nunca** convertir la diferencia entre odómetros distintos en una corrección automática ni promediar el cálculo viejo de T1Launcher.

## Actualización 10/11: APK original T1N134 recuperado

Se verificó el SHA-256 del APK recibido: `6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480`. La [inspección estática documentada](EXTRACCION_T1LAUNCHER_T1N134_2026-11-10.md) encontró que el módulo real de retrovisor de reversa es `espejor.EspejoRService`, **no** `espejo.EspejoService` (duplicación de pantalla). IDs de aplicación: **1033** marcha, **2069** mando por pulsos, **2148/1089** velocidad; falta probar significado/valores físicamente y no se implementaron nuevos setters. Para consumo, candidatos HAL **559964335 PHEV SUM FUEL** y **559964336 PHEV SUM CHRG EGY**, solo etiquetas+llamadas del APK, todavía **sin lectura ni unidad verificadas**. La lectura de litros `CiLink.litrosNafta()` coincide con CI186 y no es independiente.

**No modificar las funciones del Receiver v0.2.10 ni introducir control de espejos hasta verificar lectura, readback y restauración.** El cálculo viejo de T1Launcher permanece descartado.

## Progreso de espejo derecho y lectura real (10/11)

El propietario confirmó en Receiver 0.2.11: **derecho `2069=1` sube, `2069=2` baja**, con 4 pulsos de ida/vuelta. Pulsos de 120 ms son demasiado cortos y la nueva prueba requiere **500 ms por orden**. Left `2068` continúa como candidato sin prueba. Las posiciones guardadas por T1Launcher son estimaciones por pasos y **se descalibran** aun sin tocar mando original.

Se preparó un Receiver 0.2.12 con botón de lectura de propiedades **HAL** (`MIRROR_Z_POS`, área/rangos, vendor) usando reflexión Android Car en **solo lectura**, más controles manuales 500 ms **solo en P**, sin ir a topes, sin cambio automático R/D. La posibilidad de leer valores físicos **NO se deduce** de que el setter 2069 funcione. [Documento de QA y calibración](RECEIVER_ESPEJOS_0_2_12_CALIBRACION.md). Esta versión debe distribuirse **solo por el actualizador incorporado**; Sender/puente no cambia. No declarar auto-reversa, control de ángulos ni memoria D/R operativa hasta ensayos físicos.

**Nuevo canal OTA 0.2.12 verificado:** `releases/latest` es [v0.2.12](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.12); `receiver.json` ofrece Receiver `0.2.12/code13` y el workflow de firma/paquete [42824306707](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42824306707) **pasó**. Sender sigue en v0.2.10 y `sender.json` conservó el SHA-256 idéntico de la release previa. **Usuario actualiza dentro de Receiver**, nunca con APK aparte; no se confirmó todavía instalación de 0.2.12 en el auto. Guía: [espejos 500ms/HAL 0.2.12](RECEIVER_ESPEJOS_0_2_12_CALIBRACION.md).

## UX solicitada: actualización al abrir y scroll inmóvil (10/11/2026)

Sender y Receiver deben comprobar automáticamente su respectivo manifiesto `sender.json` / `receiver.json` al abrir y **ofrecer instalar** una versión nueva (sin descargar ni instalar sin autorización). El botón manual debe responder siempre `Esta es la última versión` cuando corresponda; los botones no deben llevar el scroll arriba. Se implementó en **0.2.13 code14 para ambos** y se documentó en [ACTUALIZADOR_Y_SCROLL_0_2_13_2026-11-10.md](ACTUALIZADOR_Y_SCROLL_0_2_13_2026-11-10.md). El propietario instaló Receiver 0.2.12 y observó P/velocidad cero; todas las propiedades HAL de posición consultadas no figuran en la lista accesible. No atribuirles ángulo ni final de carrera.

**Publicación 0.2.13:** [release latest con Sender + Receiver](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.13) y [workflow 42826440626 exitoso](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42826440626). Los dos manifiestos OTA apuntan a APK propias v0.2.13/code14 firmadas con el mismo certificado anterior; instalación real en auto/teléfono pendiente. Esta entrega agrega check al abrir, aviso explícito «Esta es la última versión» al consultar manualmente, scroll estable en ambas apps y panel T1; NO agrega lectura real de espejos. Ver [documento de actualización](ACTUALIZADOR_Y_SCROLL_0_2_13_2026-11-10.md).

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
