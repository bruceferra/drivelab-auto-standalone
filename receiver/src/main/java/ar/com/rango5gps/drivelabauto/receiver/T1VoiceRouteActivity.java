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
 * Single-tap Jetour voice entry. Navigational requests -> Waze with confirmation.
 * Other speech -> the preferred installed AI app, using documented Android SEND
 * when supported. Experimental direct voice-app launch; neither vendor
 * publishes a guaranteed third-party Intent for Gemini Live / ChatGPT Voice.
 * SpeechRecognizer runs only while this foreground Activity is visible.
 */
public final class T1VoiceRouteActivity extends T1ImmersiveActivity {
    private static final int VOICE_REQUEST=7042, AUDIO_PERMISSION=7043;
    static final String ACTION_SHORTCUT_SYSTEM_VOICE=
        "ar.com.rango5gps.drivelabauto.receiver.SYSTEM_VOICE_SHORTCUT";
    private static final String GEMINI="com.google.android.apps.bard";
    private static final String CHATGPT="com.openai.chatgpt";
    private static final String PREF="t1_voice_router_v1";
    private static final String KEY_AI="default_ai";
    private static final int BG=T1JetourTheme.NIGHT;
    private EditText spoken;
    private TextView status,chosen;
    private Button geminiChoice,chatgptChoice,selectedAction;
    private SpeechRecognizer recognizer;
    private boolean listening,openingAssistant;
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
    private SharedPreferences prefs(){
        return getSharedPreferences(PREF,MODE_PRIVATE);
    }
    private String selectedAi(){
        String value=prefs().getString(KEY_AI,GEMINI);
        return CHATGPT.equals(value)?CHATGPT:GEMINI;
    }
    private String name(String packageId){
        return CHATGPT.equals(packageId)?"ChatGPT":"Gemini";
    }
    private void chooseAi(String packageId){
        prefs().edit().putString(KEY_AI,packageId).apply();
        showSelected();
        status.setText(name(packageId)+" será la IA predeterminada para preguntas " +
            "que no sean navegación. No cambia el asistente OEM del vehículo.");
    }
    private void showSelected(){
        String active=name(selectedAi());
        if(chosen!=null)
            chosen.setText("IA ACTIVA: "+active+
                " (preguntas reconocidas)  |  NAVEGACIÓN → WAZE");
        if(geminiChoice!=null){
            geminiChoice.setText(GEMINI.equals(selectedAi())?
                "✓ GEMINI · ELEGIDO":"USAR GEMINI PARA CONSULTAS");
            T1JetourTheme.button(geminiChoice,GEMINI.equals(selectedAi())?
                T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL);
        }
        if(chatgptChoice!=null){
            chatgptChoice.setText(CHATGPT.equals(selectedAi())?
                "✓ CHATGPT · ELEGIDO":"USAR CHATGPT PARA CONSULTAS");
            T1JetourTheme.button(chatgptChoice,CHATGPT.equals(selectedAi())?
                T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL);
        }
        if(selectedAction!=null)
            selectedAction.setText("CONSULTAR "+active.toUpperCase()+
                " · TEXTO RECONOCIDO / ESCRITO");
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        initSpeechOutput();
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackground(T1JetourTheme.backdrop());
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(19,9,19,18);
        sc.addView(root);
        label(root,"🎤  ASISTENTE DE VOZ · JETOUR T1",22,T1JetourTheme.CYAN);
        chosen=label(root,"",14,T1JetourTheme.GREEN);
        showSelected();
        label(root,"Reconocimiento Google de esta T1: entiende destinos y "+
            "preguntas y devuelve el TEXTO a DriveLab. Navegación explícita "+
            "→ Waze; cuentas y clima → respuesta hablada; actualidad y fútbol "+
            "→ fuentes en línea. Gemini del sistema queda como opción "+
            "separada para preguntas libres.",14,T1JetourTheme.MUTED);

        button(root,"🎤 HABLAR · GOOGLE (DETECTAR DESTINO O PREGUNTA)",
            T1JetourTheme.GREEN_BUTTON,this::listenExternal);
        button(root,"RECONOCIMIENTO INTERNO · DIAGNÓSTICO",
            T1JetourTheme.PANEL_HI,this::listen);
        spoken=new EditText(this);
        spoken.setSingleLine(false);
        spoken.setMinLines(2);
        spoken.setHint("Consulta, orden o destino reconocido");
        T1JetourTheme.number(spoken);
        root.addView(spoken,new LinearLayout.LayoutParams(-1,-2));
        button(root,"EJECUTAR LO ESCRITO / RECONOCIDO",T1JetourTheme.PANEL_HI,
            ()->dispatch(spoken.getText().toString()));
        button(root,"WAZE · NAVEGAR A DESTINO ESCRITO",0xFF426E73,
            ()->{
                String line=spoken.getText().toString().trim();
                String route=T1VoiceIntentPolicy.routeDestination(line);
                if(route!=null)line=route;
                if(line.length()<3){
                    status.setText("Ingresá un destino válido.");return;
                }
                confirmDestination(line);
            });

        label(root,"ASISTENTE NATIVO · EXPERIMENTAL",17,T1JetourTheme.CYAN);
        button(root,"🎙 PROBAR VOZ DEL SISTEMA (SIN ABRIR APP COMPLETA)",
            T1JetourTheme.GREEN_BUTTON,this::openSystemVoice);
        label(root,"Este botón solicita la sesión de voz configurada por "+
            "Android en la Jetour. Si la ROM no ofrece un asistente de voz, "+
            "DriveLab no puede forzar Gemini Live ni el Gemini de Android Auto. "+
            "La IA elegida abajo se aplica a consultas reconocidas por DriveLab, "+
            "NO al asistente predeterminado de Android.",12,T1JetourTheme.MUTED);
        label(root,"PROBAR APPS INSTALADAS · PANTALLA COMPLETA",
            17,T1JetourTheme.CYAN);
        button(root,"GEMINI · ABRIR ASISTENTE / VOZ (PRUEBA)",
            T1JetourTheme.GREEN_BUTTON,()->openAssistant(GEMINI,true));
        button(root,"CHATGPT · ABRIR ASISTENTE / VOZ (PRUEBA)",
            T1JetourTheme.PANEL_HI,()->openAssistant(CHATGPT,true));
        selectedAction=button(root,"CONSULTAR IA ELEGIDA",
            T1JetourTheme.PANEL,
            ()->{
                String question=spoken.getText().toString().trim();
                if(question.isEmpty()){
                    status.setText("Primero ingresá una consulta.");return;
                }
                shareWithAi(selectedAi(),question);
            });
        label(root,"Cada app controla su propia sesión de audio. En Gemini " +
            "se intenta la entrada de asistente Android; si la ROM no la " +
            "expone, se abre la app normal. ChatGPT puede requerir tocar " +
            "su micrófono. Esto no es Gemini integrado en Android Auto.",
            12,T1JetourTheme.MUTED);

        label(root,"IA PREDETERMINADA PARA PREGUNTAS RECONOCIDAS",
            16,T1JetourTheme.CYAN);
        geminiChoice=button(root,"USAR GEMINI PARA LAS CONSULTAS",
            T1JetourTheme.PANEL,()->chooseAi(GEMINI));
        chatgptChoice=button(root,"USAR CHATGPT PARA LAS CONSULTAS",
            T1JetourTheme.PANEL,()->chooseAi(CHATGPT));
        showSelected();
        button(root,"MICRÓFONO GOOGLE · REPETIR RECONOCIMIENTO",
            T1JetourTheme.PANEL,this::listenExternal);

        status=label(root,"Elegí HABLAR para reconocimiento Google. "+
            "DriveLab clasificará el texto antes de abrir Waze u otra respuesta.",
            14,T1JetourTheme.WHITE);
        status.setTextIsSelectable(true);
        button(root,"VOLVER",T1JetourTheme.PANEL,this::finish);
        setContentView(sc);
        // A T1Launcher-selected shortcut starts the external Google
        // recognition interface that DOES return spoken text to DriveLab.
        // Do not intercept the OEM physical button already mapped to Waze.
        if(state==null&&ACTION_SHORTCUT_SYSTEM_VOICE.equals(getIntent().getAction()))
            root.post(this::listenExternal);
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
            status.setText("No hay servicio de reconocimiento integrado accesible. " +
                "Abriendo la alternativa Android.");
            listenExternal();
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
                }
                @Override public void onResults(Bundle b){
                    voiceHandler.removeCallbacks(recognitionTimeout);
                    listening=false;recognitionReady=false;
                    ArrayList<String> matches=b==null?null:
                        b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
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
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,
        String[] permissions,int[] granted){
        super.onRequestPermissionsResult(requestCode,permissions,granted);
        if(requestCode!=AUDIO_PERMISSION)return;
        if(granted.length>0&&granted[0]==PackageManager.PERMISSION_GRANTED)
            listen();
        else
            status.setText("Permiso de micrófono denegado; " +
                "podés seguir con el reconocedor externo o teclado.");
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
        recognize(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
    }
    private void recognize(ArrayList<String> results){
        if(isFinishing()||isDestroyed())return;
        if(results==null||results.isEmpty()||
                results.get(0)==null||results.get(0).trim().isEmpty()){
            status.setText("No se reconoció el mensaje. Reintentá.");return;
        }
        String said=results.get(0).trim();
        spoken.setText(said);spoken.setSelection(spoken.length());
        // A full voice request is deliberate: no second confirmation.
        dispatch(said,true);
    }
    private void dispatch(String phrase){
        // Typed/manual routes retain the confirmation dialog.
        dispatch(phrase,false);
    }
    private void dispatch(String phrase,boolean fromVoice){
        if(phrase==null||phrase.trim().length()<2){
            status.setText("Primero decí o escribí tu consulta.");return;
        }
        String target=T1VoiceIntentPolicy.routeDestination(phrase);
        if(target!=null){
            status.setText("NAVEGACIÓN DETECTADA → Waze/clúster · "+target);
            if(fromVoice){
                // The explicit "ir a"/"navegar a" utterance is itself
                // authorization. Waze still resolves the actual location.
                // Never treat arbitrary questions/numbers as destinations.
                navigate(target);
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
    /** Respond within DriveLab without forcing another microphone touch.
     * Output depends on Android TTS engine and its audio route.
     */
    private void initSpeechOutput(){
        try{
            speaker=new TextToSpeech(this, result->{
                if(result!=TextToSpeech.SUCCESS)return;
                if(speaker==null)return;
                speaker.setLanguage(new Locale("es","AR"));
                ttsReady=true;
                if(answerToSpeak!=null)readQueuedSpeech();
            });
        }catch(Exception ignored){ttsReady=false;}
    }
    private void readQueuedSpeech(){
        if(!ttsReady||speaker==null||answerToSpeak==null)return;
        String answer=answerToSpeak;
        answerToSpeak=null;
        speaker.speak(answer,TextToSpeech.QUEUE_FLUSH,null,"drivelab_voice_response");
    }
    private void say(String line,String audible){
        status.setText(line);
        answerToSpeak=audible;
        readQueuedSpeech();
    }
    private void confirmDestination(String place){
        new AlertDialog.Builder(this)
            .setTitle("NAVEGAR EN WAZE")
            .setMessage("Destino: "+place+
                "\n\nConfirmá que sea el lugar correcto. " +
                "Revisá el pin y la ruta en la pantalla Jetour.")
            .setNegativeButton("EDITAR",null)
            .setPositiveButton("NAVEGAR",(dialog,which)->navigate(place))
            .show();
    }
    private void navigate(String place){
        Intent i=new Intent(Intent.ACTION_VIEW,
            Uri.parse("waze://?q="+Uri.encode(place)+"&navigate=yes"))
            .setPackage("com.waze");
        try{
            startActivity(i);
            status.setText("Waze solicitado: "+place+
                ". Verificá el destino en el clúster.");
        }catch(ActivityNotFoundException | SecurityException e){
            status.setText("Waze no resolvió la navegación ("+
                e.getClass().getSimpleName()+"). Sin ruta enviada.");
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
    @Override protected void onPause(){
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
        if(speaker!=null){
            speaker.stop();
            speaker.shutdown();
            speaker=null;
        }
        ttsReady=false;
        super.onDestroy();
    }
}