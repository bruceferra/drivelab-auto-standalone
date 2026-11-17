package ar.com.rango5gps.drivelabauto.receiver;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit user request to bring the EXISTING navigation tasks to front.
 * Only Waze/Maps on the selected secondary Display 1; never OEM, launcher,
 * other tasks, screens, steering keys, bounds or HOME. No automatic retries.
 * The Jetour may keep an OEM overlay above normal Android applications.
 */
final class T1ClusterForegroundRecovery {
    private static final String WAZE="com.waze",MAPS="com.google.android.apps.maps";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    interface Done { void accept(String result); }
    private T1ClusterForegroundRecovery(){}

    static void request(Context context,Done done){
        Context app=context.getApplicationContext();
        if(!BUSY.compareAndSet(false,true)){
            if(done!=null)done.accept("TOMAR CONTROL ya en ejecución");return;
        }
        new Thread(()->{
            String message;
            try{
                if(!T1ClusterWazeRouter.enabled(app)||
                   !T1ClusterLayoutPolicy.savedDualSplit(app))
                    throw new IllegalStateException("Primero activá los dos mapas del clúster");
                Display d=T1ClusterWazeRouter.selected(app);
                if(d==null||d.getDisplayId()!=1)
                    throw new IllegalStateException("Display 1 no seleccionado");
                String a=T1ClusterLayoutPolicy.firstPackage(app);
                String b=T1ClusterLayoutPolicy.secondPackage(app);
                if(!(WAZE.equals(a)&&MAPS.equals(b)||
                     MAPS.equals(a)&&WAZE.equals(b)))
                    throw new IllegalStateException("Esta prueba solo admite Waze y Google Maps");
                if(!T1ClusterAdbResize.enabled(app))
                    throw new IllegalStateException("Puente ADB local desactivado");
                if(!stopped(app))
                    throw new IllegalStateException("No se confirmó velocidad 0 km/h");
                ActivityManager am=(ActivityManager)
                    app.getSystemService(Context.ACTIVITY_SERVICE);
                if(am==null)throw new IllegalStateException("ActivityManager no disponible");
                // Re-read the actual Display 1 task immediately before each
                // operation. Never infer an ID from another screen or memory.
                for(String pkg:new String[]{WAZE,MAPS}){
                    if(!T1ClusterWazeRouter.enabled(app)||!stopped(app))
                        throw new IllegalStateException("Prueba interrumpida: clúster OFF o vehículo en movimiento");
                    T1AdbTaskParser.Task task=task(app,pkg);
                    if(task==null)
                        throw new IllegalStateException("Sin tarea única de "+pkg+" en Display 1");
                    am.moveTaskToFront(task.id,ActivityManager.MOVE_TASK_NO_USER_ACTION);
                    Thread.sleep(200L);
                }
                message="TOMAR CONTROL SOLICITADO: tareas Waze/Maps al frente "+
                    "en Display 1, sin tocar tamaño, rueda ni tareas OEM. "+
                    "Comprobá visualmente si desaparece la capa de Spotify "+
                    "y responde la rueda; la ROM puede ignorar el foco.";
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
                message="TOMAR CONTROL interrumpido; sin confirmación visual";
            }catch(Exception ex){
                message="TOMAR CONTROL NO CONFIRMADO: "+
                    ex.getClass().getSimpleName()+" · "+
                    String.valueOf(ex.getMessage())+
                    ". No se cerraron tareas del auto ni se cambiaron los paneles.";
            }finally{BUSY.set(false);}
            final String answer=message;
            T1ClusterWazeRouter.status(app,answer);
            MAIN.post(()->{
                if(answer.startsWith("TOMAR CONTROL SOLICITADO"))
                    T1WazeWheelService.requestClusterReady(app);
                if(done!=null)done.accept(answer);
            });
        },"T1-Cluster-Manual-Foreground").start();
    }

    private static T1AdbTaskParser.Task task(Context c,String pkg)throws Exception{
        String list=T1LocalAdbClient.shell(c,"am stack list");
        T1AdbTaskParser.Task selected=T1AdbTaskParser.unique(list,1,pkg);
        if(selected==null)selected=T1AdbTaskParser.unique(
            T1LocalAdbClient.shell(c,"dumpsys activity activities"),1,pkg);
        return selected;
    }
    private static boolean stopped(Context c){
        MirrorAutomationOem oem=null;
        try{
            oem=new MirrorAutomationOem(c);
            double v=oem.speedKmh();
            return Double.isFinite(v)&&v>=0&&v<=0.5;
        }catch(Exception ignored){return false;}
        finally{if(oem!=null)oem.close();}
    }
}
