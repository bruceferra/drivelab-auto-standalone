# DriveLab Auto Receiver — Asistente de validaciones pendientes

Fecha: 13/11/2026. Objetivo: recuperar todas las pruebas ya construidas que quedaron dispersas en los menús de DriveLab, sin duplicar sondas ni arriesgar nuevas escrituras OEM.

## Auditoría de navegación del Receiver

| Pantalla | Ruta existente | Qué hay | Qué faltaba |
|---|---|---|---|
| Receiver principal | VEHÍCULO / COCKPIT / TABLERO | acceso a toda la app, ajuste de pantalla, enlace Waze y Sender | ninguna lista centralizada de pruebas |
| Cockpit | ZONAS / DIAGNÓSTICOS (expandible) | A/B de luces/puertas/baúl y luces por asiento, LED frontal, cinturones, niveles térmicos, asesor OSM, informes | estaban muy abajo y mezclados con viajes |
| VEHÍCULO | pestaña VEHÍCULO | 2126/2125, PDC, espejos 2068/2069, registro de marchas, ventanillas | no ofrecía A/B guiado por dispositivo |
| VEHÍCULO | CODE | detector de cambios OEM 45 s, lector de 25 códigos, exportación CSV | el usuario tenía que saber qué código buscar |
| VEHÍCULO | DIAGNÓSTICO | grabación 3 min de 1040/1041/1043/1045/1046, limpias, radar, capturas de eventos | sin checklist ni continuidad por pendiente |
| Volante | DETECTAR BOTONES VOLANTE · KEYEVENT | 90 s de KeyEvent vía Android | `startCapture` hacía `T1TripTelemetry.snapshot()` síncrono antes de activar; botón podía parecer bloqueado |
| Receiver / Cockpit | micrófono de destinos | escucha Android puntual y abre Waze con confirmación | falta prueba física de reconocedor y Waze clúster |

## Nuevo botón

- **PENDIENTES · ASISTENTE DE VALIDACIÓN T1** en Receiver principal.
- **PENDIENTES** en la cabecera del Cockpit.
- **PENDIENTES · ASISTENTE GUIADO DE PRUEBAS** en VEHÍCULO.

El asistente mantiene un **paso activo**, botones anterior/siguiente pendiente, índice de todas las pruebas, notas persistentes, estados locales e informes. Cada estado "VALIDADO POR USUARIO" es una declaración del propietario, nunca una homologación ni una confirmación de que exista setter OEM. Los reportes se conservan localmente; si se activó la nube con QR, el propietario puede subirlos al servidor privado de diagnóstico.

## Inventario de tareas (18 pasos)

1. Volante Android KeyEvent (código, scanCode, origen de tecla).
2. Limpia automático delantero: `2126` sensibilidad, NO velocidad de barrido.
3. Limpia trasero: `2125`.
4. Limpia manual delantero: CODE detector de 45 s, ID aún desconocido.
5. Plafones interiores, estados de puerta y baúl: `T1CabinLightReadOnlyProbe`.
6. Luces por asiento fila 2: `356519683/356519684`, áreas 0/16/32/64.
7. Luces traseras exteriores: diagnóstico separado de plafones/LED frontal, **sin código de control validado**.
8. LED frontal de bienvenida y parrilla en P: registro solo lectura de 30 s.
9. SMART/FORZADO: `1040=2/4` leído y validado manualmente; setter desde DriveLab no confirmado.
10. Objetivo SOC: `1041`, getter tipo/escala a confirmar antes de escribir.
11. SOC AUTO: `1040=3` en enum DEX original, no probado en esta unidad.
12. Regeneración: `1046` 1/2/3 candidato por nivel.
13. Auto EV/HEV/EV MAX en autopista: evaluar registros después del viaje, jamás manipular controles en circulación.
14. Radar PDC: `2049/2107`, HAL PDC sin escala física.
15. Espejos 2068/2069 y restauración R/D: manual probado, recuperación exacta no confirmada.
16. RPM motor eléctrico: sin código validado (1035 es térmico).
17. Cinturones traseros: sondeo de estados sin desactivar alarmas.
18. Micrófono de destinos en Waze del clúster: falta prueba en ROM instalada.

## Secuencia de pruebas de dos capturas

Para limpiaparabrisas `2126/2125`, luces de cabina, luces por asiento y radar, el asistente ofrece CAPTURA A / CAPTURA B:
1. Auto inmóvil y estacionado en P; identificar mando ORIGINAL a probar.
2. Tomar la lectura A desde DriveLab (se usa el lector previamente construido, nunca un setter).
3. Cambiar **un único** mando original.
4. Tomar B, comparar diferencias de señales legibles y revisar el resultado completo.
5. Registrar observación, marcar estado, compartir informe o continuar al próximo pendiente.

Si no hay lectura o si A y B son iguales, no afirmar que el vehículo no soporta ese dispositivo: puede ser problema de permiso, área, ECU, código o firmware. Cada captura de pantalla usa consultas off-thread para no bloquear la interfaz.

### Reparación del botón del volante

- `T1SteeringKeysActivity` ahora hereda `T1ImmersiveActivity`: deja espacio fijo bajo el overlay "Hola Jetour" que en la versión anterior podía superponerse al botón táctil.
- `INICIAR CAPTURA 90 S` presenta **GRABANDO** y comienza el contador inmediatamente sin esperar Binder OEM. El estado de marcha se lee en otro hilo y se muestra después.
- Nuevo botón **COMPROBAR BOTÓN TÁCTIL**: distingue una pantalla que recibe toques de una ROM que no entrega los KeyEvent del volante.
- Se muestra tiempo restante y cantidad de eventos, con una cuenta de cero explícita.
- Se puede compartir también un informe de **0 eventos**, valioso para distinguir ausencia de datos de botón inactivo.
- El lector no captura teclas globalmente ni fuerza permisos de Accessibility; si el firmware intercepta OK/ruedita, no inventa códigos.

## Límites y próximos ensayos

No hay nuevos mandos OEM de limpiaparabrisas, luces, radar, cinturones, estrategia SOC ni propulsión. Todos los cambios experimentales existentes permanecen en sus controles originales sin ser accionados automáticamente por el asistente. Se preservan las apps Sender/Receiver, el historial y la configuración local.

Para confirmar que un setter sea fiable en el futuro se necesita la ruta exacta del APK original, tipo y escala de la propiedad, permisos reales, una prueba individual detenida, lectura posterior de estado, comprobación de reversibilidad y rechazo sin efectos secundarios. Las capturas A/B de este asistente sirven para localizar candidatos, **no** autorizan esas escrituras.

Se requiere la prueba física de apertura, botón volante, reconocimiento de voz y lecturas OEM en la versión argentina Jetour T1. La herramienta no simula que se hayan completado.
