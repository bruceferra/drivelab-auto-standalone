# DriveLab Auto Receiver · división real Waze / Google Maps en Display 1

Fecha: 2026-11-15. **Implementación experimental; requiere prueba física** en Jetour T1. No afecta al Sender, micrófono 1010, volante, espejos, HVAC ni datos CAN.

## Evidencia y problema previo
- El propietario confirmó que Waze se dibuja a pantalla completa en **Display 1**, no en Display 2, y que Maps llegó a ocupar una fracción de la pantalla mientras Waze permanecía fullscreen.
- En el APK original del propietario `T1Launcher-T1N134.apk` se identificaron `ActivityOptions.setLaunchDisplayId`, `setLaunchBounds`, modo de ventana libre 5 y posterior redimensionamiento de tareas con `am task resize`.
- Los comandos públicos `am task resize <taskId> <left> <top> <right> <bottom>` requieren identidad shell/ADB autorizada; una aplicación Android normal no hereda esos permisos. Su éxito no se garantiza solo por instalar la APK.

## Implementación independiente
- **Puente ADB local autenticado**, exclusivo `127.0.0.1:5555` con RSA de 2048 bits privada de la app. Comunicación protocolo ADB host sobre TCP, sin root, sin Shizuku ni ejecución de comandos arbitrarios.
- Solo acepta listar tareas: `am stack list`, `dumpsys activity activities`; y redimensionar tareas numéricas: `am task resize <id> <4 dimensiones>`.
- Busca tareas de `com.waze` y `com.google.android.apps.maps` **en el display secundario seleccionado**. Si hay tareas ambiguas, falta display, el comando es rechazado o faltan bounds, no modifica otras tareas ni declara éxito.
- Abre Waze a la izquierda y Google Maps a la derecha con modo freeform + límites; después redimensiona **ambas tareas reales** y relee sus dimensiones. El resultado distingue `VERIFICADO POR ADB` de `SOLICITADO, NO VERIFICADO`. Verificación de bounds tampoco garantiza simultaneidad visual.
- La división arranca **50/50**, con slider 30–70 % y botones **+10/−10**. Para cambiarla o restaurar fullscreen se verifica marcha P y 0 km/h con lectura OEM. Cualquier modificación desactiva el autoarranque y requiere nueva prueba, y nunca toca la pantalla central.
- Para aceptar diseño de navegación permanente hay que tener éxito ADB reciente y confirmación visual física, nunca solo `startActivity`.
- Cuando el clúster está en modo split aprobado, los destinos Xiaomi/voz siguen entrando a Waze en la izquierda. El modo FULL sigue siendo el comportamiento predeterminado si el usuario no activa esta función.

## Preparación de ADB (necesaria, no automática)
1. Con la T1 en P, conectá una PC con `adb` a su USB de depuración ya autorizado. Comprobá `adb devices`.
2. Ejecutá **en la PC**, NO en una shell de Android: `adb tcpip 5555`.
3. En la pantalla DriveLab **Clúster → Tamaño / Posición / División** habilitá «Puente ADB local» y pulsá «Probar conexión ADB local».
4. Si Android muestra una nueva petición «Permitir depuración USB» para la clave de DriveLab, autorizala conscientemente. Ese RSA se almacena en el espacio privado de la app. Repetí «Probar conexión».
5. Elegí «Dos apps» y 50/50, después «Probar diseño». Esperá respuesta «VERIFICADO POR ADB» y comprobá **ambas aplicaciones en el tablero**. Solo entonces activá el modo.
6. Si no coincide, tocá **Pantalla completa → Probar diseño**. Si no se pudo activar ADB, siempre se conserva el camino probado para abrir Waze fullscreen.

`adb tcpip 5555` puede exponer el servidor de depuración a la red Wi-Fi, no solo a loopback. **Usar únicamente en una red confiable y desactivar ADB TCP al terminar** mediante `adb usb` desde un host autorizado o al reiniciar si la ROM vuelve a USB. No es seguro habilitarlo permanentemente en una red pública. El puente puede requerir reactivación después de apagar la T1. DriveLab no cambia esta configuración por su cuenta.

## Limitaciones por verificar
- Android 11 del Jetour debe admitir `am stack list` o `dumpsys activity activities` con un `displayId` inequívoco y bounds legibles. La sintaxis de la ROM puede variar; el parser rechaza formatos no demostrables.
- El OEM puede bloquear `am task resize` o reabrir Waze a pantalla completa al recibir otro destino. La verificación real durante la navegación y después de un arranque frío está pendiente.
- Aunque los límites de tarea coincidan, una app puede ocultar visualmente otra debido a reglas de foco/z-order o gestión de la ROM; esto solo se comprueba físicamente.
- No copiar componentes internos de T1Launcher, no root, no tareas arbitrarias, no suplantar HOME ni modificar alertas críticas del tablero.
