package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manual user-confirmed R: stop maps with CLUSTER ON, perform a real
 * CLUSTER OFF (including ADB task close), hold 10 s, then CLUSTER ON
 * with the saved freeform panes. No vehicle, OEM or Spotify process changes.
 * Requires authorized local ADB. Never claimed as visually verified. */
final class T1ClusterManualReboot {
    static final String RESULT="cluster_manual_reboot_result_v1";
    static final String TRACE="cluster_manual_reboot_trace_v2";
    // Ten COMPLETE seconds after task close finishes, matching the owner's
    // manually verified recovery of the OEM Spotify overlay.
    static final long OFF_VISIBLE_MS=10000L;
    private static final long CLOSE_TIMEOUT_MS=16000L;
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    interface Update {void accept(String message);}
    private T1ClusterManualReboot(){}

    static String last(T1ImmersiveActivity c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "Recarga R todavía no ejecutada");
    }
    static String trace(T1ImmersiveActivity c){
        return T1ClusterWazeRouter.prefs(c).getString(TRACE,
            "Sin pasos de recarga registrados");
    }
    private static void note(T1ImmersiveActivity c,String value,Update update){
        SharedPreferences p=T1ClusterWazeRouter.prefs(c);
        String history=p.getString(TRACE,"")+value+"\n";
        if(history.length()>2800)history=history.substring(history.length()-2800);
        p.edit().putString(RESULT,value).putString(TRACE,history).commit();
        if(update!=null)MAIN.post(()->update.accept(value));
    }
    private static void finish(T1ImmersiveActivity c,Update u,String result){
        note(c,result,u);
        RUNNING.set(false);
    }
    static void run(T1ImmersiveActivity owner){run(owner,null);}
    static void run(T1ImmersiveActivity owner,Update update){
        if(!RUNNING.compareAndSet(false,true)){
            if(update!=null)MAIN.post(()->update.accept("R: ya hay una recarga en curso"));
            return;
        }
        T1ClusterWazeRouter.prefs(owner).edit().putString(TRACE,"").commit();
        note(owner,"R 1/4 · Caché, detención y reinicio de Waze/Maps",update);
        new Thread(()->{
            try{
                // This sequence FORCE-STOPS active navigation packages.
                // Even with explicit confirmation it is forbidden in motion.
                // Check P and zero speed TWICE from OEM before first ADB write.
                MirrorAutomationOem safety=null;
                try{
                    safety=new MirrorAutomationOem(owner);
                    for(int attempt=0;attempt<2;attempt++){
                        int gear=safety.gear();
                        double speed=safety.speedKmh();
                        if(gear!=1||!Double.isFinite(speed)||
                           speed<0||speed>0.5){
                            finish(owner,update,
                                "R BLOQUEADA · requiere P y 0 km/h "+
                                "confirmados; OEM marcha="+gear+
                                " velocidad="+speed+" km/h");
                            return;
                        }
                        if(attempt==0)Thread.sleep(400L);
                    }
                }finally{
                    if(safety!=null)safety.close();
                }
                note(owner,"R PREVIA · P y 0 km/h confirmados; "+
                    "empieza el cierre de mapas",update);
                // Cache-only cleanup is manual, parked-only, and scoped to
                // Waze/Maps. Never use "pm clear" without --cache-only:
                // that would erase application data and preferences.
                if(!T1ClusterWazeRouter.enabled(owner)||
                   !T1ClusterLayoutPolicy.savedDualSplit(owner)||
                   T1ClusterWazeRouter.selected(owner)==null||
                   T1ClusterWazeRouter.selected(owner).getDisplayId()!=1||
                   !T1ClusterAdbResize.enabled(owner)){
                    finish(owner,update,"R BLOQUEADA · requiere clúster ON, "+
                        "Waze/Maps en Display 1 y ADB autorizado");
                    return;
                }
                String first=T1ClusterLayoutPolicy.firstPackage(owner);
                String second=T1ClusterLayoutPolicy.secondPackage(owner);
                if(!(("com.waze".equals(first)&&
                       "com.google.android.apps.maps".equals(second))||
                      ("com.waze".equals(second)&&
                       "com.google.android.apps.maps".equals(first)))){
                    finish(owner,update,"R BLOQUEADA · paneles distintos de Waze/Maps");
                    return;
                }
                note(owner,"R CACHÉ · limpiando SOLO caché de Waze y Maps",update);
                for(String pkg:new String[]{"com.waze",
                                            "com.google.android.apps.maps"}){
                    String reply=T1LocalAdbClient.shell(owner,
                        "pm clear --cache-only "+pkg);
                    if(!reply.trim().equalsIgnoreCase("Success")){
                        finish(owner,update,"R NO COMPLETADA · Android no confirmó "+
                            "limpieza de caché de "+pkg+": "+
                            reply.trim().substring(0,Math.min(160,reply.trim().length())));
                        return;
                    }
                    note(owner,"R CACHÉ OK · "+pkg,update);
                }
                // Verify ON, Waze+Maps, Display 1 and local ADB before OFF.
                T1ClusterFreshStart.Result clean=T1ClusterFreshStart.prepare(owner);
                if(!clean.ready){
                    note(owner,"R · ADB NO confirmó force-stop: "+clean.message,update);
                    MAIN.post(()->{
                        if(clean.changed&&T1ClusterWazeRouter.enabled(owner)){
                            String repair=T1ClusterLayoutPolicy
                                .reopenSavedPanelsStopped(owner);
                            note(owner,"R · Recuperando mapas: "+repair,update);
                        }
                        finish(owner,update,"R NO COMPLETADA · No se hizo "+
                            "OFF/ON porque no se verificó el cierre");
                    });
                    return;
                }
                note(owner,"R 1/4 · ADB confirmó ambos procesos detenidos",update);
                MAIN.post(()->performOff(owner,update));
            }catch(Exception ex){
                finish(owner,update,"R NO COMPLETADA: "+
                    ex.getClass().getSimpleName()+" · "+ex.getMessage());
            }
        },"T1-R-ForceStop-Waze-Maps").start();
    }
    private static void performOff(T1ImmersiveActivity owner,Update update){
        if(owner.isFinishing()||owner.isDestroyed()||
           !T1ClusterWazeRouter.enabled(owner)){
            finish(owner,update,"R CANCELADA: clúster apagado o pantalla cerrada");
            return;
        }
        SharedPreferences p=T1ClusterWazeRouter.prefs(owner);
        // Same state transition and actual ADB task removal as CLÚSTER OFF.
        p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,false)
            .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)
            .putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,true)
            .putBoolean(T1ClusterWazeRouter.RELOAD_HOLD,true)
            .putString(T1ClusterWazeRouter.STATUS,
                "R: CLÚSTER OFF (naranja) · cerrando tareas; luego 10 s").commit();
        T1ClusterEdgeFade.hide(owner);
        note(owner,"R 2/4 · CLÚSTER OFF naranja; cerrando tareas ADB. "+
            "La espera de 10 s empieza AL TERMINAR el cierre.",update);
        AtomicBoolean closeReturned=new AtomicBoolean(false);
        T1ClusterAdbResize.closeClusterApps(owner,(message,confirmed)->{
            if(!closeReturned.compareAndSet(false,true))return;
            // Keep the chip amber even after successful ADB close, while
            // we hold CLÚSTER OFF for the owner's FULL ten seconds.
            p.edit().putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,!confirmed)
                .putBoolean(T1ClusterWazeRouter.RELOAD_HOLD,true)
                .putString(T1ClusterWazeRouter.STATUS,
                    "R: CLÚSTER OFF · ESPERANDO 10 s tras cierre ADB").commit();
            note(owner,(confirmed?"R 2/4 · cierre ADB confirmado: ":
                "R 2/4 · cierre ADB NO confirmado: ")+message+
                " · ESPERANDO 10 s en CLÚSTER OFF naranja",update);
            MAIN.postDelayed(()->performOn(owner,update,confirmed),
                OFF_VISIBLE_MS);
        });
        // Do NOT bypass the OFF interval when ADB times out. The previous
        // immediate-ON timeout produced a shorter-than-requested OFF period.
        MAIN.postDelayed(()->{
            if(!closeReturned.compareAndSet(false,true))return;
            p.edit().putString(T1ClusterWazeRouter.STATUS,
                "R: ADB sin respuesta; CLÚSTER OFF · ESPERANDO 10 s").commit();
            note(owner,"R 2/4 · ADB sin respuesta a 16 s; "+
                "manteniendo CLÚSTER OFF otros 10 s",update);
            MAIN.postDelayed(()->performOn(owner,update,false),
                OFF_VISIBLE_MS);
        },CLOSE_TIMEOUT_MS);
    }
    private static void performOn(T1ImmersiveActivity owner,Update update,
                                  boolean offConfirmed){
        SharedPreferences p=T1ClusterWazeRouter.prefs(owner);
        p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,true)
            .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)
            .putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,false)
            .putBoolean(T1ClusterWazeRouter.RELOAD_HOLD,false)
            .putString(T1ClusterWazeRouter.STATUS,
                "R: CLÚSTER ON después de 10 s completos en OFF").commit();
        note(owner,"R 3/4 · CLÚSTER ON · reabriendo Waze y Maps",update);
        String opened=T1ClusterLayoutPolicy.reopenSavedPanelsStopped(owner);
        if(opened.split("APP SOLICITADA",-1).length!=3){
            finish(owner,update,"R PARCIAL · CLÚSTER ON pero faltan mapas: "+opened);
            return;
        }
        T1ClusterEdgeFade.refresh(owner);
        note(owner,"R 4/4 · Mapas solicitados, OFF ADB "+
            (offConfirmed?"confirmado":"NO confirmado")+
            ". Spotify y rueda: comprobar físicamente.",update);
        MAIN.postDelayed(()->{
            try{
                if(!T1ClusterWazeRouter.enabled(owner)){
                    finish(owner,update,"R CANCELADA: clúster apagado");
                    return;
                }
                String route=T1LastRoute.restore(owner);
                T1WazeWheelService.requestClusterReady(owner);
                try{
                    owner.startForegroundService(new Intent(owner,ReceiverService.class)
                        .setAction(ReceiverService.ACTION_START));
                }catch(Exception ex){
                    note(owner,"R: ReceiverService sin confirmar: "+
                        ex.getClass().getSimpleName(),update);
                }
                finish(owner,update,(offConfirmed?"R TERMINADA":"R PARCIAL")+
                    " · CLÚSTER OFF/ON ejecutados, cierre ADB "+
                    (offConfirmed?"confirmado":"NO confirmado")+
                    "; Waze/Maps solicitados; ruta: "+route+
                    ". Spotify y rueda pendientes de prueba física.");
                // Do NOT restart DriveLab's Activity. CLEAR_TASK briefly
                // displayed a white background and never helped cluster focus.
                // Leave the currently visible toolbar/cockpit intact.
            }catch(Exception ex){
                finish(owner,update,"R PARCIAL: "+
                    ex.getClass().getSimpleName()+" · "+ex.getMessage());
            }
        },3200L);
    }
}
