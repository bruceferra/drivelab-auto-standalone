# DriveLab Auto Receiver 0.2.63 · Asistente universal y navegación directa

## Alcance

Sobre Receiver 0.2.62, se mantiene la misma clase `T1VoiceRouteActivity` asociada al botón existente de Hola Jetour. No se cambia el launcher ni la configuración de teclas del volante.

- Se solicita autorización estándar `RECORD_AUDIO` al primer uso, y se utiliza `SpeechRecognizer` **solo en primer plano** para reducir pantallas adicionales de reconocimiento. Si el dispositivo no expone motor, existe botón para `RecognizerIntent` externo como alternativa y entrada por teclado.
- Una entrada, dos tipos de órdenes: **«navegá a X», «ir a X», «llevame a X», «cómo llegar a X»** → Waze, con confirmación de destino; cualquier pregunta común, matemáticas, noticias, tecnología → proveedor de IA seleccionado (Gemini por defecto o ChatGPT).
- El reconocido de voz se inicia una sola vez al abrir manualmente la pantalla, incluso si se invoca desde el botón que el propietario asoció a Hola Jetour. Se detiene al salir. No existe activación persistente, micrófono en segundo plano, hotword ni servicio nuevo.
- Botones experimentales **GEMINI · ABRIR ASISTENTE / VOZ** y **CHATGPT · ABRIR ASISTENTE / VOZ**. Se intenta `ACTION_ASSIST` en Gemini, y se abre su pantalla normal si no está disponible. ChatGPT se inicia como app, con control de voz propio sujeto a compatibilidad.
- Para preguntas reconocidas, `Intent.ACTION_SEND` text/plain se dirige al paquete elegido. La app destinataria decide si recibe, muestra o requiere tocar Enviar. Si no admite ese Intent, se copia el texto y se abre la aplicación para pegarlo.
- Dos botones adicionales para forzar **WAZE destino escrito** o **CONSULTAR IA** si una frase ambigua se clasifica incorrectamente. El destino de Waze se revisa antes de iniciar ruta.

## Navegación sin pantallas intermedias

En la barra superior de controles rápidos de Receiver se cambió **TABLERO / TRIP** por **COCKPIT** (directo a `T1PremiumCockpitActivity`) y **TABLERO** (directo a `T1TankDashboardActivity`). Se renombran también en el menú principal. Se incorpora botón **INICIO** en Cockpit y Tablero para limpiar pantallas apiladas y volver a Receiver de un toque, sin alterar grabación en segundo plano.

## Límites

Gemini integrado en Android Auto **no es** una aplicación adicional llamada Gemini Auto: es la integración del asistente Google dentro del entorno compatible Android Auto. DriveLab Auto es un APK Android 11 ejecutado en el headunit Jetour, no la experiencia Google Android Auto certificada. Por ello el hecho de que Gemini esté instalado no prueba que la ROM permita abrir **Gemini Live** directamente mediante `Intent`. Tampoco existe una API pública general garantizada de ChatGPT Voz para tercerizadas. Se mantienen los intentos como **EXPERIMENTALES** y se muestran errores y alternativas explícitas.

No hay API keys de terceros, transcripciones enviadas por el Receiver a nuestros servidores, ni escritura al vehículo. Los prompts se transfieren a la app AI elegida únicamente por acción explícita del usuario y de acuerdo con los mecanismos Android de compartir. El portapapeles se utiliza solo cuando la app no tiene receptor SEND. Las respuestas de audio dependen del modo y del proveedor instalado.

## Pruebas requeridas en Jetour T1

1. Al tocar Hola Jetour se abre la misma pantalla T1VoiceRouteActivity e intenta capturar voz sin diálogo de otra ventana, después del permiso inicial.
2. Decir **«Navegá a Plaza de Mayo»**: debe pedir confirmación y abrir Waze; si no se muestra clúster, reportarlo.
3. Decir **«Cuánto es treinta y siete por doce»**: debe invocar Gemini o ChatGPT predeterminado. Comprobar si la app recibe el texto y si responde con voz o exige tocar su micrófono.
4. Probar los botones directos Gemini/ChatGPT. Registrar si abre Live, chat normal, aviso de permisos o pantalla en blanco.
5. En Receiver tocar **COCKPIT**: va directo a Cockpit; **INICIO**: vuelve al Receiver. Lo mismo con **TABLERO**.
6. Reconfirmar viajes históricos, Sender 0.2.16, updater OTA y cambio EV/HEV sin alteraciones.

## QA

Se agregó `T1VoiceIntentPolicyTest.java` con casos de navegación e interrogación general y compila el APK Android desde `receiver-voice-ci.yml`. La publicación 0.2.63 compara el certificado de firma Receiver con 0.2.62 y preserva el manifiesto de Sender 0.2.16.
