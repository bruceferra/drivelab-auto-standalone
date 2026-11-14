# DriveLab Auto T1 — voz sencilla (Waze primero, Gemini ocasional)

Fecha: 2026-11-14.

## Pedido del conductor

La finalidad principal es navegación con Waze; el conductor quiere unas pocas preguntas breves del tipo «¿qué noticias hay hoy?», «¿qué clima?» o «¿qué pasó en Alaska?». Prefiere Gemini frente a ChatGPT por la respuesta más rápida que observó en la T1, pero no desea usar una app de conversación continuamente.

## Comportamiento implementado (solo presentación y preferencia)

- **Pantalla inicial simplificada:** botón principal «HABLAR · GOOGLE», más acceso explícito a Gemini cuando el vehículo está detenido y texto de estado visible. Ajustes y pruebas experimentales permanecen bajo **MOSTRAR OPCIONES AVANZADAS**, inicialmente cerrado.
- **Preferencia inicial:** Gemini se selecciona una vez en la preferencia del router; después se respetan cambios manuales de Opciones avanzadas.
- Las órdenes habladas explícitas de navegación mantienen la ruta Waze existente. Clima, titulares y cuentas conservan las rutas de DriveLab y respuesta TTS si está disponible.
- El acceso puntual a Gemini solicita `ACTION_ASSIST` para el paquete de Gemini si está expuesto; de lo contrario puede abrir la app normal. **No garantiza que comience a escuchar sin otro toque ni que opere en segundo plano**.
- Preguntas libres, como «¿qué pasó en Alaska?», **no pueden transferirse directamente a Gemini desde el reconocedor de DriveLab con respuesta conversacional automática**, porque no hay integración oficial/API en este Receiver. Se mantiene el comportamiento seguro de informar que no está conectado; el usuario puede abrir Gemini mediante el botón explícito.
- La tecla física del micrófono está gobernada por **Cluster de T1Launcher**. Debe estar iniciado; DriveLab no sustituye su mapa de botones, no agrega listener de volante ni cambia permisos. La configuración de asistente predeterminado de Android es independiente de esta preferencia.
- No se agrega Sender, acceso a notificaciones ni automatización Bluetooth. No hay escritura nueva al vehículo ni cambio de Waze Display5.

## Criterios

1. El acceso principal de la pantalla no presenta ChatGPT, experimentos de SpeechRecognizer ni selección de IA.
2. Opciones avanzadas preserva el acceso a los controles antiguos y permite volver a Gemini/ChatGPT si se desea.
3. El acceso físico T1Launcher permanece como estaba; no se reclama un nuevo enlace automático.
4. La compilación/QA Android debe pasar antes de incluir el cambio en un Receiver firmado futuro.

Sin version bump ni publicación OTA en esta rama.