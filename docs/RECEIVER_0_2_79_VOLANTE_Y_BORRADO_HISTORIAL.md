# Receiver 0.2.79 (code80) — diagnóstico de ruedita y borrado de viajes

## Reporte Jetour T1
Después de usar ELIMINAR TODO, el propietario observó **Clientes HOY = 0 y Clientes ESTE MES = 0**, pero en el panel inferior de dos columnas todavía figuraban viajes 1/2 y jornadas. No se debe confundir un contador a cero con eliminación comprobada del CSV: el cambio puede provenir del reset de preferencias de la sesión mientras la UI mantiene una instantánea previa. Además los resultados del borrado se escribían en una caja alejada del botón, fuera del historial expandido.

## Causa en código
- `T1TripDeletion.deleteAll` borra los CSV locales elegibles y limpia el estado de viajes. El método antiguo no comprobaba que la carpeta quedase sin esos CSV ni advertía si fallaba la limpieza de la cuarentena.
- `T1PremiumCockpitActivity.updateHistory` procesa una carga asíncrona. Una respuesta creada antes del borrado puede llegar después y **repintar viajes ya borrados**, y una nueva carga pedida mientras la anterior está ocupada se descarta. También se usaba `cachedMasterHistory` de jornadas antiguas sin releer siempre.
- `message()` muestra errores/éxitos en `feedback` junto a los controles principales, muy arriba de BORRAR SELECCIONADO / ELIMINAR TODO, por lo que el usuario no ve ninguna respuesta.

## Corrección
- `T1TripDeletion.blockReason` informa exactamente si VIAJE TOTAL, VIAJE 1 y/o VIAJE 2 están activos, pausados o pendientes. Sin cierre completo no borra nada. Se conserva el límite de seguridad; no se cierra ninguna jornada automáticamente.
- `deleteAll` comprueba que se limpiaron los CSV locales; no afirma éxito si los archivos quedaron en cuarentena o continúan en `t1_trips`; `deleteSelected` vuelve a leer el CSV correspondiente al viaje tras reescribirlo y fallará si el ID sigue presente. El borrado no toca `grid_charges.csv`, mapas, perfiles, ni nube/Supabase.
- La UI muestra ahora un texto persistente **justo debajo de los botones rojos**, y un diálogo al finalizar, tanto para errores de confirmación como bloqueo o éxito verdadero.
- Al confirmar el borrado incrementa `historyDeleteRevision`, invalida toda carga histórica anterior y elimina de inmediato las vistas de **Viaje 1 y 2** y **Jornadas**. Reconstruye `T1MasterTripHistory` desde disco, vuelve a cargar `T1JourneyStore.history` y `T1JourneyStore2.history`, y solo entonces pinta las tarjetas nuevas. No conserva tarjetas fantasmas.
- Se impide iniciar la reconstrucción histórica OEM mientras está activo el borrado, y se bloquea el borrado mientras se reconstruye el historial.
- Test CI `T1HistoryDeletionSourceContractTest` valida estos contratos y se ejecuta antes del APK firmado.
- El resto del cambio 0.2.79 conserva el diagnóstico seguro de la ruedita directamente en `RUTAS · VOLANTE` sin pasar por Waze, sin interferir en la acción Zoom OEM.

## Prueba física
Con todas las grabaciones **cerradas** y el auto detenido:
1. Elegir un Viaje 1/2, pulsar BORRAR SELECCIONADO y confirmar; ver mensaje contextual junto al historial y tarjeta desaparecida.
2. O usar ELIMINAR TODO y escribir exactamente ELIMINAR TODO: deben quedar **ambas columnas sin registros** y contadores diarios/mensuales en cero, sin duplicarse al entrar y salir del panel.
3. Si no pudo eliminar, el diálogo y la etiqueta del historial muestran motivo. No borrar datos desde ajustes Android ni ejecutar shell destructivo por fuera de DriveLab.
