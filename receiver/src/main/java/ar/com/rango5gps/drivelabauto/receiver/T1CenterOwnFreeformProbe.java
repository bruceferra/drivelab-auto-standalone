package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/** Explicitly initiated, isolated Android 11 freeform geometry experiment.
 * No external tasks, settings, HOME, OEM components or Display 1 are modified.
 * The sole modified task is our own temporary self-closing Activity.
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
        },"DriveLab-Own-Freeform-Display0-Bounds").start();
    }
    private static String shortText(String value){
        if(value==null||value.trim().isEmpty())return "sin texto";
        String v=value.replace('\r',' ').replace('\n',' ').trim();
        return v.substring(0,Math.min(350,v.length()));
    }
    private static boolean isOwnDisposableTask(String stack,int id,String pkg){
        if(id<=0||stack==null)return false;
        String needle="taskId="+id+":";
        int matches=0;
        for(String line:stack.split("\\r?\\n")){
            if(line.contains(needle)){
                matches++;
                if(!line.contains(pkg+"/")||
                    !line.contains("T1CenterFreeformTestActivity"))
                    return false;
            }
        }
        return matches==1;
    }
    private static String inspect(Context app){
        StringBuilder report=new StringBuilder(
            "FREEFORM · GEOMETRÍA DISPLAY 0\n"+
            "Prueba de una sola ventana temporal propia de DriveLab.\n");
        SharedPreferences prefs=app.getSharedPreferences(
            T1CenterFreeformTestActivity.PREFS,Context.MODE_PRIVATE);
        prefs.edit().remove(T1CenterFreeformTestActivity.KEY)
            .remove(T1CenterFreeformTestActivity.TASK)
            .remove(T1CenterFreeformTestActivity.TIME).commit();
        long startAt=System.currentTimeMillis();
        boolean launched=false;
        try{
            String result=T1LocalAdbClient.shell(app,START);
            report.append("ADB modo 5: ").append(shortText(result)).append('\n');
            if(T1CenterWindowModePolicy.shellRejected(result))
                return report+"ADB rechazó el lanzamiento; sin cambios.";
            launched=true;
            Thread.sleep(1350L);
            int task=prefs.getInt(T1CenterFreeformTestActivity.TASK,-1);
            long updated=prefs.getLong(T1CenterFreeformTestActivity.TIME,0);
            String ui=prefs.getString(T1CenterFreeformTestActivity.KEY,null);
            if(task<=0||updated<=startAt||ui==null||
                !ui.contains("Display=0"))
                return report+"La actividad temporal propia no confirmó "+
                    "Display 0; no se ejecutó resize.";
            report.append("ANTES Activity: task=").append(task)
                .append(" · ").append(ui).append('\n');
            String pkg=app.getPackageName();
            String stack=T1LocalAdbClient.shellTaskExcerpt(app,"am stack list",task);
            String activities=T1LocalAdbClient.shellTaskExcerpt(app,
                "dumpsys activity activities",task);
            T1AdbTaskParser.Task before=T1AdbTaskParser.exactCenter(
                stack,task,pkg);
            boolean own=isOwnDisposableTask(stack,task,pkg);
            boolean freeform=T1CenterWindowModePolicy.isFreeform(
                activities,task,pkg);
            report.append("ANTES ADB: ").append(before==null?
                "sin bounds":before.toString()).append('\n')
                .append("Tarea temporal única: ").append(own?"SÍ":"NO")
                .append(" · Freeform: ").append(freeform?"SÍ":"NO")
                .append(" · Activity multiwindow: ")
                .append(ui.contains("isInMultiWindowMode=true")?"SÍ":"NO")
                .append('\n');
            // Guard on task ID, component, display and freeform immediately
            // before the only geometry-changing ADB command.
            if(!own||!freeform||before==null||
                before.id!=task||before.display!=0||before.bounds==null||
                !ui.contains("isInMultiWindowMode=true"))
                return report+"NO SE CAMBIÓ NADA: identidad, modo o "+
                    "geometría de tarea temporal no verificados.";
            T1AdbTaskParser.Box bounds=before.bounds;
            if(bounds.left!=0||bounds.top!=0||
                bounds.right<1000||bounds.bottom<640)
                return report+"NO SE CAMBIÓ NADA: pantalla inicial "+
                    "inesperada: "+bounds;
            int half=bounds.right/2;
            T1AdbTaskParser.Box target=new T1AdbTaskParser.Box(
                0,0,half,bounds.bottom);
            // Reconfirm correct temporary component immediately before ADB.
            String fresh=T1LocalAdbClient.shellTaskExcerpt(app,"am stack list",task);
            T1AdbTaskParser.Task check=T1AdbTaskParser.exactCenter(
                fresh,task,pkg);
            if(check==null||check.id!=task||check.display!=0||
                !isOwnDisposableTask(fresh,task,pkg)||
                check.bounds==null||!check.bounds.near(bounds,0))
                return report+"NO SE CAMBIÓ NADA: la tarea temporal cambió.";
            report.append("RESIZE SOLICITADO: ").append(target).append('\n');
            String resize=T1LocalAdbClient.shell(app,
                "am task resize "+task+" "+target.geometry());
            report.append("ADB resize: ").append(shortText(resize)).append('\n');
            if(T1CenterWindowModePolicy.shellRejected(resize))
                return report+"La ROM rechazó el resize de la "+
                    "tarea Freeform propia.";
            Thread.sleep(1400L);
            String afterStack=T1LocalAdbClient.shellTaskExcerpt(app,"am stack list",task);
            T1AdbTaskParser.Task after=T1AdbTaskParser.exactCenter(
                afterStack,task,pkg);
            String afterDump=T1LocalAdbClient.shellTaskExcerpt(app,
                "dumpsys activity activities",task);
            boolean afterFreeform=T1CenterWindowModePolicy.isFreeform(
                afterDump,task,pkg);
            boolean sized=after!=null&&
                isOwnDisposableTask(afterStack,task,pkg)&&
                T1CenterFreeformGeometry.half(after.bounds,target);
            String afterUi=prefs.getString(
                T1CenterFreeformTestActivity.KEY,"sin lectura");
            report.append("DESPUÉS ADB: ").append(after==null?
                "tarea ya cerrada o sin bounds":after.toString())
                .append('\n');
            report.append("DESPUÉS Activity: ").append(afterUi).append('\n');
            report.append("Modo freeform: ").append(afterFreeform?"SÍ":"NO")
                .append(" · Tamaño 50%: ").append(sized?"SÍ":"NO")
                .append(" · Offset vertical Android: ")
                .append(after!=null&&after.bounds!=null?
                    after.bounds.top-target.top:-1).append(" px")
                .append('\n');
            report.append(afterFreeform&&sized?
                "ÉXITO: UNA VENTANA PROPIA ACEPTÓ FREEFORM Y MEDIA "+
                "PANTALLA. Queda por verificar dos apps externas.":
                "NO CONFIRMADO: la ventana propia quedó a pantalla "+
                "completa, no respetó los límites o se cerró antes "+
                "de la verificación.");
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            report.append("Prueba interrumpida.");
        }catch(Exception e){
            report.append("ERROR diagnóstico: ").append(
                shortText(e.getClass().getSimpleName()+": "+
                    String.valueOf(e.getMessage())));
        }finally{
            if(launched){
                // Activity itself executes finishAndRemoveTask after 18 s.
                // Wait for cleanup before showing the report over Receiver.
                long remain=18800L-(System.currentTimeMillis()-startAt);
                if(remain>0)try{Thread.sleep(remain);}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
                report.append("\nVentana temporal programada para "+
                    "cerrarse automáticamente; sin cambios a otras apps.");
            }
        }
        return report.toString();
    }
}
