package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Display;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Jetour owner verified that manually force-stopping Waze and Maps and then
 * reopening restored the wheel. ADB equivalent is pending physical testing.
 * Only called on real cold boot or a deliberate hidden manual REBOOT action.
 *
 * No main-thread ADB, OEM process, vehicle-gear/speed access, steering
 * injections, repeat loop, arbitrary packages or silent fallback.
 * ADB authorization for DriveLab's own RSA key is required separately from
 * Termux's trusted key. Failure prevents the subsequent auto relaunch.
 */
final class T1ClusterFreshStart {
    static final String RESULT="cluster_fresh_start_result_v1";
    static final String AT="cluster_fresh_start_at_v1";
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    static final class Result {
        final boolean ready,changed;
        final String message;
        Result(boolean ok,boolean touched,String why){
            ready=ok;changed=touched;message=why;
        }
    }
    private T1ClusterFreshStart(){}

    static String last(Context c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "Aún no se ejecutó el cierre limpio Waze + Google Maps");
    }
    private static Result store(Context c,boolean ok,String message){
        return store(c,ok,message,false);
    }
    private static Result store(Context c,boolean ok,String message,
                                boolean changed){
        T1ClusterWazeRouter.prefs(c).edit()
            .putString(RESULT,message)
            .putLong(AT,System.currentTimeMillis()).commit();
        return new Result(ok,changed,message);
    }
    static Result prepare(Context context){
        Context c=context.getApplicationContext();
        if(!BUSY.compareAndSet(false,true))
            return store(c,false,"CIERRE LIMPIO PENDIENTE: ya hay otro intento activo");
        int changed=0;
        try{
            if(!T1ClusterWazeRouter.enabled(c)||
               !T1ClusterLayoutPolicy.savedDualSplit(c))
                return store(c,false,
                    "CIERRE LIMPIO NO EJECUTADO: no hay dos paneles activos");
            Display display=T1ClusterWazeRouter.selected(c);
            if(display==null||display.getDisplayId()!=1)
                return store(c,false,
                    "CIERRE LIMPIO NO EJECUTADO: Display 1 no seleccionado");
            String a=T1ClusterLayoutPolicy.firstPackage(c);
            String b=T1ClusterLayoutPolicy.secondPackage(c);
            if(!(WAZE.equals(a)&&MAPS.equals(b)||
                 MAPS.equals(a)&&WAZE.equals(b)))
                return store(c,false,
                    "CIERRE LIMPIO NO EJECUTADO: los paneles no son Waze y Maps");
            if(!T1ClusterAdbResize.enabled(c))
                return store(c,false,
                    "CIERRE LIMPIO NO EJECUTADO: activá el puente ADB local");
            for(String pkg:new String[]{WAZE,MAPS}){
                if(!T1ClusterWazeRouter.enabled(c))
                    return store(c,false,
                        "CIERRE LIMPIO INTERRUMPIDO: clúster OFF");
                String result=T1LocalAdbClient.shell(c,"am force-stop "+pkg);
                if(error(result))
                    return store(c,false,
                        "CIERRE LIMPIO RECHAZADO por Android para "+pkg+
                        ": "+shortText(result),changed>0);
                changed++;
                // Distinguish successful ADB transport from the actual
                // absence of a running app. If Android refuses to stop it,
                // never claim a fresh launch succeeded.
                String pid=T1LocalAdbClient.shell(c,"pidof "+pkg);
                if(error(pid)||!pid.trim().isEmpty())
                    return store(c,false,
                        "CIERRE LIMPIO SIN CONFIRMAR: "+pkg+
                        " sigue activo o pidof falló: "+shortText(pid),changed>0);
            }
            if(!T1ClusterWazeRouter.enabled(c))
                return store(c,false,
                    "CIERRE LIMPIO INTERRUMPIDO: clúster OFF");
            return store(c,true,
                "ADB CONFIRMÓ SIN PROCESOS WAZE/MAPS: "+
                "se solicitará apertura nueva 50/50; confirmar rueda y capa OEM.",true);
        }catch(Exception ex){
            return store(c,false,
                "CIERRE LIMPIO NO EJECUTADO: "+
                T1LocalAdbClient.connectivityHint(ex),changed>0);
        }finally{
            BUSY.set(false);
        }
    }
    private static String shortText(String text){
        if(text==null||text.trim().isEmpty())return "(sin respuesta)";
        return text.trim().substring(0,Math.min(180,text.trim().length()));
    }
    private static boolean error(String text){
        if(text==null)return true;
        String lower=text.toLowerCase(Locale.ROOT);
        return lower.contains("permission denial")||
            lower.contains("securityexception")||
            lower.contains("error:")||
            lower.contains("exception:")||
            lower.contains("not found")||
            lower.contains("unknown command");
    }
}
