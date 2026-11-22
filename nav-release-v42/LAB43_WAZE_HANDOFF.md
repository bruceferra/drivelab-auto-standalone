# DriveLab NAV — continuación LAB43 (22 Nov 2026)

## Último resultado físico confirmado por el usuario
- OTA instalada: LAB42 `0.1.44-lab.42` (code 46).
- No muestra Waze en tablero.
- Mensaje: `SecurityException: MainActivity de Waze no es exportada; Android no permite abrirla desde NAV`.
- Cierre del VirtualDisplay NAV propio confirmado; no se detuvo ninguna aplicación globalmente.
- Mosaico central Maps + Spotify debe conservar tareas, displays, tacto y ciclo de vida. No usar T1Launcher/T1Mapa. No tocar Sender/Receiver.

## Evidencia previa
LAB41 abrió la actividad pública `com.waze.FreeMapAppActivity` en el VirtualDisplay creado por NAV, pero Waze abrió su `com.waze.MainActivity` privada como una tarea NUEVA en display físico 0. Intentar abrir directamente `MainActivity` desde NAV en LAB42 resultó inválido por falta de exportación.

## Hipótesis de LAB43 — SIN validar físicamente
1. Usar sólo `PackageManager.getLaunchIntentForPackage("com.waze")`, exigiendo la actividad launcher exportada.
2. Verificar dos inventarios ADB iniciales con **cero** tareas Waze antes del lanzamiento.
3. Detectar la única tarea nueva `com.waze.MainActivity` en una pila **exclusiva de Waze** del display físico 0; doble lectura estable; prohibir si existe Waze en otro display, IDs duplicados, stack mixto o presencia OEM.
4. Si se confirma, ejecutar por ADB local autorizado sólo `am display move-stack <stackId> <NAV-own-VirtualDisplayId>` con allowlist estricta y verificación repetida de propiedad del display.
5. Confirmar que pila y tarea Waze aparecen en VirtualDisplay NAV, que NO retornan inmediatamente al display 0, y que Maps+Spotify siguen en sus tareas originales. Aun así, **Android task verification no equivale a verificación visual**: el usuario debe comprobar el tablero.
6. En salida, limpiar únicamente pilas Waze aún verificadas dentro del display NAV. No global force-stop, no pm clear, no modificación OEM ni tarea Waze en displays ajenos.

Android AOSP documenta `am display move-stack` (distinto de `am stack move-task`); el firmware Jetour puede denegar el comando o la propia aplicación puede reabrirse en físico 0. Fallar cerrado en cualquiera de esos casos.

## Código agregado en esta rama (NO publicado)
- `NavOwnClusterWazePolicy.java`: restaurar launcher público de LAB41; no iniciar actividad privada.
- `NavWazeRelocationPolicy.java`: parser estricto, identificación de nueva pila Waze, comando, resultado y limpieza de tareas propias.
- `NavWazeRelocationPolicyTest.java`: casos de pila mixta, duplicada, pantalla ajena, preexistencia y confirmación post-traslado. **Tests escritos; no afirmar que se ejecutaron.**
- `NavOwnClusterProbe.java`: nueva ruta de lanzamiento, migración y cierre limitado.
- `tools/nav-own-cluster-waze-adb-stage.py`: autorización limitada para lanzamiento público y movimiento validado.
- `tools/nav-virtual-mosaic-stage.py`: copia de nuevo módulo puro Java a build aislada.
- `tools/nav-v43-stage.py`: versionCode 47, versionName 0.1.45-lab.43; no modifica Sender, Receiver, ni OTA.

## Publicación — BLOQUEADA hasta QA
La rama es **source-only**, no está compilada con Android SDK, no pasó tests Java ejecutados, ni está firmada con la clave original, ni se creó release ni se actualizó `bridge-lab-ota/bridge-lab.json`. La última OTA real sigue siendo LAB42.

Antes de publicar LAB43 por OTA, completar tests Java y Android build, verificar `ar.com.rango5gps.drivelabauto.boot.lab`, code 47, signer SHA-256 igual al certificado anterior, hash SHA-256 de APK, firma del manifest y actualización atómica del mismo canal OTA. Nunca solicitar instalación manual de APK. Mantener LAB42 como rollback, sin sobrescribir release/tag anterior.

## Prueba en vehículo una vez que exista OTA
Con el Jetour T1 estacionado y sin navegación activa, abrir NAV / mosaico Maps+Spotify, verificar ambos. Ejecutar una sola prueba Waze Cluster, observar si hay mapa real o sólo logo; copiar diagnóstico de tareas/displays. Confirmar mosaico/tacto tras cerrar y que ninguna app global fue detenida. No repetir LAB42.
