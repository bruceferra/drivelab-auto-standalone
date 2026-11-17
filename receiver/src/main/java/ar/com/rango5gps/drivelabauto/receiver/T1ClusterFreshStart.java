package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Display;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Jetour T1 user-verified recovery: Android's Settings > Force stop for
 * BOTH Waze and Maps, followed by reopening, restored the steering wheel.
 * Perform the equivalent two narrowly whitelisted local ADB force-stops
 * BEFORE boot/manual CLUSTER ON opens the saved dual FREEFORM panes.
 *
 * No main-thread ADB, OEM process, display changes, steering injections,
 * force-stop in motion, repeat loop, arbitrary packages or silent fallback.
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
        final boolean ready;
        final String message;
        Result(boolean ok,String why){ready=ok;message=why;}
    }
    private T1ClusterFreshStart(){}

    static String last(Context c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "Aún no se ejecutó el cierre limpio Waze + Google Maps");
    }
    private static Result store(Context c,boolean ok,String message){
        T1ClusterWazeRouter.prefs(c).edit()
            .putString(RESULT,message)
            .putLong(AT,System.currentTimeMillis()).commit();
        return new Result(ok,message);
    }
    static Result prepare(Context context){
        Context c=context.getApplicationContext();
        if(!BUSY.compareAndSet(false,true))
            return store(c,false,"CIERRE LIMPIO PENDIENTE: ya hay otro intento activo");
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
            if(!stopped(c))
                return store(c,false,
                    "CIERRE LIMPIO BLOQUEADO: no se confirmó velocidad 0 km/h");
            for(String pkg:new String[]{WAZE,MAPS}){
                if(!T1ClusterWazeRouter.enabled(c)||!stopped(c))
                    return store(c,false,
                        "CIERRE LIMPIO INTERRUMPIDO: clúster OFF o vehículo en movimiento");
                String result=T1LocalAdbClient.shell(c,"am force-stop "+pkg);
                if(error(result))
                    return store(c,false,
                        "CIERRE LIMPIO RECHAZADO por Android para "+pkg+
                        ": "+shortText(result));
                // Distinguish successful ADB transport from the actual
                // absence of a running app. If Android refuses to stop it,
                // never claim a fresh launch succeeded.
                String pid=T1LocalAdbClient.shell(c,"pidof "+pkg);
                if(error(pid)||!pid.trim().isEmpty())
                    return store(c,false,
                        "CIERRE LIMPIO SIN CONFIRMAR: "+pkg+
                        " sigue activo o pidof falló: "+shortText(pid));
            }
            if(!T1ClusterWazeRouter.enabled(c)||!stopped(c))
                return store(c,false,
                    "CIERRE LIMPIO DETENIDO: no reabrir mientras el auto se mueve");
            return store(c,true,
                "ADB CONFIRMÓ SIN PROCESOS WAZE/MAPS en Display 1: "+
                "se solicitará apertura nueva 50/50; confirmar rueda y capa OEM.");
        }catch(Exception ex){
            return store(c,false,
                "CIERRE LIMPIO NO EJECUTADO: "+
                T1LocalAdbClient.connectivityHint(ex));
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
    private static boolean stopped(Context c){
        MirrorAutomationOem oem=null;
        try{
            oem=new MirrorAutomationOem(c);
            double speed=oem.speedKmh();
            return Double.isFinite(speed)&&speed>=0&&speed<=0.5;
        }catch(Exception ignored){return false;}
        finally{if(oem!=null)oem.close();}
    }
}
