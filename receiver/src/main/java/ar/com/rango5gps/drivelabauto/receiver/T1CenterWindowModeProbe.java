package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

/** One-task, opt-in, self-restoring Android 11 window mode experiment.
 * Never changes global settings, root, HOME, OEM tasks or Display 1.
 * If the task's exact original resize mode is unknown: no mutations.
 */
final class T1CenterWindowModeProbe {
    interface Done {void completed(String result);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private T1CenterWindowModeProbe(){}
    private static String read(Context c,String command)throws Exception {
        return T1LocalAdbClient.shell(c,command);
    }
    private static String safe(String s){
        if(s==null||s.trim().isEmpty())return "sin texto";
        String compact=s.trim().replace('\r',' ').replace('\n',' ');
        return compact.substring(0,Math.min(220,compact.length()));
    }
    private static boolean userApp(Context app,String pkg){
        if(!T1CenterAdbResize.allowed(pkg)||pkg.equals(app.getPackageName())||
            T1CenterWindowModePolicy.isSystemPackage(pkg))return false;
        try{
            ApplicationInfo info=app.getPackageManager().getApplicationInfo(pkg,0);
            return (info.flags&(ApplicationInfo.FLAG_SYSTEM|
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0;
        }catch(Exception e){return false;}
    }
    private static T1AdbTaskParser.Task find(Context app,String pkg)
            throws Exception{
        String stack=read(app,"am stack list");
        T1AdbTaskParser.Task t=T1AdbTaskParser.uniqueCenter(stack,pkg);
        if(t!=null)return t;
        return T1AdbTaskParser.uniqueCenter(
            read(app,"dumpsys activity activities"),pkg);
    }
    static void test(Context context,String pkg,Done done){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String message=run(app,pkg);
            MAIN.post(()->done.completed(message));
        },"DriveLab-Display0-OneTask-ModeProbe").start();
    }
    private static String run(Context app,String pkg){
        StringBuilder result=new StringBuilder(
            "PRUEBA DE MODO · DISPLAY 0 · UNA TAREA\n");
        boolean attempted=false,restored=true;
        int taskId=-1,originalMode=-1;
        T1AdbTaskParser.Box originalBounds=null;
        try{
            if(!userApp(app,pkg))
                return result+"RECHAZADO: sólo apps instaladas por el usuario. "+
                    "No se prueban componentes OEM, HOME ni DriveLab.";
            result.append("App: ").append(pkg).append("\n");
            result.append("Multiwindow soportado: ")
                .append(safe(read(app,"am supports-multiwindow")))
                .append(" · Split screen: ")
                .append(safe(read(app,"am supports-split-screen-multi-window")))
                .append("\n");
            String activities=read(app,"dumpsys activity activities");
            String recents=read(app,"dumpsys activity recents");
            T1AdbTaskParser.Task before=find(app,pkg);
            if(before==null||before.id<=0||before.display!=0||
                before.bounds==null)
                return result+"NO SE CAMBIÓ NADA: abrí primero la aplicación "+
                    "seleccionada y volvé a DriveLab. "+
                    "Su tarea de Display 0 debe ser única y tener geometría.";
            taskId=before.id;
            originalBounds=before.bounds;
            if(!T1CenterWindowModePolicy.isFullscreen(activities,taskId,pkg))
                return result+"NO SE CAMBIÓ NADA: la tarea "+
                    taskId+" no está confirmada en modo fullscreen.";
            originalMode=T1CenterWindowModePolicy.originalResizeMode(
                activities+"\n"+recents,taskId,pkg);
            result.append("ANTES: ").append(before)
                .append(" · resizeMode original=").append(originalMode)
                .append("\n");
            if(originalMode<0||originalMode>3)
                return result+"NO SE CAMBIÓ NADA: Android no expone "+
                    "el resizeMode original de esta tarea; no puedo "+
                    "garantizar su restauración.";
            if(originalBounds.left!=0||originalBounds.top!=0||
                originalBounds.right<640||originalBounds.bottom<480)
                return result+"NO SE CAMBIÓ NADA: geometría de pantalla "+
                    "original no verificable.";
            int half=originalBounds.right/2;
            if(half<320)
                return result+"NO SE CAMBIÓ NADA: anchura insuficiente.";
            // Recheck exact task identity immediately before any mutation.
            T1AdbTaskParser.Task check=find(app,pkg);
            if(check==null||check.id!=taskId||check.display!=0||
                check.bounds==null||
                !check.bounds.near(originalBounds,0))
                return result+"NO SE CAMBIÓ NADA: la tarea cambió durante "+
                    "el diagnóstico.";
            attempted=true;
            String changed=read(app,"am task resizeable "+taskId+" 2");
            result.append("ADB resizeable(2): ").append(safe(changed)).append("\n");
            if(T1CenterWindowModePolicy.shellRejected(changed))
                return result+"La ROM rechazó explícitamente el "+
                    "modo redimensionable.";
            T1AdbTaskParser.Task afterMode=find(app,pkg);
            if(afterMode==null||afterMode.id!=taskId||afterMode.display!=0)
                return result+"La tarea cambió al intentar ajustar "+
                    "resizeable; no se ejecutó resize.";
            String resized=read(app,"am task resize "+taskId+
                " 0 0 "+half+" "+originalBounds.bottom);
            result.append("ADB resize(50%): ").append(safe(resized)).append("\n");
            Thread.sleep(450L);
            T1AdbTaskParser.Task after=find(app,pkg);
            String dump=read(app,"dumpsys activity activities");
            boolean freeform=after!=null&&after.id==taskId&&
                dump.contains("mode=freeform")&&
                T1CenterWindowModePolicy.isFreeform(dump,taskId,pkg);
            T1AdbTaskParser.Box halfBounds=new T1AdbTaskParser.Box(
                0,0,half,originalBounds.bottom);
            boolean geometry=after!=null&&after.id==taskId&&
                after.bounds!=null&&after.bounds.near(halfBounds,20);
            result.append("DESPUÉS: ").append(after==null?
                "tarea no encontrada":after.toString())
                .append("\nModo FREEFORM: ").append(freeform?"SÍ":"NO")
                .append(" · Geometría 50%: ").append(geometry?"SÍ":"NO")
                .append("\n");
            result.append(freeform&&geometry?
                "LA ROM ACEPTÓ EL MODO TEMPORAL; restaurando estado original.":
                "LA ROM NO HABILITÓ VENTANA LIBRE: la tarea se mantuvo "+
                "fullscreen o ignoró sus límites.");
        }catch(Exception e){
            result.append("La prueba devolvió ")
                .append(e.getClass().getSimpleName()).append(": ")
                .append(safe(String.valueOf(e.getMessage())));
        }finally{
            if(attempted&&taskId>0&&originalMode>=0&&
                originalBounds!=null){
                // Restore only if the exact selected user task remains
                // uniquely identifiable in Display 0. Never guess IDs.
                try{
                    T1AdbTaskParser.Task current=find(app,pkg);
                    if(current==null||current.id!=taskId||current.display!=0)
                        throw new IllegalStateException(
                            "la tarea cambió; restauración automática detenida");
                    String rect=read(app,"am task resize "+taskId+" "+
                        originalBounds.geometry());
                    String mode=read(app,"am task resizeable "+taskId+" "+
                        originalMode);
                    restored=!T1CenterWindowModePolicy.shellRejected(rect)&&
                        !T1CenterWindowModePolicy.shellRejected(mode);
                    T1AdbTaskParser.Task end=find(app,pkg);
                    restored=restored&&end!=null&&end.id==taskId&&
                        end.bounds!=null&&
                        end.bounds.near(originalBounds,20)&&
                        T1CenterWindowModePolicy.isFullscreen(
                            read(app,"dumpsys activity activities"),taskId,pkg);
                    result.append("\nRESTAURACIÓN: ")
                        .append(restored?"fullscreen y bounds originales":
                            "NO VERIFICADA; comprobá la app antes de usarla");
                    result.append("\nRestaurar bounds: ").append(safe(rect))
                        .append(" · resizeMode=").append(originalMode)
                        .append(" → ").append(safe(mode));
                }catch(Exception e){
                    result.append("\nRESTAURACIÓN NO VERIFICADA: ")
                        .append(safe(String.valueOf(e.getMessage())));
                }
            }
        }
        return result.toString();
    }
}
