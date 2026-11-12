# Receiver 0.2.38 — zona libre fija para el asistente Jetour

11/11/2026 · Jetour T1 PHEV argentina · Sender 0.2.16 sin cambios.

## Evidencia del propietario

El Receiver 0.2.37 **sí ocupa toda la pantalla**: desaparecieron la barra Android inferior y la franja gris con hora, y el velocímetro quedó bien. Permanece un **overlay OEM flotante superior** de tres esferas y texto «Hola, Jetour», que no desaparece con `setSystemUiVisibility`, fullscreen, ni con `WindowInsetsController.hide`. Esto confirma que no es simplemente la barra de estado estándar de Android. No interferir, interceptar ni ocultar ese overlay.

## Comportamiento nuevo

* **Una banda superior VACÍA**, con fondo liso, sin texto, iconos ni botones debajo del overlay, **fuera del ScrollView**: no se desplaza al recorrer la aplicación, impidiendo que un texto quede accidentalmente por detrás del asistente flotante cuando se hace scroll.
* Altura inicial **112 dp**, configurable en ciclo `0 → 80 → 112 → 144 → 176 → 0 dp`, guardada en preferencias locales para todas las pantallas Receiver. No se usa `px` fijo: dp escala según la densidad 213 del sistema T1.
* En el cockpit la zona es de color **azul oscuro idéntico al fondo** `0xFF091421`; en pantalla principal/diagnóstico y tablero técnico es fondo blanco neutro. No se pinta un bloque opaco encima del asistente: el overlay OEM sigue dibujándose por encima de DriveLab.
* Se puede ajustar desde `COCKPIT → ESPACIO JETOUR · N dp` o `Receiver principal → ESPACIO LIBRE SUPERIOR JETOUR · N dp · AJUSTAR`. El ajuste cambia altura en vivo sin cerrar el viaje ni modificar grabaciones.
* Si hay una versión argentina con overlay diferente, se puede subir a 144 o 176 dp, bajar a 80 dp o deshabilitar la franja con 0 dp sin root ni intervención del firmware.
* Se conserva el botón independiente `MOSTRAR BARRAS / PANTALLA COMPLETA` y el modo inmersivo de 0.2.37.

## Implementación

* `T1ImmersiveActivity.setContentView(View)` envuelve la vista de cada Activity con un `LinearLayout` vertical: un `View` fijo, no clickable/focusable, de altura persistida y el contenido existente con `layout_weight=1`. No altera márgenes del contenido, ni sobreescribe ventanas del sistema.
* El cockpit sobreescribe `overlayClearBackgroundColor()` para usar el mismo color oscuro del tablero; las demás pantallas conservan el fondo claro.
* El asistente OEM **nunca** se controla desde DriveLab; el objetivo es que sus esferas queden flotando sobre un área despejada. La medida es visual, sin modificación de seguridad, control de vehículo o configuración global.
* Las otras ventanas de Jetour podrían seguir en primer plano en la ROM; no garantizar que esta banda cambie su composición.

## QA física, detenido en P

1. Instalar **Receiver 0.2.38 firmado** sobre la 0.2.37, sin desinstalar.
2. Abrir Cockpit. Verificar que «Hola, Jetour» y las esferas queden sobre el fondo oscuro vacío, y que título/controles empiezan **debajo** del overlay.
3. Desplazarse hacia abajo: la zona libre debe permanecer fija, sin títulos que pasen por debajo de las esferas.
4. Si la banda es insuficiente, tocar **ESPACIO JETOUR · 112dp** hasta 144dp o 176dp. Si ocupa demasiado, optar por 80dp. Para restaurar a cero, recorrer el ciclo.
5. Comprobar que el velocímetro, inicio/final de trayecto, lectura SOC y controles OEM no cambian; Sender sigue en 0.2.16.

Prueba JVM/CI estática de altura permitida, Activity compartida, persistencia y ausencia de permisos OEM nuevos; compilación Android y certificado de firma original.
