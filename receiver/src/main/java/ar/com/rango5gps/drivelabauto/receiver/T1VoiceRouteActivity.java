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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * Single-tap Jetour voice entry. Navigational requests -> Waze with confirmation.
 * Other speech -> the preferred installed AI app, using documented Android SEND
 * when supported. Experimental direct voice-app launch; neither vendor
 * publishes a guaranteed third-party Intent for Gemini Live / ChatGPT Voice.
 * SpeechRecognizer runs only while this foreground Activity is visible.
 */
public final class T1VoiceRouteActivity extends T1ImmersiveActivity {
    private static final int VOICE_REQUEST=7042, AUDIO_PERMISSION=7043;
    private static final String GEMINI="com.google.android.apps.bard";
    private static final String CHATGPT="com.openai.chatgpt";
    private static final String PREF="t1_voice_router_v1";
    private static final String KEY_AI="default_ai";
    private static final int BG=T1JetourTheme.NIGHT;
    private EditText spoken;
    private TextView status,chosen;
    private SpeechRecognizer recognizer;
    private boolean listening,openingAssistant;

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
        if(chosen!=null)
            chosen.setText("CONSULTAS GENERALES → "+name(selectedAi())+
                "  |  NAVEGACIÓN → WAZE");
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
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
        label(root,"Hablá normalmente. «Navegá a la estación» → Waze. " +
            "«Cuánto es 37 por 12», «explicame...» o «noticias de hoy» → " +
            "la IA elegida. Las órdenes de navegación se confirman " +
            "antes de abrir Waze.",14,T1JetourTheme.MUTED);

        button(root,"🎤 HABLAR · DETECCIÓN AUTOMÁTICA",0xFF426E73,this::listen);
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

        label(root,"PROBAR ASISTENTES INSTALADOS",17,T1JetourTheme.CYAN);
        button(root,"GEMINI · ABRIR ASISTENTE / VOZ (PRUEBA)",
            T1JetourTheme.GREEN_BUTTON,()->openAssistant(GEMINI,true));
        button(root,"CHATGPT · ABRIR ASISTENTE / VOZ (PRUEBA)",
            T1JetourTheme.PANEL_HI,()->openAssistant(CHATGPT,true));
        button(root,"CONSULTAR A LA IA CON EL TEXTO ESCRITO",
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

        label(root,"IA PREDETERMINADA DEL BOTÓN ÚNICO",16,T1JetourTheme.CYAN);
        button(root,"USAR GEMINI PARA LAS CONSULTAS",
            T1JetourTheme.PANEL,()->chooseAi(GEMINI));
        button(root,"USAR CHATGPT PARA LAS CONSULTAS",
            T1JetourTheme.PANEL,()->chooseAi(CHATGPT));
        button(root,"MICRÓFONO EXTERNO · ALTERNATIVA DE RECONOCIMIENTO",
            T1JetourTheme.PANEL,this::listenExternal);

        status=label(root,"Abriendo reconocimiento local de Android al iniciar…",
            14,T1JetourTheme.WHITE);
        status.setTextIsSelectable(true);
        button(root,"VOLVER",T1JetourTheme.PANEL,this::finish);
        setContentView(sc);
        // The assigned Hola Jetour hardware launch is already a deliberate
        // foreground user action: listen once, no hot mic and no boot listener.
        if(state==null)root.post(this::listen);
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
        if(listening)return;
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
                    listening=false;
                    if(isFinishing()||isDestroyed())return;
                    status.setText("El reconocedor interno devolvió "+code+
                        ". Tocá HABLAR de nuevo o usá MICRÓFONO EXTERNO. " +
                        "Si la ROM reserva el audio, este método puede fallar.");
                }
                @Override public void onResults(Bundle b){
                    listening=false;
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
            status.setText("Inicializando micrófono sin ventana adicional…");
            recognizer.startListening(speechIntent());
        }catch(RuntimeException e){
            listening=false;
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
        dispatch(said);
    }
    private void dispatch(String phrase){
        if(phrase==null||phrase.trim().length()<2){
            status.setText("Primero decí o escribí tu consulta.");return;
        }
        String target=T1VoiceIntentPolicy.routeDestination(phrase);
        if(target!=null){
            status.setText("NAVEGACIÓN DETECTADA → Waze · "+target);
            confirmDestination(target);
        }else{
            status.setText("CONSULTA DETECTADA → "+name(selectedAi())+
                " · intentando entregar el texto reconocido.");
            shareWithAi(selectedAi(),phrase.trim());
        }
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
            "Copié la consulta al portapapeles: pegala en su chat.");
        openAssistant(packageId,false);
    }
    @Override protected void onPause(){
        listening=false;
        if(recognizer!=null){
            recognizer.cancel();
            recognizer.destroy();
            recognizer=null;
        }
        super.onPause();
    }
}