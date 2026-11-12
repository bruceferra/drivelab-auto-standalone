# Receiver 0.2.48 — recuperar los relojes OEM, retirar solamente velocímetro

## Ajuste solicitado
En v0.2.47 se eliminó por error todo el bloque de instrumentos cuando el propietario solo había pedido retirar el velocímetro grande. En v0.2.48 se vuelve a crear `T1PremiumInstrumentView` en la primera pantalla, en modo `setCompactWithoutSpeed(true)`.

## Cuatro instrumentos originales que se conservan
1. **Combustible** (CI186): lectura OEM en litros y barra de nivel. La capacidad de barra 67 L se mantiene como referencia experimental; no se altera la calibración.
2. **Batería HV SOC**: porcentaje y barra de carga, más la lectura del modo OEM 1043 (EV / HEV / EV MAX).
3. **Motor térmico**: estado derivado de RPM 1035 y lectura de RPM, sin afirmar comprobación física.
4. **Batería HV / flujo**: icono de batería, estimación de carga/descarga de corriente 1064 y amperaje con signo pendiente de validar.

La función `update(s.speed,s.fuel,s.socDisplay,s.powerMode,s.rpm,s.current)` vuelve a llamar a `invalidate()` cada nueva muestra OEM, para no congelar los indicadores.

## Diseño compacto
La nueva fila de cuatro tarjetas tiene altura proporcional de `178 / 1200` del ancho; no se dibuja el velocímetro circular ni su aguja ni la tarjeta de km/h. Se mantiene el resto de la primera pantalla: 3 botones de modos, Jornada y Cliente con Iniciar/Pausar/Terminar, temperatura del motor y de batería, viaje activo, kilómetros, nafta, electricidad, costo y autonomía. Los detalles permanecen desplegables.

No se modifican servicios, controles automáticos, viajes, informes, GPS, modos ni seguridad OEM respecto de 0.2.47.

## OTA
Receiver 0.2.48 / versionCode 49. Se instala sobre la versión anterior, conservando sus datos y firma. Sender 0.2.16 permanece sin cambios.
