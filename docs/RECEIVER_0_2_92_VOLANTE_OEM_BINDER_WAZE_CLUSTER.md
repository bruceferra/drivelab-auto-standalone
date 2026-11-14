# DriveLab Receiver 0.2.92 — Waze en clúster por ruedita OEM (prueba opt-in)

## Evidencia del APK original del propietario

Muestra `T1Launcher-T1N134.apk` analizada estáticamente, SHA-256
`6765a8fcc0645a98424de43c1e122ce771883dd46d6d6ca7264cbe47c94fd480`.
No se copió código de T1Launcher; se documentó su protocolo de interoperabilidad
a partir de descriptores de interfaz, nombres, parámetros y constantes públicos
en el bytecode del APK suministrado por el propietario.

- `VozVolante.iniciar$lambda$1` enlaza explícitamente
  `com.autolink.hardkeyservice/com.autolink.hardkeyservice.core.ALHardKeyService`.
- `VozVolante.llamar(int)` envía `com.autolink.adapterinterface.IHardKeyService`,
  un objeto Binder callback y los números de transacción 1 (registro)
  o 2 (baja). `onServiceConnected` registra, `detener` desregistra.
- `VozVolante$callback$1.onTransact` procesa transacción 3 con token
  `com.autolink.adapterinterface.IHardKeyCallback` y tres enteros:
  evento de botón, estado, valor adicional.
- Se pasa a `MandoMapa.tecla(Context,int,int)` solamente el código 1000–1003,
  con estados 100 o 101. `MandoMapa.hacer` trata 1000 y 1001 como giros
  (zoom +/−), 1002 como Atrás y 1003 como Aceptar.
- `T1MapaServicio.raices` usa `getWindowsOnAllDisplays()` y filtra
  ventanas por paquete, **no** se limita a `getRootInActiveWindow()`;
  `apretar` localiza botones accesibles y `pellizco` envía gestos a un
  `displayId` concreto mediante `GestureDescription.Builder.setDisplayId`.

## Implementación propia

- `T1OemHardKeyBridge`: suscripción OEM explícita, **apagada por defecto**;
  se conecta exclusivamente con Waze visible, libera transacción 2 al salir,
  ignora todos los demás botones (incluida voz).
- `T1WazeWheelService`: encuentra ventanas Waze en cualquier display Android,
  mapea 1000→siguiente, 1001→anterior, 1002→atrás, 1003→OK.
  El usuario debe girar antes de confirmar cualquier botón genérico;
  **no se inventan coordenadas** ni se tocan otros paquetes.
  El modo de rutas alternativas y botones de confirmación conserva su lógica.
- No se modifica el launcher HOME, Waze, Google Maps, la app original ni los
  controles CAN/OEM. Sin root, ADB persistente ni escrituras al vehículo.
- Diagnóstico en `VOLANTE / WAZE / RUTAS` expone enlace Binder, display
  de Waze y último código OEM.

## Límites reales y precauciones

1. No se sabe aún si esta ROM deja registrar más de un callback Binder.
   Si el servicio OEM solo admite uno, la suscripción de DriveLab podría
   pausar temporalmente funciones de volante de T1Launcher.
   Desactivar el puente y, si no reaparecen, reiniciar T1Launcher.
   No se activa sin consentimiento ni se mantiene en Google Maps.
2. Android puede negar `bindService` desde una APK externa: la pantalla
   muestra el error; no insistir con permisos de root ni alterar seguridad.
3. Waze puede dibujar parte de sus controles con Canvas sin nodos
   `AccessibilityNodeInfo`. En ese caso no se pulsa a ciegas. Un botón
   etiquetado permite `ACTION_CLICK` y un listado admite navegación.
4. Probar únicamente **estacionado en P**. No probar selección de rutas
   ni diálogos mientras se conduce. Un APK compilado no demuestra que el
   Binder y las ventanas sean accesibles en el auto real.
5. Antes de probar, mantener el servicio accesibilidad
   `ar.com.rango5gps.drivelabauto.receiver/.T1WazeWheelService`
   activo como en los diagnósticos anteriores. Si no existe, no se podrá
   inspeccionar Waze en pantalla 2.
6. Desactivar la opción `CONECTAR / DESCONECTAR RUEDITA OEM → WAZE`
   deja Google Maps en su implementación anterior. Si T1Launcher también
   aplica zoom durante la prueba Waze, desactivar temporalmente su opción
   `VOLANTE → MAPA` para evitar movimientos duplicados; reversible.

## Prueba de aceptación en el vehículo

Con el coche parado en P, Waze visible en el clúster:

- Entrar en Receiver → Volante / Waze y activar el nuevo puente OEM.
- Verificar estado `Binder OEM enlazado` y `Display 2 · Waze accesible`.
- Girar la ruedita: se debe actualizar `Última señal OEM` con 1000/1001
  y resaltar solamente botones reales accesibles de Waze.
- OK 1003 acciona el botón que acabás de resaltar; ATRÁS 1002 cancela.
- Salir de Waze: el puente se desconecta. Reabrir Google Maps y comprobar
  que conserva sus controles normales.
- Si falla, registrar las **dos cadenas exactas**: `PUENTE OEM WAZE`
  y `Waze en clúster`, además del código/estado al girar.
