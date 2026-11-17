# DriveLab Auto · prueba experimental de split nativo en clúster Jetour T1

Fecha: 17/11/2026. Base: Receiver 0.3.36, sin prueba física de la nueva ruta.

## Problema observado
- En Display 1 (1920x720) Waze y Maps pueden visualizarse en FREEFORM 60/40, pero la accesibilidad de Waze deja de exponer botones al dividir, aunque en pantalla completa el volante controla el foco.
- FREEFORM (setLaunchWindowingMode(5) + setLaunchBounds y ADB resize) no es split-screen nativo.
- Android 11 contempla WINDOWING_MODE_SPLIT_SCREEN_PRIMARY=3 y SECONDARY=4. El soporte global no garantiza que Jetour tenga organizer nativo en Display 1 (secundario).

## Prueba incorporada
- Menú técnico, no controles diarios: «PROBAR SPLIT NATIVO EN DISPLAY 1 · EXPERIMENTAL».
- CLÚSTER ON, diseño SPLIT, Waze izquierda y Maps derecha, ADB local autorizado y lectura OEM de P/0. Consulta de solo lectura 'am supports-split-screen-multi-window'.
- Solo con resultado 'true', intenta, usando componentes LAUNCHER resueltos localmente:
  - am start --display 1 --windowingMode 3 -n com.waze/<actividad>
  - am start --display 1 --windowingMode 4 -n com.google.android.apps.maps/<actividad>
- Revalida P/0 antes de ambas órdenes. Lista blanca fija de componentes, sin root, sin cambiar ajustes globales, sin inyección de teclas, sin tocar Display 0.
- Consulta 'am stack list' y si hace falta 'dumpsys activity activities' para identificar tareas en Display 1.

## Interpretación y recuperación
- El éxito de am start, del soporte global o de la enumeración de tareas NO prueba split nativo en Display 1 ni que ambas superficies se vean.
- Observar físicamente en P: Waze y Maps simultáneos, botones visibles con la rueda/OK/Atrás. La prueba no modifica controles de volante.
- No se guarda como modo. «APLICAR» en paneles recupera el FREEFORM anterior (en P). CLÚSTER OFF sigue recuperando instrumentos OEM.
- Sin organizer split en Display 1, no forzar con root, shell arbitraria ni APIs OEM no verificadas.

## Estado
Código preparado para compilar y probar en la T1. No declarar split nativo verificado sin prueba física.
