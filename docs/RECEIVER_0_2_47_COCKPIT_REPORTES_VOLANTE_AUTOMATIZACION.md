# Receiver v0.2.47 (code 48) — Cockpit compacto, trayectos, informes y volante

## Estado del release
Publicado por GitHub Actions desde main; APK firmada con el mismo certificado Receiver v0.2.46. `receiver.json` OTA versionCode=48 coincide con el APK. Sender 0.2.16 sin cambios.

## Pantalla principal
- Se eliminó el velocímetro grande del Cockpit (el velocímetro OEM permanece en Jetour).
- Primera pantalla: EV / HEV / EV MAX arriba; debajo JORNADA y CLIENTE con INICIAR/PAUSAR/TERMINAR; temperatura motor y batería; resumen del trayecto: km, nafta, electricidad OEM neta (experimental), costo nafta (experimental) y autonomía total a la derecha.
- Se redujeron bloques y fuentes; detalles técnicos, 12 V, historial y diagnóstico se despliegan opcionalmente. No se borró funcionalidad.
- El cambio manual EV/HEV/EV MAX es **un toque**: no hay cuadros de confirmación ni de resultado; conserva doble preflight de marcha/velocidad, lectura actual 1043, interacción manual y seguimiento de readback. En D la ruta Binder sigue sin validación física de aceptación. No usar la pantalla mientras se conduce.

## Semántica de viajes (importante)
- Si tocás INICIAR CLIENTE sin jornada activa, la app **inicia también JORNADA** porque cada cliente es un checkpoint dentro del registro maestro (no es duplicación ni pérdida de datos). El mensaje en el Cockpit lo indica.
- Los dos controles superiores tienen INICIAR / PAUSAR / TERMINAR. Durante pausa, INICIAR cambia a REANUDAR.
- PAUSAR JORNADA: cierra de forma ordenada el flujo del grabador, NO cierra el TRIP ni borra CSV, y no se reanuda en background hasta que el usuario toque REANUDAR. Al reanudar retoma el mismo CSV y no inventa recorrido durante la pausa.
- PAUSAR CLIENTE: toma un checkpoint y conserva sus km/energía; la jornada puede seguir grabando. Al reanudar se descarta el tramo transcurrido mientras el cliente estaba pausado. Terminar un cliente pausado es posible y se guarda su resultado al momento de la pausa.
- TERMINAR CLIENTE guarda el cliente sin terminar jornada; TERMINAR JORNADA requiere que el cliente haya terminado antes.
- Las luces de REC ahora indican estado con palabras y color: verde agua grabando; dorado pausado/esperando; neutro detenido; ya no se pinta rojo INICIAR cuando está activo.

## Informes: no copiar/pegar
- En diagnósticos EV/HEV, LED frontal, luces de baúl, luces por asiento, cinturones traseros, motor/temperatura y batería 12 V, botones **COMPARTIR** envían un CSV privado `code_diag_YYYYMMDD_HHMMSS.csv` mediante **el servidor Supabase ya vinculado por QR**.
- Cada informe incluye tipo (ej.: `led_frontal`, `cinturones_traseros`), fecha y texto original. El CSV privado se conserva localmente incluso si la subida no funciona; la UI muestra éxito confirmado o error.
- No se requiere copiar al portapapeles ni mandar texto al chat. Para revisar informes después, consultar `drivelab_auto_diagnostic_csv_chunks` en Supabase; los informes no se confunden con viajes.
- No se envían coordenadas GPS, VIN ni nombres de clientes en los informes, ni se altera el histórico local.

## Botones del volante — origen real y nuevo diagnóstico
- APK analizada: `T1Launcher-T1N134.apk` (archivo aportado previamente por el propietario).
- En `classes2.dex`, `com.t1launcher.MainActivity.dispatchKeyEvent` llama a `com.t1launcher.TeclasVolante.anotar`, con `KeyEvent.getKeyCode` y `KeyEvent.keyCodeToString`.
- `com.t1launcher.cluster.MandoMapa.hacer` contiene las cadenas exactas `SIN BOTÓN ACEPTAR`, `SIN BOTÓN ATRÁS`, `ZOOM +` y `ZOOM −`. También busca elementos de interfaz por texto/etiquetas y usa un servicio de accesibilidad (no está probado que use OCR de imagen).
- Los códigos concretos de OK, Atrás y ruedita **todavía necesitan observarse en el vehículo**: Android KeyEvent es diferente de los ID de 4 dígitos VehicleService o HAL.
- Nueva pantalla Receiver → VEHÍCULO → DIAGNÓSTICO → **DETECTAR BOTONES VOLANTE · KEYEVENT**. En P y 0 km/h, iniciar captura 90 segundos, pulsar uno a uno, **COMPARTIR INFORME VOLANTE**; captura keyCode, keyName, scanCode, action, repeat, source, deviceId. No consume los eventos ni reasigna funciones originales. Algunas teclas OEM reservadas pueden no llegar a la Activity.
- No se asignan aún funciones EV/HEV automáticas a botones ni se toca el tren motriz por eventos de volante.

## Recirculación automática en segundo plano
- El modo voluntario **SIEMPRE AIRE INTERIOR** ya es un foreground service independiente de la pantalla: lee el estado OEM 1004 cada 3 s y si la entrada exterior se habilita intenta restaurar 1004=1.
- v0.2.47 agrega heartbeat de la supervisión, estado visual `SERVICIO ACTIVO EN SEGUNDO PLANO` / `RECUPERANDO` y recuperación desde Receiver cuando el servicio quedó inactivo; BootReceiver restaura la configuración tras encendido o actualización.
- Importante: una suspensión de seguridad automática (3 reversiones OEM/minuto, posible desempañado, o 4 fallos OEM) NO se borra al reiniciar. No puede garantizarse vigilancia durante un apagado del vehículo, restricciones de Android o una suspensión de seguridad. Para reactivar después de pausa OEM, revisar climatización y elegir conscientemente el modo interior.
- La automatización de espejos es un servicio independiente cuando está armado, pero no se autorrestaura ciegamente en un reinicio si se desconoce su posición física.

## Procedimiento rápido de verificación
1. Con vehículo estacionado, actualizar Receiver por OTA a 0.2.47, sin borrar datos; comprobar versión en el Cockpit.
2. Abrir mapa o pantalla original después de activar SIEMPRE AIRE INTERIOR; volver a QuickControls y comprobar servicio/heartbeat (no forzar el clima ante desempañado).
3. Iniciar cliente sin jornada previa: ambos deben mostrar GRABANDO y km del cliente empiezan en 0, sin duplicar el trayecto.
4. Pausar y reanudar cliente; comprobar que no suma la distancia de la pausa. Pausar y reanudar jornada; comprobar que conserva la misma sesión/CSV.
5. Completar lectura LED, cinturón o luces; tocar COMPARTIR y esperar mensaje SERVIDOR OK; avisar para contrastar Supabase.
6. Probar volante detenido en P y compartir captura; si no llegan los eventos, consultar en T1Launcher BOTONES DEL VOLANTE y contrastar allí.
