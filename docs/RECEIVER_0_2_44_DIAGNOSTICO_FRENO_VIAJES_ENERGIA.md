# Receiver 0.2.44 — sesiones, energía y diagnóstico de freno (12/11/2026)

## Jornada: telemetría recuperada de Supabase privado

| Trip ID | Hora ART | Muestras | Distancia final |
|---|---|---:|---:|
| 20261112_071153 | 07:11:53–07:29:04 | 336 | 8,03408 km |
| 20261112_073616 | 07:36:16–09:18:25 | 1993 | 32,69145 km |
| **Suma** | | **2329** | **40,72553 km** |

La suma concuerda con los 40,7 km indicados por el propietario. Se suman solo las distancias finales de cada TRIP, no las muestras intermedias. Con STOP el usuario sí cerró una sesión y luego abrió otra; los originales se conservan. No inventar el intervalo no registrado de ~7 minutos ni unir IDs de clientes.

## Consumo OEM 500 m — datos reales y escala hipotética

| Trip | Eventos nafta | Suma cruda nafta | Eventos eléctricos | Suma cruda electricidad |
|---|---:|---:|---:|---:|
| 20261112_071153 | 15 | 52 | 15 | 110 |
| 20261112_073616 | 61 | 275 | 61 | 87 |
| Total | 76 | 327 | 76 | 197 |

Hipótesis de cálculo experimental: valor crudo /100 km por evento de 0,5 km.
- Nafta: 327 × 0,005 = **1,635 L experimentales**. Al precio de referencia 2.129 ARS/L usado por defecto en código: 1,635 × 2.129 = **3.480,915 ARS**, redondeado **$3.481**, exacto como lo mostrado.
- Electricidad: 197 × 0,005 = **0,985 kWh netos experimentales**. Signo y escala no calibrados; NO interpretar como energía comprada.
- Ninguna muestra del día entregó tensión HV válida (voltaje nulo); los contadores de carga/descarga calculados en cero NO prueban consumo real nulo.
- SOC interno 1042: primera grabación 24,7→22,7%; segunda 22,0→17,6%. Tanque crudo 51,9→51,6 y 51,5→49,7 L; discretizado, no equivale a medir litros exactos.

## Modelo de costos sin doble conteo
El propietario dice que no enchufó el auto durante el recorrido; por ende cero cargas externas *durante ese período*. Esto NO descarta energía previamente almacenada desde una carga de red anterior. Contabilizar:
1. Nafta gastada (incluye nafta usada por generador del vehículo): un solo costo por combustible.
2. Electricidad de red realmente comprada: sesiones de enchufe con kWh medidos o marcados como estimados y tarifa; no imputar electricidad por el mero funcionamiento del motor eléctrico.
3. Regeneración y carga por motor térmico: registrar flujos técnicos, **sin nuevo costo** sobre el que ya está en la nafta.
4. SOC inicial/final y stock de energía atribuible a cargas previas: estimar consumo imputable a red solo cuando el balance esté validado, sin mezclar modo EV con fuente de electricidad.

La app ya admite carga manual en grid_charges.csv pero no cuenta con detector automático validado de conexión o medidor eléctrico de red. Hay que localizar y probar propiedades OEM para estado de toma y transferencia de energía, idealmente con lectura de kWh acumulados y calibración contra medidor.

## Cambios del Receiver 0.2.44
- Diagnóstico PHEV de 3 minutos agrega **dos columnas crudas**: HAL 557867210 (freno candidato) y HAL 557867238 (validez candidata). No están calibradas ni se emplean en decisiones de escritura.
- Lecturas sugeridas de suelto/pisado/suelto **solo estacionado en P**, vehículo asegurado, varios ciclos de 3 segundos por estado.
- Vista de historial ahora muestra kWh netos experimentales aparte del costo de nafta y de la red comprada.
- Nueva suma de las sesiones maestras del día basada en archivos CSV existentes, en **solo lectura**. Nunca fusiona archivos, reinicia, detiene o modifica los checkpoints del cliente.
- El control 1043 sigue bloqueado fuera de P y velocidad cero. No se habilitó D en la APK; esa decisión requiere validación de estado de freno real y equivalencia de ruta OEM, no solo el éxito del selector original.
- Sender 0.2.16 sin cambios.
