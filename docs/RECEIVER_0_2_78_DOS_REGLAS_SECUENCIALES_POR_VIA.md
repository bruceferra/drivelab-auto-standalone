# Receiver 0.2.78 (code79) · Ciudad 1/2, Carretera 1/2, Autopista 1/2, Desconocido 1/2

## Solicitud del propietario
Cada tipo de vía debe tener dos reglas independientes, activables sucesivamente y nunca simultáneamente. Ejemplo real de intención:

- **AUTOPISTA 1**: batería real OEM **≥70%** → propulsión **EV**, gestión **SMART**, regeneración **MEDIA**.
- **AUTOPISTA 2**: batería real OEM **≤30%** → propulsión **HEV**, gestión **SOC FORZADO 40%**, regeneración configurable.
- Al entrar en autopista con 80%, ejecutar AUTOPISTA 1 tras GPS 3/3 y confirmaciones OEM. Si recorre 80 km y baja a 60, 50, 40 o 31%, **no** cambiar a HEV ni tocar SOC/regeneración: mantiene EV. Al llegar a 30% o menos, ejecutar AUTOPISTA 2. Si el térmico carga y sube a 40% o 50%, mantener HEV; recién al volver a 70% disparar AUTOPISTA 1.
- Cuando aún no se ejecutó ninguna de las dos reglas y el SOC está entre umbrales, conservar modo actual sin crear un supuesto perfil aplicado.
- Se aplica igual a CIUDAD, CARRETERA y DESCONOCIDO. Todas las reglas 1/2 tienen almacenamiento separado y un nombre único.

## Implementación
- T1OptimizedModeStore conserva la regla 1 en las claves históricas (`road_MOTORWAY_...`) y agrega la regla 2 bajo `road_MOTORWAY_b_...`. La regla 2 **está desactivada por defecto** para no modificar los perfiles ya guardados. Igual esquema para CITY/ROAD/UNKNOWN.
- T1OptimizedModeActivity muestra la regla 1 y un editor desplegable de regla 2 en cada bloque con su propulsión, SMART/FORZADO, SOC objetivo (solo 20/30/40/50/60/70), regeneración y condiciones independientes mínimo ≥ y máximo ≤ (de 5% en 5%). FORZADO solo editable para HEV. Guardar valida ambos rangos a la vez y rechaza intersecciones inclusivas: dos ramas nunca coinciden al mismo SOC.
- T1OptimizedDualRulePolicy.select selecciona la regla cuyo umbral se satisface, o ninguna si no hay condiciones coincidentes. selectWithHold conserva la última regla **ya confirmada por la ECU**, para evitar un retorno o una orden en el hueco entre umbrales. Una nueva coincidencia de la otra regla siempre prevalece.
- T1OptimizedAutoService anota `heldRoad / heldSlot / heldSignature` únicamente cuando terminaron exitosamente 1043 y 1040/1041/1046, luego de GPS único 3/3 y condiciones válidas. Con SOC entre rangos revisa getters OEM actuales de propulsión, estrategia, SOC forzado (si corresponde) y regeneración; si coinciden, puede refrescar el encabezado **MODO AUTOPISTA 1** o **MODO AUTOPISTA 2** en verde. Si no coinciden, lo deja gris **MODO PREVIO · OEM SIN CONFIRMAR**, sin enviar órdenes dentro del rango intermedio.
- Si se pierde GPS o la telemetría, no se envía una orden. Si cambia la vía, la configuración o se desactiva un bloque, el latch deja de ser elegible.
- La transición **no obliga a mantener el motor encendido** ni a recargar SOC. Solo selecciona los comandos OEM permitidos que el propietario configuró, siempre sujetos a respuesta física de la ECU.
- El recorrido previo exitoso HEV FORZADO → SMART → EV se conserva; EV MAX sigue liberando FORZADO por sí mismo.
- **Desconocido:** el modo de GPS no confiable seguía siendo fail-closed y por diseño no actúa en el controlador OEM actual. Aunque se ofrecen sus reglas 1/2 y claves separadas, no se garantiza su ejecución automática cuando no hay identificación GPS fiable. No se habilita una escritura a ciegas en vía desconocida.

## Pruebas
- T1OptimizedDualRulePolicyTest: AUTOPISTA 1 activa al 80% y queda retenida para 69/60/50/40/31/30,5; AUTOPISTA 2 activa a 30% y sigue retenida a 32/39/50/69 hasta que AUTOPISTA 1 retorne a 70%; rangos superpuestos se rechazan; SOC sin lectura no dispara cambio; segunda regla OFF no actúa; no se extrapolan porcentajes SOC forzado.
- CI Android verifica almacenamiento de ambas reglas, selección con latch, validaciones, el encabezado numerado y ausencia de órdenes en el hueco. Publicación firmada Receiver 0.2.78 código79 con mismo certificado que 0.2.77 y Sender 0.2.16 conservado.
- Validación física: configurar AUTOPISTA 1 ≥70 EV SMART / REGEN MEDIO; AUTOPISTA 2 ≤30 HEV FORZADO 40; hacer la transición real segura observando los 1040/1041/1043/1046 en pantalla original, sin repetir órdenes si 1041 no confirma.
