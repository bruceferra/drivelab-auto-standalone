package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-confirmed recovery from Bridge's earlier direct Freeform Display 1
 * experiments. Only if Android local ADB confirms a pure map-only stack.
 *
 * NO generic task removal, OEM/ADAS/Home changes, data wipe, force stop,
 * overlay manipulation or automatic execution. ADB auth is independent.
 */
final class BridgeClusterRecovery {
    static final String STATUS="cluster_recovery_status";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private BridgeClusterRecovery(){}
    static void disableNewCluster(Context context){
        BridgeStandaloneWindows.prefs(context).edit()
            .putBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,false)
            .putBoolean(BridgeStandaloneWindows.TAB_ROUTE,false).apply();
        BridgeWheelAccessibilityService.prefs(context).edit()
            .putBoolean(BridgeWheelAccessibilityService.ENABLED,false).apply();
    }
    private static void note(Context context,String message){
        BridgeStandaloneWindows.prefs(context).edit()
            .putString(STATUS,message).apply();
    }
    static String status(Context context){
        return BridgeStandaloneWindows.prefs(context).getString(STATUS,
            "No se intentó limpiar el tablero desde Bridge.");
    }
    static void ask(Activity owner){
        // Even when the owner declines ADB cleanup, stop Bridge opening
        // new maps on the cluster and release its own wheel callback.
        disableNewCluster(owner);
        if(BUSY.get()){
            note(owner,"Recuperación anterior todavía en curso.");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("LIBERAR CLÚSTER · SÓLO MAPAS DE BRIDGE")
            .setMessage("Sólo con la Jetour ESTACIONADA. Bridge ya no "+
                "enviará mapas nuevos a Display 1 y soltó su volante.\n\n"+
                "Se consultarán las tareas de Android por ADB LOCAL, "+
                "sin Termux. Si hay una pila independiente con SÓLO "+
                "Waze o Google Maps en Display 1, te pediré autorización "+
                "de nuevo para retirarla. Puede cerrar esa navegación, "+
                "pero NO borra mapas, cuentas, datos o rutas guardadas.\n\n"+
                "Si la pila contiene tareas Jetour, OEM o información "+
                "ambigua, NO se modificará nada.")
            .setNegativeButton("SÓLO DESACTIVAR",null)
            .setPositiveButton("INSPECCIONAR MAPAS",(d,w)->inspect(owner))
            .show();
    }
    private static void inspect(Activity owner){
        if(!BUSY.compareAndSet(false,true))return;
        Context app=owner.getApplicationContext();
        note(app,"Inspeccionando tareas de Display 1 por ADB local...");
        new Thread(()->{
            try{
                String state=BridgeLocalAdb.shell(app,"am stack list");
                List<BridgeClusterRecoveryPolicy.Task> tasks=
                    BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(state);
                if(tasks.isEmpty()){
                    note(app,"NO SE TOCÓ EL CLÚSTER: Android no mostró "+
                        "una pila independiente de Waze/Maps verificable. "+
                        "No se tocarán procesos Jetour.");
                    return;
                }
                StringBuilder names=new StringBuilder();
                for(BridgeClusterRecoveryPolicy.Task task:tasks)
                    names.append(task.pkg).append(" · pila ")
                        .append(task.stackId).append("\n");
                owner.runOnUiThread(()->{
                    if(owner.isFinishing()||owner.isDestroyed())return;
                    new AlertDialog.Builder(owner)
                        .setTitle("CONFIRMAR LIBERACIÓN DE MAPAS")
                        .setMessage("Android identificó en Display 1 estas "+
                            "pilas que contienen exclusivamente mapas:\n\n"+
                            names+"\nAUTORIZO retirar únicamente esas pilas. "+
                            "Si alguna cambió al volver a inspeccionar, "+
                            "Bridge cancela y no toca el sistema.")
                        .setNegativeButton("NO TOCAR",null)
                        .setPositiveButton("RETIRAR SÓLO MAPAS",
                            (d,w)->removeSelected(owner,tasks))
                        .show();
                });
            }catch(Exception fail){
                note(app,"NO SE MODIFICÓ DISPLAY 1: ADB local "+
                    "no disponible o identificación bloqueada ("+
                    fail.getClass().getSimpleName()+").");
            }finally{BUSY.set(false);}
        },"Bridge-Inspect-Map-Only-Stacks").start();
    }
    private static void removeSelected(Activity owner,
            List<BridgeClusterRecoveryPolicy.Task> selected){
        if(!BUSY.compareAndSet(false,true))return;
        Context app=owner.getApplicationContext();
        new Thread(()->{
            int removed=0;
            try{
                for(BridgeClusterRecoveryPolicy.Task wanted:selected){
                    // Fail-closed TOCTOU recheck before EACH action.
                    List<BridgeClusterRecoveryPolicy.Task> now=
                        BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
                            BridgeLocalAdb.shell(app,"am stack list"));
                    boolean same=false;
                    for(BridgeClusterRecoveryPolicy.Task current:now)
                        if(current.stackId==wanted.stackId&&
                           current.taskId==wanted.taskId&&
                           current.pkg.equals(wanted.pkg))
                            same=true;
                    if(!same)throw new SecurityException(
                        "La pila cambió; cancelado para proteger el OEM");
                    String command=BridgeClusterRecoveryPolicy.removeCommand(wanted);
                    String result=BridgeLocalAdb.shell(app,command);
                    if(result.toLowerCase(java.util.Locale.ROOT)
                        .matches("(?s).*(error|denied|exception|unknown).*"))
                        throw new SecurityException("Android rechazó la liberación");
                    removed++;
                }
                note(app,"Se solicitó retirar "+removed+
                    " pila(s) de mapas en Display 1; comprobá la pantalla. "+
                    "Bridge no volvió a abrir mapas.");
            }catch(Exception fail){
                note(app,"Liberación parcial "+removed+"/"+selected.size()+
                    "; detenido sin tocar OEM: "+
                    fail.getClass().getSimpleName());
            }finally{BUSY.set(false);}
        },"Bridge-Remove-Verified-Map-Staсks").start();
    }
}
