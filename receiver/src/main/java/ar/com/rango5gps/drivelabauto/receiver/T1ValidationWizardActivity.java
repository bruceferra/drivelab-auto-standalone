package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import org.json.JSONObject;

/**
 * Single entry point for unfinished T1 interoperability checks.
 * Keeps the owner's observations separately from OEM-confirmed evidence.
 * Reads only fixed, previously inventoried properties. No control setters.
 * All read actions are user-initiated and all Binder queries run off UI thread.
 */
public final class T1ValidationWizardActivity extends T1ImmersiveActivity {
    private static final int BG=T1JetourTheme.NIGHT,CARD=T1JetourTheme.PANEL,
        FG=T1JetourTheme.WHITE,MUTED=T1JetourTheme.MUTED,
        BLUE=T1JetourTheme.PANEL_HI,GREEN=T1JetourTheme.GREEN_BUTTON,
        GOLD=T1JetourTheme.AMBER_BUTTON,RED=T1JetourTheme.RED_BUTTON;
    private static final String PREFS="t1_validation_wizard_v1";
    private static final String NOT_TESTED="PENDIENTE", TESTED="PROBADO",
        NO_SIGNAL="SIN SEÑAL", CONFIRMED="VALIDADO POR USUARIO";

    private static final class Task {
        final String id,group,title,procedure,criteria,source,action,kind;
        Task(String id,String group,String title,String procedure,
             String criteria,String source,String action,String kind) {
            this.id=id;this.group=group;this.title=title;
            this.procedure=procedure;this.criteria=criteria;this.source=source;
            this.action=action;this.kind=kind;
        }
    }
    private static final Task[] TASKS={
        new Task("volante","CONTROLES","Botones del volante · KeyEvent",
            "Estacioná en P. Tocá ABRIR CAPTURA, verificá que diga GRABANDO, " +
            "y pulsá de a uno OK, Atrás y ruedita +/−. Esperá la cuenta regresiva. " +
            "Compartí el informe aunque no llegue ningún evento.",
            "El resultado útil es un keyCode y scanCode por botón. Si el contador " +
            "queda en cero, el sistema OEM podría estar interceptando las teclas.",
            "VOLANTE · captura Android KeyEvent", "steer",""),
        new Task("wiper_auto","CARROCERÍA","Limpia delantero · sensibilidad AUTO",
            "En P, con los limpias detenidos, tomá CAPTURA A. Cambiá SOLO " +
            "la sensibilidad AUTO con el control original Jetour (baja/alta), " +
            "sin mover el vehículo. Tomá CAPTURA B.",
            "Compará el dato 2126. Cambiar el mando original no demuestra " +
            "que DriveLab pueda escribirlo. No confundas sensibilidad con velocidad.",
            "VEHÍCULO · LIMPIAPARABRISAS / RADAR", "vehicle","wiper"),
        new Task("wiper_rear","CARROCERÍA","Limpia trasero · estado",
            "En P, tomá CAPTURA A con el limpia trasero apagado. " +
            "Activá el limpia desde el mando original y tomá CAPTURA B. " +
            "Luego devolvé el mando a apagado.",
            "El candidato de lectura es 2125. Si no cambia, puede ser otro " +
            "estado del BCM; no inventar un código de escritura.",
            "VEHÍCULO · LIMPIAPARABRISAS / RADAR","vehicle","wiper"),
        new Task("wiper_manual","CARROCERÍA","Limpia delantero · velocidad manual",
            "Estacioná en P y abrí CODE · DETECTOR 45 S. Accioná " +
            "una sola posición de barrido manual desde el mando OEM y " +
            "compará si cambia alguna lectura. No hay un ID confirmado " +
            "para velocidad manual y el 2126 NO es ese control.",
            "Si el detector no descubre señal, registrá SIN SEÑAL. " +
            "No enviar escrituras a códigos desconocidos.",
            "VEHÍCULO · CODE · DETECTOR DE CAMBIOS","code",""),
        new Task("lights_cabin","ILUMINACIÓN","Plafón trasero, puertas y baúl",
            "Con el auto inmóvil, apagá luces interiores y cerrá puertas: " +
            "CAPTURA A. Encendé el plafón OEM o abrí una puerta/baúl: CAPTURA B. " +
            "Hacé una sola acción entre capturas para saber qué cambió.",
            "Buscar cambios en luz de cabina y estados de puertas/baúl. " +
            "El estado de una puerta no prueba control eléctrico del plafón.",
            "COCKPIT · ZONAS / DIAGNÓSTICOS · LUCES TRASERAS/BAÚL",
            "cockpit","cabin"),
        new Task("lights_seats","ILUMINACIÓN","Luces individuales · segunda fila",
            "En P, apagá las luces de lectura traseras y tomá CAPTURA A. " +
            "Encendé SOLO una luz original y tomá CAPTURA B. Repetí para " +
            "cada asiento con una nueva captura inicial.",
            "Comparar 356519683/356519684 con area 0/16/32/64. " +
            "Un valor cero constante no demuestra control disponible.",
            "COCKPIT · ZONAS / DIAGNÓSTICOS · LUCES POR ASIENTO",
            "cockpit","seat"),
        new Task("rear_tail_lights","ILUMINACIÓN","Luces traseras exteriores · mantener encendidas",
            "Este pendiente es DISTINTO del plafón interior y del LED " +
            "frontal. En P, observá las luces traseras con el mando " +
            "original y anotá su estado visible y la opción OEM. " +
            "Todavía no hay una señal de control trasera validada.",
            "Falta identificar lectura y permiso BCM específicos. " +
            "No forzar salidas, deshabilitar luces obligatorias ni " +
            "confundir bienvenida delantera con luces de posición.",
            "COCKPIT · DIAGNÓSTICOS LED / VEHÍCULO · CODE","code",""),
        new Task("led_exterior","ILUMINACIÓN","Luces exteriores / tira LED",
            "Con el vehículo inmóvil y sin desactivar luces de circulación " +
            "obligatorias, abrí el diagnóstico del Cockpit. Usá REGISTRAR LED " +
            "30 S y activá únicamente funciones originales de iluminación " +
            "en P para comparar cambios.",
            "Los candidatos LED de bienvenida y parrilla no son el mando " +
            "de luces traseras de circulación. No se habilitan escrituras.",
            "COCKPIT · ZONAS / DIAGNÓSTICOS · LED FRONTAL",
            "cockpit",""),
        new Task("soc_modes","ENERGÍA","SMART / FORZADO · estrategia SOC",
            "En P, iniciá diagnóstico de modos PHEV (3 min). Cambiá " +
            "SMART y FORZADO desde la pantalla OEM y observá lecturas 1040. " +
            "Exportá el CSV del diagnóstico al terminar.",
            "Lecturas físicas conocidas: 1040=2 SMART y 1040=4 FORZADO. " +
            "La escritura desde DriveLab sigue sin readback validado.",
            "VEHÍCULO · DIAGNÓSTICO PHEV","diagn",""),
        new Task("soc_target","ENERGÍA","Objetivo SOC · propiedad 1041",
            "Con la camioneta detenida, cambiá el objetivo desde la " +
            "pantalla OEM y capturá lecturas 1041 int/float antes y después. " +
            "Tomá nota del porcentaje que muestra Jetour.",
            "Debe coincidir el objetivo visible con el código y el tipo de " +
            "dato. No escribir 1041 desde DriveLab hasta validar contrato.",
            "VEHÍCULO · DIAGNÓSTICO PHEV","diagn",""),
        new Task("soc_auto","ENERGÍA","AUTO de SOC · definición OEM",
            "Buscá si el menú ORIGINAL de tu versión ofrece AUTO. Si " +
            "aparece, registrá su nombre visible y una captura de 1040 " +
            "mientras está seleccionado. No fuerces el valor con DriveLab.",
            "El enum OEM contiene 1040=3 Auto, pero aún no sabemos si " +
            "existe o está habilitado en esta unidad argentina.",
            "VEHÍCULO · DIAGNÓSTICO PHEV","diagn",""),
        new Task("regen","ENERGÍA","Regeneración · baja / media / alta",
            "En P, cambiá de a un nivel la recuperación desde el control " +
            "original; registrá la lectura 1046 en el diagnóstico PHEV.",
            "El mapa candidato es 1/2/3. Aún falta confirmar que cada " +
            "nivel del menú corresponde a la lectura real de tu T1.",
            "VEHÍCULO · DIAGNÓSTICO PHEV","diagn",""),
        new Task("auto_road","ENERGÍA","AUTO EV / HEV / EV MAX · autopista",
            "Con el modo optimizado activado antes del recorrido, " +
            "dejá que el sistema registre las transiciones normalmente. " +
            "No manipules el Cockpit ni hagas comandos durante la marcha. " +
            "Una vez estacionado, revisá estado y CSV del modo OEM.",
            "Ya observaste un cambio en ciudad. Falta verificar " +
            "clasificación de autopista, confirmación 1043 y ausencia " +
            "de oscilaciones. Se mantienen controles originales.",
            "COCKPIT · MODO OPTIMIZADO / DIAGNÓSTICO","cockpit",""),
        new Task("pdc","SENSORES","Radar de estacionamiento / PDC",
            "En P, con el vehículo inmóvil y ningún objeto moviéndose " +
            "cerca, hacé CAPTURA A. Activá/desactivá únicamente el menú " +
            "original de radar y hacé CAPTURA B. No conduzcas para ensayar.",
            "Compará 2049, 2107 y PDC crudo. No asignar centímetros " +
            "a valores sin calibración y no alterar alertas de seguridad.",
            "VEHÍCULO · LIMPIAPARABRISAS / RADAR","vehicle","wiper"),
        new Task("mirrors","CARROCERÍA","Espejos · marcha R / restauración",
            "Probá por separado los mandos manuales en P que ya funcionan. " +
            "Para registrar R/D, usá GRABAR MARCHAS 35 S como diagnóstico. " +
            "No actives movimiento automático mientras falten stop y posición.",
            "2068/2069 manuales probados. Falta una restauración confiable " +
            "a la posición inicial y seguridad del pulso.",
            "VEHÍCULO · ESPEJOS / REGISTRO DE MARCHAS","vehicle",""),
        new Task("motor_electric","TELEMETRÍA","RPM y estado motor eléctrico",
            "En el Cockpit, abrí TEMPERATURAS / MOTOR · VER CÓDIGOS " +
            "y compará con el tablero original. No uses RPM térmicas " +
            "1035 para inferir velocidad del motor eléctrico.",
            "Sin ID único y escala confirmados para RPM eléctricas.",
            "COCKPIT · DIAGNÓSTICOS · TEMPERATURAS","cockpit",""),
        new Task("belts","SENSORES","Cinturones traseros · lectura OEM",
            "En P, usá capturas A/B desde el Cockpit, con ocupantes " +
            "que abrochen y desabrochen de forma normal. No alteres " +
            "hebillas ni silencies alarmas del vehículo.",
            "Señales de abrochado y ocupación candidatas: si no varían " +
            "hay que localizar el ECU correcto.",
            "COCKPIT · DIAGNÓSTICOS · CINTURONES","cockpit",""),
        new Task("voice","NAVEGACIÓN","Micrófono integrado · Waze clúster",
            "Con la T1 detenida, tocá MICRÓFONO, decí un destino inequívoco " +
            "y confirmá el texto. Si no hay reconocedor Android, probá teclado.",
            "Validar que el pin real de Waze aparezca en clúster; " +
            "que Android acepte el Intent no garantiza que Waze navegue.",
            "RECEIVER / COCKPIT · DESTINO POR VOZ","voice","")
    };

    private SharedPreferences prefs;
    private int selected=0;
    private boolean showIndex=false, busy=false;
    private TextView resultView, progressView;
    private EditText noteView;
    private WiperParkingProbe wiper;
    private T1CabinLightReadOnlyProbe cabin;
    private T1RearSeatLightsProbe seats;
    private final Map<String,Map<String,String>> beforeWiper=new LinkedHashMap<>();
    private final Map<String,T1CabinLightReadOnlyProbe.Snapshot> beforeCabin=
        new LinkedHashMap<>();
    private final Map<String,T1RearSeatLightsProbe.Snapshot> beforeSeats=
        new LinkedHashMap<>();

    @Override protected int overlayClearBackgroundColor(){return BG;}
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        wiper=new WiperParkingProbe(this);
        cabin=new T1CabinLightReadOnlyProbe(this);
        seats=new T1RearSeatLightsProbe(this);
        selected=Math.max(0,Math.min(TASKS.length-1,
            prefs.getInt("selected",0)));
        render();
    }
    private String state(Task task){
        return prefs.getString(task.id+"_status",NOT_TESTED);
    }
    private String now(){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
            Locale.ROOT).format(new Date());
    }
    private void select(int next){
        if(next<0||next>=TASKS.length)return;
        if(noteView!=null)saveNote();
        selected=next;
        prefs.edit().putInt("selected",selected).apply();
        render();
    }
    private void saveNote(){
        if(noteView==null)return;
        prefs.edit().putString(TASKS[selected].id+"_note",
            noteView.getText().toString().trim()).apply();
    }
    private TextView text(String content,int size,int color){
        TextView t=new TextView(this);
        t.setText(content);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(10,10,10,10);
        return t;
    }
    private LinearLayout column(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);return c;
    }
    private LinearLayout row(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);return c;
    }
    private Button button(LinearLayout c,String caption,int color,
                          Runnable action){
        Button b=new Button(this);
        b.setText(caption);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        T1JetourTheme.button(b,color);
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(3,3,3,3);c.addView(b,lp);
        return b;
    }
    private Button small(LinearLayout c,String caption,int color,
                         Runnable action){
        Button b=button(c,caption,color,action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.setMargins(3,3,3,3);b.setLayoutParams(lp);return b;
    }
    private void render(){
        Task task=TASKS[selected];
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackground(T1JetourTheme.backdrop());
        LinearLayout body=column();body.setPadding(16,8,16,24);
        scroll.addView(body);
        body.addView(text("DRIVELAB T1 · VALIDACIONES PENDIENTES",
            21,FG));
        int ok=0,none=0,attempted=0;
        for(Task item:TASKS){
            String s=state(item);
            if(CONFIRMED.equals(s))ok++;
            else if(NO_SIGNAL.equals(s))none++;
            else if(TESTED.equals(s))attempted++;
        }
        progressView=text("Total "+TASKS.length+" · validadas por vos "+ok+
            " · probadas "+attempted+" · sin señal "+none+
            " · pendientes sin probar "+(TASKS.length-ok-none-attempted),
            13,MUTED);
        body.addView(progressView);
        body.addView(text("Todas las lecturas de este asistente son SOLO LECTURA. " +
            "Estacioná en P, 0 km/h. La marca VALIDADO es tu observación, no " +
            "una certificación del control OEM. Nunca se envían órdenes a " +
            "luces, limpiaparabrisas, radar, SOC ni propulsión.",
            12,MUTED));
        LinearLayout navigator=row();body.addView(navigator);
        small(navigator,"‹ ANTERIOR",BLUE,()->select(
            (selected+TASKS.length-1)%TASKS.length));
        small(navigator,"SIG. PENDIENTE ›",GREEN,this::nextUnconfirmed);
        button(body,showIndex?"OCULTAR LISTA DE PRUEBAS":
            "VER TODOS LOS PENDIENTES · ELEGIR PRUEBA",BLUE,()->{
            saveNote();showIndex=!showIndex;render();
        });
        if(showIndex){
            for(int i=0;i<TASKS.length;i++){
                final int next=i;Task t=TASKS[i];
                int color=selected==i?GOLD:
                    CONFIRMED.equals(state(t))?GREEN:
                    NO_SIGNAL.equals(state(t))?RED:CARD;
                button(body,(i+1)+" · "+t.title+" · "+state(t),color,
                    ()->{showIndex=false;select(next);});
            }
        }
        LinearLayout card=column();card.setPadding(13,13,13,15);
        card.setBackground(T1JetourTheme.surface(CARD,17));body.addView(card);
        card.addView(text((selected+1)+"/"+TASKS.length+" · "+
            task.group+" · "+state(task),14,MUTED));
        card.addView(text(task.title,22,FG));
        card.addView(text("QUÉ HACER\n"+task.procedure,15,FG));
        card.addView(text("QUÉ TENEMOS QUE VALIDAR\n"+task.criteria,
            13,MUTED));
        card.addView(text("DÓNDE ESTABA ANTES: "+task.source,
            12,MUTED));
        if(!task.kind.isEmpty()){
            LinearLayout pair=row();card.addView(pair);
            Button a=small(pair,"1 · CAPTURA A",BLUE,()->capture(task,false));
            Button b=small(pair,"2 · CAPTURA B",GREEN,()->capture(task,true));
            // Captura A se guarda durante 30 minutos: podés abrir temporalmente
            // la pantalla original Jetour, cambiar una opción y volver a B.
            // Callbacks guard concurrency; buttons never remain disabled
            // after returning from another Activity while a read completes.
            card.addView(text("Entre A y B accioná una sola opción " +
                "desde el menú/mando ORIGINAL de Jetour. No se " +
                "envían escrituras desde DriveLab.",12,MUTED));
        }
        button(card,"ABRIR PRUEBA ORIGINAL · "+task.source,
            BLUE,()->openModule(task.action));
        resultView=text(prefs.getString(task.id+"_report",
            "Sin evidencia guardada para esta prueba."),13,FG);
        resultView.setTextIsSelectable(true);
        card.addView(resultView);
        button(card,"GUARDAR / COMPARTIR INFORME DE ESTA PRUEBA",
            BLUE,()->shareCurrent(false));
        noteView=new EditText(this);
        noteView.setSingleLine(false);
        noteView.setMinLines(2);
        T1JetourTheme.number(noteView);
        noteView.setHint("Qué hiciste, qué valor cambió, qué no respondió… (sin VIN ni domicilios)");
        noteView.setText(prefs.getString(task.id+"_note",""));
        card.addView(noteView);
        button(card,"GUARDAR OBSERVACIÓN",BLUE,()->{
            saveNote();
            resultView.setText(prefs.getString(task.id+"_report","")+
                "\nObservación guardada · "+now());
        });
        card.addView(text("RESULTADO DE ESTA PRUEBA",14,MUTED));
        LinearLayout verdict=row();card.addView(verdict);
        small(verdict,"FUNCIONÓ",GREEN,()->mark(CONFIRMED));
        small(verdict,"SIN SEÑAL",RED,()->mark(NO_SIGNAL));
        small(verdict,"PROBÉ / DUDAS",GOLD,()->mark(TESTED));
        button(card,"VOLVER A PENDIENTE",CARD,()->mark(NOT_TESTED));
        button(body,"COMPARTIR RESUMEN DE TODAS LAS PRUEBAS",
            BLUE,()->shareCurrent(true));
        button(body,"← VOLVER AL COCKPIT",CARD,()->finish());
        setContentView(scroll);
    }
    private void nextUnconfirmed(){
        for(int k=1;k<=TASKS.length;k++){
            int j=(selected+k)%TASKS.length;
            if(!CONFIRMED.equals(state(TASKS[j]))){
                select(j);return;
            }
        }
        select((selected+1)%TASKS.length);
    }
    private void mark(String value){
        saveNote();
        prefs.edit().putString(TASKS[selected].id+"_status",value)
            .putString(TASKS[selected].id+"_updated",now()).apply();
        render();
    }
    private void openModule(String key){
        saveNote();
        Intent intent;
        switch(key){
            case "steer":
                intent=new Intent(this,T1SteeringKeysActivity.class);break;
            case "vehicle":
                intent=new Intent(this,ReceiverVehicleActivity.class)
                    .putExtra("open_tab",0);break;
            case "code":
                intent=new Intent(this,ReceiverVehicleActivity.class)
                    .putExtra("open_tab",2);break;
            case "diagn":
                intent=new Intent(this,ReceiverVehicleActivity.class)
                    .putExtra("open_tab",3);break;
            case "cockpit":
                intent=new Intent(this,T1PremiumCockpitActivity.class)
                    .putExtra("open_validation_advanced",true);break;
            case "voice":
                intent=new Intent(this,T1VoiceRouteActivity.class);break;
            default: return;
        }
        startActivity(intent);
    }
    private String summary(){
        StringBuilder s=new StringBuilder(
            "DRIVELAB T1 · ESTADO DEL WIZARD · "+now()+"\n"+
            "Resultados declarados por el propietario, NO homologación OEM.\n");
        for(int i=0;i<TASKS.length;i++){
            Task t=TASKS[i];
            s.append("\n").append(i+1).append(" · ").append(t.title)
                .append(" · ").append(state(t))
                .append(" · ").append(prefs.getString(t.id+"_updated","sin fecha"));
            String note=prefs.getString(t.id+"_note","");
            if(!note.isEmpty())s.append("\nObservación: ").append(note);
            String evidence=prefs.getString(t.id+"_report","");
            if(!evidence.isEmpty())s.append("\nEvidencia: ").append(
                evidence.length()>900?evidence.substring(0,900)+" [recortado]":evidence);
        }
        return s.toString();
    }
    private void shareCurrent(boolean all){
        saveNote();
        String report=all?summary():
            "Wizard · "+TASKS[selected].title+"\n"+
            "Estado propietario: "+state(TASKS[selected])+"\n"+
            "Observación: "+prefs.getString(TASKS[selected].id+"_note","")+
            "\n"+prefs.getString(TASKS[selected].id+"_report",
                 "Prueba aún sin captura; adjuntar diagnóstico original.");
        if(resultView!=null)resultView.setText(
            "Guardando informe local y enviando si tenés nube QR activa…");
        T1ReportShare.share(this,all?"wizard_resumen":
            "wizard_"+TASKS[selected].id,report,status->{
                if(resultView!=null&&!isFinishing()&&!isDestroyed())
                    resultView.setText(status+"\n\n"+
                        prefs.getString(TASKS[selected].id+"_report",""));
            });
    }
    private void status(String msg){
        if(resultView!=null&&!isFinishing()&&!isDestroyed())
            resultView.setText(msg);
    }
    private void storeReport(String key,String value){
        prefs.edit().putString(key+"_report",value)
            .putString(key+"_read_at",now()).apply();
        if(TASKS[selected].id.equals(key))status(value);
    }
    private void capture(Task task,boolean after){
        if(busy){
            status("Otra lectura OEM sigue activa; esperá su resultado.");return;
        }
        if(after&&!hasBase(task)){
            status("FALTA CAPTURA A RECIENTE: tomá una lectura inicial " +
                "con el mando OEM en su primer estado. La base dura 30 minutos.");return;
        }
        busy=true;
        String id=task.id;
        status("LEYENDO OEM · "+(after?"CAPTURA B":"CAPTURA A")+
            " · puede tardar algunos segundos. NO se escribe al vehículo.");
        switch(task.kind){
            case "wiper":{
                boolean started=wiper.readOnce(map->{
                    try{
                        Map<String,String> copy=new LinkedHashMap<>(map);
                        if(!after){
                            beforeWiper.put(id,copy);
                            saveBaseline(id,"wiper",new JSONObject(copy).toString());
                            storeReport(id,"CAPTURA A · "+now()+"\n"+
                                summarizeMap(copy,id));
                        }else{
                            Map<String,String> a=cachedWiper(id);
                            storeReport(id,compareWiper(a,copy,id));
                        }
                    }finally{busy=false;}
                });
                if(!started){
                    busy=false;
                    status("Lectura de limpia/radar ya en curso. " +
                        "No se envió otro pedido.");
                }
                break;
            }
            case "cabin":
                new Thread(()->{
                    T1CabinLightReadOnlyProbe.Snapshot snapshot=cabin.read();
                    runOnUiThread(()->{
                        try{
                            if(!after){
                                beforeCabin.put(id,snapshot);
                                saveBaseline(id,"cabin",encodeCabin(snapshot));
                                storeReport(id,"CAPTURA A · "+snapshot.describe());
                            }else{
                                T1CabinLightReadOnlyProbe.Snapshot old=cachedCabin(id);
                                if(old!=null)storeReport(id,
                                    T1CabinLightReadOnlyProbe.Snapshot.compare(old,snapshot));
                            }
                        }finally{busy=false;}
                    });
                },"T1-Wizard-Cabin-ReadOnly").start();
                break;
            case "seat":
                new Thread(()->{
                    T1RearSeatLightsProbe.Snapshot snapshot=seats.read();
                    runOnUiThread(()->{
                        try{
                            if(!after){
                                beforeSeats.put(id,snapshot);
                                saveBaseline(id,"seat",encodeSeats(snapshot));
                                storeReport(id,"CAPTURA A · "+snapshot.summary());
                            }else{
                                T1RearSeatLightsProbe.Snapshot old=cachedSeats(id);
                                if(old!=null)storeReport(id,
                                    T1RearSeatLightsProbe.Snapshot.compare(old,snapshot));
                            }
                        }finally{busy=false;}
                    });
                },"T1-Wizard-Seat-ReadOnly").start();
                break;
            default:
                busy=false;
                status("Esta prueba se realiza con la pantalla original.");
        }
    }
    private boolean hasBase(Task task){
        if("wiper".equals(task.kind))return cachedWiper(task.id)!=null;
        if("cabin".equals(task.kind))return cachedCabin(task.id)!=null;
        if("seat".equals(task.kind))return cachedSeats(task.id)!=null;
        return false;
    }
    private void saveBaseline(String id,String kind,String json){
        prefs.edit().putString(id+"_baseline_"+kind,json)
            .putLong(id+"_baseline_at",System.currentTimeMillis()).apply();
    }
    private boolean recent(String id){
        long stamp=prefs.getLong(id+"_baseline_at",0L);
        long age=System.currentTimeMillis()-stamp;
        return stamp>0&&age>=0&&age<=1800000L;
    }
    private static String encodeCabin(T1CabinLightReadOnlyProbe.Snapshot s){
        JSONObject j=new JSONObject();
        try{
            j.put("header",s.header);
            for(Map.Entry<Integer,String> e:s.values.entrySet())
                j.put(Integer.toString(e.getKey()),e.getValue());
        }catch(Exception ignored){}
        return j.toString();
    }
    private static T1CabinLightReadOnlyProbe.Snapshot decodeCabin(String raw){
        try{
            JSONObject j=new JSONObject(raw);
            T1CabinLightReadOnlyProbe.Snapshot s=
                new T1CabinLightReadOnlyProbe.Snapshot();
            s.header=j.optString("header","");
            Iterator<String> it=j.keys();
            while(it.hasNext()){
                String key=it.next();
                if("header".equals(key))continue;
                try{s.values.put(Integer.parseInt(key),j.optString(key,""));}
                catch(NumberFormatException ignored){}
            }
            return s.values.isEmpty()?null:s;
        }catch(Exception e){return null;}
    }
    private static String encodeSeats(T1RearSeatLightsProbe.Snapshot s){
        JSONObject j=new JSONObject();
        try{
            j.put("header",s.header);
            for(int k=0;k<s.readings.length;k++)
                for(int i=0;i<s.readings[k].length;i++)
                    if(s.readings[k][i]!=null)
                        j.put(k+"_"+i,s.readings[k][i]);
        }catch(Exception ignored){}
        return j.toString();
    }
    private static T1RearSeatLightsProbe.Snapshot decodeSeats(String raw){
        try{
            JSONObject j=new JSONObject(raw);
            T1RearSeatLightsProbe.Snapshot s=
                new T1RearSeatLightsProbe.Snapshot();
            s.header=j.optString("header","");
            int count=0;
            for(int k=0;k<s.readings.length;k++)
                for(int i=0;i<s.readings[k].length;i++){
                    String key=k+"_"+i;
                    if(j.has(key)){
                        s.readings[k][i]=j.optString(key,"");count++;
                    }
                }
            return count==0?null:s;
        }catch(Exception e){return null;}
    }
    private Map<String,String> cachedWiper(String id){
        if(!recent(id))return null;
        Map<String,String> memory=beforeWiper.get(id);
        if(memory!=null)return memory;
        try{
            JSONObject j=new JSONObject(prefs.getString(id+"_baseline_wiper","{}"));
            Map<String,String> map=new LinkedHashMap<>();
            Iterator<String> it=j.keys();
            while(it.hasNext()){
                String key=it.next();map.put(key,j.optString(key,""));
            }
            if(!map.isEmpty()){beforeWiper.put(id,map);return map;}
        }catch(Exception ignored){}
        return null;
    }
    private T1CabinLightReadOnlyProbe.Snapshot cachedCabin(String id){
        if(!recent(id))return null;
        T1CabinLightReadOnlyProbe.Snapshot s=beforeCabin.get(id);
        if(s!=null)return s;
        s=decodeCabin(prefs.getString(id+"_baseline_cabin","{}"));
        if(s!=null)beforeCabin.put(id,s);
        return s;
    }
    private T1RearSeatLightsProbe.Snapshot cachedSeats(String id){
        if(!recent(id))return null;
        T1RearSeatLightsProbe.Snapshot s=beforeSeats.get(id);
        if(s!=null)return s;
        s=decodeSeats(prefs.getString(id+"_baseline_seat","{}"));
        if(s!=null)beforeSeats.put(id,s);
        return s;
    }
    private static boolean useful(String x){
        // -1 is the unavailable/uninitialized sentinel observed on this T1
        // for wiper AUTO 2126. Never count it as a valid comparable value.
        return x!=null&&!x.isEmpty()&&!x.trim().equals("-1")&&
            !x.trim().equals("-1.0")&&!x.startsWith("SIN ")&&
            !x.startsWith("no disponible")&&!x.startsWith("—");
    }
    private static boolean scope(String id,String key){
        switch(id){
            case "wiper_auto":return key.startsWith("2126");
            case "wiper_rear":return key.startsWith("2125");
            case "pdc":return key.startsWith("2049")||
                key.startsWith("2107")||key.startsWith("PDC ")||
                key.contains("TRASERO")||key.contains("DELANTERO");
            default:return true;
        }
    }
    private static String summarizeMap(Map<String,String> map,String id){
        StringBuilder s=new StringBuilder();
        for(Map.Entry<String,String> e:map.entrySet())
            if(scope(id,e.getKey()))
                s.append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        return s.toString();
    }
    private static String compareWiper(Map<String,String> a,Map<String,String> b,
                                       String id){
        StringBuilder s=new StringBuilder(
            "COMPARACIÓN OEM A / B · "+id+"\n");
        int known=0,changed=0;
        for(Map.Entry<String,String> e:b.entrySet()){
            String key=e.getKey();
            if(!scope(id,key))continue;
            String old=a==null?null:a.get(key),next=e.getValue();
            if(useful(old)&&useful(next)){
                known++;
                if(!old.equals(next)){
                    changed++;
                    s.append("CAMBIÓ · ").append(key).append(" : ")
                        .append(old).append(" → ").append(next).append("\n");
                }
            }
        }
        s.append("Valores legibles en ambas: ").append(known)
            .append("; diferencias: ").append(changed).append("\n");
        if(changed==0)s.append(
            "Sin variación observable. No prueba ausencia de hardware; " +
            "puede faltar permiso, ECU o ID correcto.\n");
        s.append("\nCAPTURA A\n").append(summarizeMap(a,id))
            .append("\nCAPTURA B\n").append(summarizeMap(b,id))
            .append("\nSOLO LECTURA. No se enviaron órdenes.");
        return s.toString();
    }
}