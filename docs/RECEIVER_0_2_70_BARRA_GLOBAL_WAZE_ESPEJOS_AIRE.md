# Receiver 0.2.70 · Barra global, Waze desde el volante, espejos y aire

## Barra fija y scroll
- `T1ImmersiveActivity` incorpora una barra **compartida** fuera del contenido `ScrollView`: primera fila DRIVELAB/T1 + EV / HEV / EV MAX (lectura 1043 y botones manuales OEM); segunda fila horizontal desplazable con OPTIMIZADO ON/OFF, CONFIGURAR OPT., ESPEJOS PLEGAR/DESPLEGAR, alarma peatones 2074, VENTILACIÓN TOTAL, CIERRE TOTAL, estado aire interior, COCKPIT, VEHÍCULO, PENDIENTES, IA/WAZE, RUTAS/VOLANTE e INICIO. El cuerpo de cada pantalla mantiene scroll y el encabezado nunca se desplaza.
- Los botones antiguos PANTALLA (mostrar/ocultar barras), SIMULADOR y la barra de rescate duplicada no se muestran arriba; pantalla inmersiva encendida por defecto y margen HolaJetour=0 porque MainthorCar lo eliminó.
- El simulador sigue accesible desde pantallas secundarias, al igual que controles avanzados OEM del hub.

## Espejos automáticos de reversa y botón manual
- `MirrorAutomationService` se activa al iniciar Receiver si no existe una condición de posición incierta; watchdog reanuda **un modo previamente armado** cuando cambia la pantalla. No intenta recrear una secuencia de pulsos parcialmente ejecutada.
- Inclinación al detectar una **transición nueva a R** en modo automático conserva velocidad de maniobra.
- **Retorno a la posición D**: la máquina de estados detecta D OEM válida durante 800 ms y solicita los pulsos relativos inversos **sin exigir frenar, superar 10 km/h ni mantenerse por debajo de 75 km/h**. Durante el movimiento debe mantenerse D y una velocidad OEM plausible; se registran lecturas.
- **RECUPERAR ESPEJOS 10 PASOS ARRIBA** sigue siendo un botón manual para recalibración, solo con auto detenido, porque manda 20 pulsos temporizados sin sensor de ángulo. No se habilita como operación sin restricciones en movimiento. El retorno automático es la operación adecuada al pasar a D.
- PLEGAR/DESPLEGAR usa **una sola acción** que lee el estado 2003 (0 plegado, 1 desplegado), elige el inverso solamente si pudo leer, verifica precondiciones de maniobra lenta y hace un único setter con lectura posterior. **No** hace un segundo "release=0" (ese cero significaría plegar). La posición física requiere verificación visual en el vehículo.

## Recirculación
- Se preselecciona `MODE_INTERIOR` y se inicia el supervisor de fondo al abrir una vez DriveLab en el proceso. El servicio lee 1004 cada 3 s y solicita 1004=1 si observa 1004=2.
- El watchdog en cambio de Activity recupera un servicio detenido inadvertidamente, **pero no** ignora `PAUSED`: si la ECU revirtió la configuración tres veces/minuto o hay cuatro errores, pausa para no interferir con desempañado/visibilidad.
- El botón fijo AIRE INTERIOR AUTO indica ON verificado, PAUSADO o REVISAR; desde el diálogo se puede reactivar el modo pausado conscientemente, en condiciones seguras.

## Waze rutas alternativas desde botones
- Estudio de T1Launcher T1N134: el APK contiene servicios de Accesibilidad y módulos `VozVolante`, `MandoMapa` y `T1MapaServicio` ligados al mando y a Google Maps. La copia pública de GitHub solo incluye instalador web, no código fuente del APK.
- Esta implementación es **independiente**: `T1WazeWheelService` es opt-in desde Ajustes → Accesibilidad y requiere habilitar CONTROL WAZE. No tiene permisos OEM ni root. Solo se activa si el primer plano tiene paquete `com.waze`.
- `T1WheelSettingsActivity`: calibrar los eventos de siguiente, anterior, OK, Atrás y RUTAS opcional (si las teclas físicas se entregan como `KeyEvent` a Android). En calibración no consume la tecla ni interfiere con el OEM.
- En el mapa OK o tecla RUTAS busca el control accesible "Rutas alternativas", "Routes", etc., sin tocar coordenadas; en la lista detectada con 2 a 6 tarjetas etiquetadas y título/encabezado válido, siguiente/anterior recorren, OK hace clic en el nodo, Atrás vuelve. Si faltan etiquetas, si Waze no entrega el árbol o si T1Launcher consume la tecla antes, **no se envía una orden alternativa**.
- Los controles que no puede identificar siguen en Waze. Nada de inyección global, auto-click o gestos con pantalla apagada.
- **No probado físicamente**: la ROM, la versión concreta de Waze, selección de rutas y posibles conflictos con el mando de T1Launcher se deben verificar estacionado antes de habilitar la función en uso.

## Verificaciones obligatorias
- Compilar APK código 71, firmada con **la misma identidad** de Receiver 0.2.69; el OTA Receiver debe ser 0.2.70 y Sender mantenerse en 0.2.16.
- Comparar pantalla Cockpit/Hub/Vehicle/Voz/Historial con barra fija y scroll de contenido.
- Probar espejos primero en parado: R inclina, D devuelve sin frenar; confirmar físicamente ambos lados, registrar última transición, 1089/2148 y error si lo hay.
- Probar recirculación: confirmar 1004=1 en servicio, cambiar a Cockpit y Waze, y verificar `LAST_POLL`; respetar `PAUSED`.
- Probar Waze: activar Accesibilidad, aprender 5 teclas, comprobar `LAST_KEY` con Waze abierto, mostrar botón accesible de rutas, seleccionar opción alternativa A/B/C con el auto estacionado. Si un control no es accesible, recoger nombres de nodos de forma voluntaria, no adivinar coordenadas.
