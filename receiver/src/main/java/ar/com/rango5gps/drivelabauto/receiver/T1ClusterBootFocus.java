package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot owner opt-in experiment at cold ignition:
 * raise EXISTING Waze/Maps Display 1 tasks only, after the cluster clean
 * boot sequence and route restoration have finished. No Spotify launch,
 * force-stop, OEM task close, input injection or infinite retry.
 * The OEM Spotify overlay may still remain above normal Android tasks.
 */
final class T1ClusterBootFocus {
    static final String ENABLED="boot_map_foreground_opt_in";
    static final String STATUS="boot_map_foreground_status";
    private static final AtomicBoolean EXECUTING=new AtomicBoolean(false);
    private T1ClusterBootFocus(){}

    static String describe(Context c){
        SharedPreferences p=T1ClusterWazeRouter.prefs(c);
        return (p.getBoolean(ENABLED,false)?"AUTO FOCO ON":"AUTO FOCO OFF")+
            " · "+p.getString(STATUS,"Sin prueba posterior al arranque");
    }

    static void afterClusterBoot(Context context,Handler main,
                                 long bootRouteRequestAt){
        Context app=context.getApplicationContext();
        SharedPreferences p=T1ClusterWazeRouter.prefs(app);
        if(!p.getBoolean(ENABLED,false))return;
        if(!EXECUTING.compareAndSet(false,true))return;
        p.edit().putString(STATUS,
            "Esperando mapas + accesibilidad después de ARRANQUE OFF/ON…").apply();
        main.postDelayed(()->{
            if(!eligible(app,bootRouteRequestAt)){
                p.edit().putString(STATUS,
                    "AUTO FOCO OMITIDO: mapas cambiaron, clúster OFF "+
                    "o diseño ya no es Waze/Maps").apply();
                EXECUTING.set(false);return;
            }
            // Exactly one foreground attempt. If it fails because the maps
            // are still loading, retry only once; speed=0 is checked again
            // inside the recovery for each actual task movement.
            T1ClusterForegroundRecovery.request(app,result->{
                p.edit().putString(STATUS,"AUTO FOCO 1/2: "+result).apply();
                if(!result.startsWith("TOMAR CONTROL NO CONFIRMADO")||
                   !eligible(app,bootRouteRequestAt)){
                    EXECUTING.set(false);return;
                }
                main.postDelayed(()->{
                    if(!eligible(app,bootRouteRequestAt)){
                        p.edit().putString(STATUS,
                            "AUTO FOCO 2/2 OMITIDO: cambió navegación").apply();
                        EXECUTING.set(false);return;
                    }
                    T1ClusterForegroundRecovery.request(app,second->{
                        p.edit().putString(STATUS,
                            "AUTO FOCO 2/2: "+second).apply();
                        EXECUTING.set(false);
                    });
                },4500L);
            });
        },2000L);
    }

    private static boolean eligible(Context app,long requestedAt){
        SharedPreferences p=T1ClusterWazeRouter.prefs(app);
        return p.getBoolean(ENABLED,false)&&
            T1ClusterWazeRouter.enabled(app)&&
            T1ClusterWazeRouter.autoBoot(app)&&
            T1ClusterLayoutPolicy.savedDualSplit(app)&&
            T1ClusterWazeRouter.selected(app)!=null&&
            T1ClusterWazeRouter.prefs(app)
                .getLong(T1ClusterWazeRouter.LAST_REQUEST_MS,0L)==requestedAt;
    }
}
