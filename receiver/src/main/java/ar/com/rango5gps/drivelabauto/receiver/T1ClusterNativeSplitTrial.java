package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

/**
 * One-shot Android 11 SPLIT_SCREEN_PRIMARY/SECONDARY experiment on Display 1.
 * Runs ONLY from an explicit technical-menu press, with OEM 0 km/h before EACH
 * shell mutation. Never changes saved FREEFORM layout, wheel, mic or routes.
 *
 * The global "supports-split-screen-multi-window" answer does NOT prove
 * that the Jetour's secondary Display 1 has a SplitScreenTaskOrganizer.
 * Results are deliberately "SOLICITADO", not "VERIFICADO", until the user
 * sees both apps and the real control focus working on the cluster.
 */
final class T1ClusterNativeSplitTrial {
    static final String RESULT="cluster_native_split_trial_result";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";

    interface Done { void completed(String message); }
    private T1ClusterNativeSplitTrial(){}

    static void run(Context context,Done done){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            String result;
            try{
                if(!T1ClusterWazeRouter.enabled(app))
                    throw new IllegalStateException("CLÚSTER está OFF");
                Display d=T1ClusterWazeRouter.selected(app);
                if(d==null||d.getDisplayId()!=1)
                    throw new IllegalStateException(
                        "Prueba bloqueada: se necesita Display 1 seleccionado");
                if(!T1ClusterLayoutPolicy.MODE_SPLIT.equals(
                        T1ClusterLayoutPolicy.mode(app))||
                   !WAZE.equals(T1ClusterLayoutPolicy.firstPackage(app))||
                   !MAPS.equals(T1ClusterLayoutPolicy.secondPackage(app))||
                   T1ClusterLayoutPolicy.inverted(app))
                    throw new IllegalStateException(
                        "Prueba preparada solo para Waze izquierda y Maps derecha");
                if(!T1ClusterAdbResize.enabled(app))
                    throw new IllegalStateException(
                        "Se necesita el puente ADB local ya autorizado");
                if(!stopped(app))
                    throw new IllegalStateException(
                        "BLOQUEADO: no se confirmó velocidad 0 km/h");
                String supported=T1LocalAdbClient.shell(app,
                    "am supports-split-screen-multi-window");
                if(!"true".equalsIgnoreCase(supported.trim()))
                    throw new IllegalStateException(
                        "Android no declara soporte split-screen: "+
                        supported.trim());
                // Resolve the LAUNCHER components locally. Never accept
                // arbitrary activities or shell fragments from the UI.
                String waze=component(app,WAZE);
                String maps=component(app,MAPS);
                if(!stopped(app))
                    throw new IllegalStateException(
                        "BLOQUEADO: cambió la velocidad antes de Waze");
                String one=T1LocalAdbClient.shell(app,
                    "am start --display 1 --windowingMode 3 -n "+waze);
                if(rejected(one))throw new IllegalStateException(
                    "Android rechazó Waze primario: "+shorten(one));
                Thread.sleep(700L);
                if(!stopped(app))
                    throw new IllegalStateException(
                        "PRUEBA PARCIAL: vehículo en movimiento; Maps no se abrió");
                String two=T1LocalAdbClient.shell(app,
                    "am start --display 1 --windowingMode 4 -n "+maps);
                if(rejected(two))throw new IllegalStateException(
                    "Android rechazó Maps secundario: "+shorten(two));
                Thread.sleep(950L);
                String tasks=T1LocalAdbClient.shell(app,"am stack list");
                T1AdbTaskParser.Task w=T1AdbTaskParser.unique(tasks,1,WAZE);
                T1AdbTaskParser.Task m=T1AdbTaskParser.unique(tasks,1,MAPS);
                if(w==null||m==null){
                    String dump=T1LocalAdbClient.shell(app,
                        "dumpsys activity activities");
                    if(w==null)w=T1AdbTaskParser.unique(dump,1,WAZE);
                    if(m==null)m=T1AdbTaskParser.unique(dump,1,MAPS);
                }
                result="SPLIT NATIVO SOLICITADO (modos 3/4) en Display 1. "+
                    "Waze: "+String.valueOf(w)+" · Maps: "+
                    String.valueOf(m)+". "+
                    "La respuesta de Android NO confirma una división nativa "+
                    "real: mirá si ambos mapas están simultáneos y si aparecen "+
                    "sus botones con la rueda. APLICAR restaura la división "+
                    "FREEFORM guardada. No se cambió la configuración permanente.";
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
                result="Prueba nativa interrumpida; no se guardaron cambios.";
            }catch(Exception ex){
                result="PRUEBA NATIVA NO CONFIRMADA: "+shorten(
                    T1LocalAdbClient.connectivityHint(ex))+
                    ". La configuración FREEFORM quedó guardada; "+
                    "APLICAR recupera el diseño guardado al detenerse.";
            }
            final String message=result;
            T1ClusterWazeRouter.prefs(app).edit()
                .putString(RESULT,message).apply();
            MAIN.post(()->{if(done!=null)done.completed(message);});
        },"T1-NativeSplit-Display1-Trial").start();
    }
    private static String component(Context c,String pkg){
        Intent launcher=c.getPackageManager().getLaunchIntentForPackage(pkg);
        ComponentName name=launcher==null?null:launcher.getComponent();
        if(name==null||!pkg.equals(name.getPackageName()))
            throw new IllegalStateException("Sin actividad de inicio: "+pkg);
        String className=name.getClassName();
        if(!className.matches("[A-Za-z0-9_.$]{1,200}"))
            throw new IllegalStateException("Actividad no válida: "+pkg);
        return name.flattenToShortString();
    }
    private static boolean stopped(Context c){
        MirrorAutomationOem oem=null;
        try{
            oem=new MirrorAutomationOem(c);
            double speed=oem.speedKmh();
            return Double.isFinite(speed)&&
                speed>=0&&speed<=0.5;
        }catch(Exception ex){return false;}
        finally{if(oem!=null)oem.close();}
    }
    private static boolean rejected(String output){
        String x=String.valueOf(output).toLowerCase(java.util.Locale.ROOT);
        return x.contains("error:")||x.contains("exception")||
            x.contains("permission denial")||
            x.contains("unknown command")||x.contains("unable to resolve")||
            x.contains("activity not found");
    }
    private static String shorten(String text){
        String value=String.valueOf(text);
        return value.substring(0,Math.min(280,value.length()));
    }
}
