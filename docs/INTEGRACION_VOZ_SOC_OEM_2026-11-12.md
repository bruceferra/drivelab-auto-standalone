# DriveLab Auto — voz en clúster y gestión SOC OEM · 12/11/2026

## Alcance de esta rama
- Se agrega botón `🎤 DESTINO` visible en la cabecera de Cockpit y `DESTINO POR VOZ · WAZE CLÚSTER` en el inicio de Receiver.
- Nueva actividad `T1VoiceRouteActivity`: utiliza el servicio de reconocimiento de voz instalado en Android mediante `RecognizerIntent`, propone destino en español, permite editarlo por teclado y **requiere confirmación humana** antes de abrir `com.waze` con enlace `waze://?q=...&navigate=yes`.
- Si la ROM T1 no cuenta con reconocedor de voz compatible, la app informa la ausencia y mantiene la entrada por teclado. El resultado en clúster requiere prueba física. No se solicitan grabaciones permanentes ni permisos de fondo.
- No depende ni abre automáticamente una APK separada de DriveLab Voz/Driver Mic. La implementación nueva es independiente; para replicar exactamente el procesamiento de voz de la app antigua hay que recuperar su código o APK real.

## Hallazgo probado en APK OEM, no en vehículo
En los DEX del `ALVehicleSettings`, el enum `CarPhevSocMode` lista:
- 0 `NotActive`
- 1 `Default`
- 2 `SAVE`
- 3 `Auto`
- 4 `Charge`

El propietario confirmó en Jetour T1 argentina lectura `1040=2` cuando el modo visible es **Smart**, y `1040=4` cuando es **Forzado**. El valor `1040=3` es una definición estática OEM, NO un modo probado ni confirmado como utilizable en la ROM argentina. Por ese motivo las dos pantallas de lectura existentes ahora etiquetan `3` como `AUTO OEM NO PROBADO`, sin agregarlo como control seleccionable.

La clave `1041` está denominada `KeyHvacPhevSetSoc` y corresponde al objetivo SOC. El OEM también define valores 20,30,40,48,50,56,60,64,70,72 en el enum `CarSocElectricityMode`. **No asumir que el valor del enum, el porcentaje y el entero del Binder tienen la misma codificación.**

## Diferencias entre ver y accionar
- Confirmado del vehículo: lectura `1040=2/4`, lectura `1041` y comportamiento del menú original, y cambios `1043` desde Receiver en P. El usuario también confirmó cambio de modo automatizado por OSM en ciudad el 12/11.
- No demostrado en estas muestras: escritura desde el paquete Receiver a `1040` o `1041` con readback posterior, tipo de setter de `1041`, semántica operativa de `1040=3`, efectos parciales del rechazo y aceptación de comandos SOC en D.
- El servicio automatizado existente **continúa escribiendo solo `1043`**. Las preferencias Smart/Forzado/objetivo SOC/regeneración siguen sin escritura; no describir el automatismo como `SOC completo`.

## Validación y prioridades
1. Probar que el botón de voz funcione con un proveedor Android en T1. Dictar un destino inequívoco, comprobar el texto mostrado y el pin final de Waze. Si no hay servicio, usar teclado. Todo con vehículo detenido.
2. Para SOC, documentar primero el **tipo real** del getter y setter de `1041`, `1040` y leer readback tras tocar la configuración original; después implementar un ensayo **manual, individual, detenido en P**, con confirmación y posibilidad de volver a Smart. Recién con prueba física de escritura, diseñar el paso opcional a modo automático.
3. Para AUTO SOC `1040=3`, confirmar si aparece en la UI OEM de la versión argentina y observar su funcionamiento antes de incluirlo como perfil.
4. Limpiaparabrisas: `2126` sensibilidad automática, `2125` trasero, y HAL de lectura/escritura detectados; sin setter ni valores confirmados desde Receiver. Mantener solo diagnóstico.
5. Luces interiores segunda fila/baúl: lecturas AOSP con áreas `16/32/64`, sin comando de encendido probado; no automatizar todavía. Las luces exteriores traseras no deben confundirse con los plafones de cabina.
6. Cuando el propietario comparta captura de la interfaz OEM Jetour, crear tokens visuales de color/espaciado/fondos en otra rama sin alterar control ni telemetría.

No se cambia Sender 0.2.16, datos históricos, servicio de viajes ni firmware OEM. Compilar con CI y no publicar una release ni prometer funcionamiento físico del reconocimiento sin ensayos.
