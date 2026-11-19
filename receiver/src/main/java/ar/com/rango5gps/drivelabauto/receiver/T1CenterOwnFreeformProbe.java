package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/** Isolated freeform test: starts only our own disposable activity on
 * Display 0 via ADB. No other task's resizeMode, geometry or HOME is changed.
 */
final class T1CenterOwnFreeformProbe {
    interface Done {void completed(String outcome);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String START=
        "am start --display 0 --windowingMode 5 -n "+
        "ar.com.rango5gps.drivelabauto.receiver/.T1CenterFreeformTestActivity";
    private T1CenterOwnFreeformProbe(){}
    static void run(Context context,Done done){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String result=inspect(app);
            MAIN.post(()->done.completed(result));
        },"DriveLab-Own-Freeform-Display0-Test").start();
    }
    private static String shortText(String value){
        if(value==null||value.trim().isEmpty())return "sin texto";
        String v=value.replace('\r',' ').replace('\n',' ').trim();
        return v.substring(0,Math.min(350,v.length()));
    }
    private static String inspect(Context app){
        StringBuilder report=new StringBuilder(
            "FREEFORM AISLADO · DISPLAY 0\n"+
            "Sólo una actividad temporal de DriveLab. "+
            "No altera tareas de ProManager, Uber, clúster ni HOME.\n");
        SharedPreferences prefs=app.getSharedPreferences(
            T1CenterFreeformTestActivity.PREFS,Context.MODE_PRIVATE);
        // Remove prior measurements so stale data cannot be reported.
        prefs.edit().remove(T1CenterFreeformTestActivity.KEY)
            .remove(T1CenterFreeformTestActivity.TASK)
            .remove(T1CenterFreeformTestActivity.TIME).commit();
        try{
            String commandResult=T1LocalAdbClient.shell(app,START);
            report.append("ADB inicio modo 5: ")
                .append(shortText(commandResult)).append('\n');
            if(T1CenterWindowModePolicy.shellRejected(commandResult)){
                return report+"Android rechazó el inicio. "+
                    "No se cambió ninguna tarea externa.";
            }
            Thread.sleep(1900L);
            int task=prefs.getInt(T1CenterFreeformTestActivity.TASK,-1);
            long updated=prefs.getLong(T1CenterFreeformTestActivity.TIME,0);
            String ui=prefs.getString(T1CenterFreeformTestActivity.KEY,null);
            if(task<=0||updated<=0||ui==null)
                return report+"La actividad temporal no confirmó su inicio. "+
                    "Puede que la ROM no acepte modo 5 en Display 0.";
            report.append("Actividad propia: task=").append(task)
                .append(" · ").append(ui).append('\n');
            String dump=T1LocalAdbClient.shell(app,
                "dumpsys activity activities");
            String pkg=app.getPackageName();
            boolean freeform=T1CenterWindowModePolicy.isFreeform(
                dump,task,pkg);
            boolean multi=ui.contains("isInMultiWindowMode=true");
            boolean onCenter=ui.contains("Display=0");
            report.append("ADB modo freeform: ").append(freeform?"SÍ":"NO")
                .append(" · Activity multiwindow: ").append(multi?"SÍ":"NO")
                .append(" · Display 0: ").append(onCenter?"SÍ":"NO")
                .append('\n');
            report.append(freeform&&multi&&onCenter?
                "FREEFORM DISPONIBLE para una actividad propia. "+
                "Falta verificar geometría y compatibilidad de apps externas.":
                "FREEFORM NO CONFIRMADO: Android inició la actividad en "+
                "otro modo, rechazó freeform o no expuso el estado exacto.");
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            report.append("Diagnóstico interrumpido.");
        }catch(Exception e){
            report.append("Diagnóstico no completado: ")
                .append(shortText(e.getClass().getSimpleName()+": "+
                    String.valueOf(e.getMessage())));
        }finally{
            // The temporary activity closes itself by finishAndRemoveTask.
            try{Thread.sleep(2400L);}catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
            report.append("\nActividad de prueba configurada para "+
                "cerrarse automáticamente a los 3,8 segundos.");
        }
        return report.toString();
    }
}
