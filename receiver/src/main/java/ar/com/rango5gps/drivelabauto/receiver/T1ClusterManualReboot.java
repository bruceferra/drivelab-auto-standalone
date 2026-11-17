package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manual user-confirmed R: stop maps with CLUSTER ON, perform a real
 * CLUSTER OFF (including ADB task close), wait briefly, then CLUSTER ON
 * with the saved freeform panes. No vehicle, OEM or Spotify process changes.
 * Requires authorized local ADB. Never claimed as visually verified. */
final class T1ClusterManualReboot {
    static final String RESULT="cluster_manual_reboot_result_v1";
    static final String TRACE="cluster_manual_reboot_trace_v2";
    private static final long OFF_VISIBLE_MS=1100L;
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
        note(owner,"R 1/4 · Deteniendo Waze y Maps con CLÚSTER ON",update);
        new Thread(()->{
            try{
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
            .putString(T1ClusterWazeRouter.STATUS,
                "R: CLÚSTER OFF · cerrando tareas del display").commit();
        T1ClusterEdgeFade.hide(owner);
        note(owner,"R 2/4 · CLÚSTER OFF, cerrando tareas por ADB",update);
        AtomicBoolean closeReturned=new AtomicBoolean(false);
        T1ClusterAdbResize.closeClusterApps(owner,(message,confirmed)->{
            if(!closeReturned.compareAndSet(false,true))return;
            p.edit().putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,!confirmed)
                .commit();
            note(owner,(confirmed?"R 2/4 · OFF confirmado: ":
                "R 2/4 · OFF no confirmado: ")+message,update);
            MAIN.postDelayed(()->performOn(owner,update,confirmed),
                OFF_VISIBLE_MS);
        });
        // Restore ON if ADB stalls. Late close actions bail out once ON.
        MAIN.postDelayed(()->{
            if(!closeReturned.compareAndSet(false,true))return;
            note(owner,"R 2/4 · Cierre ADB demoró más de 16 s; "+
                "restaurando ON sin confirmar OFF",update);
            performOn(owner,update,false);
        },CLOSE_TIMEOUT_MS);
    }
    private static void performOn(T1ImmersiveActivity owner,Update update,
                                  boolean offConfirmed){
        SharedPreferences p=T1ClusterWazeRouter.prefs(owner);
        p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,true)
            .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)
            .putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,false)
            .putString(T1ClusterWazeRouter.STATUS,
                "R: CLÚSTER ON · abriendo diseño guardado").commit();
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
                if(!owner.isFinishing()&&!owner.isDestroyed()){
                    Intent main=new Intent(owner,ReceiverActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    owner.startActivity(main);
                }
            }catch(Exception ex){
                finish(owner,update,"R PARCIAL: "+
                    ex.getClass().getSimpleName()+" · "+ex.getMessage());
            }
        },3200L);
    }
}
