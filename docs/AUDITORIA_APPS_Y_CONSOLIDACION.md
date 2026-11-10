# Auditoría consolidada: qué DriveLab existe y qué se conserva

**Corte de auditoría:** repositorios revisados hasta 2026-11-10; v0.2.10 es la última release verificada de Sender/Receiver. Una versión presente en fuente o en una nota vieja no certifica qué APK está instalada en cada dispositivo.

## Inventario de aplicaciones y linaje

| Componente | Repo / proyecto | Paquete Android | Fuente/release auditada | Rol y destino |
|---|---|---|---|---|
| DriveLab estable (GPS) | bruceferra/drivelab / android | ar.com.rango5gps | Fuente 0.39.0; documentación de instalación histórica 0.37.0 | **CONSERVAR**: registrar viajes, kilómetros, tiempos, rutas, consumo por viaje, historial y exportaciones. No tocar datos de viaje activos. |
| DriveLab Uber Beta | bruceferra/drivelab / flavor android | ar.com.rango5gps.uberbeta | 0.67.25-beta, versionCode 92 en fuente | Ensayo/asesor de ofertas y recepción de navegación Uber. Mantener separado hasta migrar únicamente lo útil; no incluir OCR/Accesibilidad dentro de Receiver por defecto. |
| DriveLab T1 (laboratorio) | bruceferra/drivelab / drivelab-t1 | ar.com.rango5gps.t1 | 0.1.22, versionCode 23 en fuente | Inventario OEM, energía, CODE, ventanillas, AVAS, puente antiguo. **NO DESINSTALAR** antes de verificar v0.2.10. |
| DriveLab Auto original (Xiaomi) | bruceferra/drivelab / drivelab-auto | ar.com.rango5gps.auto | 0.1.4, versionCode 5 en fuente | Primer sender del puente LAN antiguo a DriveLab T1. No es intercambiable con el nuevo Receiver. |
| **DriveLab Auto Sender actual** | bruceferra/drivelab-auto-standalone / sender | ar.com.rango5gps.drivelabauto.sender | **0.2.10, code 11, release firmada** | Xiaomi: compartir/pegar destinos, opciones solo clúster y clúster + Xiaomi, vinculación, actualización. |
| **DriveLab Auto Receiver actual** | bruceferra/drivelab-auto-standalone / receiver | ar.com.rango5gps.drivelabauto.receiver | **0.2.10, code 11, release firmada** | Jetour: puente cifrado + Waze, Share local de Maps, arranque opcional, actualización, VEHÍCULO, ENERGÍA y CODE experimentales. |
| T1Launcher de MainthorCar | Repositorio de tercero | com.t1launcher | T1N134 inspeccionada estáticamente | Referencia de interoperabilidad, módulos de espejos, viajes, cluster y consumo por tanque. No es nuestro código ni está fusionado. |
| T1 Testigo | Externo | com.t1testigo | Versión no auditada aquí | CSV/observación externa. Su Android/data no es legible por otra app normal en Android 11 sin exportación autorizada. |

No apareció en estos repositorios una APK separada identificable como “Driver Uber Way”; la funcionalidad Uber/Waze estaba en Uber Beta y en los puentes.

## Menús y botones: DriveLab estable y Uber Beta

**DriveLab estable — barra principal:** INICIO, VIAJES, MAPA, CLUB, PERFIL.

- INICIO: GPS, registro continuo, iniciar/pausar/reanudar/finalizar viajes, favoritos, seguimiento y clasificación manual de recorrido ciudad/autopista/otro.
- VIAJES: historial, vista detallada, combinaciones y reversión de combinación, edición y eliminación deliberadas, reportes de distancia/consumo, exportación CSV.
- MAPA: traza GPS, reproducción y zoom, OSM/PBA-CABA, abrir Maps/Waze, exportación/share PNG, GeoJSON, KML y GPX; no confundir traza GPS histórica con ruta que Google Maps recalcula.
- CLUB: fichas locales y colaboración/comunidad; perfil, métricas y comparaciones según función disponible.
- PERFIL: alias, favoritos, alertas, configuración y actualización.

**Uber Beta — además de los anteriores:** UBER con subpaneles WAZE, OFERTAS, OCR UBER, TOQUES / X, REGLAS, ESTADO. Controles rápidos para ABRIR UBER, PAUSAR/REANUDAR ASESOR y ACTUALIZAR. Reglas por tarifa por km/h, distancia de recogida, piso, trayecto largo, etc.; OCR/Accesibilidad y pruebas de rechazo por X. **El cierre de tarjetas de Uber por X no estaba confirmado como fiable en pruebas reales; no marcarlo resuelto ni migrarlo al Receiver por defecto.** Incluye compartir/pegar enlaces de Google Maps/WhatsApp/Waze para el puente anterior.

## Menús y botones: DriveLab T1 0.1.22 laboratorio

Solapas: **ENERGÍA, CODE, CONEXIÓN, PUENTE, ACTUALIZAR, ACERCA**.

- Siempre visibles: VENTILACIÓN TOTAL, CIERRE TOTAL, ALARMA PEATÓN ON/OFF manual.
- ENERGÍA: LEER LITROS OEM UNA VEZ; SOC interno 1042, SOC visible 1062, corriente 1064, indicadores aún pendientes de escala/verificación.
- CODE: detector diferencial 45 segundos, lectura de señales, SUBIR/BAJAR como anotaciones de estudio manual, lector de 25 IDs, ANTERIOR −25 / SIGUIENTE +25 (estos **no son pasos de apertura de ventanas**), inicio editable, SUBIR DIAGNÓSTICO y EXPORTAR CSV.
- CONEXIÓN: inventario Binder/paquetes OEM, descriptor e inspección para validar lecturas.
- PUENTE: vínculo Xiaomi de seis dígitos, activar/detener receptor, pruebas La Plata y Mar del Plata, permiso segundo plano, Waze automático, app al encender, inicio automático, desvinculación.
- ACTUALIZAR: verificar, descargar e instalar APK firmada desde su canal T1 anterior.

## Menús y botones: DriveLab Auto original v0.1.4

Pantalla simple en Xiaomi: SOLO CLÚSTER / CLÚSTER + XIAOMI, PEGAR ENLACE WHATSAPP/MAPS, VINCULAR T1 (6 DÍGITOS), PROBAR CONEXIÓN, ENVIAR PRUEBA A LA PLATA, ABRIR UBER DRIVER, DESVINCULAR, ACTUALIZAR ESTADO. No tenía panel OEM ni Spotify.

## Menús y botones: Standalone 0.2.10

**Sender Xiaomi:** SOLO CLÚSTER / CLÚSTER + XIAOMI; VINCULAR RECEIVER (6 dígitos) y confirmar coincidencia; PROBAR ENVÍO REAL A LA PLATA; PEGAR ENLACE GOOGLE MAPS / WHATSAPP; ACTUALIZAR SENDER; DESVINCULAR. No hay OEM ni telemetría.

**Receiver T1, pantalla principal de mapas/puente:** INICIAR / DETENER RECEPTOR, VINCULAR XIAOMI, AUTORIZAR CÓDIGO COINCIDENTE, ACTUALIZAR ESTADO, INICIO AL ENCENDER T1, AUTORIZAR SEGUNDO PLANO, apertura automática Waze, selector de app de arranque, quitar app de inicio, PRUEBA DIRECTA A LA PLATA/MAR DEL PLATA, ACTUALIZAR RECEIVER, reintento de instalación, instalador nativo y ajustes de permisos. De v0.2.9 hereda aparecer en **Compartir → DriveLab · Navegar en clúster** dentro de Google Maps en la T1.

**Receiver T1, nuevo acceso VEHÍCULO · VENTANILLAS / ENERGÍA / CODE (v0.2.10):**
- VEHÍCULO: ventilación 1/4 y cierre grupales, controles de ventilación/cierre por cada ventana ID 2004–2007; cambio manual de AVAS 2074 con confirmación.
- ENERGÍA: leer CI186, 1042, 1062, 1064 puntualmente; texto de otras señales por investigar y nota Spotify sin automatizar.
- CODE: iniciar/detener detector de 45 s, avanzar/retroceder 25 IDs, elegir ID inicial, leer tanda 25, exportar CSV a Descargas/DriveLabAuto.

**Importante:** la nueva pantalla VEHÍCULO / ENERGÍA / CODE se abre desde el menú principal; todavía no existe la interfaz completa de seis pestañas visuales tipo launcher prevista a futuro. No confundir código añadido con actuador físicamente probado.

## Historia del nuevo par

- 0.2.0–0.2.6: separación Sender/Receiver independiente, seguridad de puente, pruebas, servicio y actualizador.
- 0.2.7: experimento de Spotify/Bluetooth con listener.
- **0.2.8: reversión intencional del listener de notificaciones y automatismo Bluetooth** por permisos sensibles/Play Protect.
- 0.2.9: destinos Google Maps por nombre/place y Share target de Google Maps en la propia T1 para abrir Waze.
- **0.2.10:** conserva navegación y suma módulos OEM T1 manuales, energía y CODE, manteniendo ambos APK/versionCode sincronizados.

## Qué quedará y qué podría retirarse después

**Objetivo futuro acordado:** DriveLab Auto Receiver será la app integrada del vehículo (mapas, funciones de vehículo, tablero y posiblemente reproductor); DriveLab estable conservará registro de viajes, GPS, recorridos e historial. Uber Beta podría reducirse o separarse según resultados reales. DriveLab T1 de laboratorio se retiraría **solo cuando cada lector y control equivalente del Receiver esté probado**.

No realizar todavía limpieza ni desinstalación. Preservar firmas/canales, datos SQLite, CSV, viajes de tanque en curso, configuración de favoritos y emparejamientos; evitar que dos receptores de protocolos diferentes compitan por la misma fuente de navegación.

## Fuentes del código

- https://github.com/bruceferra/drivelab-auto-standalone
- https://github.com/bruceferra/drivelab/tree/main/drivelab-t1 (privado)
- https://github.com/bruceferra/drivelab/tree/main/drivelab-auto (privado)
- https://github.com/bruceferra/drivelab/tree/main/android (privado)
- https://github.com/MainthorCar/t1launcher (tercero; revisar licencia)
