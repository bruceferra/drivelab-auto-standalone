# DriveLab Auto / DriveLab Boot — handoff recuperado (19-11-2026, Argentina)

## Fuente de verdad y versiones
- Repo independiente: https://github.com/bruceferra/drivelab-auto-standalone (rama main).
- Última release confirmada al iniciar recuperación: Receiver **v0.3.91, versionCode 192**: https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.3.91.
- Versión preparada en el código: **Receiver v0.3.92, versionCode 193** y **DriveLab Boot v0.1.0, versionCode 1**; publicación por workflow `.github/workflows/publish-drivelab-boot-010-receiver-0392.yml`.
- **RELEASE PUBLICADA Y VERIFICADA:** https://github.com/bruceferra/drivelab-auto-standalone/releases/tag/v0.3.92 (19/11/2026, ejecución de firma exitosa https://github.com/bruceferra/drivelab-auto-standalone/actions/runs/44602045870). Assets: `DriveLab-Auto-Receiver-v0.3.92.apk` (6.240.433 bytes), `DriveLab-Boot-v0.1.0.apk` (28.688 bytes), `boot.sha256`, `receiver.sha256`, `receiver.json`, `sender.json`.
- Se corrigió test de mosaico desactualizado: esperaba 2–3 paneles pese a que la implementación ya soportaba 1–3. Primera corrida 44601721790 falló por esa aserción; segunda corrida indicada arriba es la que debe considerarse para el release.
- **QA y release firmadas confirmadas en GitHub Actions**. Todavía no hay validación física de Boot en el T1. Conservar certificado original y Sender.

## Objetivo del usuario
Un único DriveLab Auto Receiver en Jetour T1 (Android 11) para Cockpit, navegación, datos OEM, viajes, carga y funciones de asistencia; DriveLab Boot independiente para un mosaico de **Display 0**. Preservar **T1Launcher HOME**, **Display 1/clúster**, Waze, servicios que ya funcionan, apps anteriores y garantía. Sin root ni comandos que cierren tareas OEM.

## DriveLab Boot — código ya creado
- Aplicación independiente en módulo `boot/`, paquete `ar.com.rango5gps.drivelabauto.boot`.
- Un panel al 100 %, dos paneles, o tres (principal izquierda/derecha, arriba/abajo, tres columnas); porcentajes con slider 1–99 y botón 50/50; elección de apps instaladas.
- Reutiliza, por IPC protegido con firma igual, el Receiver y sus tareas Freeform/ADB para Display 0; detecta geometría real, +101 px de barra en firmware observado; intenta recuperar tareas.
- Autoinicio 15 s después del arranque, **opt-in apagado por defecto**. Solo desactiva el autolanzamiento de interfaz Receiver si el usuario lo delega; Receiver foreground, clúster, carga, viajes y servicios OEM siguen activos.
- **Pendiente en T1**: instalar y comprobar apertura visible de mosaico tras retorno del Activity de configuración, persistencia tras apagar, ratio real de Spotify/Maps/Waze; algunas apps como YouTube pueden rechazar Freeform. No afirmar aceptación física por compilar.

## Pendientes inmediatos reportados por el usuario
1. **Viaje fantasma 18/11 07:43**: ID `20261118074300`, tarjeta sintética en SharedPreferences `T1RebuiltMorning`, no CSV físico. El código `T1TripDeletion.deleteSelected(slot=1,id)` ahora usa `removeReconstructedTrip` y verifica flag de baja. Falta verificar desaparición real tras instalar en la T1; desde GitHub no se puede borrar la base privada del coche. No volver a importar en forma automática.
2. **Viaje de 19/11**: tiene que aparecer como viaje normal; reporte de recorte desde 19/11 08:00 usa muestras OEM preexistentes; no atribuir kilómetros sin muestras y no fusionar con el sintético. Proteger grabador activo y **no borrar sin terminar** un viaje en curso/pausado.
3. **Cabecera**: fecha/hora compactas a la **izquierda** (ej. `19 nov` y `HH:mm`) y título `T1` pequeño/ocultable en Freeform, sin tapar botones del extremo derecho. Código `T1HeaderClockFormat`, `T1GlobalToolbar`; probar al 33 %, 50 %, 70 % y pantalla completa.
4. **Capturas**: gesture de tres dedos hacia abajo Display 0 mediante `T1ScreenshotGestureService` opcional habilitado por Accesibilidad. Guarda PNG privado; abre previsualización y **Android Compartir** al confirmar. Puede alterar el táctil y se debe probar estacionado. El endpoint autenticado de imágenes de `promanager.com.ar` no está verificado; **no subir automáticamente capturas ni inventar dirección**.
5. **Botones compartir/reportes**: `T1ReportShare` guarda CSV local y usa QR/`T1CloudDiagnostics`. **Backend verificado en esta recuperación**: Edge Function `drivelab-auto-diagnostics` activa (versión 5) en Supabase de ProManager; la consulta agregada del 19/11 mostró **1 dispositivo, 159 fragmentos CSV y 4945 lotes de muestras de viajes**; última subida registrada **2026-11-19 19:24:45 UTC (16:24 ART)**. Esto demuestra recepción de datos en el backend, **no** que todos los botones estén conectados ni que las capturas PNG se suban. El endpoint solo acepta patrones de CSV y lotes TRIP, **no imágenes**; `promanager.com.ar` no cuenta aquí con endpoint autenticado de PNG verificado. Mantener revisión y compartir explícito hasta diseñar uno.

## Registro energético sin inventar datos
- 18/11 histórico cerrado del informe aportado: **21,03 km**, **15,61 km EV**, **5,42 km ICE**, **6,23 kWh** usados y **1,59 kWh** regenerados. Un parcial adicional 2,85 km se superponía y no se suma.
- Carga histórica del 17/11 alrededor de 22:25: verificar entrada SQLite, fin y energía medida de red; SOC de forma aislada no equivale a kWh de red.
- Registro de carga AUTO por cable OEM e integración V×I: distinguir origen red / batería / regeneración; no fabricar kWh cuando faltan voltaje, corriente o contadores; garantizar continuidad del registro durante OTA y encendido.
- Borrado selectivo de cargas importadas manualmente no debe borrar carga automática ni sesiones abiertas.

## Seguimiento de funcionalidades que requieren prueba física
- **Mosaico + ADB**: confirmar tasks/IDs y bounds reales, no tapar con pantalla fullscreen; no forzar STOP a aplicaciones OEM, no alterar HOME.
- **Clúster / Display 1**: comportamiento Waze y Maps (único / 50-50), recuperación al reiniciar y teclas del volante: rueda/OK/atrás, posible interferencia de foco; mantener T1Launcher operativo.
- **Audio/voz**: Sender Xiaomi BT/A2DP, Spotify `NOT PROVIDED`, activación audio tras Play; Gemini/ChatGPT/Waze comando único volante y respuesta hablada TTS sin pantalla invasiva. No afirmar que esté validado en la camioneta.
- **Espejos**: bajada R (~20 pulsos), retorno al D >5 km/h o P >30 s, recuperar posición manual sin acumulación de pulsos; evitar escrituras inseguras OEM.
- **Optimización SOC**: modos ciudad/carretera/autopista, doble regla A/B, confirmación de tres lecturas, regeneración, rechazo OEM del forzado SOC y umbrales reales; separar solicitud de escritura de lectura confirmada. AVAS activo cuando se circula.
- **Aire recirculación**, limpiaparabrisas y sensores estacionamiento: evitar afirmar mandos confirmados sin respuesta OEM; análisis de IDs con lectura y pruebas en P.
- **Viajes y análisis de mapas**: viajes / jornadas / kilometraje OEM 500m sin duplicación, cálculo de consumo, OSM/IGM y porcentaje rutas desconocidas, análisis en segundo plano sin reiniciar al cambiar de solapa.
- **Borrar todo**: bloqueo deliberado si hay sesión activa, pausada o pendiente; el borrado local no equivale al de la nube ni de carga. Mensajes explícitos y confirmación.

## Restricciones de regresión
No tocar repo original `bruceferra/drivelab`, ProManager ni DriveLab T1, T1Launcher OEM/HOME, tareas vehículo, Display 1; no root, no cambios globales de ajustes, no regenerar claves de firma, no borrar sesiones activas, mapas offline, cargador, emparejamiento Sender/Receiver ni preferencias de combustible. Probar detenido y confirmar en dispositivo lo que CI no puede verificar.

## Criterio de entrega
1. Confirmar corrida de publicación **exitosa**.
2. Verificar release `v0.3.92` con **DriveLab-Auto-Receiver-v0.3.92.apk** y **DriveLab-Boot-v0.1.0.apk**, `receiver.json`, `sender.json`, SHA y misma firma Receiver previa.
3. Instalar Receiver encima del actual sin desinstalar; luego Boot independiente.
4. Revisar Cockpit, viaje fantasma/fecha, capturas manuales; probar mosaico manual 2 paneles y diagnóstico ADB; dejar autoinicio desactivado hasta validar.
5. Con el vehículo en P, comprobar reinicio y servicios, sin alterar T1Launcher/clúster.
