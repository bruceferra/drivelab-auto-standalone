# Receiver v0.2.57 / versionCode 58 — AVAS manual e interior automático

## Pedido del propietario
- **AVAS peatones OEM 2074:** un solo botón manual ON/OFF como el sistema original del Jetour T1. Sin inicio automático, sin guardias de segundo plano, sin esperar 12 segundos, sin interlock inventado de P o velocidad 0. El cambio debe obedecer a la lectura real 2074 y a la aceptación del VehicleService OEM.
- **Climatización:** al abrir DriveLab Auto mantener el aire en **recirculación interior** sin tocar botón. Seguirlo vigilando en segundo plano aunque se abran Maps u otras pantallas; recuperar el modo interior cuando el sistema HVAC original cambie a entrada exterior. Mantener prioridad del desempañado OEM.

## AVAS — `PedestrianAlertControl`
- Usa el mismo servicio OEM `com.autolink.app.vehicleservice.VehicleService`, lee el código **2074** (`0=OFF`, `1=ON`) y transmite la **orden opuesta una sola vez**, siempre causada por **un toque manual**.
- No requiere una marcha P, velocidad 0, ni usa los getters 1033/2148/1089 como condición desde DriveLab. La política de aceptación interna del módulo OEM sigue vigente y una negativa se muestra como tal. El toggle no se activa al arrancar, por temporizador, por GPS ni desde BootReceiver.
- Se redujo el bloqueo artificial de **12 s** a **350 ms** solamente para evitar pulsaciones duplicadas al procesar una orden anterior. El servicio responde con lectura 2074 confirmada o estado no confirmado; si no hay conexión, informa el error en lugar de fingir éxito.
- El mismo control lo utilizan `ReceiverQuickControls` y `ReceiverVehicleActivity`. Se corrigieron los mensajes que pedían P/0 km/h incorrectamente.

## HVAC — `RecirculationGuardService`
- El usuario pidió mantener aire del **habitáculo** en lugar de entrada exterior.
- Propiedad OEM **1004=1**: recirculación activa. **1004=2**: entrada exterior permitida. La propiedad **2101** permanece estrictamente de solo lectura.
- `ReceiverActivity.onCreate` llama a `RecirculationGuardService.activateOnAppOpen`: selecciona `MODE_INTERIOR` y levanta su **servicio foreground** automáticamente en cada apertura nueva, sin interacción. El servicio lee 1004 cada ~3 s; solo emite escritura 1004=1 si estaba 1004=2 y lee posteriormente el resultado.
- Se conserva `START_STICKY`, notificación persistente y `BootReceiver` para restaurar el servicio cuando ya estaba configurado en INTERIOR tras un reinicio/reemplazo del paquete.
- Los botones dejan claro que **AIRE INTERIOR · AUTO** significa vigilancia automática. **AIRE EXTERIOR · OEM** permite entregar el control al clima original durante la sesión. En la siguiente apertura nueva de DriveLab Auto vuelve a predominar la preferencia de interior indicada por el propietario.
- Si el HVAC revierte tres cambios en un minuto, o cuatro lecturas OEM consecutivas fallan, el guardia se **pausa e informa** en vez de combatir reiteradamente una posible acción OEM de desempañado. Abrir la app nunca salta esa pausa de seguridad; el usuario puede reactivar manualmente el guardia tras comprobar que no haya empañamiento.
- No se limita el número de horas de recirculación artificialmente, pero la ventilación de habitáculo y el parabrisas se deben supervisar: evitar siempre aire exterior puede aumentar CO₂/humedad o empañar cristales. El sistema OEM conserva autoridad final. Una comprobación 1004=1 no garantiza que no exista ninguna infiltración de aire exterior.

## Cambios en curso incluidos en esta compilación
La misma rama contiene, todavía pendientes de prueba física, estos cambios previos:
- Historial de Viaje 1 y Viaje 2 seleccionable con seis indicadores y botón de reconstrucción de kilómetros OEM en archivo sidecar; conserva CSV y protege el último Viaje 2 correcto.
- Condicional de batería del modo optimizado **SOC actual ≥/≤ X%** y objetivo forzado SOC separado.
- Clasificación OSM por velocidad legal `maxspeed`: avenidas urbanas 50/60 km/h = ciudad y rutas 80+ km/h = carretera, con referencias RN/RP y excepción autopista.
- Avisos cortos CIUDAD/CARRETERA/AUTOPISTA de 1,7 s, sin "Entrando a" ni parpadeos por GPS transitorio.
- Se conserva el odómetro OEM preferente desde 0.2.56, simulador experimental, tres viajes, sender 0.2.16 y seis indicadores.

## Validación
Los tests Java puros y Android CI verifican compilación, certificado de firma anterior, versiónCode 58, compatibilidad OTA y preservación de Sender. La respuesta real del HVAC/AVAS OEM en movimiento no puede probarse desde GitHub Actions: el usuario debe ensayarla con precaución, observando lectura 2074 y estado 1004. No representar la verificación automática como prueba física.
