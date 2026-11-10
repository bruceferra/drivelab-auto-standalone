# Plan de pruebas, incidencias y depuración sin perder datos

**Fecha:** 2026-11-10. Este plan distingue compilación, integración y validación física. No ejecutar maniobras ni interaccionar con pantallas mientras se conduce.

## Matriz por capa

| Capa | Qué existe | Qué está comprobado | Qué falta |
|---|---|---|---|
| Código Sender/Receiver 0.2.10 | Dos APK firmadas publicadas | Build y verificaciones GitHub Actions v0.2.10 exitosas | Instalación y pruebas en ambas plataformas del usuario |
| Protocolo 48391/48392 | ECDH, AES-GCM, SAS 6 cifras, antirrepetición | Pruebas automatizadas de protocolo | Hotspot/IP real, pairing y casos de red intermitente |
| Waze Xiaomi → T1 | Enlace compartido/pegado, solo cluster, modo doble | Flujo original probado en versiones antiguas; código nuevo preparado | Verificar flujo **actual v0.2.10** físicamente y con servicio oculto |
| Compartir Google Maps en T1 | ShareToClusterActivity v0.2.9+ | Código/QA | Selección Android, búsqueda por nombre, apertura clúster y confirmación visual |
| Autoarranque | Receiver foreground y BOOT_COMPLETED opt-in | Código/QA | ROM de T1, segunda sesión, IP, reconexión, qué app se abre |
| Ventanillas | 2004–2007 cierre/vent 1/4 grupo e individuales | Código reutilizado de T1, QA compila | **Permiso/UID y movimiento físico desde Receiver 0.2.10**, OEM no garantizado |
| AVAS 2074 | Alternancia manual con cooldown | Código/QA compila | Leer/verificar físicamente estado y dejar ON antes de circular |
| CI186/SOC/CODE | Lectura puntal, detector y CSV | CI186 validado anteriormente en DriveLab T1 vs menú OEM; QA nuevo | Verificar desde paquete nuevo y ampliar calibraciones |
| Consumo real / tablero | Reglas de datos y maquetas | Surtidor y muestras reportadas | Algoritmo calibrado, persistencia, gráficos, confiabilidad |
| Música | Reproducción Spotify externa y fuente OEM | Usuario reproduce audio, reporta cortes y NOT PROVIDED | Aislar Bluetooth/Connect/AVRCP; no hay automatización DriveLab |
| DriveLab estable | GPS/viajes/historial | Datos y usos reales reportados | Inventario de datos y respaldo antes de depurar funcionalidad |

## Secuencia de aceptación de v0.2.10

1. **Respaldo/registro previo:** anotar versión instalada, paquete, certificado y estado del puente; mantener APK v0.2.9 descargable. Respaldar viajes/CSV por su procedimiento normal, sin alterar sesiones activas.
2. **Actualizar Sender y Receiver EN SU MISMO PAQUETE** por su botón interno o release firmada, sin desinstalar. Verificar que emparejamiento y preferencias persisten.
3. **Puente:** en P, probar vinculación si hace falta, prueba a La Plata, destinos de Google Maps/WhatsApp, SOLO CLÚSTER y CLÚSTER + XIAOMI, receptor oculto, sin red, IP cambiada, reboot de la T1. Registrar no solo ACK sino imagen de Waze en clúster y destino real.
4. **Maps local:** en Google Maps T1 usar Compartir → DriveLab · Navegar en clúster. Comprobar coordenadas vs lugar por nombre, y que no se inventa un destino si el enlace no es resoluble.
5. **Ventanillas (en P, despejadas):** lectura inicial/diagnóstico; una ventana a la vez; cerrar y ventilar desde estado cerrado; observar físicamente movimiento y lectura final; luego probar grupos. Si falla permiso OEM, abortar secuencia; no forzar setters/privilegios ni repetir sin diagnóstico. **Cancelar impide los siguientes mandos, no invierte el motor en marcha.**
6. **AVAS:** el toggler puede apagar la alerta de seguridad; no se requiere apagarla para demostrar mapas. Si se prueba, hacerlo solo detenido, confirmar estado real y **finalizar ON**. Nunca poner OFF como valor de arranque.
7. **Energía y CODE:** leer litros CI186 y comparar con menú oculto en mismo minuto; SOC 1042/1062 y corriente 1064 con condición EV/HEV; detector 45 s y cambios aislados; tandas de 25 y CSV a Descargas/DriveLabAuto. Identificar errores Binder sin reemplazarlos por ceros falsos.
8. **Reinicio:** comprobar autoinicio del receptor y del Waze opcional con la camioneta detenida, sin órdenes de vidrios/alarma, sin cambios de Spotify introducidos por la app.
9. **Regresión de actualizador:** verificar repositorio/releases y manifiestos SHA-256; no instalar debug sobre release ni borrar app para actualizar.

## Incidencia abierta: Spotify / NOT PROVIDED

**Síntomas observados en la conversación:** tras apagar/encender empezó a sonar Spotify, luego alguna sincronización aparentemente interrumpió audio; desde Xiaomi o T1 el play comienza y se corta; aparece NOT PROVIDED en sistema; con Bluetooth Xiaomi apagado, Spotify podía seguir mostrando Xiaomi y los mandos del volante quedaban en RADIO. Estos hechos **no demuestran** que DriveLab Auto sea culpable ni que Connect por Wi-Fi sea seguro el origen.

**Plan de aislamiento con vehículo detenido:**
- Registrar fuente OEM seleccionada (Radio/Bluetooth/Android Auto/u otra), icono de salida de Spotify (este dispositivo o Xiaomi), Wi-Fi/hotspot, Bluetooth y versión de Spotify.
- Probar reproducción en T1 con Xiaomi fuera de la sesión Connect; después probar reproducción en Xiaomi con BT activado; anotar cuál inicia y cuál corta, y qué muestra el volante.
- Comparar con Receiver detenido temporalmente **solo como prueba no destructiva**; comprobar si la falla ocurre igual. Restaurar el receptor después.
- Si ocurre solo con BT/AVRCP conectado, revisar propiedad de sesión, fuente y metadatos; si pasa también sin BT, revisar Spotify Connect o foco de audio del sistema. No inferir causa antes del resultado.
- **No reintroducir** NotificationListenerService ni receptores de BT experimentales de 0.2.7; fueron retirados en 0.2.8. Evitar sugerir desactivar Play Protect.

## Incidencias y pendientes de producto

- El enlace de Maps por nombre puede abrir el primer resultado en Waze; usuario debe verificar el pin.
- La ROM T1 puede bloquear actividad desde segundo plano y Binder de lectura/escritura según UID.
- CI186 puede contener valor OEM filtrado; no sustituir cálculo de surtidor.
- **Espejos/reversa:** la inspección estática del APK T1N134 halló `EspejoRService`, el getter **1033** de marcha, el setter por pulsos **2069** y velocidades **2148/1089**. **No** están validados en la T1 ni habilitados en Receiver; el algoritmo de tercero guarda una posición estimada local en lugar de leer ángulo físico. Investigar especialmente restauración y cancelación; no migrar setters ni ejecutarlos automáticamente. Véase [extracción T1N134](EXTRACCION_T1LAUNCHER_T1N134_2026-11-10.md).
- **Lectura candidata de acumulados PHEV:** investigar en modo lectura y en P los códigos HAL **559964335** (`PHEV SUM FUEL`) y **559964336** (`PHEV SUM CHRG EGY`). Unidades, acceso y relación con litros/energía siguen **PENDIENTES**.
- Reclinado/asientos continúa sin códigos verificados. Techo 2008 y señales desconocidas quedan en modo observación.
- Techo 2008 y señales desconocidas quedan en modo de observación.
- UI completa tipo launcher, navegación multimedia por volante y sustitución de Android Auto requieren diseño/validación adicionales.

## Receptor experimental 0.2.11-MIRROR-QA (10/11/2026)

- **Compilación firmada:** GitHub Actions [run 42817695751](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42817695751) **PASÓ**. Archivo de prueba firmado y SHA-256 en [prerelease](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11-mirror-qa). Sender v0.2.10 y Receiver estable v0.2.10 no se cambiaron.
- **Prueba en vehículo:** **NO PROBADO**. Requiere P, velocidad OEM legible ~0 km/h y confirmación manual. El código mueve el espejo por un solo pulso de 120 ms (ID 2069 con 1 o 2) y solicita liberar con 0. Dirección y lado **PENDIENTES**. Puede no moverse si las lecturas OEM son inaccesibles o no coinciden.
- **Prohibiciones:** no testear en circulación, no confiar en topes mecánicos como parada, no activar movimiento automático en reversa, no reemplazar la 0.2.10 estable en el canal de actualizaciones hasta validación. Si falla STOP o hay movimiento inesperado, detener y usar los controles de fábrica; no seguir circulando hasta verificar.
- [Instrucciones detalladas y criterios PASS/FAIL](https://github.com/bruceferra/drivelab-auto-standalone/blob/feature/receiver-espejos-qa-20261110/docs/RECEIVER_ESPEJOS_QA_0_2_11.md).

## Cuándo retirar los componentes viejos

**No ahora.** Antes de eliminar DriveLab T1 de laboratorio o puente original, deben cumplirse: todas las funciones que realmente se usan están presentes y probadas en Receiver, mapa en clúster robusto, recuperabilidad de APK/firma, respaldos de CSV e historial, y ningún viaje de tanque activo afectado. Solo después se puede desinstalar por paquete e identificar servicios conflictivos. No borrar en bloque historiales personales, canales ni firmas.

Antes de reducir DriveLab estable a viajes: definir catálogo de funcionalidades usadas, asegurar SQLite y exportaciones, probar actualización limpia y migración reversible. Club, Uber Beta, telemetría y rutas pueden tener datos/funciones que no deban eliminarse sin criterio explícito.

## Cómo reportar cada prueba

Registrar: fecha/hora, versión app, equipo/firmware, acción, estado anterior, resultado real, lectura posterior, código y tipo, archivo log/CSV, y **criterio PASÓ / FALLÓ / NO PROBADO**. No confundir “código compila”, “Binder acepta transacción”, “se mueve físicamente” y “valor útil para tablero”. No compartir IP, códigos de vinculación, VIN ni ubicación exacta en GitHub público.
