# DriveLab Auto · comparadores SOC inclusivos y estrictos (17/11/2026)

Se amplían los límites de SOC REAL OEM para reglas 1 y 2 de las cuatro vías: CIUDAD, CARRETERA, AUTOPISTA y DESCONOCIDO.

## Editor
- Límite mínimo: selector ≥ o >, con porcentaje de 5 a 100 en pasos de cinco.
- Límite máximo: selector ≤ o <, con porcentaje de 5 a 100 en pasos de cinco.
- Cada límite se habilita/deshabilita de manera independiente. Los comparadores aparecen en cada perfil, tanto regla 1 como 2.
- SOC FORZADO (OEM 1041) conserva exclusivamente los presets originales 20/30/40/50/60/70; los operadores se aplican al SOC real medido, nunca al SOC forzado.
- El operador y el porcentaje se guardan como parte de la regla. Configuraciones previas se cargan inclusivas ≥ y ≤, conservando su comportamiento.
- No se guarda un intervalo vacío (por ejemplo >30 y ≤30 dentro de la misma regla).
- Dos reglas que se tocan exactamente en un punto solo se superponen si las dos incluyen dicho punto.

## Ejemplos comprobados por tests
- Regla 1 SOC ≤20; Regla 2 SOC >20: no se superponen en 20.
- Regla 1 SOC <30; Regla 2 SOC ≥30: no se superponen en 30.
- Regla 1 SOC ≤20; Regla 2 SOC ≥20: sí se superponen en 20 y se bloquea el guardado.
- Regla 1 SOC >20: 20 excluido y 20.01 incluido.
- Regla 2 SOC <30: 30 excluido y 29.99 incluido.
- Cuando ningún umbral disparó, no se emiten órdenes OEM nuevas. Entre ambos umbrales se conserva solo la última regla confirmada por el vehículo, como antes.
- Una edición de operadores invalida la firma del perfil OEM retenido, por lo que no se reutiliza un modo guardado como confirmado cuando ya no corresponde.

## Alcance
Cambios en T1OptimizedModePolicy, T1OptimizedDualRulePolicy, T1OptimizedModeStore, T1OptimizedModeActivity y firma T1OptimizedAutoService. No cambia el hardware OEM, ni la política de validación de escritos EV/HEV/regen; requiere confirmación física de comportamiento en Jetour antes de confiar en nuevas transiciones automáticas.
