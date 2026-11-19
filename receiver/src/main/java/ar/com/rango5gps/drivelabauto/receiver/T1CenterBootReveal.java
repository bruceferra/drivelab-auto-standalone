package ar.com.rango5gps.drivelabauto.receiver;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;

/**
 * "MOSTRAR MOSAICO" for the standalone DriveLab Boot UI.
 *
 * Never launch an activity, force-stop a process, clear storage, resize
 * existing tasks or change Jetour/OEM window settings. Reorder ONLY exact
 * saved DriveLab-managed Freeform tasks on Display 0, after verifying every
 * ID/package/display/window mode and nonoverlapping existing bounds.
 */
final class T1CenterBootReveal {
    interface Done {void finished(String report);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private T1CenterBootReveal(){}

    static void request(Context context,String[] packages,Done done){
        Context app=context.getApplicationContext();
        String[] requested=packages.clone();
        new Thread(()->{
            final int count=requested.length;
            final int[] tasks=new int[count];
            String failure=null;
            try{
                if(count<1||count>3)
                    throw new IllegalArgumentException("Cantidad de paneles inválida");
                HashSet<Integer> uniqueIds=new HashSet<>();
                HashSet<String> uniquePackages=new HashSet<>();
                T1AdbTaskParser.Box[] boxes=new T1AdbTaskParser.Box[count];
                for(int i=0;i<count;i++){
                    String pkg=requested[i];
                    if(pkg==null||!uniquePackages.add(pkg)||
                       !(app.getPackageName().equals(pkg)||
                         T1CenterUserPackagePolicy.allowed(pkg)))
                        throw new SecurityException("Panel "+(i+1)+" no autorizado");
                    T1AdbTaskParser.Task actual=
                        T1CenterSavedFreeformMosaic.verifiedRememberedTask(
                            app,pkg);
                    if(actual.id<=0||actual.display!=0||actual.bounds==null||
                       !uniqueIds.add(actual.id))
                        throw new SecurityException("Tarea inválida de "+pkg);
                    tasks[i]=actual.id;
                    boxes[i]=actual.bounds;
                }
                if(!T1CenterMosaicViewport.disjoint(boxes))
                    throw new IllegalStateException(
                        "Las ventanas guardadas se superponen; no se recuperaron");
            }catch(Exception error){
                failure=error.getClass().getSimpleName()+": "+error.getMessage();
            }
            if(failure!=null){
                String msg="MOSAICO NO RECUPERADO: "+failure+
                    "\nNo se cerraron aplicaciones ni se borraron datos. "+
                    "Si los IDs cambiaron, prepará de nuevo el diseño; "+
                    "no uses Forzar detención.";
                MAIN.post(()->done.finished(msg));
                return;
            }
            MAIN.post(()->{
                try{
                    ActivityManager am=(ActivityManager)
                        app.getSystemService(Context.ACTIVITY_SERVICE);
                    if(am==null)throw new IllegalStateException(
                        "Android no permite recuperar tareas");
                    int moved=0;
                    for(int id:tasks){
                        am.moveTaskToFront(id,ActivityManager.MOVE_TASK_NO_USER_ACTION);
                        moved++;
                    }
                    // Request accepted by ActivityManager; only physical
                    // inspection can prove Jetour drew the tiles on top.
                    done.finished("MOSAICO_RECUPERADO_SOLICITADO: "+moved+
                        " ventana(s) existentes reordenadas en Display 0. "+
                        "Sin nuevas apps, sin borrar datos y sin forzar "+
                        "detención. COMPROBÁ VISUALMENTE.");
                }catch(Exception error){
                    done.finished("MOSAICO NO RECUPERADO: Android rechazó "+
                        "poner las tareas al frente ("+
                        error.getClass().getSimpleName()+"). "+
                        "Conservamos las apps y su almacenamiento.");
                }
            });
        },"DriveLab-Boot-Reveal-Known-Display0").start();
    }
}
