package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.UtteranceProgressListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Single-tap Jetour voice entry. Destinations follow the visible cluster map.
 * Other speech -> the preferred installed AI app, using documented Android SEND
 * when supported. Experimental direct voice-app launch; neither vendor
 * publishes a guaranteed third-party Intent for Gemini Live / ChatGPT Voice.
 * SpeechRecognizer runs only while this foreground Activity is visible.
 */
public final class T1VoiceRouteActivity extends T1ImmersiveActivity {
    private static final int VOICE_REQUEST=7042, AUDIO_PERMISSION=7043;
    static final String ACTION_SHORTCUT_GOOGLE_RECOGNITION=
        "ar.com.rango5gps.drivelabauto.receiver.GOOGLE_RECOGNITION_SHORTCUT";
    /** Physical 1010 button: transparent, internal SpeechRecognizer.
     * Android still creates a foreground Activity to get mic permission/
     * recognition; there is no third-party Google recognition pop-up.
     */
    static final String ACTION_OEM_QUIET_RECOGNITION=
        "ar.com.rango5gps.drivelabauto.receiver.OEM_QUIET_RECOGNITION";
    private static final String GEMINI="com.google.android.apps.bard";
    private static final String CHATGPT="com.openai.chatgpt";
    private static final String PREF="t1_voice_router_v1";
    private static final String KEY_AI="default_ai";
    private static final int BG=T1JetourTheme.NIGHT;
    private TextView status,heard,audioStatus;
    private String lastAudible,activeSpeechId;
    private boolean screenResumed,speechFocusHeld;
    private long speechReadyAt;
    private int speechSequence;
    private AudioManager audioManager;
    private AudioFocusRequest speechFocusRequest;
    private SpeechRecognizer recognizer;
    private boolean listening,openingAssistant,quietOem;
    private long ttsWaitStarted;
    private final Handler voiceHandler=new Handler(Looper.getMainLooper());
    private boolean recognitionReady;
    private final Runnable recognitionTimeout=()->{
        if(!listening||recognitionReady||isFinishing()||isDestroyed())return;
        listening=false;
        if(recognizer!=null){
            try{recognizer.cancel();}catch(Exception ignored){}
            recognizer.destroy();recognizer=null;
        }
        if(status!=null)status.setText("El reconocimiento interno no responde "+
            "en esta T1. Usá HABLAR · VOZ DEL SISTEMA: abre el asistente "+
            "que sí escucha, sin repetir el toque.");
        finishQuietAfterError("SpeechRecognizer sin respuesta tras 6,5 segundos.");
    };
    private TextToSpeech speaker;
    private boolean ttsReady;
    private String answerToSpeak;

    @Override protected int overlayClearBackgroundColor(){return BG;}

    private TextView label(LinearLayout parent,String value,int size,int color){
        TextView text=new TextView(this);
        text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setPadding(10,9,10,9);
        parent.addView(text);
        return text;
    }
    private Button button(LinearLayout parent,String title,int color,
                          Runnable action){
        Button b=new Button(this);
        b.setText(title);b.setTextSize(15);
        T1JetourTheme.button(b,color);
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(5,4,5,4);
        parent.addView(b,lp);
        return b;
    }
    private String name(String packageId){
        return CHATGPT.equals(packageId)?"ChatGPT":"Gemini";
    }
    @Override protected void onCreate(Bundle state){
        quietOem=ACTION_OEM_QUIET_RECOGNITION.equals(
            getIntent()==null?null:getIntent().getAction());
        if(quietOem)setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(state);
        if(quietOem){
            // No screen-sized assistant nor floating Google dialog:
            // a transparent Activity owns the microphone only for this
            // physical press, and uses the SAME Waze/math/news dispatcher.
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getWindow().clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            getWindow().setDimAmount(0f);
            getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            getWindow().setGravity(android.view.Gravity.TOP|
                android.view.Gravity.CENTER_HORIZONTAL);
            // The OEM translucent full-screen Activity without content
            // was displayed as an entirely BLACK screen on this T1 ROM.
            // Give it a small actual decor window and visible status instead.
            status=new TextView(this);
            heard=new TextView(this);
            audioStatus=new TextView(this);
            status.setText("🎤 Escuchando…");
            status.setGravity(android.view.Gravity.CENTER);
            status.setSingleLine(false);
            status.setMaxLines(4);
            status.setTextColor(T1JetourTheme.WHITE);
            status.setTextSize(17f);
            status.setBackground(T1JetourTheme.surface(
                T1JetourTheme.PANEL_HI,16));
            audioStatus.setText("AUDIO: esperando motor TTS");
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(14,8,14,10);
            card.setBackground(T1JetourTheme.surface(T1JetourTheme.PANEL_HI,20));
            TextView title=new TextView(this);
            title.setText("🎤  DRIVELAB · VOZ");
            title.setTextSize(17f);
            title.setTextColor(T1JetourTheme.CYAN);
            card.addView(title);
            status.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            card.addView(status);
            heard.setTextColor(T1JetourTheme.WHITE);
            heard.setTextSize(14f);
            heard.setMaxLines(2);
            card.addView(heard);
            getWindow().setContentView(card);
            getWindow().setLayout(T1JetourTheme.dp(this,520),
                T1JetourTheme.dp(this,154));
            initSpeechOutput();
            voiceHandler.postDelayed(this::listen,250L);
            return;
        }
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackground(T1JetourTheme.backdrop());
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(19,9,19,18);
        sc.addView(root);
        label(root,"🎤  VOZ · WAZE Y CONSULTAS",22,T1JetourTheme.CYAN);
        label(root,"Un solo micrófono: decí «llevame a Ezeiza», "+
            "«400 más 200», «qué clima hay» o «qué noticias tenés hoy».",
            15,T1JetourTheme.WHITE);
        button(root,"🎤 HABLAR · WAZE, CLIMA, NOTICIAS Y CUENTAS",
            T1JetourTheme.GREEN_BUTTON,this::listenExternal);
        heard=label(root,"ESCUCHÉ: todavía nada",13,T1JetourTheme.MUTED);
        status=label(root,"Tocá HABLAR: Google reconoce la frase y DriveLab "+
            "muestra acá la respuesta. El sonido depende del audio de la T1.",
            15,T1JetourTheme.WHITE);
        status.setTextIsSelectable(true);
        audioStatus=label(root,"AUDIO: comprobando voz de Android…",
            13,T1JetourTheme.MUTED);
        button(root,"⚙️ AJUSTES DE VOZ ANDROID (TTS)",
            T1JetourTheme.PANEL_HI,this::openTtsConfiguration);
        button(root,"REINTENTAR MOTOR DE VOZ",
            T1JetourTheme.PANEL_HI,()->{
                if(speaker!=null){
                    try{speaker.stop();speaker.shutdown();}
                    catch(RuntimeException ignored){}
                    speaker=null;
                }
                ttsReady=false;ttsWaitStarted=0L;
                showAudio("reintentando inicialización TTS…");
                initSpeechOutput();
            });
        button(root,"🔊 PROBAR / REPETIR AUDIO",
            T1JetourTheme.PANEL_HI,()->{
                if(lastAudible==null||lastAudible.isEmpty())
                    say("PRUEBA DE AUDIO · voz de DriveLab",
                        "Hola, soy DriveLab. Si me escuchás, "+
                        "la salida de voz de la camioneta funciona.");
                else{
                    answerToSpeak=lastAudible;
                    readQueuedSpeech();
                }
            });
        label(root,"CONSULTAS · RESPUESTA EN DRIVELAB",15,T1JetourTheme.CYAN);
        label(root,"Destinos, clima, noticias y cuentas se contestan acá. "+
            "Para preguntas libres sin integración, se informa la limitación "+
            "sin abrir otra aplicación.",13,T1JetourTheme.MUTED);
        button(root,"VOLVER A DRIVELAB",T1JetourTheme.PANEL,()->{
            Intent main=new Intent(this,ReceiverActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(main);
            finish();
        });
        button(root,"CERRAR",T1JetourTheme.PANEL,this::finish);
        setContentView(sc);
        // TTS init after the status field exists, to report engine failures.
        initSpeechOutput();
        // The physical microphone remains owned by T1Launcher Cluster;
        // the optional configured shortcut uses the same external recognizer.
        if(state==null&&ACTION_SHORTCUT_GOOGLE_RECOGNITION.equals(getIntent().getAction()))
            root.post(this::listenExternal);
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        // Pressing physical OEM mic 1010 again while the voice screen is
        // already on top must still open a fresh recognition round.
        if(intent!=null&&ACTION_OEM_QUIET_RECOGNITION.equals(
                intent.getAction())){
            // Repeat after a press without ever launching Google UI.
            if(recognizer!=null){
                try{recognizer.cancel();recognizer.destroy();}
                catch(RuntimeException ignored){}
                recognizer=null;
            }
            listening=false;recognitionReady=false;
            voiceHandler.postDelayed(this::listen,250L);
        }else if(intent!=null&&ACTION_SHORTCUT_GOOGLE_RECOGNITION.equals(
                intent.getAction()))
            voiceHandler.postDelayed(this::listenExternal,250L);
    }

    private Intent speechIntent(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-AR");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"es-AR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,
            "¿A dónde querés ir o qué querés preguntarle a la IA?");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        return i;
    }
    private void finishQuietAfterError(String reason){
        if(!quietOem)return;
        T1OemMicrophoneService.prefs(this).edit()
            .putString(T1OemMicrophoneService.LAST_VOICE_STATUS,reason).apply();
        voiceHandler.postDelayed(()->{
            if(!isFinishing()&&!isDestroyed())finish();
        },400L);
    }
    private void finishQuietIfNavigation(){
        if(!quietOem)return;
        T1OemMicrophoneService.prefs(this).edit()
            .putString(T1OemMicrophoneService.LAST_VOICE_STATUS,
                String.valueOf(status.getText())).apply();
        voiceHandler.postDelayed(()->{
            if(!isFinishing()&&!isDestroyed())finish();
        },400L);
    }
    private void listen(){
        if(listening){
            if(status!=null)status.setText("Reconocimiento interno activo. "+
                "Si no responde, se libera automáticamente y podés reintentar.");
            return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=
                PackageManager.PERMISSION_GRANTED){
            status.setText("Para reconocer la voz dentro de DriveLab, " +
                "autorizá el micrófono cuando Android lo solicite.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                AUDIO_PERMISSION);
            return;
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            status.setText("No hay servicio de reconocimiento integrado accesible.");
            if(quietOem)finishQuietAfterError(
                "Sin servicio RecognitionService; no se abrió ventana externa.");
            else listenExternal();
            return;
        }
        try{
            if(recognizer!=null){recognizer.destroy();recognizer=null;}
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle b){
                    recognitionReady=true;
                    voiceHandler.removeCallbacks(recognitionTimeout);
                    status.setText("● ESCUCHANDO · decí una orden o pregunta.");
                }
                @Override public void onBeginningOfSpeech(){
                    status.setText("Escuchando tu voz…");
                }
                @Override public void onRmsChanged(float value){}
                @Override public void onBufferReceived(byte[] data){}
                @Override public void onEndOfSpeech(){
                    status.setText("Procesando lo que dijiste…");
                }
                @Override public void onError(int code){
                    voiceHandler.removeCallbacks(recognitionTimeout);
                    listening=false;recognitionReady=false;
                    if(isFinishing()||isDestroyed())return;
                    status.setText("El reconocedor interno devolvió "+code+
                        ". Tocá HABLAR de nuevo o usá MICRÓFONO EXTERNO. " +
                        "Si la ROM reserva el audio, este método puede fallar.");
                    finishQuietAfterError("RecognitionService falló con código "+code+
                        "; el micrófono OEM 1010 llegó.");
                }
                @Override public void onResults(Bundle b){
                    voiceHandler.removeCallbacks(recognitionTimeout);
                    listening=false;recognitionReady=false;
                    ArrayList<String> matches=b==null?null:
                        b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    // Let the recognizer release its audio route before TTS.
                    speechReadyAt=android.os.SystemClock.uptimeMillis()+400L;
                    recognize(matches);
                }
                @Override public void onPartialResults(Bundle partial){
                    ArrayList<String> values=partial==null?null:
                        partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(values!=null&&!values.isEmpty()&&status!=null)
                        status.setText("Escuchando: "+values.get(0));
                }
                @Override public void onEvent(int event,Bundle extras){}
            });
            listening=true;
            recognitionReady=false;
            status.setText("Inicializando micrófono interno…");
            recognizer.startListening(speechIntent());
            // The stock T1 can silently ignore SpeechRecognizer without
            // invoking onReadyForSpeech or onError; do not leave the tap dead.
            voiceHandler.removeCallbacks(recognitionTimeout);
            voiceHandler.postDelayed(recognitionTimeout,6500L);
        }catch(RuntimeException e){
            voiceHandler.removeCallbacks(recognitionTimeout);
            listening=false;recognitionReady=false;
            status.setText("La ROM no permitió escucha integrada ("+
                e.getClass().getSimpleName()+"). Probá el micrófono externo.");
            finishQuietAfterError("SpeechRecognizer no inició: "+
                e.getClass().getSimpleName());
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,
        String[] permissions,int[] granted){
        super.onRequestPermissionsResult(requestCode,permissions,granted);
        if(requestCode!=AUDIO_PERMISSION)return;
        if(granted.length>0&&granted[0]==PackageManager.PERMISSION_GRANTED)
            listen();
        else{
            status.setText("Permiso de micrófono denegado para DriveLab.");
            finishQuietAfterError("Permiso RECORD_AUDIO denegado.");
        }
    }
    private void listenExternal(){
        try{
            status.setText("Abriendo reconocedor externo de Android…");
            startActivityForResult(speechIntent(),VOICE_REQUEST);
        }catch(ActivityNotFoundException | SecurityException e){
            status.setText("No hay reconocedor externo accesible. " +
                "Escribí la consulta en el campo.");
        }
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=VOICE_REQUEST)return;
        if(result!=Activity.RESULT_OK||data==null){
            status.setText("La escucha fue cancelada o no reconoció voz.");return;
        }
        // Let the external Google recognition overlay release the car audio
        // channel before we request focus for our spoken answer.
        speechReadyAt=android.os.SystemClock.uptimeMillis()+550L;
        recognize(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
    }
    private void recognize(ArrayList<String> results){
        if(isFinishing()||isDestroyed())return;
        if(results==null||results.isEmpty()||
                results.get(0)==null||results.get(0).trim().isEmpty()){
            status.setText("No se reconoció el mensaje. Reintentá.");
            finishQuietAfterError("Micrófono escuchó sin resultados útiles.");
            return;
        }
        String said=results.get(0).trim();
        if(heard!=null)heard.setText("ESCUCHÉ: "+said);
        // A full voice request is deliberate: no second confirmation.
        dispatch(said,true);
    }
    private void dispatch(String phrase,boolean fromVoice){
        if(phrase==null||phrase.trim().length()<2){
            status.setText("Primero decí o escribí tu consulta.");return;
        }
        String request=phrase.trim().toLowerCase(java.util.Locale.ROOT);
        boolean control=request.startsWith("controlar ")||
            request.startsWith("controla ")||
            request.startsWith("seleccionar ")||
            request.startsWith("cambiar a ")||
            request.startsWith("cambia a ");
        if(control&&(request.endsWith("panel a")||request.endsWith("waze"))){
            T1WheelTarget.select(this,false);
            status.setText("VOLANTE · PANEL A");
            if(quietOem)finishQuietIfNavigation();
            return;
        }
        if(control&&(request.endsWith("panel b")||
                    request.endsWith("google maps"))){
            T1WheelTarget.select(this,true);
            status.setText("VOLANTE · PANEL B");
            if(quietOem)finishQuietIfNavigation();
            return;
        }
        String target=T1VoiceIntentPolicy.routeDestination(phrase);
        if(target!=null){
            status.setText("NAVEGACIÓN DETECTADA · "+target+
                " · se enviará al navegador visible en el clúster");
            if(fromVoice){
                // The explicit "ir a"/"navegar a" utterance is itself
                // authorization. Waze still resolves the actual location.
                // Never treat arbitrary questions/numbers as destinations.
                navigate(target);
                finishQuietIfNavigation();
            }else{
                // Keep an explicit confirm for typed test/manual actions.
                confirmDestination(target);
            }
            return;
        }
        String math=T1VoiceMath.calculate(phrase);
        if(math!=null){
            say("CÁLCULO LOCAL · "+math,math);
            return;
        }
        String greeting=T1VoiceSmallTalk.answer(phrase);
        if(greeting!=null){
            say("SALUDO · "+greeting,greeting);
            return;
        }
        if(T1VoiceIntentPolicy.isWeatherQuery(phrase)){
            status.setText("CLIMA · consultando pronóstico actualizado…");
            new Thread(()->{
                String result;
                try{result=T1VoiceWeather.now(getApplicationContext());}
                catch(Exception e){
                    result="No pude consultar el pronóstico actualizado. "+
                        "Revisá conexión a internet y ubicación.";
                }
                final String answer=result;
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed())say(
                        "CLIMA ACTUAL · "+answer,answer);
                });
            },"T1-Voice-Weather-HTTPS").start();
            return;
        }
        if("San Lorenzo futbol resultado".equals(
                T1VoiceNewsTopic.topic(phrase))){
            status.setText("SAN LORENZO · consultando último marcador final…");
            new Thread(()->{
                String result;
                try{result=T1VoiceFootball.latestSanLorenzo();}
                catch(Exception ex){
                    result="No pude confirmar el resultado en la fuente "+
                        "de partidos. Revisá la conexión o consultá noticias.";
                }
                final String answer=result;
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed())
                        say("SAN LORENZO · "+answer,answer);
                });
            },"T1-Voice-Football-HTTPS").start();
            return;
        }
        if(T1VoiceNewsTopic.topic(phrase)!=null){
            status.setText("ACTUALIDAD · consultando titulares recientes…");
            new Thread(()->{
                String result;
                try{result=T1VoiceNews.headlines(phrase);}
                catch(Exception ex){
                    result="No pude consultar titulares actualizados. "+
                        "Revisá la conexión o usá la consulta externa.";
                }
                final String answer=result;
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed())
                        say("TITULARES ACTUALES · "+answer,answer);
                });
            },"T1-Voice-News-RSS").start();
            return;
        }
        // No generic Gemini answer API is connected. Starting Gemini with
        // ACTION_SEND opens fullscreen; ACTION_VOICE_COMMAND would require
        // repeating this recognized question in a different session.
        // Never switch apps automatically while driving or fabricate facts.
        say("CONSULTA LIBRE · Servicio de IA conversacional aún no conectado. "+
                "La consulta quedó reconocida: "+phrase.trim(),
            "Entendí tu pregunta, pero todavía no tengo un asistente "+
                "conversacional conectado para responderla directamente. "+
                "Podés consultar el clima, resultados deportivos, noticias "+
                "y hacer cuentas, o usar la voz del sistema para Gemini.");
    }
    /** Android TTS with explicit, transient navigation speech audio focus.
     * TTS success does NOT prove OEM loudspeakers are selected, so report
     * preparation/failure separately from what the driver actually hears.
     */
    private AudioAttributes speechAudioAttributes(){
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    }
    private void showAudio(String message){
        if(audioStatus!=null)audioStatus.setText("AUDIO: "+message);
        if(quietOem)T1OemMicrophoneService.prefs(this).edit()
            .putString(T1OemMicrophoneService.LAST_VOICE_STATUS,
                "AUDIO: "+message).apply();
    }
    private void releaseSpeechFocus(){
        if(audioManager!=null&&speechFocusRequest!=null&&speechFocusHeld){
            try{audioManager.abandonAudioFocusRequest(speechFocusRequest);}
            catch(RuntimeException ignored){}
        }
        speechFocusHeld=false;
    }
    private boolean requestSpeechFocus(){
        try{
            audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
            if(audioManager==null){
                showAudio("el sistema no ofrece AudioManager; salida incierta");
                return false;
            }
            if(speechFocusRequest==null)
                speechFocusRequest=new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(speechAudioAttributes())
                    .setOnAudioFocusChangeListener(change->{
                        if(change==AudioManager.AUDIOFOCUS_LOSS)
                            runOnUiThread(()->{
                                if(speaker!=null)speaker.stop();
                                releaseSpeechFocus();
                                showAudio("interrumpido por otra fuente del vehículo");
                            });
                    }).build();
            int result=audioManager.requestAudioFocus(speechFocusRequest);
            speechFocusHeld=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if(!speechFocusHeld)
                showAudio("sin foco de reproducción; revisar fuente/volumen de la T1");
            return speechFocusHeld;
        }catch(RuntimeException ex){
            showAudio("no se pudo solicitar audio: "+
                ex.getClass().getSimpleName());
            return false;
        }
    }
    private void finishUtterance(String id,String error){
        runOnUiThread(()->{
            if(!id.equals(activeSpeechId))return;
            releaseSpeechFocus();
            if(quietOem){
                T1OemMicrophoneService.prefs(this).edit().putString(
                    T1OemMicrophoneService.LAST_VOICE_STATUS,
                    error==null?"TTS completó síntesis; comprobar audio del vehículo":
                        "TTS falló: "+error).apply();
                voiceHandler.postDelayed(()->{
                    if(!isFinishing()&&!isDestroyed())finish();
                },200L);
            }
            if(error==null)
                showAudio("Android finalizó la voz; verificar parlantes si no se oyó");
            else{
                showAudio("falló reproducción: "+error+
                    " · revisar motor TTS, fuente y volumen multimedia");
                if(status!=null)status.append(
                    "\n🔈 No se pudo reproducir la respuesta; "+
                    "podés verla escrita y probar el audio.");
            }
        });
    }
    /** Android's TTS engine configuration is a user choice, not a
     * phantom permission that DriveLab can grant itself. AOSP Settings may
     * expose this action, while some Jetour ROM builds omit it.
     */
    private void openTtsConfiguration(){
        Intent open=new Intent("com.android.settings.TTS_SETTINGS");
        try{
            if(open.resolveActivity(getPackageManager())!=null){
                startActivity(open);
                showAudio("Ajustes de voz abiertos: seleccioná motor TTS "+
                    "e instalá voz española, después tocá REINTENTAR.");
                return;
            }
            Intent install=new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            if(install.resolveActivity(getPackageManager())!=null){
                startActivity(install);
                showAudio("Instalación de datos de voz solicitada; "+
                    "reintentá el motor después de completar.");
                return;
            }
            showAudio("Esta ROM no expone Ajustes de voz ni instalador "+
                "de datos TTS. Debe habilitarse un motor compatible "+
                "desde Ajustes/Aplicaciones de Android.");
        }catch(RuntimeException ex){
            showAudio("Jetour bloqueó ajustes de voz: "+
                ex.getClass().getSimpleName());
        }
    }

    private void initSpeechOutput(){
        try{
            // OnInit may race with assignment of speaker; marshal the
            // callback to main queue after construction completes.
            speaker=new TextToSpeech(this,result->
                voiceHandler.post(()->completeSpeechInitialization(result)));
        }catch(RuntimeException ex){
            ttsReady=false;
            showAudio("error iniciando TTS: "+ex.getClass().getSimpleName());
        }
    }
    private void completeSpeechInitialization(int result){
        if(isFinishing()||isDestroyed())return;
        if(result!=TextToSpeech.SUCCESS||speaker==null){
            ttsReady=false;
            showAudio("motor de voz TTS no disponible (init="+result+"). "+
                "Comprobar voz instalada en Android de la T1.");
            return;
        }
        String engineInfo="motor="+speaker.getDefaultEngine();
        try{engineInfo+=" · visibles="+speaker.getEngines().size();}
        catch(RuntimeException ignored){}
        int language;
        try{
            language=speaker.setLanguage(new Locale("es","AR"));
            if(language==TextToSpeech.LANG_MISSING_DATA||
                language==TextToSpeech.LANG_NOT_SUPPORTED)
                language=speaker.setLanguage(new Locale("es"));
        }catch(RuntimeException ex){
            ttsReady=false;
            showAudio("error idioma TTS · "+engineInfo);
            return;
        }
        if(language==TextToSpeech.LANG_MISSING_DATA||
           language==TextToSpeech.LANG_NOT_SUPPORTED){
            ttsReady=false;
            showAudio("falta voz española en Android · "+engineInfo);
            return;
        }
        int audio=speaker.setAudioAttributes(speechAudioAttributes());
        showAudio((audio==TextToSpeech.ERROR?
            "ROM rechazó ruta multimedia · ":"TTS multimedia preparado · ")+engineInfo);
        speaker.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){
                runOnUiThread(()->{
                    if(id.equals(activeSpeechId))
                        showAudio("sintetizando por canal multimedia…");
                });
            }
            @Override public void onDone(String id){
                finishUtterance(id,null);
            }
            @Override public void onError(String id){
                finishUtterance(id,"síntesis TTS");
            }
            @Override public void onError(String id,int code){
                finishUtterance(id,"TTS código "+code);
            }
        });
        ttsReady=true;
        if(answerToSpeak!=null)readQueuedSpeech();
    }
    private void readQueuedSpeech(){
        if(answerToSpeak==null||isFinishing()||isDestroyed())return;
        // Activity results arrive before onResume. The recognition overlay
        // can still own audio focus; let it close before the spoken answer.
        if(!screenResumed)return;
        long wait=speechReadyAt-android.os.SystemClock.uptimeMillis();
        if(wait>0){
            voiceHandler.postDelayed(this::readQueuedSpeech,wait);
            return;
        }
        if(!ttsReady||speaker==null){
            showAudio("motor TTS aún no operativo; verificar voz "+
                "instalada en Ajustes de la T1. Texto disponible.");
            if(quietOem){
                // TTS init is asynchronous. Allow up to 4 seconds before
                // concluding that this head-unit has no usable voice engine.
                long now=android.os.SystemClock.uptimeMillis();
                if(ttsWaitStarted==0L)ttsWaitStarted=now;
                if(now-ttsWaitStarted<4000L)
                    voiceHandler.postDelayed(this::readQueuedSpeech,450L);
                else finishQuietAfterError(String.valueOf(
                    audioStatus.getText())+" · respuesta="+
                    String.valueOf(status.getText()));
            }
            return;
        }
        String answer=answerToSpeak;
        answerToSpeak=null;
        releaseSpeechFocus();
        requestSpeechFocus();
        String id="drivelab_voice_response_"+(++speechSequence);
        activeSpeechId=id;
        int sent=speaker.speak(answer,TextToSpeech.QUEUE_FLUSH,null,id);
        if(sent==TextToSpeech.ERROR)
            finishUtterance(id,"Android rechazó speak()");
    }
    private void say(String line,String audible){
        if(status!=null)status.setText(line);
        lastAudible=audible;
        answerToSpeak=audible;
        readQueuedSpeech();
    }
    private void confirmDestination(String place){
        new AlertDialog.Builder(this)
            .setTitle("NAVEGAR EN EL MAPA ABIERTO")
            .setMessage("Destino: "+place+
                "\n\nConfirmá que sea el lugar correcto. " +
                "Revisá el pin y la ruta en la pantalla Jetour.")
            .setNegativeButton("EDITAR",null)
            .setPositiveButton("NAVEGAR",(dialog,which)->navigate(place))
            .show();
    }
    private void navigate(String place){
        if(T1ClusterWazeRouter.enabled(this)){
            // The OEM microphone follows the ACTUAL currently visible
            // navigation window on Display 1, not a saved Waze preference.
            // In a dual pane layout, only the active/selected visible
            // navigator receives the destination: NEVER open both by default.
            String pkg=T1WazeWheelService.visibleNavigationOnCluster(this);
            if(pkg==null){
                status.setText("No se pudo identificar el mapa abierto en "+
                    "Display 1. No abrí Waze ni cambié de navegador.");
                return;
            }
            if("com.google.android.apps.maps".equals(pkg)){
                T1NavigationTargets.Result maps=T1NavigationTargets.send(
                    this,ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget.MAPS,
                    "search",place,true);
                if(maps.attempted)T1LastRoute.remember(this,
                    ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget.MAPS,
                    "search",place);
                status.setText(maps.status);
                return;
            }
            if("com.waze".equals(pkg)){
                Intent waze=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("waze://?q="+Uri.encode(place)+"&navigate=yes"))
                    .setPackage("com.waze");
                if(T1ClusterWazeRouter.routeDestination(this,waze))
                    T1LastRoute.remember(this,
                        ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget.WAZE,
                        "search",place);
                status.setText(T1ClusterWazeRouter.prefs(this).getString(
                    T1ClusterWazeRouter.STATUS,"Sin resultado de clúster"));
            }
            return; // never open Waze on center display unexpectedly
        }
        // Only outside the independent cluster, maintain old explicit
        // Waze behavior for manual voice use.
        Intent waze=new Intent(Intent.ACTION_VIEW,
            Uri.parse("waze://?q="+Uri.encode(place)+"&navigate=yes"))
            .setPackage("com.waze");
        try{
            startActivity(waze);
            status.setText("Waze solicitado: "+place+
                ". Verificá el destino en la pantalla.");
        }catch(ActivityNotFoundException | SecurityException e){
            status.setText("No se abrió navegación ("+
                e.getClass().getSimpleName()+").");
        }
    }
    /** This is the ONLY platform-supported system assistant probe here.
     * ACTION_VOICE_COMMAND goes to the OS default Digital Assistant, not
     * necessarily Gemini, and never uses a private Gemini deep link.
     * No silent fullscreen fallback: the user wants eyes-free behavior.
     */
    private void openSystemVoice(){
        Intent voice=new Intent(Intent.ACTION_VOICE_COMMAND);
        try{
            if(voice.resolveActivity(getPackageManager())==null)
                throw new ActivityNotFoundException("La ROM no ofrece ACTION_VOICE_COMMAND");
            openingAssistant=true;
            startActivity(voice);
            status.setText("Android recibió la solicitud de asistente de voz "+
                "del sistema. La app que conteste depende de los ajustes OEM, "+
                "no de la selección Gemini/ChatGPT de DriveLab. "+
                "Comprobá si la voz realmente responde.");
        }catch(ActivityNotFoundException|SecurityException e){
            status.setText("SIN ASISTENTE DE VOZ NATIVO: la ROM Jetour no "+
                "expone una sesión compatible ("+
                e.getClass().getSimpleName()+"). No abrí Gemini "+
                "a pantalla completa. Para voz tipo Android Auto se necesita "+
                "la integración oficial o un servicio conversacional propio.");
        }
    }
    private boolean installed(String packageId){
        return getPackageManager().getLaunchIntentForPackage(packageId)!=null;
    }
    private void openAssistant(String packageId,boolean tryVoice){
        if(!installed(packageId)){
            status.setText(name(packageId)+" no figura instalada o no expone " +
                "una pantalla iniciable en la T1. No se instaló nada.");
            return;
        }
        // This is an experiment: ASSIST is public but the vendor may not
        // expose that action in this package or start listening directly.
        if(tryVoice&&GEMINI.equals(packageId)){
            Intent assist=new Intent(Intent.ACTION_ASSIST).setPackage(packageId);
            if(assist.resolveActivity(getPackageManager())!=null){
                try{
                    openingAssistant=true;
                    startActivity(assist);
                    status.setText("Se solicitó el asistente Gemini. " +
                        "Verificá si abrió el modo de voz.");
                    return;
                }catch(ActivityNotFoundException | SecurityException ignored){}
            }
        }
        Intent main=getPackageManager().getLaunchIntentForPackage(packageId);
        if(main==null){
            status.setText("No se encontró la pantalla de "+name(packageId));return;
        }
        try{
            openingAssistant=true;
            startActivity(main);
            status.setText(name(packageId)+" abierta. Si no comienza a escuchar " +
                "automáticamente, tocá el botón de voz de la aplicación.");
        }catch(ActivityNotFoundException | SecurityException e){
            status.setText("No se pudo abrir "+name(packageId)+": "+
                e.getClass().getSimpleName());
        }
    }
    private void shareWithAi(String packageId,String question){
        if(!installed(packageId)){
            status.setText("Reconocí tu consulta, pero "+name(packageId)+
                " no está disponible en esta T1. Elegí la otra IA o abrila " +
                "manualmente. Texto conservado en el campo.");return;
        }
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,question);
        send.setPackage(packageId);
        try{
            // ACTION_SEND is the documented Android text handoff. It is not
            // an API contract for voice response or automatic AI submission.
            if(send.resolveActivity(getPackageManager())!=null){
                openingAssistant=true;
                startActivity(send);
                status.setText("Consulta entregada a "+name(packageId)+
                    " mediante Compartir. Revisá la ventana y enviá el " +
                    "mensaje si la app no lo envía automáticamente.");
                return;
            }
        }catch(ActivityNotFoundException | SecurityException ignored){}
        // No unsupported private deep links: keep the user's text available.
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cb!=null)cb.setPrimaryClip(
            ClipData.newPlainText("Consulta DriveLab",question));
        status.setText(name(packageId)+" no acepta Compartir texto en esta ROM. "+
            "Copié tu consulta al portapapeles. No abrí la app completa "+
            "automáticamente: tocá su botón de prueba solo si estás detenido.");
    }
    @Override protected void onResume(){
        super.onResume();
        screenResumed=true;
        if(answerToSpeak!=null)
            voiceHandler.postDelayed(this::readQueuedSpeech,180L);
    }
    @Override protected void onPause(){
        screenResumed=false;
        voiceHandler.removeCallbacks(recognitionTimeout);
        listening=false;recognitionReady=false;
        if(recognizer!=null){
            recognizer.cancel();
            recognizer.destroy();
            recognizer=null;
        }
        super.onPause();
    }
    @Override protected void onDestroy(){
        voiceHandler.removeCallbacksAndMessages(null);
        releaseSpeechFocus();
        if(speaker!=null){
            speaker.stop();
            speaker.shutdown();
            speaker=null;
        }
        ttsReady=false;
        super.onDestroy();
    }
}