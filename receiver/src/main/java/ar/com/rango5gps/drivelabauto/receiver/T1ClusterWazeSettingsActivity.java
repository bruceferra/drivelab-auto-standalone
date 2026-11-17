package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Display;
import android.widget.Button;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.TreeMap;

/** Explicit, reversible setup; no invisible window or cloned launcher UI. */
public final class T1ClusterWazeSettingsActivity extends T1ImmersiveActivity {
    private boolean showTechnical;
    private void label(LinearLayout parent,String text,int size){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(size);
        t.setPadding(12,12,12,12);t.setTextIsSelectable(true);
        parent.addView(t);
    }
    private void button(LinearLayout parent,String text,Runnable run){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setOnClickListener(v->run.run());parent.addView(b);
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);render();
    }
    @Override protected void onResume(){
        super.onResume();render();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(16,16,16,24);
        scroll.addView(body);
        SharedPreferences p=T1ClusterWazeRouter.prefs(this);
        boolean selected=T1ClusterWazeRouter.selectionExists(this);
        boolean on=T1ClusterWazeRouter.enabled(this);
        boolean boot=T1ClusterWazeRouter.autoBoot(this);
        label(body,"APLICACIONES EN CLÚSTER · DRIVELAB",22);
        label(body,"Implementación independiente. No usa ventana flotante, "+
            "MediaProjection ni el Cluster de T1Launcher. "+
            "No lo apagues hasta confirmar Waze en el tablero y probar los "+
            "botones del volante.",14);
        label(body,"Android multidisplay: "+
            (T1ClusterWazeRouter.multiDisplay(this)?"SOPORTADO":"NO INFORMADO")+
            "\nDisplay elegido: "+p.getInt(T1ClusterWazeRouter.DISPLAY_ID,-1)+
            " · "+p.getString(T1ClusterWazeRouter.DISPLAY_NAME,"NINGUNO")+
            "\nDestino a clúster: "+(on?"ACTIVO":"APAGADO")+
            "\nAplicación del clúster: "+
                T1ClusterWazeRouter.selectedAppLabel(this)+
            "\nApp al encender: "+(boot?"ACTIVO":"APAGADO")+
            "\nResultado: "+p.getString(T1ClusterWazeRouter.STATUS,
                "Sin prueba; ningún display elegido"),14);
        label(body,"CIERRE LIMPIO MAPAS · "+T1ClusterFreshStart.last(this),14);
        label(body,"ÚLTIMA RECARGA R · "+T1ClusterManualReboot.last(this),14);
        label(body,"ARRANQUE AUTO OFF/ON · "+p.getString(
            T1ClusterWazeRouter.BOOT_RELOAD_RESULT,
            "Aún no probado tras reiniciar la camioneta"),14);
        button(body,"CONFIGURAR PANELES A/B · APLICAR",()->
            startActivity(new Intent(this,T1ClusterLayoutSettingsActivity.class)));
        if(T1ClusterWazeRouter.selected(this)==null)
            button(body,"SELECCIONAR DISPLAY DEL TABLERO",this::chooseDisplay);
        button(body,showTechnical?"OCULTAR MENÚ TÉCNICO":
            "MENÚ TÉCNICO · DISPLAY / PRUEBAS",()->{
            showTechnical=!showTechnical;render();
        });
        if(showTechnical){
            // Small, hidden maintenance action: never consume the fixed top
            // toolbar or add another driving shortcut.
            LinearLayout rebootLine=new LinearLayout(this);
            rebootLine.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            body.addView(rebootLine);
            Button reboot=new Button(this);
            reboot.setText("REBOOT");
            reboot.setAllCaps(false);
            reboot.setTextSize(11f);
            rebootLine.addView(reboot,new LinearLayout.LayoutParams(
                (int)(104*getResources().getDisplayMetrics().density),
                (int)(42*getResources().getDisplayMetrics().density)));
            reboot.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("REBOOT · DRIVELAB / MAPAS")
                .setMessage("¿Estás seguro? Detiene Waze y Maps, "+
                    "hace CLÚSTER OFF, cierra tareas, espera 10 s completos "+
                    "y hace CLÚSTER ON con el diseño guardado. Interrumpe rutas.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("REBOOT",(dialog,which)->{
                    T1ClusterManualReboot.run(this);
                    render();
                }).show());
            label(body,"PASOS ÚLTIMA R · "+T1ClusterManualReboot.trace(this),13);
            label(body,"DIAGNÓSTICOS ANTIGUOS · NO NECESARIOS PARA EL USO NORMAL",14);
        label(body,"Pantallas secundarias públicas detectadas: "+
            T1ClusterWazeRouter.candidates(this).size()+
            ". Si elegís una, se desactivará la ruta anterior hasta "+
            "que pruebes Waze y vuelvas a activar el nuevo display.",13);
        button(body,"1 · DETECTAR / ELEGIR PANTALLA DEL CLÚSTER",
            this::chooseDisplay);
        button(body,"2 · PROBAR WAZE EN EL DISPLAY ELEGIDO · DETENIDO",
            this::testWaze);
        label(body,"Podés elegir Waze, Google Maps u otra app instalada. "+
            "Los destinos del Xiaomi conservan su selección; el micrófono "+
            "envía la ruta al mapa realmente visible en Display 1.",13);
        button(body,"TAMAÑO / POSICIÓN / DIVISIÓN Y MICRÓFONO OEM",
            ()->startActivity(new Intent(this,
                T1ClusterLayoutSettingsActivity.class)));
        button(body,"ELEGIR APLICACIÓN DEL CLÚSTER",this::chooseApp);
        button(body,"PROBAR APLICACIÓN ELEGIDA · DETENIDO",this::testSelectedApp);
        button(body,on?"3 · DESACTIVAR CLÚSTER DRIVELAB":
            "3 · ACTIVAR DESTINOS WAZE AL CLÚSTER",()->{
            if(on){
                // OFF pauses routing. It does not change the approved
                // panel geometry and must not erase a prior split layout.
                p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,false)
                    .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)
                    .putString(T1ClusterWazeRouter.STATUS,
                        "Desactivado. Destinos vuelven a Waze normal de DriveLab.")
                    .apply();render();return;
            }
            if(!selected||T1ClusterWazeRouter.selected(this)==null||
                    !T1ClusterWazeRouter.multiDisplay(this)){
                T1ClusterWazeRouter.status(this,
                    "Primero elegí un display secundario público válido.");
                render();return;
            }
            new AlertDialog.Builder(this)
                .setTitle("WAZE DIRECTO AL CLÚSTER")
                .setMessage("Activar solamente después de comprobar con la "+
                    "camioneta detenida que Waze se muestra en el tablero. "+
                    "Android puede bloquear o ignorar esta ruta. No sustituye "+
                    "los controles del volante ni el HOME de T1Launcher.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("ACTIVAR", (d,w)->{
                    p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,true)
                        .putString(T1ClusterWazeRouter.STATUS,
                            "Destinos del Xiaomi se solicitarán en display "+
                            "secundario. Si falla se mostrará diagnóstico.")
                        .apply();render();
                }).show();
        });
        button(body,boot?"4 · DESACTIVAR APP AL ENCENDER":
            "4 · AUTOABRIR APP ELEGIDA EN CLÚSTER AL ENCENDER",()->{
            if(boot){
                p.edit().putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)
                    .apply();render();return;
            }
            if(!on){
                T1ClusterWazeRouter.status(this,
                    "Primero activá destinos al clúster y probá que se vea Waze.");
                render();return;
            }
            new AlertDialog.Builder(this)
                .setTitle("ARRANQUE AUTOMÁTICO DE LA APP ELEGIDA")
                .setMessage("DriveLab Receiver iniciará en segundo plano y "+
                    "solicitará abrir la app elegida SOLO en el display seleccionado "+
                    "después del encendido real. Se necesita autorización "+
                    "Android para arranque en segundo plano. No se cierra "+
                    "T1Launcher ni se reemplaza el HOME del vehículo.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("ACTIVAR", (d,w)->{
                    p.edit().putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)
                        .apply();
                    getSharedPreferences(ReceiverService.PREFS,MODE_PRIVATE)
                        .edit().putBoolean("auto_boot",true).apply();
                    try{
                        startForegroundService(new Intent(this,
                            ReceiverService.class)
                            .setAction(ReceiverService.ACTION_START));
                    }catch(RuntimeException error){
                        T1ClusterWazeRouter.status(this,
                            "No pudo arrancar Receiver: "+
                            error.getClass().getSimpleName());
                    }
                    render();
                }).show();
        });
        label(body,"La prueba directa solo se permite a 0 km/h "+
            "confirmados por las lecturas OEM. El display puede ser 2, 5 "+
            "u otro según el arranque de esta T1. No se elige por su número "+
            "de manera automática. Si Android lo rechaza, DriveLab NO abre "+
            "Waze sobre la pantalla principal como reemplazo.",12);
        button(body,"ACTUALIZAR PANTALLAS Y ESTADO",this::render);
        }
        button(body,"VOLVER A DRIVELAB",this::finish);
        setContentView(scroll);
    }
    private void chooseDisplay(){
        List<Display> displays=T1ClusterWazeRouter.candidates(this);
        if(displays.isEmpty()){
            T1ClusterWazeRouter.status(this,"Android no expone ningún "+
                "display secundario público. No se abre el clúster OEM.");
            render();return;
        }
        String[] labels=new String[displays.size()];
        for(int i=0;i<displays.size();i++)
            labels[i]=T1ClusterWazeRouter.describe(displays.get(i));
        // Android AlertDialog cannot present setMessage() AND setItems():
        // the message occupies the same content view as the list and hides it.
        // Keep instructions on the parent screen; list candidates here.
        new AlertDialog.Builder(this)
            .setTitle("ELEGIR DISPLAY · "+displays.size()+" DISPONIBLE(S)")
            .setItems(labels,(d,which)->{
                T1ClusterWazeRouter.choose(this,displays.get(which));
                render();
            }).setNegativeButton("CANCELAR",null).show();
    }
    /** Installed public launcher activities only; no OEM modules. */
    private void chooseApp(){
        PackageManager pm=getPackageManager();
        Intent launcher=new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        TreeMap<String,String> choices=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<ResolveInfo> activities=pm.queryIntentActivities(launcher,0);
        for(ResolveInfo ri:activities){
            if(ri.activityInfo==null)continue;
            String pkg=ri.activityInfo.packageName;
            if(!T1ClusterWazeRouter.isSelectablePackage(this,pkg))continue;
            CharSequence label=ri.loadLabel(pm);
            String name=label==null||label.length()==0?pkg:label.toString();
            choices.put(name+" · "+pkg,pkg);
        }
        for(String pkg:new String[]{"com.waze","com.google.android.apps.maps"}){
            if(!T1ClusterWazeRouter.isSelectablePackage(this,pkg))continue;
            if(!choices.containsValue(pkg))
                choices.put((pkg.equals("com.waze")?"Waze":"Google Maps")+
                    " · "+pkg,pkg);
        }
        if(choices.isEmpty()){
            T1ClusterWazeRouter.status(this,"Android no muestra apps iniciables.");
            render();return;
        }
        String[] labels=choices.keySet().toArray(new String[0]);
        String[] pkgs=choices.values().toArray(new String[0]);
        // No setMessage: it hides setItems on Jetour Android 11.
        new AlertDialog.Builder(this)
            .setTitle("ELEGIR APP · "+labels.length+" DISPONIBLES")
            .setItems(labels,(dialog,which)->{
                T1ClusterWazeRouter.chooseApp(this,pkgs[which],labels[which]);
                render();
            }).setNegativeButton("CANCELAR",null).show();
    }
    private void testWaze(){runParkedTest(false);}
    private void testSelectedApp(){runParkedTest(true);}
    private void runParkedTest(boolean selectedApp){
        if(T1ClusterWazeRouter.selected(this)==null){
            T1ClusterWazeRouter.status(this,
                "Primero seleccioná un display secundario válido.");
            render();return;
        }
        T1ClusterWazeRouter.status(this,"Comprobando 0 km/h sin mover vehículo…");
        render();
        new Thread(()->{
            MirrorAutomationOem oem=null;
            String error=null;
            try{
                oem=new MirrorAutomationOem(this);
                double kmh=oem.speedKmh();
                if(!Double.isFinite(kmh)||kmh<0||kmh>0.5)
                    error="Prueba bloqueada. Se requiere velocidad "+
                        "0 km/h verificadas por OEM (1033 / 2148 / 1089).";
            }catch(Exception e){
                error="No se puede confirmar velocidad con OEM: "+
                    e.getClass().getSimpleName();
            }finally{
                if(oem!=null)oem.close();
            }
            String result=error;
            runOnUiThread(()->{
                if(isFinishing())return;
                if(result!=null){
                    T1ClusterWazeRouter.status(this,result);render();return;
                }
                if(selectedApp)
                    T1ClusterWazeRouter.openSelectedAppTest(this);
                else
                    T1ClusterWazeRouter.openManualTest(this);
                render();
            });
        },"DriveLab-Cluster-SpeedZero-Check").start();
    }
}