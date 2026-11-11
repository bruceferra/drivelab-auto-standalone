# DriveLab Auto · Receiver 0.2.32 + Sender 0.2.15 (11/11/2026)

## Implementación solicitada: TRIP incorpora cada tramo OEM de 500 metros

El modo **TABLERO T1 → INICIAR TRIP** ahora empieza **dos grabaciones simultáneas**, gestionadas por el foreground service del TRIP, sin necesidad de dejar la pantalla de DriveLab visible:

1. `trip_<id>.csv` – muestras cada ~3 segundos de marcha, velocidad, distancia (GPS u OEM estimado), SOC, consumo eléctrico HV bruto, combustible estimado y promedios OEM. Se agregaron seis columnas para **contadores y últimos valores crudos** del evento real de 500 m (nafta y electricidad) y callbacks 50 km.
2. `trip_oem_<id>.csv` – registros **originales** de callbacks CI (timestamp, canal, float raw) durante **toda la duración del viaje**: `NAFTA_500_m`, `ELÉCTRICO_500_m`, y `NAFTA_50_km` / `ELÉCTRICO_50_km`. Se guardan **TODOS los eventos de 500 m incluso cuando dos segmentos tienen igual valor**. Solo se compactan repeticiones idénticas de 50 km, que el OEM manda cientos de veces sin actualizar el promedio.

Un par 500m con valores `9, -12` seguido de `8, -5` en la prueba de campo del 11/11/2026 prueba la emisión del instrumental y la posibilidad de correlacionar tramos con timestamps, pero **no confirma unidades absolutas ni que todo negativo eléctrico sea regeneración**. Durante el TRIP se cuentan tramos y se muestran últimos valores crudos, sin convertirlos a litros o kWh. Se preservan ambos CSV después de DETENER TRIP; la escucha se desregistra al finalizar, nunca se queda encendida para siempre. El panel **VEHÍCULO → DIAGNÓSTICO** conserva su prueba independiente de 3 minutos.

### Nube y privacidad

- Los lotes TRIP se sincronizan por `T1TripSync`, incluyendo `oem500FuelCount`, `oem500ElectricCount`, `oem500FuelRaw`, `oem500ElectricRaw`, `oem50FuelCallbackRaw`, `oem50ElectricCallbackRaw`. Las coordenadas GPS y el VIN **no se envían** a Supabase.
- El archivo `trip_oem_<id>.csv` no contiene GPS ni VIN, y se puede subir al endpoint privado de diagnósticos `drivelab-auto-diagnostics` con la credencial QR ya guardada en la T1. Se solicita subida al terminar el TRIP únicamente cuando el listener ya dejó de escribir, y se permite reintento desde TABLERO T1 → **EXPORTAR TRAMOS OEM 500 m DEL ÚLTIMO TRIP** sin ADB. Exportar el CSV principal también intenta disparar la sincronización de tramos.
- Servidor Supabase: se amplió la lista blanca de nombres `trip_oem_YYYYMMDD_HHmmss.csv` y el esquema de muestras numéricas autorizado. Tabla diagnósticos privada con RLS; no se agregó endpoint de lectura pública.
- El archivo `trip_<id>.csv` original **puede contener GPS**; NO se envía como CSV crudo a la nube, solo lotes saneados. Se puede exportar localmente si el propietario lo decide.

### Sender 0.2.15: Uber debe mantenerse visible

Android requiere un Activity para aparecer en el menú Compartir/Navegar de Uber/Maps. `RouteActivity` ahora es un **trampolín transparente, sin historial y sin interfaz de progreso**: reenvía inmediatamente el enlace a `RouteDispatchService` y termina. El servicio foreground temporal resuelve destino, entrega cifrada y después termina. En **SOLO CLÚSTER**, no abre ninguna ventana de DriveLab ni Waze en el Xiaomi. Si falla la entrega, se publica una **notificación para consultar o abrir Waze manualmente**; no emergen diálogos sobre Uber. En modo **CLÚSTER + XIAOMI**, se intenta abrir Waze como pidió el usuario, sujeto a restricciones de Android. Conservar el mecanismo QR ECDH actual; no se necesita nueva vinculación ni permisos de accesibilidad.

### Siguiente etapa (no vender como implementada)

Con varios eventos consecutivos de 500m y kilómetros reales se podrá correlacionar **100 tramos** para intentar reconstruir la media móvil OEM de 50km, separando energía recuperada de la consumida según el modelo que resulte de mediciones. Todavía no se muestra una cifra de litros reales por segmento ni se deduce que el signo negativo siempre signifique frenada regenerativa. No se alteran AVAS, EV/HEV, frenos, motor ni sensores de seguridad.

## Publicación

Revisar PR y CI primero. Versiones: Sender **0.2.15 / code 16** y Receiver **0.2.32 / code 33**. Nuevo workflow de publicación firmado usa release conjunto tag **v0.2.32**, verifica firmantes de ambas APK contra releases existentes y actualiza `sender.json` y `receiver.json`. No borra ni reemplaza versiones antiguas. Probar en T1 real que los contadores aumenten, que se reciben ambos archivos y que el Sender no roba el foco de Uber.
