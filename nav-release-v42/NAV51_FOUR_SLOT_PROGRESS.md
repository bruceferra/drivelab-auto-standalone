# NAV 51 | Cuatro espacios A/B — desarrollo aislado sobre LAB 50

**Estado:** código de política y controlador de intercambios con pruebas Java.
**NO incorporado a la aplicación, sin APK nuevo ni OTA.**
LAB 50 (0.1.52-lab.50, code 54) sigue siendo la versión funcional
confirmada en el vehículo. Sender Xiaomi y Receiver permanecen intactos.

## Objetivo confirmado por el conductor

- Central A y Central B son las dos ventanas táctiles de la T1.
- Tablero A y Tablero B son dos ventanas del mismo tablero físico;
  con una sola aplicación el tablero usa el ancho disponible.
- Cada panel de la central tendrá un control discreto Mover ↗.
  Al tocarlo se listarán Central A/B y Tablero A/B. Si el destino está
  ocupado se **intercambian** las aplicaciones. Si está libre se mueve
  sin sustituirla.
- Desde la central se podrá seleccionar también Tablero A ↔ Tablero B;
  el tablero NO necesita táctil ni ruedita.
- Maps, Waze, Spotify y otras aplicaciones **compatibles con displays
  secundarios** podrán elegirse. No garantizar una app arbitraria si
  Android impide ejecutarla en un VirtualDisplay.
- Sender debe continuar entregando a la **misma tarea** de Maps o Waze
  aunque cambie la superficie donde se vea. Si existen dos instancias
  iguales, obligar a elegir destino explícitamente.

## Arquitectura

Los tres displays virtuales de LAB 50 pertenecen respectivamente a
Maps central, Spotify central y Waze clúster. No mover tareas Android ni
volver a lanzar esas apps al intercambiar.

1. Crear un cuarto panel de presentación en el clúster.
2. Reservar una cuarta superficie virtual NAV para una aplicación adicional
   solo cuando el usuario la elija. No modificar los tres IDs existentes.
3. Mantener un mapeo app/tarea/VirtualDisplay → panel visible, con sesión
   y revisión monotónicas. El mapeo sólo se publica tras comprobaciones.
4. Al mover una app, VirtualDisplay.setSurface() redirecciona la
   visualización del mismo display virtual al panel de destino.
   En un intercambio entre dos apps, rebind de ambas superficies.
5. Confirmar el propietario de cada VD, doble lectura de identidades
   task/stack y aparición de fotogramas en ambos destinos.
6. Ante error, restaurar **ambas** conexiones anteriores. Si no se
   confirma la restauración, bloquear nuevos movimientos y presentar
   recuperación segura sin force-stop ni desplazamientos OEM.
7. Recalcular gestos de la central según el VD actualmente visible en
   cada panel; rechazar gestos en el tablero físico.

## Trabajo ya materializado en esta rama

- NavFourSlotPolicy.java: cuatro espacios, inventario 1:1,
  revisión/sesión, destino libre u ocupado, routing de Sender.
- NavFourSlotSwapController.java: doble comprobación de identidad,
  verificación de hosts NAV, cambios de superficies, espera de fotogramas,
  publicación sólo tras éxito y rollback si falla.
- Pruebas Java de ambos: intercambio ocupado/libre, duplicación de Maps,
  autenticación, error de Android tras un cambio parcial, rollback
  verificable y rollback incierto bloqueado.

**Siguiente integración indispensable antes de una OTA:** driver Android
real para las superficies y los fotogramas, layout doble A/B de la
Presentation física, selector táctil y compatibilidad del Sender cuando
hay dos instancias de la misma app. Eso requiere una compilación Android
aislada y una prueba en la Jetour estacionada. No habilitar todavía
movimientos de tareas reales.

El diseño no intercepta rueda, micrófono ni botones OEM; esos siguen
pendientes y son módulos separados.
