package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** The owner's hidden, explicit navigation-only reboot (not a car reboot).
 * Force-stop Waze & Maps via owner-authorized local ADB, re-create the
 * stored FREEFORM split, re-submit the cached route and reopen DriveLab UI.
 * Keeps ReceiverService listening to Sender; cannot and does not force-stop
 * our own process, since that would prevent the recovery sequence.
 *
 * This action is never triggered by CLUSTER ON, route reception, or an
 * arbitrary app lifecycle. No vehicle writes, gear checks or speed gates.
 */
final class T1ClusterManualReboot {
    static final String RESULT="cluster_manual_reboot_result_v1";
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private T1ClusterManualReboot(){}

    static String last(T1ImmersiveActivity c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "REBOOT manual aún no ejecutado");
    }

    private static void note(T1ImmersiveActivity c,String result){
        T1ClusterWazeRouter.prefs(c).edit().putString(RESULT,result).commit();
    }

    static void run(T1ImmersiveActivity owner){
        if(!RUNNING.compareAndSet(false,true)){
            note(owner,"REBOOT ya está en curso; no se repite");
            return;
        }
        note(owner,"REBOOT: solicitando cierre completo de Waze y Maps");
        new Thread(()->{
            try{
                T1ClusterFreshStart.Result clean=T1ClusterFreshStart.prepare(owner);
                if(!clean.ready){
                    note(owner,"REBOOT CANCELADO: "+clean.message);
                    RUNNING.set(false);
                    return;
                }
                MAIN.post(()->{
                    if(owner.isFinishing()||owner.isDestroyed()||
                       !T1ClusterWazeRouter.enabled(owner)){
                        note(owner,"REBOOT CANCELADO: pantalla/clúster cerrado");
                        RUNNING.set(false);
                        return;
                    }
                    String opened=T1ClusterLayoutPolicy
                        .reopenSavedPanelsStopped(owner);
                    if(opened.split("APP SOLICITADA",-1).length!=3){
                        note(owner,"REBOOT PARCIAL: "+opened);
                        RUNNING.set(false);
                        return;
                    }
                    note(owner,"REBOOT: dos mapas nuevos solicitados; "+
                        "restaurando ruta y reiniciando pantalla DriveLab. "+
                        "La rueda y la capa OEM requieren prueba física.");
                    // Keep the encrypted Sender listener alive. Android's
                    // force-stop of our own package cannot relaunch itself.
                    // Explicitly ensure ReceiverService is running.
                    try{
                        Intent receiver=new Intent(owner,ReceiverService.class)
                            .setAction(ReceiverService.ACTION_START);
                        owner.startForegroundService(receiver);
                    }catch(Exception ex){
                        note(owner,"REBOOT: mapas solicitados, pero Receiver "+
                            "no confirmó reinicio: "+ex.getClass().getSimpleName());
                    }
                    MAIN.postDelayed(()->{
                        if(!T1ClusterWazeRouter.enabled(owner)){
                            RUNNING.set(false);return;
                        }
                        String route=T1LastRoute.restore(owner);
                        T1WazeWheelService.requestClusterReady(owner);
                        String previous=T1ClusterWazeRouter.prefs(owner)
                            .getString(RESULT,"");
                        note(owner,previous+" · Ruta: "+route);
                        try{
                            Intent main=new Intent(owner,ReceiverActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            owner.startActivity(main);
                        }catch(Exception error){
                            note(owner,"REBOOT: no pudo relanzar DriveLab: "+
                                error.getClass().getSimpleName());
                        }finally{
                            RUNNING.set(false);
                        }
                    },3200L);
                });
            }catch(Exception ex){
                note(owner,"REBOOT ERROR: "+ex.getClass().getSimpleName()+
                    " · "+String.valueOf(ex.getMessage()));
                RUNNING.set(false);
            }
        },"T1-Manual-Map-ForceStop-Reboot").start();
    }
}
