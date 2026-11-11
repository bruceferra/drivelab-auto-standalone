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
- **Espejos/reversa:** la inspección estática del APK T1N134 halló `EspejoRService`, el getter **1033** de marcha, el setter por pulsos **2069** y velocidades **2148/1089**. **NO** están validados en la T1; Receiver v0.2.11 incluye únicamente una prueba **manual** y acotada del setter 2069 (no auto-reversa). El algoritmo de tercero guarda una posición estimada local en lugar de leer ángulo físico. Investigar especialmente restauración y cancelación; no migrar setters ni ejecutarlos automáticamente. Véase [extracción T1N134](EXTRACCION_T1LAUNCHER_T1N134_2026-11-10.md).
- **Lectura candidata de acumulados PHEV:** investigar en modo lectura y en P los códigos HAL **559964335** (`PHEV SUM FUEL`) y **559964336** (`PHEV SUM CHRG EGY`). Unidades, acceso y relación con litros/energía siguen **PENDIENTES**.
- Reclinado/asientos continúa sin códigos verificados. Techo 2008 y señales desconocidas quedan en modo observación.
- Techo 2008 y señales desconocidas quedan en modo de observación.
- UI completa tipo launcher, navegación multimedia por volante y sustitución de Android Auto requieren diseño/validación adicionales.

## Receiver 0.2.11 · OTA de espejos de ensayo (10/11/2026)

- **Build/certificado/publicación:** [Action 42819862706](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42819862706) **PASÓ**; el proceso verificó que el certificado de Receiver 0.2.11 coincide con 0.2.10. [Release v0.2.11 (latest)](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.11) incorpora `receiver.json` y conserva `sender.json` 0.2.10 sin cambios, contrastados por digest SHA-256.
- **Método de actualización:** pulsar **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA** en la propia app. Debe detectar 0.2.11, descargar, validar hash/certificado y pedir autorización de Android. **NO entregar ni instalar una APK manual, ni cambiar Sender**. El éxito del proceso de publicación no implica que el sistema Android ya haya completado la instalación en T1.
- **Prueba de espejo:** **NO PROBADO EN VEHÍCULO.** Requiere marcha P 1033=1 y velocidad OEM ~0; confirma cada operación manual; un pulso ID 2069=1/2 de 120 ms y liberación candidata 2069=0. Sentido, lado y parada física están **PENDIENTES**. Si los getters no responden, el piloto cancela el movimiento. Si se mueve anormalmente, usar controles OEM y suspender.
- **Fuera de alcance:** no HOLD continuo, no activación automática al engranar R, no memorias ni uso al arrancar; no confiar en topes mecánicos como parada. Sender, mapas, Waze, servicios del puente, controles de ventanas/AVAS y viajes quedan sin modificaciones funcionales.
- [Guía y criterios de aceptación](RECEIVER_ESPEJOS_QA_0_2_11.md).

## Prueba física pendiente: Receiver 0.2.12 (OTA, 10/11)

- [Release v0.2.12](https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.2.12) está publicada como `latest`; workflow de firma/actualización [42824306707](https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/42824306707) **PASÓ**. `sender.json` conserva el mismo hash que la release anterior, Sender sigue 0.2.10.
- **No probado en el vehículo**: movimiento sostenido de 500 ms en espejo derecho 2069; comandos experimentales izquierdo 2068; lectura HAL `MIRROR_Z_POS` y propiedades de posición vendor. El derecho **sí** estaba validado con pulsos de 120 ms, `1` arriba/`2` abajo.
- Actualizar exclusivamente por **ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA** de la propia aplicación. No instalar APK separada.
- [Plan detallado para registrar valores, permisos y sentidos](RECEIVER_ESPEJOS_0_2_12_CALIBRACION.md). Sin auto-tilt de R ni regreso D hasta validar posición, parada y lectura de marcha.

## Cuándo retirar los componentes viejos

**No ahora.** Antes de eliminar DriveLab T1 de laboratorio o puente original, deben cumplirse: todas las funciones que realmente se usan están presentes y probadas en Receiver, mapa en clúster robusto, recuperabilidad de APK/firma, respaldos de CSV e historial, y ningún viaje de tanque activo afectado. Solo después se puede desinstalar por paquete e identificar servicios conflictivos. No borrar en bloque historiales personales, canales ni firmas.

Antes de reducir DriveLab estable a viajes: definir catálogo de funcionalidades usadas, asegurar SQLite y exportaciones, probar actualización limpia y migración reversible. Club, Uber Beta, telemetría y rutas pueden tener datos/funciones que no deban eliminarse sin criterio explícito.

## Cómo reportar cada prueba

Registrar: fecha/hora, versión app, equipo/firmware, acción, estado anterior, resultado real, lectura posterior, código y tipo, archivo log/CSV, y **criterio PASÓ / FALLÓ / NO PROBADO**. No confundir “código compila”, “Binder acepta transacción”, “se mueve físicamente” y “valor útil para tablero”. No compartir IP, códigos de vinculación, VIN ni ubicación exacta en GitHub público.

## Reparación de interfaz de vinculación T1/Xiaomi en Receiver 0.2.29

Usuario reportó el 11/11/2026 que DriveLab Auto quedó sin conexión entre Xiaomi y la T1 y que al pulsar **VINCULAR XIAOMI · 6 DÍGITOS** parecía abrirse y cerrarse una pantalla. Sin logs ADB del equipo no es posible afirmar que fuera un crash.

**Inspección del código anterior:** el botón de T1 **no tenía un diálogo modal**: enviaba `ReceiverService.ACTION_PAIR` para abrir una ventana de 120 s en el puerto TCP 48392. Solo cuando Sender desde Xiaomi iniciaba `beginPairing` por esa IP se creaba el código SAS de seis dígitos y se mostraba en la pantalla base. El `Runnable` cada 750 ms verificaba `pair_status` y hacía un `render()` **completo**, recreando la jerarquía de vistas y dando la impresión de que un panel desaparecía. El enlace ECDH guardado en `secret` en Receiver **no se borra** por iniciar `ACTION_PAIR` y una actualización de APK con misma firma/paquete conserva prefs.

**Corrección Receiver 0.2.29:** se reemplazó el redibujado completo cada 750 ms por actualizaciones independientes de `TextView`: estado de vinculación, estado del listener 48392, IP T1 actual, contador de 120 s, presencia/ausencia **sin revelar** la clave instalada, y código SAS cuando llega del Xiaomi. `ReceiverService.listenPair` anota `pair_port_status` al abrir, cerrar o fallar el bind. Se muestran los tres pasos de confirmación T1/Xiaomi. No cambia la criptografía, el protocolo, los puertos, ni el orden de confirmación; se conservan TRIP, mapas y módulos OEM.

**Procedimiento de recuperación:** T1 y Xiaomi en misma LAN; en Receiver tocar INICIAR RECEPTOR, anotar la IP y verificar `PUERTO 48392 ABIERTO`; luego VINCULAR XIAOMI, y dentro de 120 s en Sender introducir la IP actual y tocar MOSTRAR CÓDIGO, comparar SAS, autorizar primero en T1 y confirmar en Xiaomi. Si figura ERROR PUERTO 48392 o no aparece código, capturar texto de estado antes de borrar datos; no desinstalar. Si el Xiaomi conserva IP vieja, reemplazarla por IP nueva.

## Receiver 0.2.30 — diagnóstico OEM persistente al salir de DriveLab

**Objetivo del usuario:** la T1 debe permitir usar libremente la pantalla de navegación o menú OEM mientras los servicios realmente automáticos siguen funcionando; reducir el exceso de botones visibles y entender `1040`, `1043`, SOC protegido, regeneración, limpiaparabrisas, PDC y la ausencia de eventos de consumo de 500 m.

**Implementado:** pestaña nueva VEHÍCULO → **DIAGNÓSTICO** con botones `INICIAR DIAGNÓSTICO OEM · 3 MIN EN SEGUNDO PLANO`, `DETENER`, `EXPORTAR CSV DE MODOS / SENSORES` y `EXPORTAR CSV DE EVENTOS DE CONSUMO`. Se crea un foreground service de **180 s**, **únicamente después de un toque del usuario**, que consulta a intervalos de ~3 s y graba valores OEM crudos de 1033, 2148, 1040, 1041 int/float, 1042, 1043, 1045, 1046, 2125, 2126, 2049, 2107, 2074 y diez propiedades HAL PDC de frente/atrás. También mantiene su propia escucha de los cuatro callbacks OEM de promedio 500 m/50 km con registro y desregistro acotado. Al ir a Waze/Maps o menú Jetour no termina la grabación. El estado se conserva en SharedPreferences, con contador y muestras, y muestra errores. Un lector HAL no disponible deja campo vacío y no cancela los otros módulos.

**Datos:** genera `phev_modes_YYYYMMDD_HHmmss.csv` y `oem_callbacks_YYYYMMDD_HHmmss.csv` dentro de los archivos privados del Receiver, exportables por MediaStore a `Descargas/DriveLabAuto` **después de parar**. Ninguna señal de 500 m se acumula como litros hasta comprobar unidades/ventana del contador. Ni se equiparan datos brutos PDC a centímetros.

**Correspondencias T1 argentina confirmadas físicamente:** 1043=1 EV, 2 HEV, 3 EV MAX azul; 1040=2 Smart ahorro inteligente, 1040=4 Forzado / Charge. 1040=3 queda por identificar. Lectura 1041 SOC protegido y 1046 regeneración (1 bajo/2 medio/3 alto). Solo diagnóstico, no escritura a la ECU, sensores, limpia o aviso de peatones.

**Simplificación de pantalla:** en inicio, botones de espejos relativos y ventilación secundaria bajo botón **MOSTRAR CONTROLES AVANZADOS**; inicio/reinicio de receptor, vinculación, actualizaciones y ajustes tras el desplegable **CONEXIÓN XIAOMI / AJUSTES / ACTUALIZAR**. La pantalla informa estado de conexión al margen del desplegable. El servicio LAN, espejos AUTO, recirculación opt-in y TRIP activo siguen regidos por sus propios servicios, no por mantener la actividad visible. Los **botones de accionamiento manual** requieren abrir la UI: no son comandos remotos ni una automatización oculta.

**Pendiente (NO implementado):** flujo QR de vinculación para sugerir IP del Receiver al Sender. Mantener comprobación SAS de 6 dígitos y consentimiento en ambos equipos para evitar vinculación a un tercero de la misma red; un QR puede simplificar introducir IP pero no debería suprimir la confirmación de clave. La desactivación persistente/automática de AVAS 2074 no se implementó, ni se relajaron restricciones de seguridad en marcha. Los modos PHEV no se alteran por GPS/SOC hasta comprobar permisos y condiciones de seguridad.

**Cómo probar:** con el auto detenido, abrir DIAGNÓSTICO e iniciar la prueba, pasar a pantalla nativa y cambiar manualmente Smart / Forzado o EV/HEV desde controles Jetour, sin manejar por esta razón. Al final, regresar, revisar estado y exportar ambos CSV. Si se desea comprobar eventos de 500 m, activar un nuevo registro antes de un trayecto normal y no manipular la pantalla durante la marcha; volver a consultar cuando se estacione.

## Corrección preparada para próximo Receiver: RECUPERAR ESPEJOS vuelve a AUTO

**Pedido usuario 11/11/2026, seguimiento issue #35:** al usar `RECUPERAR ESPEJOS · 10 PASOS ARRIBA` no quiere tocar manualmente `ESPEJOS AUTO` después; **recuperación exitosa debe dejar AUTO activo**. Se ajustó `MirrorAutomationService` en rama `fix/mirror-recovery-auto-rearm-next`:

- `ACTION_RECOVER` pausa AUTO durante los 10 pulsos con vehículo **detenido y marcha OEM conocida**, y completa los movimientos por lado con neutralización del comando `0` al finalizar cada uno.
- Solo si ambos espejos reciben los 10 comandos completos, reactiva `AUTO=true`, deja mensaje `AUTO REACTIVADO` y **crea una FSM nueva**; evita arrastrar estado `TILTED` antiguo, por lo que no vuelve a bajar hasta una **nueva** transición no-R → R comprobada.
- Si el OEM devuelve error, la secuencia se interrumpe o el vehículo se mueve, AUTO permanece desactivado; **no** se presume conocer el ángulo físico de los espejos.
- Los botones manuales de bajar/subir sí mantienen el comportamiento de desarmar AUTO.
- Tests unitarios agregados para completar ambos espejos, rechazar parcial y exigir transición fresca a R tras recuperar.
- **La corrección no resuelve todavía el bug principal de issue #35** (R baja correctamente, D >10 km/h no restaura): revisar selección de velocidad 2148/1089 y estados del servicio con evidencias de campo.
- Mantener 0.2.30 publicada durante pruebas del usuario: esta rama debe incorporarse con la siguiente versión, no publicar sin verificar y combinar con la causa del no-retorno.
