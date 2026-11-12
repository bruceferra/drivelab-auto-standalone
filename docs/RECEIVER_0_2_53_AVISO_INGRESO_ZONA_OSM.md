# Receiver 0.2.53 code54 — aviso grande de cambio de tipo de vía

## Objetivo
El texto pequeño de OSM no resulta legible mientras se prueba la clasificación offline en el vehículo. Mostrar una **banda flotante ancha, visible y temporal** en el borde superior del Cockpit, sin exigir interacción ni impedir tocar controles.

## Mensajes
- **ENTRANDO A CIUDAD**
- **ENTRANDO A CARRETERA**
- **ENTRANDO A AUTOPISTA**
- **ZONA DESCONOCIDA**

## Implementación
- `T1PremiumCockpitActivity` agrega un `FrameLayout` superpuesto al `ScrollView` del Cockpit. La banda queda **arriba y fija incluso al hacer scroll**, pero bajo la franja ya reservada para el asistente de voz OEM. Letra grande de 26sp, alta legibilidad, color distintivo por categoría, sin intercepción de toques.
- Cada aviso se oculta después de **4,5 segundos** con fundido de 360ms. No crea ventanas bloqueantes ni toca otros programas, no exige permiso Android `SYSTEM_ALERT_WINDOW`.
- `T1RoadTransitionGate` da tratamiento a la primera categoría reconocida, pero **evita repetir el aviso en cada consulta (~10s)**. Requiere dos lecturas consecutivas para un cambio de categoría, y tres para Desconocido (mitiga cortes breves de GPS y calles paralelas). El cambio de vía solo se basa en el clasificador offline existente.
- Al salir del Cockpit o desactivar asesor, se oculta el cartel y se cancela el temporizador del aviso. Con asesor desactivado no aparecen carteles. Se mantiene la política de **solo lectura OSM**: ninguna clase de carretera ordena propulsión, SOC o regeneración.
- Hay tests Java puros `T1RoadTransitionGateTest` para rebotes falsos, cambios válidos, categoría desconocida y etiquetas.

## Compatibilidad
Receiver **0.2.53/versionCode 54** firmado y OTA; Sender **0.2.16** sin cambios. Mantiene panel MODO OPTIMIZADO de las cuatro clases y perfiles de 0.2.52, seis métricas, tres viajes, sincronización y mapas offline.
