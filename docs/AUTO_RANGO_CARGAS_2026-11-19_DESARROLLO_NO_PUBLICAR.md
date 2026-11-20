# DriveLab Auto · informe por rangos con cargas · borrador del 19/11/2026

## Evidencia del usuario (foto de la T1)

Intervalo 2026-11-19 08:00–21:50: 4 encendidos OEM, 207,03 km GPS, 0,00 km OEM, 605,5 min de telemetría, **0,000 kWh salida / 0,000 kWh entrada**. El cero significa *sin mediciones HV válidas* en el informe anterior, no gasto físico nulo. La carga iniciada ~21:40 registra voltaje/corriente HV válidos por VHAL y es una sesión distinta del registrador de conducción.

## Causas confirmadas en source

- `T1AutoDrivePolicy.integrate` anulaba **también la distancia OEM** si fallaba 1063 (voltaje).
- `T1AutoDriveOem.read` no tenía respaldo HAL `559940910` + `559940911`; `T1ChargeOemReader` sí había verificado el respaldo para cargas.
- `T1AutoJourneyStore` no persistía CI186, SOC interno 1042 ni calidad del voltaje, y su `RangeReport.Slice` imprimía 0,000 kWh sin indicar cobertura HV válida.
- `T1AutoJourneyActivity.generateRange` sólo llamaba `T1AutoJourneyStore.between`, mientras las cargas estaban en `T1ChargeStore`. La reconstrucción externa del 18/11 aparecía encima del informe y confundía períodos.

## Correcciones en esta rama (NO EN EL AUTO)

1. Separar kilómetros de voltaje/corriente: GPS y velocidad OEM pueden contar aunque falle el getter HV.
2. VHAL opcional **read-only**, con misma verificación de voltaje y corriente que usa la carga. Si no coincide se guarda SIN HV VERIFICADO. No escribe propiedades ni cambia modos.
3. SQLite de viajes automáticos migra v1 a v2 en modo preservación (columnas nuevas para 1042, CI186 y caída candidata; viejos registros quedan intactos). Cargas siguen en su SQLite independiente.
4. Estimación CI186 por **mínimos sucesivos de nivel**: descarta saltos grandes, subidas pequeñas por oleaje y refuels grandes. No interpreta ese candidato como inyección ni litros certificados.
5. Informe con botones HOY y ÚLTIMOS 7 DÍAS, intervalos libres a minuto, eventos conducción/carga ordenados por tiempo y exportación CSV combinada. Enchufes abiertos/parciales se muestran, pero sus kWh no se contabilizan como cargas completas del intervalo.
6. Indicador de **minutos HV verificados**. Si no hay ninguno, muestra «SIN HV VERIFICADO» en lugar de consumo eléctrico cero. Regeneración y carga del generador térmico NO son separables todavía.
7. La reconstrucción del 18/11 es una referencia externa con título independiente y no se suma a ningún intervalo OEM.
8. No se cambia el estado de autoencendido ni se inicia ningún servicio desde las nuevas opciones de rango; sólo se consulta el SQLite existente.

## Lo que aún NO se puede prometer

- Reconstruir 207 km del 19/11 con kWh y litros que no se registraron: imposible desde un cero histórico. No backfill ficticio.
- Separar físicamente kWh de enchufe, kWh de recuperación por frenado y kWh generados por motor: hace falta identificar señales OEM de fuente y calibrar un modelo contable. El 'kWh pagado imputado' es una estimación de costo, no marca física.
- CI186 mide **nivel restante**, no inyección ni litros consumidos. Sólo es un candidato y requiere trayectos reales con surtidor.
- El costo calculado a partir de kWh HV es una estimación mínima, mientras un medidor de pared puede incluir pérdidas.
- El registrador AUTO actualmente corre dentro de Receiver, no Bridge. NO modificar el autoarranque que el usuario ya desactivó. La futura migración a Bridge requiere un servicio único y permisos.
- Sin validación física, tampoco se garantiza que VHAL 559940910/911 devuelvan valores en conducción aunque sí hayan respondido al enchufar.

## Criterios antes de publicar

1. Pruebas de compilación Android y tests de fórmula, migración SQLite v1→v2, rangos día/semana y superposición de cargas.
2. Validar por 10–20 min estacionado/conducción real con el propietario: tensión y corriente HAL al acelerar/recuperar; porcentajes de cobertura HV, muestras CI186, GPS y odómetro.
3. Confirmar carga activa y finalizada en orden temporal, con dinero y unidades coherentes, y sin duplicar carga manual importada.
4. No silenciar alertas OEM, no borrar rutas/datos, no force-stop, sin activar autoboot por defecto.
