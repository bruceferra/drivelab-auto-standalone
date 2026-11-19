package ar.com.rango5gps.drivelabauto.receiver;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** User-facing recent task manager for Jetour Android 11, Display 0.
 * This is NOT a system-wide process killer. It sees tasks with ADB, checks
 * real installed app identity and only force-stops explicitly confirmed
 * user-installed apps not present on another display. HOME, vehicle, maps,
 * navigation, Receiver and OEM remain protected.
 */
public final class T1AppTaskManagerActivity extends T1ImmersiveActivity {
    private static final String SELF="ar.com.rango5gps.drivelabauto.receiver";
    private static final String MOSAIC_PREFS="drivelab_center_mosaic_v1";
    private LinearLayout body;
    private String status="Consultando tareas de Android…";
    private boolean busy=false;
    private final ArrayList<Row> apps=new ArrayList<>();

    private static final class Row {
        final String pkg,label;
        final int center,other;
        final boolean eligible;
        Row(String pkg,String label,int center,int other,boolean eligible){
            this.pkg=pkg;this.label=label;this.center=center;
            this.other=other;this.eligible=eligible;
        }
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF0D1722);
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(18,12,18,32);
        scroll.addView(body);
        setContentView(scroll);
        refresh();
    }
    private void info(String text,int size){
        TextView view=new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setPadding(12,10,12,12);
        body.addView(view);
    }
    private void action(String title,Runnable listener){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(title);
        b.setEnabled(!busy);
        b.setOnClickListener(v->listener.run());
        body.addView(b);
    }
    private boolean validUserApp(PackageManager pm,String pkg){
        if(!T1AppStopPolicy.allowPackage(pkg))return false;
        try{
            ApplicationInfo app=pm.getApplicationInfo(pkg,0);
            if((app.flags&(ApplicationInfo.FLAG_SYSTEM|
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)return false;
            Intent launcher=pm.getLaunchIntentForPackage(pkg);
            if(launcher==null||launcher.getComponent()==null||
                !pkg.equals(launcher.getComponent().getPackageName()))
                return false;
            ArrayList<ResolveInfo> homeCandidates=new ArrayList<>();
            android.content.ComponentName home=
                pm.getHomeActivities(homeCandidates);
            if(home==null||pkg.equals(home.getPackageName()))
                return false;
            for(ResolveInfo h:homeCandidates)
                if(h.activityInfo!=null&&
                    pkg.equals(h.activityInfo.packageName))return false;
            return true;
        }catch(Exception ex){return false;}
    }
    private void refresh(){
        if(busy)return;
        busy=true;status="Leyendo tareas abiertas y recientes…";render();
        Context app=getApplicationContext();
        new Thread(()->{
            ArrayList<Row> found=new ArrayList<>();
            String message;
            try{
                PackageManager pm=app.getPackageManager();
                List<T1CenterTaskInventory.Entry> snapshot=
                    T1CenterTaskInventory.parse(
                        T1LocalAdbClient.shellTaskInventory(app));
                for(T1CenterTaskInventory.Entry e:snapshot){
                    // Tasks alone do not prove process is resident in RAM.
                    String label=e.pkg;
                    try{
                        ApplicationInfo ai=pm.getApplicationInfo(e.pkg,0);
                        if((ai.flags&(ApplicationInfo.FLAG_SYSTEM|
                            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
                            continue;
                        label=String.valueOf(pm.getApplicationLabel(ai));
                    }catch(Exception ex){continue;}
                    boolean eligible=e.centerTasks>0&&
                        e.otherDisplayTasks==0&&
                        validUserApp(pm,e.pkg);
                    found.add(new Row(e.pkg,label,e.centerTasks,
                        e.otherDisplayTasks,eligible));
                }
                Collections.sort(found,Comparator.comparing(
                    (Row r)->r.label,String.CASE_INSENSITIVE_ORDER));
                message=found.size()+" aplicación(es) de usuario con "+
                    "tareas de Android. Son ventanas/tareas recientes, "+
                    "no un medidor de RAM.";
            }catch(Exception ex){
                message="No pude obtener las tareas: "+
                    T1LocalAdbClient.connectivityHint(ex);
            }
            String done=message;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                apps.clear();apps.addAll(found);status=done;busy=false;
                render();
            });
        },"DriveLab-UserTaskInventory").start();
    }
    private String availableMemory(){
        ActivityManager am=(ActivityManager)getSystemService(
            Context.ACTIVITY_SERVICE);
        if(am==null)return "RAM: no disponible";
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memory);
        long megabyte=1024L*1024L;
        return "RAM disponible: "+memory.availMem/megabyte+
            " MB de "+memory.totalMem/megabyte+
            " MB · dato global de Android, no de cada aplicación";
    }
    private void render(){
        if(body==null)return;
        body.removeAllViews();
        info("APPS ABIERTAS · DISPLAY 0",23);
        info("Lista de tareas de usuario. No muestra ni detiene procesos "+
            "del vehículo, Android, navegación, HOME ni DriveLab principal. "+
            "Puede incluir tareas recientes sin un proceso activo.",13);
        info(status,14);
        info(availableMemory(),13);
        action("ACTUALIZAR LISTA",this::refresh);
        action("REINICIAR MOSAICO · CERRAR SESIONES",this::confirmResetMosaic);
        int eligible=0;
        for(Row row:apps)if(row.eligible)eligible++;
        final int closable=eligible;
        action("FORZAR DETENCIÓN · TODAS LAS APPS PERMITIDAS ("+
            closable+")",this::confirmStopAll);
        info("Una detención forzada puede cortar audio, notificaciones y "+
            "trabajo sin guardar. No borra datos ni aplicaciones. "+
            "Las apps del clúster y el HOME permanecen protegidos.",12);
        if(apps.isEmpty())info("No hay tareas de aplicaciones de "+
            "usuario disponibles o la consulta no respondió.",14);
        for(Row row:apps){
            info(row.label+" · "+row.pkg+"\nDisplay 0: "+
                row.center+" tarea(s) · otras pantallas: "+
                row.other+(row.eligible?"":" · PROTEGIDA"),13);
            if(row.eligible){
                action("FORZAR DETENCIÓN · "+row.label,
                    ()->confirmOne(row));
            }
        }
    }
    private void confirmOne(Row row){
        if(busy||!row.eligible)return;
        new AlertDialog.Builder(this)
            .setTitle("FORZAR DETENCIÓN · "+row.label)
            .setMessage("Cerrar completamente "+row.label+" ("+
                row.pkg+") y sus tareas de usuario. "+
                "Puede interrumpir música, notificaciones y trabajo "+
                "sin guardar. No se borra información. "+
                "La acción se cancela si Android ahora detecta "+
                "la aplicación en otra pantalla. ¿Continuar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DETENER",(d,w)->executeStop(
                Collections.singletonList(row.pkg),false))
            .show();
    }
    private void confirmStopAll(){
        if(busy)return;
        ArrayList<String> targets=new ArrayList<>();
        for(Row row:apps)if(row.eligible)targets.add(row.pkg);
        if(targets.isEmpty()){
            new AlertDialog.Builder(this)
                .setMessage("No hay apps permitidas para detener. "+
                    "Las del auto, navegación y clúster no se incluyen.")
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("DETENER "+targets.size()+" APPS DE USUARIO")
            .setMessage("Forzar detención de todas las aplicaciones "+
                "permitidas que aparecen en Display 0. "+
                "Se verifica nuevamente cada una antes de detenerla; "+
                "puede perderse trabajo sin guardar y dejar de sonar "+
                "Spotify. NO se detienen DriveLab, Waze, Maps, HOME, "+
                "ni componentes OEM. ¿Confirmás?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DETENER TODAS",(d,w)->
                executeStop(targets,false))
            .show();
    }
    private void confirmResetMosaic(){
        if(busy)return;
        new AlertDialog.Builder(this)
            .setTitle("REINICIAR MOSAICO · DISPLAY 0")
            .setMessage("Cerrar las aplicaciones de usuario seleccionadas "+
                "y creadas por el mosaico, y la SEGUNDA ventana Cockpit "+
                "si sigue disponible. La DriveLab original, el HOME, "+
                "el clúster y los controles del auto no se tocan. "+
                "Después se olvidan los IDs de las tareas anteriores "+
                "para crear ventanas nuevas la próxima vez. "+
                "Puede interrumpir audio o trabajo sin guardar. ¿Reiniciar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("REINICIAR",(d,w)->{
                SharedPreferences selection=getSharedPreferences(
                    MOSAIC_PREFS,MODE_PRIVATE);
                HashSet<String> unique=new HashSet<>();
                for(int i=0;i<3;i++){
                    String pkg=selection.getString("pkg"+i,"");
                    if(pkg!=null&&!pkg.isEmpty())unique.add(pkg);
                }
                ArrayList<String> targets=new ArrayList<>(unique);
                executeStop(targets,true);
            }).show();
    }
    private String closeSecondCockpit(Context context)throws Exception{
        int id=T1CenterSavedFreeformMosaic.rememberedTaskId(context,SELF);
        if(id<=0)return "Segunda ventana DriveLab: sin sesión registrada";
        if(id==getTaskId())
            return "Segunda ventana DriveLab: es la tarea actual, protegida";
        String raw=T1LocalAdbClient.shellTaskExcerpt(
            context,"am stack list",id);
        T1AdbTaskParser.Task exact=T1AdbTaskParser.exactCenter(
            raw,id,SELF);
        if(exact==null||!raw.contains("T1PremiumCockpitActivity"))
            return "Segunda ventana DriveLab: el ID guardado ya no existe";
        ActivityManager am=(ActivityManager)context.getSystemService(
            Context.ACTIVITY_SERVICE);
        if(am==null)throw new IllegalStateException(
            "ActivityManager no disponible");
        for(ActivityManager.AppTask task:am.getAppTasks()){
            ActivityManager.RecentTaskInfo info=task.getTaskInfo();
            if(info!=null&&info.taskId==id){
                task.finishAndRemoveTask();
                T1CenterSavedFreeformMosaic.forget(context,SELF);
                return "Segunda ventana DriveLab: cerrada, "+
                    "sin cerrar la principal";
            }
        }
        return "Segunda ventana DriveLab: Android no permitió "+
            "eliminarla; no se tocaron otras tareas";
    }
    private void executeStop(List<String> requested,boolean resetMosaic){
        if(busy)return;
        busy=true;status="Deteniendo solamente apps autorizadas…";render();
        Context app=getApplicationContext();
        new Thread(()->{
            StringBuilder result=new StringBuilder();
            if(resetMosaic){
                try{result.append(closeSecondCockpit(app)).append('\n');}
                catch(Exception e){result.append(
                    "Segunda ventana DriveLab no cerrada: ")
                    .append(e.getClass().getSimpleName()).append('\n');}
            }
            int stopped=0,skipped=0;
            HashSet<String> unique=new HashSet<>(requested);
            for(String pkg:unique){
                if(pkg==null||pkg.equals(SELF))continue;
                if(resetMosaic&&
                    T1CenterSavedFreeformMosaic.rememberedTaskId(
                        app,pkg)<=0)continue;
                try{
                    if(!T1AppStopPolicy.allowPackage(pkg))
                        throw new SecurityException("App protegida");
                    String response=T1LocalAdbClient.shellStopApprovedUserApp(
                        app,pkg);
                    if(T1CenterWindowModePolicy.shellRejected(response))
                        throw new IllegalStateException(response);
                    T1CenterSavedFreeformMosaic.forget(app,pkg);
                    stopped++;
                    result.append("Detención solicitada: ")
                        .append(pkg).append('\n');
                }catch(Exception ex){
                    skipped++;
                    result.append("No se detuvo ").append(pkg)
                        .append(": ").append(ex.getClass().getSimpleName())
                        .append('\n');
                }
            }
            if(resetMosaic){
                // An ID might be stale even when its task has already
                // vanished. Forget the mapping; never kill arbitrary apps.
                for(String pkg:unique)
                    T1CenterSavedFreeformMosaic.forget(app,pkg);
            }
            result.append("Solicitadas: ").append(stopped)
                .append(" · No detenidas: ").append(skipped)
                .append(". No se tocaron HOME, sistema ni otras pantallas.");
            String report=result.toString();
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                status=report;busy=false;render();
                new AlertDialog.Builder(this)
                    .setTitle(resetMosaic?
                        "RESULTADO · REINICIAR MOSAICO":
                        "RESULTADO · DETENER APPS")
                    .setMessage(report)
                    .setPositiveButton("ACTUALIZAR LISTA",
                        (d,w)->refresh()).show();
            });
        },"DriveLab-SafeUserAppStop").start();
    }
}
