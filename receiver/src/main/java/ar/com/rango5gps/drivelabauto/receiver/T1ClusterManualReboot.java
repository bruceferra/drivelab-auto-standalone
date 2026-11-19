package ar.com.rango5gps.drivelabauto.receiver;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * Emergency Android 11 safety shutdown of the legacy MAPAS/REBOOT flow.
 *
 * Android 11 ignores the --cache-only switch to pm clear: that command
 * deletes ALL application user data (Waze and Google Maps accounts,
 * offline maps and settings). No cleanup, force-stop, OFF/ON or task
 * mutation is performed from either the toolbar or technical REBOOT.
 *
 * Do not re-enable until a separately audited NON-DESTRUCTIVE recovery
 * mechanism is physically verified on the target ROM with owner consent.
 */
final class T1ClusterManualReboot {
    static final String RESULT="cluster_manual_reboot_result_v1";
    static final String TRACE="cluster_manual_reboot_trace_v2";
    // Historical constant kept for existing callers; no timer is run.
    static final long OFF_VISIBLE_MS=10000L;
    interface Update {void accept(String message);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String DISABLED=
        "REPARAR MAPAS DESACTIVADO POR SEGURIDAD · Android 11 "+
        "puede borrar todos los datos con pm clear --cache-only. "+
        "No se modificó Waze, Google Maps ni el clúster.";
    private T1ClusterManualReboot(){}

    static String last(T1ImmersiveActivity c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "REPARAR MAPAS desactivado por seguridad");
    }
    static String trace(T1ImmersiveActivity c){
        return T1ClusterWazeRouter.prefs(c).getString(TRACE,
            "Sin operaciones de recuperación: función suspendida");
    }
    static void run(T1ImmersiveActivity owner){
        run(owner,null);
    }
    static void run(T1ImmersiveActivity owner,Update update){
        SharedPreferences prefs=T1ClusterWazeRouter.prefs(owner);
        prefs.edit().putString(RESULT,DISABLED).putString(TRACE,DISABLED)
            .commit();
        if(update!=null)MAIN.post(()->update.accept(DISABLED));
        // Never issue ADB commands or change OEM task/cluster state.
    }
}
