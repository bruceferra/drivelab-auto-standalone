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
public final class ReceiverActivity extends Activity {
    private static volatile boolean visible;
    private String message="";
    private ReceiverQuickControls quickControls;
    private final Handler pairUiHandler=new Handler(Looper.getMainLooper());
    private String pairUiSnapshot="";
    private ScrollView currentScroll;
    private long lastAutoCheckElapsed=0;
    private final Runnable pairUiRefresh=new Runnable(){
        @Override public void run(){
            if(!visible||isFinishing()||isDestroyed())return;
            SharedPreferences p=prefs();
            String latest=p.getString("pair_status","")+"|"+
                p.getString("pair_code","")+"|"+
                p.getLong("approved_id",0)+"|"+
                getSharedPreferences("drivelab_native_update",MODE_PRIVATE)
                    .getString("last_install_status","");
            if(!latest.equals(pairUiSnapshot))render();
            if(quickControls!=null)quickControls.refresh();
            pairUiHandler.postDelayed(this,750);
        }
    };
    public static boolean isVisible(){return visible;}
    private SharedPreferences prefs(){return getSharedPreferences(ReceiverService.PREFS,MODE_PRIVATE);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);quickControls=new ReceiverQuickControls(this);render();
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
        view.setPadding(7,10,7,10);root.addView(view);
    }
    private void button(LinearLayout root,String text,Runnable task){
        Button button=new Button(this);button.setText(text);button.setAllCaps(false);
        button.setOnClickListener(v->task.run());root.addView(button);
    }
    private void report(String text){message=text;render();}
    private void render(){
        final int restoreY=currentScroll==null?0:currentScroll.getScrollY();
        SharedPreferences pairState=prefs();
        pairUiSnapshot=pairState.getString("pair_status","")+"|"+
            pairState.getString("pair_code","")+"|"+
            pairState.getLong("approved_id",0)+"|"+
            getSharedPreferences("drivelab_native_update",MODE_PRIVATE)
                .getString("last_install_status","");
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(22,20,22,35);scroll.addView(body);
        SharedPreferences p=prefs();
        quickControls.attach(body); // FIRST and always visible above menus
        label(body,"DRIVELAB AUTO · RECEIVER",23);
        label(body,"v0.2.22 · TRIP OEM 500 m / 50 km · Jetour T1",13);
        button(body,"VEHÍCULO · VENTANILLAS / ENERGÍA / CODE",()->
            startActivity(new Intent(this,ReceiverVehicleActivity.class)));
        button(body,"TABLERO T1 · TANQUES / CONSUMO / TELEMETRÍA",()->
            startActivity(new Intent(this,T1TankDashboardActivity.class)));
        label(body,"Jetour T1 · Receptor cifrado independiente",14);
        label(body,"IP de T1: "+ReceiverService.localIp()+" · Puertos 48391 / 48392",15);
        label(body,"ESTADO: "+p.getString("status","Sin iniciar")+
            "\nDestinos recibidos: "+p.getInt("received",0)+
            "\nÚltima navegación: "+p.getString("last_open","Sin pruebas"),14);
        button(body,"INICIAR RECEPTOR · en segundo plano",()->start(ReceiverService.ACTION_START));
        button(body,"DETENER RECEPTOR",()->new AlertDialog.Builder(this)
            .setMessage("Se detendrá el receptor y su inicio automático. ¿Confirmás?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DETENER",(d,w)->start(ReceiverService.ACTION_STOP)).show());
        button(body,"VINCULAR XIAOMI · 6 DÍGITOS",()->start(ReceiverService.ACTION_PAIR));
        label(body,"Vinculación: "+p.getString("pair_status","Aún sin vincular"),14);
        label(body,"El código aparece automáticamente cuando el Xiaomi "+
            "inicia la vinculación. COMPARÁ los 6 dígitos en ambos equipos. "+
            "Después autorizá en la T1 y confirmá en Xiaomi.",13);
        if(p.contains("pair_code"))label(body,"CÓDIGO DE COMPARACIÓN: "+p.getString("pair_code",""),22);
        button(body,"AUTORIZAR CÓDIGO COINCIDENTE · PASO 1",this::approve);
        button(body,"ACTUALIZAR ESTADO",this::render);
        boolean auto=p.getBoolean("auto_boot",false);
        label(body,"Inicio automático del receptor: "+(auto?"ACTIVO":"DESACTIVADO"),16);
        button(body,auto?"Desactivar inicio automático":"ACTIVAR INICIO AL ENCENDER T1",()->{
            p.edit().putBoolean("auto_boot",!auto).apply();
            if(!auto)start(ReceiverService.ACTION_START);else render();
        });
        label(body,"Permiso para iniciar Waze desde segundo plano: "+
            (Settings.canDrawOverlays(this)?"CONCEDIDO":"SIN AUTORIZAR"),14);
        button(body,"AUTORIZAR SEGUNDO PLANO EN ANDROID",this::requestBackground);
        boolean waze=p.getBoolean("open_waze",true);
        button(body,waze?"Desactivar apertura automática de Waze":
            "Activar apertura automática de Waze",()->{
            p.edit().putBoolean("open_waze",!waze).apply();render();
        });
        label(body,"App opcional al encender: "+p.getString("launch_label","NINGUNA")+
            "\nEstado último arranque: "+p.getString("boot_status","Sin pruebas"),13);
        button(body,"ELEGIR APP PARA ABRIR AL ENCENDER",this::pickBootApp);
        boolean launch=p.getBoolean("launch_app_on_boot",false);
        button(body,launch?"Desactivar apertura de app al encender":
            "Activar apertura de app al encender",()->{
            if(!launch&&(!p.getBoolean("auto_boot",false)||
                !Settings.canDrawOverlays(this)||p.getString("launch_package","").isEmpty())){
                report("Para abrir una app al encender: activá inicio automático, "+
                    "concedé permiso de segundo plano y elegí la app.");return;
            }
            p.edit().putBoolean("launch_app_on_boot",!launch).apply();render();
        });
        button(body,"Quitar app de inicio",()->{
            p.edit().remove("launch_package").remove("launch_label")
                .putBoolean("launch_app_on_boot",false).apply();render();
        });
        button(body,"PRUEBA DIRECTA A LA PLATA",()->direct("-34.9214,-57.9545"));
        button(body,"PRUEBA DIRECTA A MAR DEL PLATA",()->direct("-38.0055,-57.5426"));
        button(body,"ACTUALIZAR RECEIVER · INSTALACIÓN DIRECTA",
            ()->Updater.check(this,"receiver",this::report));
        if(!message.isEmpty())label(body,"Actualizador / estado: "+message,14);
        label(body,"Estado instalador T1: "+
            getSharedPreferences("drivelab_native_update",MODE_PRIVATE)
                .getString("last_install_status","Aún no hay instalación solicitada"),14);
        button(body,"REINTENTAR INSTALACIÓN YA DESCARGADA",
            ()->Updater.retryInstall(this,this::report));
        button(body,"INSTALADOR NATIVO ALTERNATIVO",
            ()->Updater.tryNativeIntent(this,this::report));
        button(body,"ABRIR AJUSTES DE RECEIVER (PERMISO)",
            ()->Updater.openReceiverSettings(this,this::report));
        label(body,"El sistema Android debe confirmar la instalación. "+
            "Si Jetour rechaza ambos métodos, el estado indicará el motivo. "+
            "No desactives Play Protect ni desinstales Receiver.",13);
        label(body,"El servicio sigue activo aunque cierres esta pantalla. "+
            "No fuerza Wi-Fi ni modifica Android Auto o T1Launcher. "+
            "La apertura en clúster depende de la ROM de la camioneta.",13);
        Receiver3DButtons.apply(body);
        setContentView(scroll);
        currentScroll=scroll;
        if(restoreY>0)scroll.post(()->{
            if(currentScroll==scroll)scroll.scrollTo(0,restoreY);
        });
    }
    private void start(String action){
        Intent service=new Intent(this,ReceiverService.class).setAction(action);
        try{
            if(ReceiverService.ACTION_STOP.equals(action))startService(service);
            else startForegroundService(service);
            report(ReceiverService.ACTION_PAIR.equals(action)?
                "Vinculación abierta 2 minutos. Iniciá el Xiaomi y tocá ACTUALIZAR ESTADO.":
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
