package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/** Two-pane proof on Display 0, exclusively using TWO DriveLab-owned,
 * self-removing Activity tasks. No OEM, HOME, cluster or third-party tasks
 * are changed. Strict ADB allowlist restricts both launches and exact IDs.
 */
final class T1CenterDualFreeformProbe {
    interface Done {void completed(String report);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String PKG="ar.com.rango5gps.drivelabauto.receiver";
    private static final String START_LEFT=
        "am start --display 0 --windowingMode 5 -n "+PKG+
        "/.T1CenterDualLeftActivity";
    private static final String START_RIGHT=
        "am start --display 0 --windowingMode 5 -n "+PKG+
        "/.T1CenterDualRightActivity";
    private T1CenterDualFreeformProbe(){}

    static void run(Context context,Done done){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String result=inspect(app);
            MAIN.post(()->done.completed(result));
        },"DriveLab-DualFreeform-Display0-Test").start();
    }
    private static String summary(String raw){
        if(raw==null||raw.trim().isEmpty())return "sin texto";
        String s=raw.replace('\r',' ').replace('\n',' ').trim();
        return s.substring(0,Math.min(180,s.length()));
    }
    private static String read(Context app,String cmd,int task)throws Exception{
        return T1LocalAdbClient.shellTaskExcerpt(app,cmd,task);
    }
    private static int task(SharedPreferences prefs,String side,long since){
        int id=prefs.getInt(side+"_task",-1);
        long at=prefs.getLong(side+"_at",0);
        String ui=prefs.getString(side+"_ui","");
        return id>0&&at>=since&&ui.contains("Display=0")&&
            ui.contains("isInMultiWindowMode=true")?id:-1;
    }
    private static boolean own(String stack,int task,String component){
        if(task<=0||stack==null)return false;
        String needle="taskId="+task+":";
        int count=0;
        for(String raw:stack.split("\\r?\\n")){
            if(!raw.contains(needle))continue;
            count++;
            if(!raw.contains(PKG+"/")||!raw.contains(component))
                return false;
        }
        return count==1;
    }
    private static boolean visible(String dump,int task){
        if(dump==null)return false;
        String needle="#"+task;
        for(String raw:dump.split("\\r?\\n")){
            int at=raw.indexOf(needle);
            if(at<0||at+needle.length()<raw.length()&&
                Character.isDigit(raw.charAt(at+needle.length())))continue;
            if(raw.contains(PKG)&&raw.contains("visible=true"))return true;
        }
        return false;
    }
    private static T1AdbTaskParser.Task checkedTask(
            Context app,int id,String component)throws Exception{
        String stack=read(app,"am stack list",id);
        if(!own(stack,id,component))
            throw new IllegalStateException("La tarea temporal "+
                component+" no coincide con el ID "+id);
        T1AdbTaskParser.Task result=T1AdbTaskParser.exactCenter(
            stack,id,PKG);
        if(result==null||result.display!=0)
            throw new IllegalStateException("Tarea no pertenece a Display 0");
        if(!T1CenterWindowModePolicy.isFreeform(read(app,
            "dumpsys activity activities",id),id,PKG))
            throw new IllegalStateException("Tarea "+id+" no está en freeform");
        return result;
    }
    private static String inspect(Context app){
        StringBuilder out=new StringBuilder(
            "DOS VENTANAS FREEFORM · DISPLAY 0\n"+
            "Sólo DriveLab Izquierda y DriveLab Derecha.\n");
        SharedPreferences prefs=app.getSharedPreferences(
            T1CenterDualPanelActivity.PREFS,Context.MODE_PRIVATE);
        String L=T1CenterDualPanelActivity.L;
        String R=T1CenterDualPanelActivity.R;
        prefs.edit().remove(L+"_task").remove(L+"_at").remove(L+"_ui")
            .remove(R+"_task").remove(R+"_at").remove(R+"_ui").commit();
        long started=System.currentTimeMillis();
        boolean launched=false;
        try{
            String result=T1LocalAdbClient.shell(app,START_LEFT);
            if(T1CenterWindowModePolicy.shellRejected(result))
                return out+"ADB rechazó la ventana izquierda: "+summary(result);
            launched=true;
            Thread.sleep(850L);
            int leftId=task(prefs,L,started);
            if(leftId<1)
                return out+"Izquierda no confirmó Display 0 y multiwindow.";
            T1AdbTaskParser.Task left=checkedTask(
                app,leftId,"T1CenterDualLeftActivity");
            if(left.bounds==null||left.bounds.left!=0||
                left.bounds.top!=0||left.bounds.right<1200||
                left.bounds.bottom<640||left.bounds.right>4096)
                return out+"Geometría inicial izquierda inesperada: "+left;
            int width=left.bounds.right,height=left.bounds.bottom;
            int half=width/2;
            T1AdbTaskParser.Box desiredLeft=new T1AdbTaskParser.Box(
                0,0,half,height);
            String updateLeft=T1LocalAdbClient.shell(app,
                "am task resize "+leftId+" "+desiredLeft.geometry());
            if(T1CenterWindowModePolicy.shellRejected(updateLeft))
                return out+"ADB rechazó el ancho izquierdo: "+
                    summary(updateLeft);
            out.append("IZQUIERDA: task ").append(leftId)
                .append(" · comando=").append(summary(updateLeft))
                .append('\n');
            // Verify the left task remains ours before creating the next.
            left=checkedTask(app,leftId,"T1CenterDualLeftActivity");
            if(!T1CenterFreeformGeometry.half(left.bounds,desiredLeft))
                return out+"Izquierda no respetó tamaño media pantalla: "+
                    left;
            String startRight=T1LocalAdbClient.shell(app,START_RIGHT);
            if(T1CenterWindowModePolicy.shellRejected(startRight))
                return out+"ADB rechazó la derecha: "+summary(startRight);
            Thread.sleep(1000L);
            int rightId=task(prefs,R,started);
            if(rightId<1||rightId==leftId)
                return out+"La derecha no abrió una tarea independiente en "+
                    "Display 0 con multiwindow.";
            T1AdbTaskParser.Task right=checkedTask(
                app,rightId,"T1CenterDualRightActivity");
            T1AdbTaskParser.Box desiredRight=new T1AdbTaskParser.Box(
                half,0,width,height);
            // No action is taken until we have proved this is an exact
            // independent right-side DriveLab freeform task.
            String updateRight=T1LocalAdbClient.shell(app,
                "am task resize "+rightId+" "+desiredRight.geometry());
            if(T1CenterWindowModePolicy.shellRejected(updateRight))
                return out+"ADB rechazó el ancho derecho: "+
                    summary(updateRight);
            Thread.sleep(1300L);
            String leftStack=read(app,"am stack list",leftId);
            String rightStack=read(app,"am stack list",rightId);
            String leftDump=read(app,"dumpsys activity activities",leftId);
            String rightDump=read(app,"dumpsys activity activities",rightId);
            T1AdbTaskParser.Task actualLeft=T1AdbTaskParser.exactCenter(
                leftStack,leftId,PKG);
            T1AdbTaskParser.Task actualRight=T1AdbTaskParser.exactCenter(
                rightStack,rightId,PKG);
            boolean identity=own(leftStack,leftId,
                "T1CenterDualLeftActivity")&&own(rightStack,rightId,
                "T1CenterDualRightActivity");
            boolean modes=T1CenterWindowModePolicy.isFreeform(
                leftDump,leftId,PKG)&&T1CenterWindowModePolicy.isFreeform(
                rightDump,rightId,PKG);
            boolean bothVisible=visible(leftDump,leftId)&&
                visible(rightDump,rightId);
            boolean sized=actualLeft!=null&&actualRight!=null&&
                T1CenterFreeformGeometry.twoColumns(actualLeft.bounds,
                    actualRight.bounds,width,height);
            String leftUi=prefs.getString(L+"_ui","sin lectura");
            String rightUi=prefs.getString(R+"_ui","sin lectura");
            out.append("Izq REAL: ").append(actualLeft).append('\n')
                .append("Der REAL: ").append(actualRight).append('\n')
                .append("Izq Activity: ").append(leftUi).append('\n')
                .append("Der Activity: ").append(rightUi).append('\n')
                .append("IDs distintos: ").append(leftId!=rightId?"SÍ":"NO")
                .append(" · identidad propia: ").append(identity?"SÍ":"NO")
                .append('\n')
                .append("Ambas freeform: ").append(modes?"SÍ":"NO")
                .append(" · columnas 50/50: ").append(sized?"SÍ":"NO")
                .append(" · visibles simultáneamente: ")
                .append(bothVisible?"SÍ":"NO").append('\n');
            out.append(identity&&modes&&sized&&bothVisible?
                "ÉXITO: Android admite DOS VENTANAS PROPIAS SIMULTÁNEAS "+
                "en Display 0. Queda probar apps externas.":
                "PARCIAL: ver detalles. Freeform, dimensiones y visibilidad "+
                "se comprueban de manera independiente.");
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            out.append("La prueba fue interrumpida.");
        }catch(Exception e){
            out.append("ERROR: ").append(summary(
                e.getClass().getSimpleName()+": "+e.getMessage()));
        }finally{
            if(launched){
                // Neither temporary pane is forcibly removed by ADB;
                // each removes its own task after exactly 30 seconds.
                long remain=31000L-(System.currentTimeMillis()-started);
                if(remain>0)try{Thread.sleep(remain);}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
                out.append("\nAmbas ventanas temporales se cierran "+
                    "automáticamente; sin acciones sobre otras apps.");
            }
        }
        return out.toString();
    }
}
