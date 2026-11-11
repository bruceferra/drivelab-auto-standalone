# Investigar origen OEM de consumo 1084/1085 (Jetour T1)

## Confirmación del propietario (10/11/2026)

- `VEHICLE:1085`: consumo de nafta **L/100 km de los últimos 50 km**. Verificado contra pantalla OEM (5,1 L/100 km).
- `VEHICLE:1084`: consumo eléctrico neto **kWh/100 km de los últimos 50 km**. Verificado contra pantalla OEM (−2,3 kWh/100 km). Negativo indica balance eléctrico neto bajo convención OEM, no determina por sí solo cuánta energía procede del motor térmico o regeneración.
- Para una ventana exactamente de 50 km: 5,1 × 0,5 = 2,55 L *estimados dentro de esa ventana*, y −2,3 × 0,5 = −1,15 kWh netos. **No compararlos con un TRIP de 52,24 km ni con combustible repostado en otro intervalo sin alinear fechas/distancias.**
- `1049`: energía descargada según catálogo de usuario; unidad, acumulación y reseteo por validar. `1048` vinculado a contadores de carga, definición precisa pendiente.
- `1035`: RPM térmico candidato; contraste con Testigo obligatorio antes de usar estado motor para corregir consumo.

## Trabajo implementado en rama experimental

El TRIP lee y guarda `1084` y `1085` **crudos** al final de cada fila CSV, sin sustituir la nafta PROXY histórica. El servidor privado admite en lotes `oemElectricRolling50` y `oemFuelRolling50`, sin GPS. El tablero muestra ambos bajo el rótulo explícito «OEM últimos 50 km» y distinto del TRIP en curso.

## Origen del cálculo de consumo

La app Receiver consulta `com.autolink.app.vehicleservice.VehicleService`, interfaz `IVehicleControl`, getter float de IDs `1084/1085`. Eso demuestra **de dónde obtiene Receiver el número**, no dónde ni cómo lo calcula Jetour. Es posible que una app OEM lo calcule con datos de la ECU, pero también que la ECU/BMS entregue el resultado cerrado.

Dos pistas HAL sin confirmar en la unidad argentina, encontradas en análisis anterior de T1Launcher T1N134:

- `559964335` — `SETTINGS PHEV SUM FUEL` (nombre de catálogo de código).
- `559964336` — `SETTINGS PHEV SUM CHRG EGY`.

Investigar su origen y unidad; **NO asumir que representan litros físicos realmente inyectados ni kWh eléctricos de la red**.

## Extracción SOLO LECTURA por ADB

1. Desde una PC Windows con ADB y conexión USB autorizada a la T1, ejecutar `adb devices`.
2. Ejecutar `tools/export-jetour-oem-apks.ps1` desde PowerShell. Solo hace `pm path` y `adb pull`, y calcula SHA256 localmente. No instala, elimina, modifica archivos ni exige root.
3. Archivo resultante: `Jetour-OEM-Audit.zip`. Analizar `classes.dex`, recursos, nombres de propiedades y conexiones JNI/HAL de tres paquetes OEM: vehicleservice, carservice y clusterinteraction.
4. Si algún `adb pull` devuelve «Permission denied», aceptar la restricción; no intentar root, permisos ocultos ni modificaciones de firmware.
5. No incluir información personal, coordenadas GPS, VIN, claves o archivos de `/data/data` en el paquete.
6. Siguiente etapa: identificar si hay APIs read-only para contadores acumulados o instantáneos que nos permitan calcular TRIP A/B reales, sin una ventana fija de 50 km.

**Importante:** disponer de un APK OEM no garantiza descubrir la fórmula (puede residir en firmware de ECU, CAN o un HAL nativo). Comenzar por análisis estático y pruebas solo de lectura.
