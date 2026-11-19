package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.HashSet;

/** Reopen the already existing Display 0 Freeform mosaic.
 * No shell writes, new launches, task reparenting, OEM operations, resize,
 * global settings or process termination. No automatic restart on boot.
 *
 * All task IDs must originate from DriveLab's explicit saved mosaic, and
 * be revalidated against their exact package, Freeform mode and display.
 * A bounds/ActivityRecord report is NOT physical simultaneous visibility:
 * driver confirms visual result on the actual head unit.
 */
final class T1CenterMosaicRecovery {
    interface Done {void finished(boolean requested,String report);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String SETTINGS="drivelab_center_mosaic_v1";
    private static final String SELF=
        "ar.com.rango5gps.drivelabauto.receiver";

    private T1CenterMosaicRecovery(){}

    static void restore(Activity owner,Done done){
        Context context=owner.getApplicationContext();
        new Thread(()->{
            int[] ids=null;
            String[] names=null;
            String result;
            try{
                SharedPreferences p=context.getSharedPreferences(
                    SETTINGS,Context.MODE_PRIVATE);
                int mode=p.getInt("mode",0);
                if(mode<0||mode>4)
                    throw new IllegalStateException(
                        "Distribución guardada inválida");
                int count=mode==4?1:mode==T1CenterMosaicGeometry.TWO_COLUMNS?2:3;
                ids=new int[count];
                names=new String[count];
                T1AdbTaskParser.Box[] windows=
                    new T1AdbTaskParser.Box[count];
                HashSet<String> packages=new HashSet<>();
                HashSet<Integer> uniqueIds=new HashSet<>();
                for(int i=0;i<count;i++){
                    String pkg=p.getString("pkg"+i,"");
                    if(pkg==null||
                        !(SELF.equals(pkg)||
                            T1CenterUserPackagePolicy.allowed(pkg))||
                        !packages.add(pkg))
                        throw new IllegalStateException(
                            "Panel "+(i+1)+": aplicación inválida");
                    T1AdbTaskParser.Task task=
                        T1CenterSavedFreeformMosaic.verifiedRememberedTask(
                            context,pkg);
                    if(task.id<=0||task.display!=0||task.bounds==null||
                        !uniqueIds.add(task.id))
                        throw new IllegalStateException(
                            "Panel "+(i+1)+": tarea incorrecta");
                    ids[i]=task.id;
                    names[i]=pkg;
                    windows[i]=task.bounds;
                }
                if(!T1CenterMosaicViewport.disjoint(windows))
                    throw new IllegalStateException(
                        "Las ventanas guardadas se superponen");
                result="Se verificaron "+count+
                    " ventanas Freeform de Display 0. "+
                    "Se traerán las MISMAS tareas, sin lanzarlas "+
                    "de nuevo ni cambiar sus tamaños.";
            }catch(Exception ex){
                ids=null;
                names=null;
                result="NO SE PUEDE RECUPERAR: "+
                    ex.getClass().getSimpleName()+": "+
                    String.valueOf(ex.getMessage())+
                    ". Entrá en Mosaico para reconstruir "+
                    "la sesión. No se abrió ni cerró ninguna app.";
            }
            final int[] verified=ids;
            final String[] verifiedPackages=names;
            final String prepared=result;
            MAIN.post(()->{
                if(owner.isFinishing()||owner.isDestroyed())return;
                if(verified==null){
                    saveResult(context,prepared);
                    done.finished(false,prepared);
                    return;
                }
                ActivityManager manager=(ActivityManager)
                    owner.getSystemService(Context.ACTIVITY_SERVICE);
                if(manager==null){
                    done.finished(false,"Android no ofrece ActivityManager");
                    return;
                }
                int moved=0;
                StringBuilder log=new StringBuilder(prepared).append('\n');
                for(int i=0;i<verified.length;i++){
                    try{
                        // REORDER_TASKS is declared by Receiver. A user
                        // deliberately tapped the restore button/shortcut.
                        // This moves an EXACT task to foreground; unlike
                        // ACTION_MAIN launching, it preserves Freeform bounds.
                        manager.moveTaskToFront(verified[i],0);
                        moved++;
                        log.append("Al frente solicitado: ")
                            .append(verifiedPackages[i])
                            .append(" tarea ").append(verified[i])
                            .append('\n');
                    }catch(Exception ex){
                        log.append("Android rechazó la tarea ")
                            .append(verified[i]).append(": ")
                            .append(ex.getClass().getSimpleName())
                            .append(". No se reintentará abriendo otra app.\n");
                        break;
                    }
                }
                boolean complete=moved==verified.length;
                log.append(complete?
                    "REAPERTURA SOLICITADA: revisá visualmente "+
                    "los paneles. Android puede no mostrarlos juntos.":
                    "RECUPERACIÓN PARCIAL. Revisá el diagnóstico "+
                    "y reiniciá sólo el mosaico si es necesario.");
                String report=log.toString();
                saveResult(context,report);
                if(complete){
                    Toast.makeText(owner,
                        "Volviendo al mosaico · "+moved+" ventanas",
                        Toast.LENGTH_LONG).show();
                    // The main Receiver settings task is fullscreen and
                    // would occlude the recovered tiles; do not background
                    // an own Cockpit tile if restore was tapped inside it.
                    boolean ownsTile=false;
                    for(int id:verified)
                        if(id==owner.getTaskId())ownsTile=true;
                    if(!ownsTile)owner.moveTaskToBack(true);
                }
                done.finished(complete,report);
            });
        },"DriveLab-Restore-Existing-Freeform").start();
    }
    private static void saveResult(Context context,String message){
        context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE)
            .edit().putString("last_mosaic_restore",message).apply();
    }
}
