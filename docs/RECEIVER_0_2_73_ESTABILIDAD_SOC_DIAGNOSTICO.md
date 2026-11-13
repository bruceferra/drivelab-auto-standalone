# Receiver 0.2.73 · Perfil optimizado estable y diagnóstico real de SOC

## Problema observado por el usuario
- En CIUDAD el modo optimizado funciona con SMART, pero luego vuelve a mostrar 1/3, 2/3, 3/3 pese a seguir en la misma vía.
- El perfil de AUTOPISTA con SOC FORZADO supera dos pasos y falla en el tercero. En pruebas previas, la ECU llegó a mostrar SMART con objetivo forzado 20% pese a que getter 1041 reportó RAW 56 y el controlador interpretó lectura no confirmada.
- El usuario indicó expresamente no deshabilitar las escrituras de 1040, 1041 y 1046. SOC FORZADO solo tiene los valores 20,30,40,50,60,70.

## Correcciones
1. T1OptimizedAutoGate conserva la vía clasificada tras un cambio exitoso de 1043. Sigue recibiendo GPS y telemetría para detectar **cambio de vía real**, pero no reinicia votos tras cada orden. Si desaparece GPS o la clasificación es desconocida, vuelve a exigir confirmación.
2. T1SocRegenTransitionGate compara clave de destino estrategia+objetivo(forzado)+regeneración. Solo necesita 3 lecturas consecutivas para **un destino nuevo**. Una vez confirmada una configuración, no repite 1/3 ni reenvía la misma orden en cada ciclo de GPS. Si cambian preferencias o el SOC entra/sale de una condición, el destino cambia y la nueva transición se evalúa de nuevo.
3. El piloto no exige ni consulta el SOC objetivo anterior de 1041 para escribir el nuevo. Se envía solo el nuevo destino autorizado y se comprueba después con getter entero y flotante independientes. No se transforma RAW 56 en un porcentaje y nunca se envía rollback a 1041. Conserva doble preflight de seguridad de marcha/velocidad, un intento por ID y fail-stop si el OEM no confirma.
4. Diagnóstico de cada intento visible desde CONFIGURAR OPT. → **VER / COMPARTIR DIAGNÓSTICO SOC · 1/2/3**. Registro privado persistente:
   - ETAPA_1_PRECHECK / OK: marcha y velocidad, más lectura de estrategia/regeneración
   - ETAPA_2_WRITE_REQ / BINDER_OK: setter y reconocimiento Binder (esto NO significa éxito físico)
   - ETAPA_3_READBACK / LECTURA / OK / INCIERTA: qué devuelve OEM 1041 entero y flotante comparado con el nuevo objetivo
   - SOC_REGEN_AUTO_ERROR: causa final, sin VIN ni ubicación GPS
5. No se afirma que el getter de 1041 sea una lectura fiable del menú Jetour. La prueba de campo servirá para saber si el error sucede antes de la escritura o en el getter posterior.

## Diagnóstico para enviar
Con el auto detenido, entrar en CONFIGURAR OPT., tocar VER / COMPARTIR DIAGNÓSTICO SOC · 1/2/3 y compartir el informe después de una única transición. Si la ECU rechazó la orden, no insistir hasta verificar manualmente en el menú original.

## Waze / volante
Sigue siendo experimental el servicio de Accesibilidad y calibración de teclas físicas en RUTAS · VOLANTE (KEYCODEs de Android). Depende de que T1Launcher/ROM entregue eventos y de que Waze exponga rutas alternativas en el árbol de accesibilidad. No hay confirmación de control físico ni API oficial de Waze en Jetour T1.
