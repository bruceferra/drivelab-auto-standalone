# DriveLab Auto 0.2.62 · Tema Jetour T1 OEM visual

Fecha: 2026-11-13

## Referencia y muestreo

Referencias: seis capturas digitales directas ADB 1920×1080 del software Jetour T1, aportadas por el propietario. Sin transformación de cámara. Los fondos son casi negros con grises carbón; la selección está en cian grisáceo, y los botones activos usan un verde limpio. Se evitó el azul saturado que tenía el primer diseño.

Tokens de la implementación central en `T1JetourTheme.java`:

| Token | Color hexadecimal | Origen/uso |
|---|---|---|
| NIGHT | `#01040B` | Pixel del fondo del launcher OEM |
| DEEP | `#111416` | Sombras y barras negras |
| PANEL | `#1D2024` | Paneles carbón de controles |
| PANEL_HI | `#303B43` | Controles elevados/inactivos |
| CYAN | `#A0D4DA` | Cian claro de selección del menú |
| TURQUOISE | `#6C979B` | Tarjeta de ajuste seleccionada |
| GREEN | `#6ACA4E` | Tile verde del launcher OEM |
| GREEN_BUTTON | `#326B39` | Verde oscuro para mantener contraste blanco; borde verde OEM |
| BLUE | `#2F9CFB` | Tile azul de Instrumentos del launcher |
| AMBER | `#F29A2F` | Tile multimedia del launcher |
| AMBER_BUTTON | `#795327` | Ámbar oscurecido para contraste blanco |
| RED | `#DF444B` | Aproximación visual para advertencia, no pixel normalizado ADB |
| RED_BUTTON | `#833B42` | Fondo oscuro de acción de riesgo; borde rojo |

Los fondos y los tonos de panel son aproximaciones deliberadas a zonas representativas, no una reconstrucción fotográfica de la pantalla. No se copian iconos, fuentes ni gráficos propietarios: vectores originales de automóvil, tablero, batería, micrófono, espejo, ventanilla, ventilación, peatón, códigos y validaciones.

## Aplicación

- `ReceiverActivity` y `ReceiverQuickControls`: barra principal, controles con estados en verde, rojo, ámbar y celeste. Se conservan pulsos, permisos y funciones OEM existentes.
- `ReceiverVehicleActivity`: tabs, lector de datos y botones oscuros legibles.
- `T1PremiumCockpitActivity`: fondos, cards, modos EV/HEV/EV MAX, AUTO ON verde y OFF rojo; accesos con iconos OEM originales.
- `T1ValidationWizardActivity`: colores unificados en Pendiente/Funcionó/Sin señal/Probé; tarjeta de captura y textos en blanco.
- `T1WhatIfActivity`: corrección del contraste original de las listas desplegables, campos numéricos y resultado; fondo Jetour oscuro.
- `T1OptimizedModeActivity`: cuatro perfiles con tarjetas carbón, inputs y dropdowns legibles, AUTO en verde/rojo; sin alterar la estrategia de control.
- `T1TankDashboardActivity`: paneles y controles coherentes, datos claros.
- `T1VoiceRouteActivity`: pantalla micrófono e ingreso de destino.

## Límites y seguridad

Solo visual: no se alteran las escrituras OEM, los mapeos HAL, la telemetría, las geozonas, el control de recirculación, el protocolo de Receiver/Sender, los historiales CSV ni la fuente de datos del simulador.

`Receiver3DButtons` respeta los elementos marcados por el tema compartido para no sustituir sus iconos, contraste y estados por el viejo tinte azul genérico. Los controles táctiles especiales de `ReceiverQuickControls` mantienen su lógica y animación de pulsación.

## Validación

Compilación Android y regresión deben completarse en GitHub Actions; la representación final de colores depende de la calibración del display T1. Requiere revisión física de contraste y tamaños en 1920×1080.
