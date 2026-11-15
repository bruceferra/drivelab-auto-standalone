package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import java.util.List;

/** The missing post-launch operation from the T1Launcher APK:
 * obtain the REAL Waze/Maps task IDs, run "am task resize", then query
 * task bounds again. Requires locally authorized ADB TCP via 127.0.0.1:5555.
 * Deliberately refuses unknown display, duplicated tasks or arbitrary apps.
 */
final class T1ClusterAdbResize {
    static final String OPT_IN="cluster_adb_task_resize_opt_in";
    static final String VERIFIED_AT="cluster_adb_last_verified_ms";
    static final String RESULT="cluster_adb_result";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    interface Done {void completed(String result,boolean verified);}
    private T1ClusterAdbResize(){}
    static boolean enabled(Context c){
        return T1ClusterWazeRouter.prefs(c).getBoolean(OPT_IN,false);
    }
    static void enable(Context c,boolean on){
        T1ClusterWazeRouter.prefs(c).edit()
            .putBoolean(OPT_IN,on)
            .putLong(VERIFIED_AT,0L)
            .putBoolean(T1ClusterLayoutPolicy.ACTIVE,false)
            .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)
            .putString(RESULT,on?
                "Puente habilitado. Autorizá depuración ADB local y PROBÁ "+
                "en P. La conexión no funciona sin adbd TCP 5555.":
                "Puente ADB desactivado. Waze fullscreen sigue disponible.")
            .apply();
    }
    static void probe(Context c,Done done){
        new Thread(()->{
            String result;
            boolean ok=false;
            try{
                String out=T1LocalAdbClient.shell(c,"am stack list");
                ok=out!=null&&!out.trim().isEmpty()&&
                    !errorOutput(out);
                result=ok?"ADB LOCAL CONECTADO: tareas legibles. "+
                    "Falta confirmar los tamaños en Display 1.":
                    "ADB responde pero no se obtuvo lista de tareas: "+
                    String.valueOf(out).substring(0,Math.min(
                        200,String.valueOf(out).length()));
            }catch(Exception error){
                result=T1LocalAdbClient.connectivityHint(error);
            }
            deliver(c,result,ok,done);
        },"T1-Local-ADB-Probe").start();
    }
    static void apply(Context c,boolean split,long launchDelayMs,Done done){
        if(!enabled(c)){
            deliver(c,"Puente ADB desactivado: habilitalo "+
                "para redimensionar tareas reales",false,done);
            return;
        }
        Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                if(launchDelayMs>0)
                    Thread.sleep(Math.min(launchDelayMs,2000L));
                Display selected=T1ClusterWazeRouter.selected(app);
                if(selected==null||selected.getDisplayId()<=0)
                    throw new IllegalStateException(
                        "Display real del clúster no está seleccionado");
                if(!T1ClusterLayoutPolicy.MODE_SPLIT.equals(
                    T1ClusterLayoutPolicy.mode(app)) && split)
                    throw new IllegalStateException(
                        "El modo dividido cambió durante la prueba");
                Rect left=T1ClusterLayoutPolicy.geometry(
                    app,selected,false);
                Rect right=split?
                    T1ClusterLayoutPolicy.geometry(app,selected,true):null;
                String result=applyMeasured(app,selected.getDisplayId(),
                    split,left,right);
                deliver(app,result,result.startsWith("VERIFICADO"),done);
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
                deliver(app,"Redimensionamiento cancelado",false,done);
            }catch(Exception e){
                deliver(app,T1LocalAdbClient.connectivityHint(e),
                    false,done);
            }
        },"T1-Adb-Resize-Cluster").start();
    }
    private static boolean errorOutput(String s){
        String l=s.toLowerCase(java.util.Locale.ROOT);
        return l.contains("permission denial")||
            l.contains("securityexception")||
            l.contains("error:")||
            l.contains("exception:")||
            l.contains("unknown command");
    }
    private static String taskList(Context c)throws Exception{
        String result=T1LocalAdbClient.shell(c,"am stack list");
        if(errorOutput(result))throw new IllegalStateException(
            "Android rechazó listar tareas: "+result.substring(
                0,Math.min(140,result.length())));
        return result;
    }
    private static T1AdbTaskParser.Task find(Context c,String list,
                                              int display,String pkg)
            throws Exception {
        T1AdbTaskParser.Task t=T1AdbTaskParser.unique(list,display,pkg);
        if(t!=null)return t;
        // Android 11 vendor may omit task bounds in am stack list;
        // dumpsys fallback is READ-ONLY. Still reject task ID ambiguity.
        String dump=T1LocalAdbClient.shell(c,
            "dumpsys activity activities");
        return T1AdbTaskParser.unique(dump,display,pkg);
    }
    /** Actual service call is limited to Waze + Google Maps already
     * visible on selected secondary display. Never move a HOME/OEM task.
     */
    private static String applyMeasured(Context c,int display,boolean split,
                                        Rect left,Rect right)throws Exception{
        String before=taskList(c);
        T1AdbTaskParser.Task waze=find(c,before,display,WAZE);
        if(waze==null)
            return "NO APLICADO: tarea Waze no identificada de forma "+
                "única en Display "+display+". No tocaré tareas OEM.";
        T1AdbTaskParser.Task maps=split?
            find(c,before,display,MAPS):null;
        if(split&&(maps==null||maps.id==waze.id))
            return "NO APLICADO: tarea de Google Maps no está "+
                "identificada de forma única en Display "+display;
        String wazeCommand=command(waze.id,left);
        String changed=T1LocalAdbClient.shell(c,wazeCommand);
        if(errorOutput(changed))
            return "WAZE NO REDIMENSIONADO: "+
                changed.substring(0,Math.min(200,changed.length()));
        if(split){
            String changedMaps=T1LocalAdbClient.shell(c,
                command(maps.id,right));
            if(errorOutput(changedMaps))
                return "MAPS NO REDIMENSIONADO: "+
                    changedMaps.substring(0,
                        Math.min(200,changedMaps.length()))+
                    " · Waze puede haber cambiado parcialmente.";
        }
        // A request without shell error is NOT success: read task bounds.
        try{Thread.sleep(300L);}catch(InterruptedException ex){
            Thread.currentThread().interrupt();
        }
        String after=taskList(c);
        T1AdbTaskParser.Task wazeNow=find(c,after,display,WAZE);
        T1AdbTaskParser.Task mapsNow=split?find(c,after,display,MAPS):null;
        T1AdbTaskParser.Box wazeBox=box(left),mapsBox=split?box(right):null;
        boolean wazeOk=wazeNow!=null&&wazeNow.id==waze.id&&
            wazeNow.bounds!=null&&wazeNow.bounds.near(wazeBox,20);
        boolean mapsOk=!split||(mapsNow!=null&&mapsNow.id==maps.id&&
            mapsNow.bounds!=null&&mapsNow.bounds.near(mapsBox,20));
        if(wazeOk&&mapsOk)
            return "VERIFICADO POR ADB: Display "+display+
                " · Waze task "+waze.id+" "+wazeNow.bounds+
                (split?" · Google Maps task "+maps.id+
                    " "+mapsNow.bounds:"")+
                ". Mirá el tablero: esto NO prueba que ambas se vean.";
        return "SOLICITADO, NO VERIFICADO: Waze="+
            String.valueOf(wazeNow)+
            (split?" · Maps="+String.valueOf(mapsNow):"")+
            " · solicitado "+wazeBox+
            (split?" / "+mapsBox:"")+
            ". ROM ignoró geometría o task list no expone límites.";
    }
    private static T1AdbTaskParser.Box box(Rect r){
        return new T1AdbTaskParser.Box(r.left,r.top,r.right,r.bottom);
    }
    private static String command(int taskId,Rect r){
        if(taskId<=0||taskId>9999999||r.left<0||r.top<0||
           r.right<=r.left||r.bottom<=r.top||
           r.right>8192||r.bottom>8192)
            throw new IllegalArgumentException("Task/Rect inseguro");
        return "am task resize "+taskId+" "+
            r.left+" "+r.top+" "+r.right+" "+r.bottom;
    }
    private static void deliver(Context c,String message,boolean success,
                                Done done){
        T1ClusterWazeRouter.prefs(c).edit()
            .putString(RESULT,message)
            .putLong(VERIFIED_AT,success?System.currentTimeMillis():0L)
            .apply();
        MAIN.post(()->{if(done!=null)done.completed(message,success);});
    }
}
