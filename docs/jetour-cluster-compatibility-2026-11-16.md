# DriveLab Auto · pruebas de clúster Jetour T1 (16/11/2026)

## Confirmado físicamente
- Android 11 Display 1 (1920×720): Waze a la izquierda y Google Maps a la derecha, divisiones 50/50 y 60/40 comprobadas.
- CLÚSTER ON abre Waze; CLÚSTER OFF cerrado correctamente con ADB local autorizado y `am stack remove` de las tareas verificadas de Waze / Google Maps; reaparecen los instrumentos OEM.
- Receiver v0.3.13 elimina la restricción P/0 **solo** para CLÚSTER OFF (salida/restauración); modificaciones de geometría siguen limitadas a P/0.

## Ventana derecha: problema por aplicación (no universal)
- **Spotify**: repetidos flashes/recreación mostrando el fondo OEM entre frames.
- **Pro Manager**: mismo flicker y aparición del fondo.
- **YouTube**: permanece estable en la misma ventana derecha.
- **Waze y Google Maps**: división y tamaño se ven estables en las pruebas del usuario.
- Diagnóstico correcto: NO inferir que todo el panel derecho funciona mal. Investigar por app `onConfigurationChanged`, actividad recreada, transición/lifecycle al recibir `setLaunchBounds` / modo freeform, compatibilidad `resizeableActivity`, rendering Surface y overlays OEM.
- Antes de modificar ventanas, capturar en P y parado por ADB los eventos de `ActivityTaskManager`/`WindowManager` y tareas/superficies de app concreta. No introducir loops automáticos de relanzamiento, resize o focus que puedan intensificar parpadeos.
- No atribuir causa a Spotify o Pro Manager sin logs que la respalden. YouTube estable es el caso de control.

## Próximas funciones
- Botones OEM del volante: rueda para mover foco en Waze/apps, OK para aceptar y Atrás para retroceder. Evitar cualquier redistribución involuntaria a funciones OEM (ventanas, plegado, etc). Hacer pruebas de lectura sin escribir para cada código.
- Mejorar transición/degradé entre ventanas, solo después de estabilidad.
- Sender: predeterminado persistente Waze / Google Maps / Ambos; navegación simultánea a dos apps exige configuración split aprobada. Validar que ambos *inicien navegación* real en Display 1 antes de declarar éxito.
