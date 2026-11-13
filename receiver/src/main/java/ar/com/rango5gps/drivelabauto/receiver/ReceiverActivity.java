package ar.com.rango5gps.drivelabauto.receiver;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;
import ar.com.rango5gps.drivelabauto.common.Updater;

/** Receiver hub with separate quick-control pilots; navigation service unchanged. */
public final class ReceiverActivity extends T1ImmersiveActivity {
    @Override protected int overlayClearBackgroundColor(){return T1JetourTheme.NIGHT;}
    private static volatile boolean visible;
    private String message="";
    private ReceiverQuickControls quickControls;
    private final Handler pairUiHandler=new Handler(Looper.getMainLooper());
    private TextView pairStatusView,pairCodeView,pairWindowView,pairPortView;
    private ImageView pairQrView;
    private String lastPairQrValue="";
    private ScrollView currentScroll;
    private boolean connectionExpanded=false;
    private boolean advancedControlsExpanded=false;
    private long lastAutoCheckElapsed=0;
    private final Runnable pairUiRefresh=new Runnable(){
        @Override public void run(){
            if(!visible||isFinishing()||isDestroyed())return;
            refreshPairPanel();
            if(quickControls!=null)quickControls.refresh();
            pairUiHandler.postDelayed(this,750);
        }
    };
    public static boolean isVisible(){return visible;}
    private SharedPreferences prefs(){return getSharedPreferences(ReceiverService.PREFS,MODE_PRIVATE);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // Fresh app launch: user requested cabin-air recirculation to become
        // the DEFAULT without touching its button. The existing foreground
        // guard continues while other screens/Maps are in front and survives
        // reboot through BootReceiver. OEM safety PAUSED is respected.
        // Auto air and reverse mirrors are now armed by the shared base
        // Activity, so all Receiver pages receive the same defaults.
        quickControls=new ReceiverQuickControls(this);
        render();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;quickControls.onResume();render();
        pairUiHandler.removeCallbacks(pairUiRefresh);
        pairUiHandler.postDelayed(pairUiRefresh,450);
        Updater.onResume(this,this::report);
        long now=SystemClock.elapsedRealtime();
        if(lastAutoCheckElapsed==0||now-lastAutoCheckElapsed>120000){
            lastAutoCheckElapsed=now;
            Updater.checkOnOpen(this,"receiver",this::report);
        }
    }
    @Override protected void onPause(){
        visible=false;
        quickControls.onPause();
        pairUiHandler.removeCallbacks(pairUiRefresh);
        super.onPause();
    }
    private void label(LinearLayout root,String text,int size){
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);
        view.setTextColor(size>=20?T1JetourTheme.CYAN:T1JetourTheme.MUTED);
        view.setPadding(7,10,7,10);root.addView(view);
    }
    private void button(LinearLayout root,String text,Runnable task){
        Button button=new Button(this);button.setText(text);button.setAllCaps(false);
        T1JetourTheme.button(button,
            text.contains("PENDIENT")?0xFF426E73:
            text.contains("ACTUALIZAR RECEIVER")?T1JetourTheme.GREEN_BUTTON:
            T1JetourTheme.PANEL_HI);
        button.setOnClickListener(v->task.run());root.addView(button);
    }
    private void report(String text){message=text;render();}
    private void render(){
        final int restoreY=currentScroll==null?0:currentScroll.getScrollY();
        ScrollView scroll=new ScrollView(this);
        scroll.setBackground(T1JetourTheme.backdrop());
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(22,20,22,35);scroll.addView(body);
        SharedPreferences p=prefs();
        label(body,"DRIVELAB AUTO · RECEIVER",23);
        // Read the installed APK metadata, never show a stale hard-coded
        // version banner (observed earlier 0.2.31 / 0.2.36 screenshot).
        String installedVersion="desconocida";
        try{
            installedVersion=getPackageManager().getPackageInfo(
                getPackageName(),0).versionName;
        }catch(Exception ignored){}
        label(body,"v"+installedVersion+
            " · pantalla inmersiva + área libre Hola Jetour",13);
        // Immersive full screen is always enabled in this T1.
        // HolaJetour was removed by the installed Launcher. No bar toggles.
        button(body,"VEHÍCULO · VENTANILLAS / ENERGÍA / CODE",()->
            startActivity(new Intent(this,ReceiverVehicleActivity.class)));
        button(body,"PENDIENTES · ASISTENTE DE VALIDACIÓN T1",()->
            startActivity(new Intent(this,T1ValidationWizardActivity.class)));
        button(body,"🎤 ASISTENTE · WAZE / GEMINI / CHATGPT",()->
            startActivity(new Intent(this,T1VoiceRouteActivity.class)));
        button(body,"COCKPIT · JORNADA / VIAJES / ENERGÍA",()->
            startActivity(new Intent(this,T1PremiumCockpitActivity.class)));
        button(body,"TABLERO · TANQUES / CONSUMO / TELEMETRÍA",()->
            startActivity(new Intent(this,T1TankDashboardActivity.class)));
        label(body,"Conexión Receiver: "+p.getString("status","Sin iniciar")+
            " · IP "+ReceiverService.localIp()+
            " · "+p.getString("pair_port_status","sin datos del puerto 48392"),14);
        button(body,connectionExpanded?
            "OCULTAR CONEXIÓN Y CONFIGURACIÓN":
            "CONEXIÓN XIAOMI / AJUSTES / ACTUALIZAR",()->{
            connectionExpanded=!connectionExpanded;
            render();
        });
        LinearLayout settings=new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setVisibility(connectionExpanded?android.view.View.VISIBLE:
            android.view.View.GONE);
        body.addView(settings);
        label(settings,"Jetour T1 · Receptor cifrado independiente",14);
        label(settings,"IP de T1: "+ReceiverService.localIp()+" · Puertos 48391 / 48392",15);
        label(settings,"ESTADO: "+p.getString("status","Sin iniciar")+
            "\nDestinos recibidos: "+p.getInt("received",0)+
            "\nÚltima navegación: "+p.getString("last_open","Sin pruebas"),14);
        button(settings,"INICIAR RECEPTOR · en segundo plano",()->start(ReceiverService.ACTION_START));
        button(settings,"DETENER RECEPTOR",()->new AlertDialog.Builder(this)
            .setMessage("Se detendrá el receptor y su inicio automático. ¿Confirmás?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DETENER",(d,w)->start(ReceiverService.ACTION_STOP)).show());
        button(settings,"VINCULAR XIAOMI POR QR · SIN CÓDIGOS",()->
            start(ReceiverService.ACTION_PAIR_QR));
        pairQrView=new ImageView(this);
        pairQrView.setAdjustViewBounds(true);
        pairQrView.setMaxHeight(490);
        pairQrView.setVisibility(android.view.View.GONE);
        settings.addView(pairQrView);
        lastPairQrValue="";
        button(settings,"MÉTODO ANTERIOR · 6 DÍGITOS",
            ()->start(ReceiverService.ACTION_PAIR));
        label(settings,"VINCULACIÓN POR QR: con ambos equipos en la misma red, "+
            "tocá el botón QR y escaneá desde DriveLab Auto Sender en Xiaomi. "+
            "El enlace cifrado se confirma automáticamente. "+
            "No requiere escribir IP ni comparar seis dígitos.",14);
        pairStatusView=pairLabel(settings,"Vinculación: comprobando…",16);
        pairPortView=pairLabel(settings,"Puerto 48392: comprobando…",14);
        pairWindowView=pairLabel(settings,"Ventana de vinculación: no iniciada",14);
        pairCodeView=pairLabel(settings,"CÓDIGO: esperando solicitud de Xiaomi",20);
        label(settings,"Alternativa manual: si el lector QR de Xiaomi no está "+
            "disponible, usá el botón MÉTODO ANTERIOR, compará los seis dígitos "+
            "y autorizá primero en T1, después en Sender.",13);
        button(settings,"AUTORIZAR CÓDIGO COINCIDENTE · PASO 1",this::approve);
        button(settings,"ACTUALIZAR ESTADO",this::render);
        boolean auto=p.getBoolean("auto_boot",false);
        label(settings,"Inicio automático del receptor: "+(auto?"ACTIVO":"DESACTIVADO"),16);
        button(settings,auto?"Desactivar inicio automático":"ACTIVAR INICIO AL ENCENDER T1",()->{
            p.edit().putBoolean("auto_boot",!auto).apply();
            if(!auto)start(ReceiverService.ACTION_START);else render();
        });
        label(settings,"Permiso para iniciar Waze desde segundo plano: "+
            (Settings.canDrawOverlays(this)?"CONCEDIDO":"SIN AUTORIZAR"),14);
        button(settings,"AUTORIZAR SEGUNDO PLANO EN ANDROID",this::requestBackground);
        boolean waze=p.getBoolean("open_waze",true);
        button(settings,waze?"Desactivar apertura automática de Waze":
            "Activar apertura automática de Waze",()->{
            p.edit().putBoolean("open_waze",!waze).apply();render();
        });
        label(settings,"App opcional al encender: "+p.getString("launch_label","NINGUNA")+
            "\nEstado último arranque: "+p.getString("boot_status","Sin pruebas"),13);
        button(settings,"ELEGIR APP PARA ABRIR AL ENCENDER",this::pickBootApp);
        boolean launch=p.getBoolean("launch_app_on_boot",false);
        button(settings,launch?"Desactivar apertura de app al encender":
            "Activar apertura de app al encender",()->{
            if(!launch&&(!p.getBoolean("auto_boot",false)||
                !Settings.canDrawOverlays(this)||p.getString("launch_package","").isEmpty())){
                report("Para abrir una app al encender: activá inicio automático, "+
                    "concedé permiso de segundo plano y elegí la app.");return;
            }
            p.edit().putBoolean("launch_app_on_boot",!launch).apply();render();
        });
        button(settings,"Quitar app de inicio",()->{
            p.edit().remove("launch_package").remove("launch_label")
                .putBoolean("launch_app_on_boot",false).apply();render();
        });
        button(settings,"PRUEBA DIRECTA A LA PLATA",()->direct("-34.9214,-57.9545"));
        button(settings,"PRUEBA DIRECTA A MAR DEL PLATA",()->direct("-38.0055,-57.5426"));
        button(settings,"CONTROLES OEM AVANZADOS",()->{
            advancedControlsExpanded=!advancedControlsExpanded;
            render();
        });
        if(advancedControlsExpanded){
            LinearLayout quickAdvanced=new LinearLayout(this);
            quickAdvanced.setOrientation(LinearLayout.VERTICAL);
            settings.addView(quickAdvanced);
            quickControls.attach(quickAdvanced);
        }
        button(settings,"ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA",
            ()->Updater.check(this,"receiver",this::report));
        if(!message.isEmpty())label(settings,"Actualizador / estado: "+message,14);
        label(settings,"Estado instalador T1: "+
            getSharedPreferences("drivelab_native_update",MODE_PRIVATE)
                .getString("last_install_status","Aún no hay instalación solicitada"),14);
        button(settings,"REINTENTAR INSTALACIÓN YA DESCARGADA",
            ()->Updater.retryInstall(this,this::report));
        button(settings,"INSTALADOR NATIVO ALTERNATIVO",
            ()->Updater.tryNativeIntent(this,this::report));
        button(settings,"ABRIR AJUSTES DE RECEIVER (PERMISO)",
            ()->Updater.openReceiverSettings(this,this::report));
        label(settings,"El sistema Android debe confirmar la instalación. "+
            "Si Jetour rechaza ambos métodos, el estado indicará el motivo. "+
            "No desactives Play Protect ni desinstales Receiver.",13);
        label(settings,"El servicio sigue activo aunque cierres esta pantalla. "+
            "No fuerza Wi-Fi ni modifica Android Auto o T1Launcher. "+
            "La apertura en clúster depende de la ROM de la camioneta.",13);
        Receiver3DButtons.apply(body);
        setContentView(scroll);
        refreshPairPanel();
        currentScroll=scroll;
        if(restoreY>0)scroll.post(()->{
            if(currentScroll==scroll)scroll.scrollTo(0,restoreY);
        });
    }
    private TextView pairLabel(LinearLayout parent,String value,int size){
        TextView view=new TextView(this);
        view.setText(value);view.setTextSize(size);
        view.setTextIsSelectable(true);
        view.setPadding(7,9,7,9);
        parent.addView(view);
        return view;
    }
    private void refreshPairPanel(){
        if(pairStatusView==null||pairCodeView==null||
            pairWindowView==null||pairPortView==null)return;
        SharedPreferences p=prefs();
        pairStatusView.setText("Estado de vinculación: "+
            p.getString("pair_status","Sin solicitud registrada"));
        pairPortView.setText("Conexión: "+p.getString("status","Sin iniciar")+
            " | "+p.getString("pair_port_status","Puerto de vinculación: aún sin iniciar")+
            " | IP T1: "+ReceiverService.localIp()+
            " | Clave de T1: "+
            (p.contains("secret")?"CONFIGURADA":"NO CONFIGURADA"));
        long until=p.getLong("pair_until",0);
        long ms=until-System.currentTimeMillis();
        pairWindowView.setText(ms>0?
            "VENTANA ABIERTA · quedan "+((ms+999)/1000)+" segundos para vincular.":
            "Ventana cerrada. Tocá VINCULAR XIAOMI y empezá desde el teléfono.");
        String code=p.getString("pair_code","");
        boolean qrMode=p.getString("pair_qr_ticket","").length()==48;
        pairCodeView.setText(qrMode?
            "MODO QR · escaneá con Sender en Xiaomi (no pide código)":
            (code.matches("[0-9]{6}")?
                "CÓDIGO T1: "+code+" · compará con Xiaomi":
                "CÓDIGO: esperando solicitud manual desde Xiaomi"));
        if(pairQrView!=null){
            String token=p.getString("pair_qr_ticket","");
            String ip=ReceiverService.localIp();
            boolean ready=p.getLong("pair_until",0)>System.currentTimeMillis()&&
                token.matches("[0-9a-f]{48}")&&!ip.isEmpty();
            String value=ready?"drivelabauto://pair?ip="+ip+"&ticket="+token:"";
            if(!value.equals(lastPairQrValue)){
                lastPairQrValue=value;
                if(ready){
                    try{
                        pairQrView.setImageBitmap(T1QrRenderer.pairing(value));
                        pairQrView.setVisibility(android.view.View.VISIBLE);
                    }catch(Exception ex){
                        pairQrView.setVisibility(android.view.View.GONE);
                    }
                }else{
                    pairQrView.setImageDrawable(null);
                    pairQrView.setVisibility(android.view.View.GONE);
                }
            }
        }
    }
    private void start(String action){
        Intent service=new Intent(this,ReceiverService.class).setAction(action);
        try{
            if(ReceiverService.ACTION_STOP.equals(action))startService(service);
            else startForegroundService(service);
            report(ReceiverService.ACTION_PAIR_QR.equals(action)?
                "QR de vinculación activo 2 minutos. Escaneá con DriveLab Sender en Xiaomi.":
                ReceiverService.ACTION_PAIR.equals(action)?
                "Vinculación manual abierta 2 minutos. Compará los seis dígitos.":
                "Solicitud enviada al servicio. Actualizá estado.");
        }catch(Exception ex){report("Android bloqueó el inicio del servicio: "+
            ex.getClass().getSimpleName());}
    }
    private void approve(){
        SharedPreferences p=prefs();
        String code=p.getString("pair_code","");long id=p.getLong("pair_id",0);
        if(code.length()!=6||id==0||System.currentTimeMillis()>p.getLong("pair_until",0)){
            report("No hay código vigente. Tocá VINCULAR XIAOMI y pedí el código desde el teléfono.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("COMPARAR: "+code)
            .setMessage("Autorizá únicamente si el Xiaomi muestra estos mismos seis números.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("SÍ, COINCIDEN",(d,w)->{
                p.edit().putLong("approved_id",id)
                    .putString("pair_status","AUTORIZADA · confirmá ahora en el Xiaomi").apply();render();
            }).show();
    }
    private void requestBackground(){
        new AlertDialog.Builder(this)
            .setTitle("Abrir Waze sin dejar Receiver visible")
            .setMessage("Para Android 11, concedé a DriveLab Auto Receiver el permiso "+
                "'Mostrar sobre otras apps'. El receptor no dibuja ninguna ventana "+
                "flotante ni lee la pantalla; usa el permiso solo para solicitar "+
                "abrir Waze cuando está en segundo plano. El fabricante puede restringirlo.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR AJUSTES",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));}
                catch(Exception error){report("Abrí Ajustes → Apps → DriveLab Auto Receiver → "+
                    "Mostrar sobre otras apps");}
            }).show();
    }
    private void direct(String coordinates){
        Intent open=new Intent(Intent.ACTION_VIEW,
            Uri.parse("waze://?ll="+coordinates+"&navigate=yes"));
        open.setPackage("com.waze");
        try{startActivity(open);}catch(Exception error){report("Waze no se pudo iniciar");}
    }
    private void pickBootApp(){
        PackageManager pm=getPackageManager();
        Intent search=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all=pm.queryIntentActivities(search,0);
        TreeMap<String,String> names=new TreeMap<>();
        for(ResolveInfo result:all){
            if(result.activityInfo==null)continue;
            String pkg=result.activityInfo.packageName;
            if(getPackageName().equals(pkg))continue;
            CharSequence label=result.loadLabel(pm);
            String name=(label==null?pkg:label.toString())+" · "+pkg;
            names.put(name,pkg);
        }
        if(names.isEmpty()){report("No se encontraron apps iniciables");return;}
        String[] options=names.keySet().toArray(new String[0]);
        String[] packages=names.values().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("APP AL ENCENDER T1")
            .setItems(options,(d,n)->{
                prefs().edit().putString("launch_package",packages[n])
                    .putString("launch_label",options[n]).apply();render();
            }).setNegativeButton("CANCELAR",null).show();
    }
}
