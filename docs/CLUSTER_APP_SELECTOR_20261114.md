# Selector de aplicaciones del clúster — DriveLab Auto

Fecha: 2026-11-14. Prueba física del propietario: Display 1 muestra Waze a pantalla completa; Display 2 aceptó la solicitud Android pero no lo mostró en tablero. No se puede deducir si Display 2 corresponde a otra pantalla.

## Objetivo
Elegir qué app instalada abre DriveLab en el tablero, sin clonar la pantalla central ni depender de la ventana de T1Launcher.

## Implementación
- Mantiene Display 1 y los ajustes previos. No reclama HOME ni toca el Cluster tercero.
- La pantalla de configuración lista actividades públicas MAIN/LAUNCHER y permite elegir Waze, Google Maps u otra app visible. Android 11 consulta explícitamente Google Maps y Waze; sin QUERY_ALL_PACKAGES.
- Excluye la app propia, T1Launcher HOME, com.autolink.*, SystemUI y Ajustes. Revalida el paquete y su activity antes de iniciar.
- Guarda aplicación y etiqueta; cambiar la app DESACTIVA su autoarranque hasta validar la nueva elección. No desactiva la ruta de navegación Waze.
- Prueba de Waze y prueba de app elegida requieren P/0 km/h confirmados por OEM; usan ActivityOptions.setLaunchDisplayId para el display elegido y no abren ventana flotante ni hacen fallback al centro.
- Al encender, si el usuario ya habilitó autoarranque y el servicio Receiver inicia, se solicita abrir la aplicación elegida. Default Waze.
- Los destinos desde Sender, voz y Maps compartido seguirán enviándose a Waze, aunque la app de arranque elegida sea Google Maps. Una navegación entrante puede desplazar visualmente la otra app.

## Limitaciones
- Android puede aceptar startActivity sin que la app se dibuje; eso pasó en Display 2. Cada app se prueba físicamente con el vehículo detenido.
- La división simultánea Waze/Maps todavía NO está implementada; depende de multiwindow/ROM y debe garantizar que no se oculten instrumentos del vehículo.
- Cluster de T1Launcher puede seguir controlando el micrófono y el volante. Mantener opción de reactivarlo.