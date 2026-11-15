# T1 Receiver · Volante OEM 1010 + ruedita Waze + reconocimiento interno

2026-11-15. Implementación independiente y experimental para validar en Jetour T1. Este cambio **NO modifica espejos, HVAC ni Sender**.

## Hechos de la T1 que ya confirmó el propietario
- Display 1 permite Waze a pantalla completa; Display 2 acepta petición Android pero no dibuja Waze.
- Botón físico del volante: callback OEM Binder de `T1Launcher-T1N134.apk`, `key=1010`, `action=100`. Desde DriveLab abre el reconocedor externo, el usuario dijo «Ir al Obelisco» y el destino llegó a Waze.
- La ventana externa del asistente no le resulta adecuada. Los cálculos se muestran escritos porque el TTS del headunit informa motor no operativo.
- Los controles rueda/OK/Atrás todavía no están confirmados en el clúster sin T1Launcher. Códigos OEM 1000/1001/1002/1003 salen del mismo APK, pero requieren Accesibilidad activa y controles accesibles reales de Waze.

## Cambio en esta revisión
1. `T1OemMicrophoneService` es dueño de **UN callback Binder** cuando micrófono 1010 está activado. Envía códigos 1000..1003 a `T1WazeWheelService` mediante despacho de la MISMA instancia/proceso; antes se bloqueaban mutuamente.
2. `T1WazeWheelService` conserva filtrado estricto al árbol de ventanas accesibles de `com.waze` y al Display seleccionado. Mic activo => libera su propio Binder para no duplicar registros; mic inactivo => función previa de rueda opt-in, sin cambio. Tras actualizar de v0.3.1, se realiza **una sola migración** del ajuste de ruedita previamente desactivado; luego respeta OFF manual.
3. La ruedita, sobre el mapa Waze de Display 1 y sin un diálogo, intenta acercar/alejar mediante gesto Android de dos dedos dirigido **exclusivamente al mismo display donde está Waze**. No son taps globales ni inyección al sistema OEM. La pantalla «RUTAS · VOLANTE» permite alternar zoom y selección de botones accesibles. Si no hay Accessibility activa o Waze no está visible, se informa y no se actúa. No se garantiza que el OEM permita los gestos hasta probar en vehículo.
4. El botón 1010 solicita `ACTION_OEM_QUIET_RECOGNITION`: actividad de tema translúcido, sin construir la pantalla completa del asistente, con `SpeechRecognizer` interno y permiso RECORD_AUDIO. Aplica el **mismo** enrutador: navegación hablada → Waze/clúster; cuentas, clima, noticias → respuesta local/TTS. Si la ROM no ofrece RecognitionService o el permiso está denegado, no se abre automáticamente Gemini/Google externo. Anota el error para verlo en configuración del clúster.
5. El motor de voz TTS se sigue inicializando en Android: nuevo botón para Ajustes de voz (`com.android.settings.TTS_SETTINGS` cuando exista), instalación de datos de voz (`ACTION_INSTALL_TTS_DATA` cuando haya actividad disponible), REINTENTAR TTS y diagnóstico. **No se puede garantizar audio hasta que haya engine/voz realmente operativos y ruteo multimedia funcional.**
6. No usa credenciales de Gemini, no graba audio en segundo plano, no reemplaza HOME ni usa root/ADB.
7. El proyecto de pantalla dividida/resize por ADB continúa **sin implementar** en esta versión. No confundir con el gestor de eventos OEM de volante.

## Pruebas físicas recomendadas en P
1. Actualizar Receiver sin desinstalar. Verificar que el módulo Cluster del tercero está apagado y que DriveLab tiene permiso Micrófono (Mientras se usa).
2. Abrir DriveLab > WAZE EN CLÚSTER > TAMAÑO/POSICIÓN/DIVISIÓN Y MICRÓFONO. Debe figurar «micrófono activado» y enlazado. Si no, apagar/encender ese switch.
3. Pulsar mic 1010; **no debería aparecer la ventanita externa del reconocedor**. Dictar «Ir al Obelisco» y comprobar navegación en Display 1. Si no reconoce, abrir diagnóstico «Reconocimiento/TTS» y registrar texto; sin fallback oculto.
4. Asegurar permiso Accesibilidad para «DriveLab · Waze y volante», si ROM ofrece esa pantalla. Con Waze visible en Display 1, girar una pulsación de rueda y observar zoom; probar OK y Atrás SOLO sobre controles Waze accesibles. Nunca se hace un BACK global si Waze no presenta su propio botón.
5. Para voz hablada abrir la pantalla «IA / WAZE», botón «REINTENTAR MOTOR DE VOZ», «PROBAR / REPETIR AUDIO». Si aún falla, «AJUSTES DE VOZ ANDROID», elegir motor con idioma español/instalar datos, y repetir. Si Android Jetour no expone TTS ni permite engine, quedará texto; no afirmar que habla.
6. No cambiar configuración de espejos ni activar divisiones experimentales durante la prueba.

La compilación Android y test de fuentes no sustituye las pruebas OEM físicas.
