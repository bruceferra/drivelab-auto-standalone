package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opt-in experiment launching ONLY ProManager and Uber Beta in newly
 * allocated Freeform tasks on center Display 0. Existing tasks are never
 * resized, closed, force-stopped or modified. The experiment does not touch
 * system/OEM activities, HOME, accessibility or secondary Display 1.
 */
final class T1CenterExternalFreeformProbe {
    interface Done {void completed(String report);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    static final String LEFT_PKG="ar.com.promanager.nativeapp",
        RIGHT_PKG="ar.com.rango5gps.uberbeta";
    private T1CenterExternalFreeformProbe(){}

    static void run(Context context,Done done){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String result=inspect(app);
            MAIN.post(()->done.completed(result));
        },"DriveLab-UserApp-Freeform-Display0").start();
    }

    private static String concise(String value){
        if(value==null||value.trim().isEmpty())return "sin texto";
        value=value.replace('\r',' ').replace('\n',' ').trim();
        return value.substring(0,Math.min(190,value.length()));
    }
    private static ComponentName launchable(Context app,String pkg)
            throws Exception{
        if(!pkg.equals(LEFT_PKG)&&!pkg.equals(RIGHT_PKG))
            throw new SecurityException("Paquete externo no autorizado");
        PackageManager pm=app.getPackageManager();
        ApplicationInfo info=pm.getApplicationInfo(pkg,0);
        if((info.flags & (ApplicationInfo.FLAG_SYSTEM|
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
            throw new SecurityException("No manipular app del sistema");
        Intent launcher=pm.getLaunchIntentForPackage(pkg);
        ComponentName component=launcher==null?null:launcher.getComponent();
        if(component==null||!pkg.equals(component.getPackageName()))
            throw new IllegalStateException("Launcher no disponible: "+pkg);
        if(!component.flattenToString().matches(
                "[A-Za-z0-9_.$]+/[A-Za-z0-9_.$/]{1,220}"))
            throw new SecurityException("Componente inesperado: "+pkg);
        return component;
    }
    private static List<T1AdbTaskParser.Task> tasks(Context app,String pkg)
            throws Exception {
        return T1AdbTaskParser.tasksOnCenter(
            T1LocalAdbClient.shellApprovedAppTasks(app,pkg),pkg);
    }
    private static Set<Integer> ids(List<T1AdbTaskParser.Task> list){
        Set<Integer> out=new HashSet<>();
        for(T1AdbTaskParser.Task t:list)out.add(t.id);
        return out;
    }
    private static T1AdbTaskParser.Task newTask(Context app,String pkg,
            Set<Integer> before)throws Exception{
        T1AdbTaskParser.Task fresh=null;
        for(T1AdbTaskParser.Task t:tasks(app,pkg)){
            if(before.contains(t.id))continue;
            if(fresh!=null)throw new IllegalStateException(
                "Más de una tarea nueva; no puedo identificar la ventana");
            fresh=t;
        }
        if(fresh==null||fresh.id<=0||fresh.bounds==null||fresh.display!=0)
            throw new IllegalStateException("Android reutilizó una tarea "+
                "existente o no informó bounds; NO se redimensiona");
        return fresh;
    }
    private static T1AdbTaskParser.Task exact(Context app,
            int id,String pkg)throws Exception{
        String dump=T1LocalAdbClient.shellTaskExcerpt(
            app,"am stack list",id);
        T1AdbTaskParser.Task task=T1AdbTaskParser.exactCenter(
            dump,id,pkg);
        if(task==null||task.bounds==null||task.display!=0)
            throw new IllegalStateException("Task ID "+id+
                " no confirmado en Display 0");
        String activities=T1LocalAdbClient.shellTaskExcerpt(app,
            "dumpsys activity activities",id);
        if(!T1CenterWindowModePolicy.isFreeform(activities,id,pkg))
            throw new IllegalStateException("Task "+id+
                " inició fullscreen, no Freeform");
        return task;
    }
    private static boolean visible(Context app,int task,String pkg)
            throws Exception{
        String dump=T1LocalAdbClient.shellTaskExcerpt(app,
            "dumpsys activity activities",task);
        String needle="#"+task;
        for(String line:dump.split("\\r?\\n")){
            int at=line.indexOf(needle);
            if(at<0||at+needle.length()<line.length()&&
                Character.isDigit(line.charAt(at+needle.length())))
                continue;
            if(line.contains(pkg)&&line.contains("visible=true"))return true;
        }
        return false;
    }
    private static String launch(Context app,ComponentName component)
            throws Exception{
        String command="am start --display 0 --windowingMode 5 "+
            "-f 0x18000000 -n "+component.flattenToString();
        String output=T1LocalAdbClient.shell(app,command);
        if(T1CenterWindowModePolicy.shellRejected(output))
            throw new IllegalStateException("La ROM rechazó inicio: "+
                concise(output));
        return concise(output);
    }
    private static void size(Context app,int id,String pkg,
            T1AdbTaskParser.Box target)throws Exception{
        exact(app,id,pkg); // ensure exact task remains freeform now
        String out=T1LocalAdbClient.shell(app,
            "am task resize "+id+" "+target.geometry());
        if(T1CenterWindowModePolicy.shellRejected(out))
            throw new IllegalStateException("ADB resize rechazado: "+
                concise(out));
    }
    private static String inspect(Context app){
        StringBuilder report=new StringBuilder(
            "DOS APPS EXTERNAS · DISPLAY 0 · TEST\n"+
            "ProManager izquierda + Uber Beta derecha.\n"+
            "Se crean tareas nuevas. Nunca se redimensionan tareas antiguas.\n");
        try{
            ComponentName pro=launchable(app,LEFT_PKG);
            ComponentName uber=launchable(app,RIGHT_PKG);
            Set<Integer> oldPro=ids(tasks(app,LEFT_PKG));
            Set<Integer> oldUber=ids(tasks(app,RIGHT_PKG));
            report.append("Tareas previas: ProManager ")
                .append(oldPro.size()).append(" · Uber Beta ")
                .append(oldUber.size()).append('\n');
            report.append("Inicio ProManager: ")
                .append(launch(app,pro)).append('\n');
            Thread.sleep(850L);
            T1AdbTaskParser.Task left=newTask(app,LEFT_PKG,oldPro);
            left=exact(app,left.id,LEFT_PKG);
            T1AdbTaskParser.Box screen=left.bounds;
            if(screen.left!=0||screen.top<0||screen.top>180||
                screen.right<1200||screen.right>4096||
                screen.bottom-screen.top<640)
                throw new IllegalStateException(
                    "Bounds iniciales ProManager inesperados: "+screen);
            int width=screen.right,height=screen.bottom-screen.top;
            int half=width/2;
            T1AdbTaskParser.Box leftTarget=new T1AdbTaskParser.Box(
                0,0,half,height);
            size(app,left.id,LEFT_PKG,leftTarget);
            T1AdbTaskParser.Task leftSized=exact(app,left.id,LEFT_PKG);
            if(!T1CenterFreeformGeometry.half(leftSized.bounds,leftTarget))
                throw new IllegalStateException(
                    "ProManager no aceptó media pantalla: "+leftSized);
            report.append("ProManager: task ").append(left.id)
                .append(" · ").append(leftSized.bounds).append('\n');
            report.append("Inicio Uber Beta: ")
                .append(launch(app,uber)).append('\n');
            Thread.sleep(1000L);
            T1AdbTaskParser.Task right=newTask(app,RIGHT_PKG,oldUber);
            right=exact(app,right.id,RIGHT_PKG);
            if(right.id==left.id)
                throw new IllegalStateException("Tareas no independientes");
            T1AdbTaskParser.Box rightTarget=new T1AdbTaskParser.Box(
                half,0,width,height);
            size(app,right.id,RIGHT_PKG,rightTarget);
            Thread.sleep(1250L);
            left=exact(app,left.id,LEFT_PKG);
            right=exact(app,right.id,RIGHT_PKG);
            boolean sized=T1CenterFreeformGeometry.twoColumns(
                left.bounds,right.bounds,width,height);
            boolean concurrently=visible(app,left.id,LEFT_PKG)&&
                visible(app,right.id,RIGHT_PKG);
            report.append("ProManager REAL: ").append(left).append('\n')
                .append("Uber Beta REAL: ").append(right).append('\n')
                .append("Columnas 50/50: ").append(sized?"SÍ":"NO")
                .append(" · Visibles a la vez: ")
                .append(concurrently?"SÍ":"NO").append('\n')
                .append(sized&&concurrently?
                    "ÉXITO: aplicaciones externas en mosaico Display 0.":
                    "PRUEBA PARCIAL: ver tamaños y visibilidad reales.").append('\n');
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            report.append("Prueba interrumpida.\n");
        }catch(Exception e){
            report.append("SIN CONFIRMACIÓN: ").append(concise(
                e.getClass().getSimpleName()+": "+e.getMessage())).append('\n');
        }
        report.append("Sin cambios a HOME, Display 1 ni OEM. "+
            "No se cierran ni eliminan tareas externas automáticamente. "+
            "Volvé a DriveLab para ver este resultado.");
        return report.toString();
    }
}
