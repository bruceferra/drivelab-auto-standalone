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
    private boolean technicalExpanded=false;
    private boolean technicalOemExpanded=false;
    private static final String UPDATE_POLL_PREFS="drivelab_receiver_update_poll";
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
        // A second, explicitly requested DriveLab cockpit panel on Display 0.
        // The shell starts a new Freeform Receiver task WITHOUT LAUNCHER
        // category, then opens Cockpit inside this new task. Never finishes
        // or relaunches the original Driver/Cockpit task.
        if(getIntent()!=null&&getIntent().getBooleanExtra(
                "drivelab_center_cockpit_tile",false)){
            startActivity(new Intent(this,T1PremiumCockpitActivity.class));
            finish();
            return;
        }
        // Fresh app launch: user requested cabin-air recirculation to become
        // the DEFAULT without touching its button. The existing foreground
        // guard continues while other screens/Maps are in front and survives
        // reboot through BootReceiver. OEM safety PAUSED is respected.
        // Auto air and reverse mirrors are now armed by the shared base
        // Activity, so all Receiver pages receive the same defaults.
        // Start directly in Cockpit from launcher or the owner's boot
        // auto-open. Explicit INICIO navigation remains the settings hub.
        if((getIntent()!=null&&getIntent().hasCategory(Intent.CATEGORY_LAUNCHER))||
           (getIntent()!=null&&getIntent().getBooleanExtra(
               "drivelab_open_after_boot",false))){
            Intent cockpit=new Intent(this,T1PremiumCockpitActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(cockpit);
            finish();
            return;
        }
        quickControls=new ReceiverQuickControls(this);
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(getIntent().getBooleanExtra("drivelab_open_after_boot",false)){
            prefs().edit().putString("boot_status",
                "DriveLab abrió su pantalla tras el arranque de Android").apply();
            getIntent().removeExtra("drivelab_open_after_boot");
        }
        if(isFinishing()||quickControls==null)return;
        visible=true;quickControls.onResume();render();
        pairUiHandler.removeCallbacks(pairUiRefresh);
        pairUiHandler.postDelayed(pairUiRefresh,450);
        Updater.reconcileInstalled(this);
        Updater.onResume(this,this::report);
        // A new Activity is created on many T1 screen changes. Do not
        // exhaust the unauthenticated GitHub API rate limit by checking
        // on every return from Cockpit, Maps or cluster settings.
        // The manual "COMPROBAR ACTUALIZACIÓN" remains immediate.
        SharedPreferences poll=getSharedPreferences(UPDATE_POLL_PREFS,MODE_PRIVATE);
        long now=System.currentTimeMillis(),previous=poll.getLong("last",0L);
        if(previous<=0||now<previous||now-previous>=30L*60L*1000L){
            poll.edit().putLong("last",now).apply();
            Updater.checkOnOpen(this,"receiver",this::report);
        }
    }
    @Override protected void onPause(){
        visible=false;
        if(quickControls!=null)quickControls.onPause();
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
        // Daily home: no duplicate fixed-toolbar controls or lab pilots.
        label(body,"FUNCIONES",17);
        button(body,"COCKPIT · VIAJES Y ENERGÍA",()->
            startActivity(new Intent(this,T1PremiumCockpitActivity.class)));
        // Navigation/window design is OWNED by the separate DriveLab
        // Bridge APK. This data/telemetry app must not duplicate controls.
        label(body,"MAPAS · VENTANAS · QR XIAOMI → DRIVELAB BRIDGE",13);
        if(T1BootInstaller.installed(this)){
            button(body,"ABRIR DRIVELAB BRIDGE",
                ()->T1BootInstaller.openBoot(this,this::report));
        }else{
            label(body,"Bridge no instalado. Este Receiver no configura "+
                "las ventanas ni toma el volante.",13);
        }
        button(body,"APPS ABIERTAS · CERRAR / LIMPIAR MEMORIA",()->
            startActivity(new Intent(this,T1AppTaskManagerActivity.class)));
        button(body,"CAPTURA DE PANTALLA · TRES DEDOS / COMPARTIR",()->
            startActivity(new Intent(this,T1ScreenshotReviewActivity.class)));
        button(body,"VEHÍCULO · ESTADO Y AJUSTES",()->
            startActivity(new Intent(this,ReceiverVehicleActivity.class)));
        button(body,technicalExpanded?"CERRAR MENÚ TÉCNICO":
            "MENÚ TÉCNICO · PRUEBAS Y AJUSTES",()->{
            technicalExpanded=!technicalExpanded;render();
        });
        LinearLayout settings=new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setVisibility(connectionExpanded?android.view.View.VISIBLE:
            android.view.View.GONE);
        body.addView(settings);
        LinearLayout technical=new LinearLayout(this);
        technical.setOrientation(LinearLayout.VERTICAL);
        technical.setVisibility(technicalExpanded?android.view.View.VISIBLE:
            android.view.View.GONE);
        body.addView(technical);
        if(technicalExpanded){
            label(technical,"DIAGNÓSTICO Y CONFIGURACIÓN AVANZADA",17);
            label(technical,"Mapas, tableros y rueda se configuran "+
                "en DriveLab Bridge. Aquí sólo vehículo, registros "+
                "y apagado de servicios anteriores.",13);
            button(technical,"PENDIENTES · VALIDACIONES OEM",()->
                startActivity(new Intent(this,T1ValidationWizardActivity.class)));
            button(technical,"TABLERO · TANQUES Y TELEMETRÍA",()->
                startActivity(new Intent(this,T1TankDashboardActivity.class)));
            button(technical,"OPTIMIZADOR · CONFIGURAR",()->
                startActivity(new Intent(this,T1OptimizedModeActivity.class)));
            button(technical,"ASISTENTE DE VOZ · CONFIGURAR",()->
                startActivity(new Intent(this,T1VoiceRouteActivity.class)));
            button(technical,technicalOemExpanded?
                "OCULTAR CONTROLES OEM DE PRUEBA":
                "CONTROLES OEM DE PRUEBA · ESPEJOS",()->{
                technicalOemExpanded=!technicalOemExpanded;render();
            });
            if(technicalOemExpanded){
                LinearLayout oldControls=new LinearLayout(this);
                oldControls.setOrientation(LinearLayout.VERTICAL);
                technical.addView(oldControls);
                quickControls.attach(oldControls);
            }
        }
        label(settings,"CONEXIÓN SEGURA · XIAOMI",16);
        label(settings,"IP de T1: "+ReceiverService.localIp()+" · Puertos 48391 / 48392",15);
        label(settings,"ESTADO: "+p.getString("status","Sin iniciar")+
            "\nDestinos recibidos: "+p.getInt("received",0)+
            "\nÚltima navegación: "+p.getString("last_open","Sin pruebas"),14);
        button(technical,"INICIAR RECEPTOR · en segundo plano",()->start(ReceiverService.ACTION_START));
        button(technical,"DETENER RECEPTOR",()->new AlertDialog.Builder(this)
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
        label(technical,"Inicio automático del receptor: "+(auto?"ACTIVO":"DESACTIVADO"),16);
        button(technical,auto?"Desactivar inicio automático":"ACTIVAR INICIO AL ENCENDER T1",()->{
            p.edit().putBoolean("auto_boot",!auto).apply();
            if(!auto)start(ReceiverService.ACTION_START);else render();
        });
        label(technical,"Permiso para iniciar Waze desde segundo plano: "+
            (Settings.canDrawOverlays(this)?"CONCEDIDO":"SIN AUTORIZAR"),14);
        button(technical,"AUTORIZAR SEGUNDO PLANO EN ANDROID",this::requestBackground);
        boolean waze=p.getBoolean("open_waze",true);
        label(technical,"Cuando CLÚSTER está ON, las rutas del Sender entran "+
            "aunque esté apagada la apertura de Waze fuera del clúster.",13);
        button(technical,waze?"DESACTIVAR APERTURA FUERA DEL CLÚSTER":
            "ACTIVAR APERTURA FUERA DEL CLÚSTER",()->{
            p.edit().putBoolean("open_waze",!waze).apply();render();
        });
        label(technical,"App opcional al encender: "+p.getString("launch_label","NINGUNA")+
            "\nEstado último arranque: "+p.getString("boot_status","Sin pruebas"),13);
        button(technical,"ELEGIR APP PARA ABRIR AL ENCENDER",this::pickBootApp);
        boolean launch=p.getBoolean("launch_app_on_boot",false);
        button(technical,launch?"Desactivar apertura de app al encender":
            "Activar apertura de app al encender",()->{
            if(!launch&&(!p.getBoolean("auto_boot",false)||
                !Settings.canDrawOverlays(this)||p.getString("launch_package","").isEmpty())){
                report("Para abrir una app al encender: activá inicio automático, "+
                    "concedé permiso de segundo plano y elegí la app.");return;
            }
            p.edit().putBoolean("launch_app_on_boot",!launch).apply();render();
        });
        button(technical,"Quitar app de inicio",()->{
            p.edit().remove("launch_package").remove("launch_label")
                .putBoolean("launch_app_on_boot",false).apply();render();
        });
        button(technical,"PRUEBA DIRECTA A LA PLATA",()->direct("-34.9214,-57.9545"));
        button(technical,"PRUEBA DIRECTA A MAR DEL PLATA",()->direct("-38.0055,-57.5426"));
        label(settings,"Receiver instalado: "+Updater.installedVersion(this),14);
        button(settings,"COMPROBAR ACTUALIZACIÓN RECEIVER",
            ()->Updater.check(this,"receiver",this::report));
        if(!message.isEmpty())label(settings,"Actualizador / estado: "+message,14);
        label(settings,"Instalador: "+Updater.installationStatus(this),14);
        if(Updater.hasPendingInstall(this)){
            button(settings,"REINTENTAR INSTALACIÓN DESCARGADA",
                ()->Updater.retryInstall(this,this::report));
            button(settings,"INSTALADOR NATIVO ALTERNATIVO",
                ()->Updater.tryNativeIntent(this,this::report));
            button(settings,"ABRIR AJUSTES RECEIVER (PERMISO)",
                ()->Updater.openReceiverSettings(this,this::report));
        }
        label(settings,"Instalación Android: no desinstales Receiver para actualizar.",13);
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
    /** Persist a single, explicit owner selection, without claiming HOME.
     * RECEIVER's existing BootReceiver + foreground service handle cold boot.
     * Android/Jetour may still block presenting an Activity from background.
     */
    private void toggleOwnBoot(){
        SharedPreferences p=prefs();
        boolean own=p.getBoolean("launch_app_on_boot",false)&&
            getPackageName().equals(p.getString("launch_package",""));
        if(own){
            p.edit().putBoolean("launch_app_on_boot",false)
                .putString("boot_status","Apertura de DriveLab desactivada por usuario")
                .apply();
            report("Apertura de DriveLab al encender: DESACTIVADA. "+
                "Los servicios que ya uses mantienen su propia preferencia.");
            return;
        }
        p.edit().putBoolean("auto_boot",true)
            .putBoolean("launch_app_on_boot",true)
            .putString("launch_package",getPackageName())
            .putString("launch_label","DriveLab Auto Receiver")
            .putString("boot_status","Configurado; pendiente de comprobar próximo "+
                "arranque real de Android").apply();
        start(ReceiverService.ACTION_START);
        if(!Settings.canDrawOverlays(this)){
            report("Configurado, pero falta permitir 'Mostrar sobre otras apps'.");
            requestBackground();
        }else{
            report("Inicio automático configurado. En el próximo arranque "+
                "real de Android se solicitará abrir DriveLab; T1Launcher "+
                "seguirá siendo HOME.");
        }
    }
    private void requestBackground(){
        new AlertDialog.Builder(this)
            .setTitle("Permiso para abrir DriveLab al encender y Waze")
            .setMessage("Android 11 puede restringir que una app abra otra "+
                "pantalla desde segundo plano. Permití 'Mostrar sobre otras "+
                "apps' para DriveLab Auto Receiver. Esta app no dibuja "+
                "una ventana flotante ni lee la pantalla; el permiso permite "+
                "solicitar la apertura. El sistema Jetour aún puede bloquearla.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR AJUSTES",(d,w)->{
                try{
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+getPackageName())));
                }catch(Exception error){
                    try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));}
                    catch(Exception ignored){
                        report("Abrí Ajustes → Apps → DriveLab Auto Receiver → "+
                            "Mostrar sobre otras apps");
                    }
                }
            }).show();
    }
    private void direct(String coordinates){
        Intent open=new Intent(Intent.ACTION_VIEW,
            Uri.parse("waze://?ll="+coordinates+"&navigate=yes"));
        open.setPackage("com.waze");
        if(T1ClusterWazeRouter.enabled(this)){
            T1ClusterWazeRouter.routeDestination(this,open);
            report(T1ClusterWazeRouter.prefs(this).getString(
                T1ClusterWazeRouter.STATUS,"Clúster sin resultado"));
            return;
        }
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
