# DriveLab Auto 0.3.98 · separación de DriveLab Nav

Fecha: 22/11/2026. Se separan responsabilidades sin borrar datos del Jetour ni forzar desinstalación.

## DriveLab Nav (único dueño)
Navegación y destinos Xiaomi; Waze/Google Maps; pantallas Display 0 / Display 1 y clúster; mosaicos Freeform, ventanas y tareas ADB; volante para navegación; asistentes de voz, accesibilidad y envío de destinos. DriveLab Auto no abre sus puertos LAN 48391/48392 ni arranca estas funciones.

## DriveLab Auto conserva
- Viaje 1, Viaje 2 y Total; jornadas, historial, CSV, exportaciones.
- Viajes automáticos por encendido OEM, SQLite/GPS, reanudación después de reiniciar.
- Análisis de viajes y sus mapas históricos OSM/IGM; sin abrir apps de navegación.
- Carga automática independiente, energía HV, tanque, combustible y autonomía.
- Optimización de modos, controles OEM, espejos, ventanas, AVAS, recirculación, diagnóstico.
- Actualizador firmado Receiver, preferencias y datos actuales.

## Eliminación segura en esta edición
Componentes Android de navegación deshabilitados en manifest; autoarranque y servicios de Waze, volante y micrófono aislados; barra e Inicio sin accesos a Nav. Se preserva el código Java histórico como dependencia *no ejecutada*: no se elimina físicamente sin probar imports y contratos, para no arriesgar las grabaciones. El APK independiente DriveLab Boot no se modifica ni desinstala; la integración de mosaicos que dependía de Receiver está deshabilitada, pues Nav es el nuevo dueño.

## Protocolo de mañana
1. Instalar Receiver 0.3.98 sobre el Receiver existente. No desinstalar ni borrar almacenamiento.
2. Verificar viajes manuales, automáticos, CSV, históricos, cargas, consumo y optimizador. Confirmar que sesiones activas/pausadas siguen intactas.
3. Reiniciar detenido y comprobar reanudación, sin reabrir navegación desde Auto.
4. Comprobar que Nav sigue manejando Waze/Maps, Spotify, ventanas y clúster; Auto no interfiere.
5. Mantener la release 0.3.97 como respaldo. Pruebas de manejo, sensores y actuadores OEM solo con el vehículo detenido y verificando readback.

**Firma:** versión 0.3.98, versionCode 199; publicación exige firma idéntica a Receiver v0.3.97 y conserva sender.json v0.2.22; no toca Sender, Boot ni proyectos antiguos.
