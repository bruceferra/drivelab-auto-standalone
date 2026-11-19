# Recuperación Boot 0.1.2 / Receiver 0.3.95 · 19-11-2026

## Incidente reportado desde Jetour
El usuario confirmó que instaló Receiver 0.3.94 y DriveLab Boot 0.1.1, pero **la navegación entre aplicaciones resultó confusa**: al tocar Boot se terminaba mostrando Receiver; no encontraba el selector Waze/Google dentro de Boot y veía un menú «PROBAR» de Receiver.

Causas comprobadas en código previo:
1. `T1BootInstaller.openBoot()` llamaba `startActivity()` desde Receiver sin `FLAG_ACTIVITY_NEW_TASK`; Android podía colocar BootActivity dentro del *task stack* de Receiver.
2. `BootActivity.reopen()` ejecutaba directamente una actividad de Receiver `T1MosaicShortcutActivity`; el botón «RECUPERAR VENTANAS ABIERTAS» llevaba fuera de Boot.
3. `BootActivity.apply()` hacía `moveTaskToBack(true)` sin una confirmación separada, ocultando su interfaz y exponiendo las ventanas Receiver.
4. La configuración de Display 1 estaba debajo de todo Display 0 y oculta tras habilitar un autoinicio. No había pestañas, ni se mostraban las versiones reales instaladas.
5. La prueba Display 1 desde Boot antes reenviaba la configuración combinada que también creaba el mosaico de Display 0.

## Cambios incorporados
- **Receiver 0.3.95 / code196**, **Boot 0.1.2 / code3**, APK firmadas con la misma identidad de versiones anteriores.
- `boot/src/main/AndroidManifest.xml`: ícono distintivo `@drawable/ic_drivelab_boot`, etiqueta propia y `BootActivity` con `taskAffinity` independiente + `singleTask`.
- `T1BootInstaller.openBoot()`: `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`; Boot se abre en tarea separada del Receiver.
- `BootActivity`: pestañas superiores **CENTRAL · 0** y **CLÚSTER · 1**. Se ve la versión real de Boot y Receiver. La pestaña central permite elegir 1–3 apps, proporción, margen gris Freeform. La pestaña clúster ofrece directamente **ELEGIR MAPA 1 · WAZE** y **ELEGIR MAPA 2 · GOOGLE MAPS**, o cambiar a un solo mapa. No hace falta activar autoinicio para acceder a los selectores.
- **PROBAR SOLO DISPLAY 1 · SIN SALIR DE BOOT** envía una solicitud firmada exclusiva al servicio Receiver y permanece en la pantalla Boot; exige selección previa del Display 1 en Receiver y lectura OEM de 0 km/h. No relanza Display 0.
- **ABRIR / PROBAR MOSAICO CENTRAL** envía solamente Display 0. Tras enviar la solicitud se presenta un cuadro y el usuario puede elegir **SEGUIR CONFIGURANDO** o **MOSTRAR MOSAICO**; solo esta última opción oculta Boot. Si Cockpit es un panel de Display 0, su ventana se identifica como Receiver, pero no es la configuración de Boot.
- Eliminado el acceso que llevaba desde Boot a `T1MosaicShortcutActivity`. Para visualizar ventanas existentes hay un botón que explícitamente oculta Boot, sin abrir actividades de Receiver.
- `BootConfig.sendCenterOnly` y `BootConfig.sendClusterOnly`, nuevo `APPLY_BOOT_CLUSTER_ONLY` en bridge firmado, con ejecución en `T1BootMosaicBridgeService`. El autoinicio combinado de 15s sigue disponible por separado.
- `receiver/tests/T1BootStandaloneUiIsolationSourceTest.java` inspecciona tabs, selectors y separación real de los flujos. Se preservan todas las validaciones de motor, certificados y seguridad previas.

## Cómo verificar en la unidad (vehículo detenido)
1. Actualizar Receiver 0.3.95 desde ⟳ o instalar desde release. **No desinstalar Receiver** para conservar preferencias, viajes y datos.
2. Dentro de Receiver → Inicio tocar **INSTALAR DRIVELAB BOOT · SIN WEBADB**; se actualizará Boot 0.1.2 (code3) con chequeo de release, SHA-256 y certificado. No desinstalar Boot.
3. Abrir el ícono nuevo **DriveLab BOOT · Config**, distinto del Receiver. Arriba debe decir **BOOT 0.1.2 | RECEIVER 0.3.95**.
4. Tocar la pestaña **CLÚSTER · 1**: deben verse los botones **ELEGIR MAPA 1** y **ELEGIR MAPA 2**. Pueden elegirse Waze y Google Maps, independientemente de activar autoinicio.
5. Tocar **PROBAR SOLO DISPLAY 1 · SIN SALIR DE BOOT**. La pantalla de Boot debe permanecer visible, sin saltar a Receiver. Solo hay que mirar si se abren las ventanas de mapas en el cluster y leer diagnóstico.
6. Tocar **CENTRAL · 0** y luego **ABRIR / PROBAR MOSAICO CENTRAL**. Debe aparecer diálogo de confirmación; solo **MOSTRAR MOSAICO** manda Boot al fondo. Si se eligió Cockpit, en el panel central aparece Receiver.
7. Mantener el autoinicio del clúster desactivado hasta observar el resultado en pantalla real, confirmar diseño y verificar velocidad 0 km/h.
8. Las barras gris Freeform / inferior SystemUI y capas Jetour aún pueden existir. No se eliminan OEM, ni se hace force-stop, ni se alteran HOME, clúster OEM o controles de vehículo.

## Riesgos y límites
- Ninguna herramienta remota puede leer automáticamente la pantalla física ni la lista de paquetes efectivamente instalados en el Jetour sin un diagnóstico proporcionado por el usuario.
- Las pruebas automatizadas verifican código y firma, no sustituyen una prueba de la versión instalada ni de ROM Android.
- El nombre de Boot en la UI del Jetour puede tardar en refrescarse en el launcher OEM. En el nuevo Boot, **las versiones de ambas APK se muestran por lectura directa de PackageManager** y permiten verificar la instalación real.
