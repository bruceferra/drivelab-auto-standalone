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

/** Opt-in daily center-display mosaic with saved layout and 2/3 user apps.
 * Uses the Freeform mode-5 launch + task resize proven on Jetour T1.
 * Existing unrelated fullscreen tasks are NEVER resized or removed.
 * Tasks created by this function may be resized again when user reapplies.
 * No HOME/OEM/cluster/system/global-settings operations.
 */
final class T1CenterSavedFreeformMosaic {
    interface Done {void completed(String result);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String PREFS="drivelab_center_managed_freeform_v1";
    private static final String SELF=
        "ar.com.rango5gps.drivelabauto.receiver";
    private T1CenterSavedFreeformMosaic(){}

    static void apply(Context context,String[] packages,
            T1CenterMosaicGeometry.Tile[] tiles,int count,Done done){
        Context app=context.getApplicationContext();
        String[] requested=packages.clone();
        new Thread(()->{
            String result=perform(app,requested,tiles,count);
            MAIN.post(()->done.completed(result));
        },"DriveLab-Saved-Freeform-Display0").start();
    }
    private static String compact(String value){
        if(value==null||value.trim().isEmpty())return "sin texto";
        String s=value.trim().replace('\r',' ').replace('\n',' ');
        return s.substring(0,Math.min(180,s.length()));
    }
    private static ComponentName selectedLauncher(Context c,String pkg)
            throws Exception{
        // A second, independent task shows the original DriveLab Cockpit.
        // All external apps remain subject to PackageManager/OEM checking.
        if(SELF.equals(pkg))
            return new ComponentName(c,ReceiverActivity.class);
        if(!T1CenterUserPackagePolicy.allowed(pkg))
            throw new SecurityException(
                "No se permiten aplicaciones del sistema/OEM: "+pkg);
        PackageManager pm=c.getPackageManager();
        ApplicationInfo info=pm.getApplicationInfo(pkg,0);
        if((info.flags&(ApplicationInfo.FLAG_SYSTEM|
            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
            throw new SecurityException("App del sistema no permitida: "+pkg);
        Intent launcher=pm.getLaunchIntentForPackage(pkg);
        ComponentName component=launcher==null?null:launcher.getComponent();
        if(component==null||!pkg.equals(component.getPackageName()))
            throw new IllegalStateException("No encontré launcher de "+pkg);
        return component;
    }
    private static List<T1AdbTaskParser.Task> tasks(Context c,String pkg)
            throws Exception{
        return T1AdbTaskParser.tasksOnCenter(
            SELF.equals(pkg)?T1LocalAdbClient.shellOwnReceiverTasks(c):
                T1LocalAdbClient.shellApprovedAppTasks(c,pkg),pkg);
    }
    private static Set<Integer> ids(List<T1AdbTaskParser.Task> tasks){
        Set<Integer> out=new HashSet<>();
        for(T1AdbTaskParser.Task task:tasks)out.add(task.id);
        return out;
    }
    private static T1AdbTaskParser.Task exact(Context c,int taskId,
            String pkg)throws Exception{
        String raw=T1LocalAdbClient.shellTaskExcerpt(
            c,"am stack list",taskId);
        T1AdbTaskParser.Task found=T1AdbTaskParser.exactCenter(
            raw,taskId,pkg);
        if(found==null||found.id!=taskId||found.display!=0||
            found.bounds==null)
            throw new IllegalStateException("ID "+taskId+
                " no pertenece a "+pkg+" en Display 0");
        String mode=T1LocalAdbClient.shellTaskExcerpt(
            c,"dumpsys activity activities",taskId);
        if(!T1CenterWindowModePolicy.isFreeform(mode,taskId,pkg))
            throw new IllegalStateException(
                "La tarea "+taskId+" no está en Freeform");
        if(SELF.equals(pkg)&&
            !raw.contains("T1PremiumCockpitActivity"))
            throw new IllegalStateException(
                "La tarea "+taskId+" no corresponde a un segundo Cockpit");
        return found;
    }
    private static int managedId(SharedPreferences prefs,String pkg){
        return prefs.getInt("task_"+pkg,-1);
    }
    private static T1AdbTaskParser.Task managed(Context c,
            SharedPreferences prefs,String pkg)throws Exception{
        int id=managedId(prefs,pkg);
        if(id<=0)return null;
        boolean present=false;
        for(T1AdbTaskParser.Task t:tasks(c,pkg))
            if(t.id==id){present=true;break;}
        if(!present)return null;
        try{return exact(c,id,pkg);}
        catch(Exception e){return null;}
    }
    private static T1AdbTaskParser.Task create(Context c,
            SharedPreferences prefs,String pkg,ComponentName launcher)
            throws Exception{
        Set<Integer> previous=ids(tasks(c,pkg));
        String output=SELF.equals(pkg)?
            T1LocalAdbClient.shellStartOwnCockpitFreeform(c):
            T1LocalAdbClient.shellStartCenterUserFreeform(c,launcher);
        if(T1CenterWindowModePolicy.shellRejected(output))
            throw new IllegalStateException(
                "ADB rechazó Freeform: "+compact(output));
        Thread.sleep(SELF.equals(pkg)?1450L:850L);
        T1AdbTaskParser.Task fresh=null;
        for(T1AdbTaskParser.Task t:tasks(c,pkg)){
            if(previous.contains(t.id))continue;
            if(fresh!=null)throw new IllegalStateException(
                "Dos tareas nuevas ambiguas: "+pkg);
            fresh=t;
        }
        if(fresh==null||fresh.bounds==null||fresh.display!=0)
            throw new IllegalStateException(
                "Android reutilizó tarea anterior o no dio ID nuevo: "+pkg);
        T1AdbTaskParser.Task checked=exact(c,fresh.id,pkg);
        // Record ONLY an app task allocated through this explicit flow.
        prefs.edit().putInt("task_"+pkg,checked.id).apply();
        return checked;
    }
    private static boolean visible(Context c,int id,String pkg)
            throws Exception{
        String dump=T1LocalAdbClient.shellTaskExcerpt(
            c,"dumpsys activity activities",id);
        String tag="#"+id;
        for(String line:dump.split("\\r?\\n")){
            int index=line.indexOf(tag);
            if(index<0)continue;
            int after=index+tag.length();
            if(after<line.length()&&
                Character.isDigit(line.charAt(after)))continue;
            if(line.contains(pkg)&&line.contains("visible=true"))
                return true;
        }
        return false;
    }
    private static void resize(Context c,int task,String pkg,
            T1CenterMosaicGeometry.Tile tile)throws Exception{
        if(tile==null||tile.left<0||tile.top<0||
            tile.right<=tile.left+100||tile.bottom<=tile.top+100||
            tile.right>4096||tile.bottom>4096)
            throw new IllegalArgumentException("Bounds fuera de Display 0");
        exact(c,task,pkg);
        String command="am task resize "+task+" "+tile.bounds();
        String out=T1LocalAdbClient.shell(c,command);
        if(T1CenterWindowModePolicy.shellRejected(out))
            throw new IllegalStateException("ADB resize "+pkg+
                " rechazado: "+compact(out));
    }
    private static String perform(Context c,String[] packages,
            T1CenterMosaicGeometry.Tile[] tiles,int count){
        StringBuilder out=new StringBuilder(
            "MOSAICO CONFIGURABLE · DISPLAY 0\n"+
            "Freeform 5 + tareas creadas por DriveLab.\n");
        try{
            if(count<2||count>3||tiles==null||
                packages.length<count||tiles.length<count)
                throw new IllegalArgumentException("Elegí 2 o 3 paneles");
            HashSet<String> distinct=new HashSet<>();
            ComponentName[] starters=new ComponentName[count];
            for(int i=0;i<count;i++){
                String pkg=packages[i];
                if(!distinct.add(pkg))
                    throw new IllegalStateException(
                        "Elegí aplicaciones diferentes por panel");
                starters[i]=selectedLauncher(c,pkg);
            }
            SharedPreferences managed=c.getSharedPreferences(
                PREFS,Context.MODE_PRIVATE);
            int[] taskIds=new int[count];
            for(int i=0;i<count;i++){
                String pkg=packages[i];
                T1AdbTaskParser.Task t=managed(c,managed,pkg);
                String origin="YA CREADA";
                if(t==null){
                    t=create(c,managed,pkg,starters[i]);
                    origin="NUEVA";
                }
                taskIds[i]=t.id;
                resize(c,t.id,pkg,tiles[i]);
                Thread.sleep(300L);
                t=exact(c,t.id,pkg);
                boolean ok=T1CenterFreeformGeometry.tile(t.bounds,tiles[i]);
                out.append("Panel ").append(i+1).append(" · ")
                    .append(pkg).append(" · ").append(origin)
                    .append(" · tarea ").append(t.id)
                    .append(" · REAL ").append(t.bounds)
                    .append(" · geometría ").append(ok?"OK":"DIFERENTE")
                    .append('\n');
                if(!ok)throw new IllegalStateException(
                    "El panel "+(i+1)+" no respetó su tamaño; "+
                    "se detiene sin modificar otras tareas");
            }
            Thread.sleep(600L);
            boolean allVisible=true,allSized=true;
            int commonOffset=Integer.MIN_VALUE;
            for(int i=0;i<count;i++){
                T1AdbTaskParser.Task t=exact(c,taskIds[i],packages[i]);
                boolean sized=T1CenterFreeformGeometry.tile(
                    t.bounds,tiles[i]);
                allSized&=sized;
                allVisible&=visible(c,t.id,packages[i]);
                int offset=t.bounds.top-tiles[i].top;
                if(commonOffset==Integer.MIN_VALUE)commonOffset=offset;
                else if(Math.abs(offset-commonOffset)>30)allSized=false;
            }
            out.append("Geometrías ").append(allSized?"SÍ":"NO")
                .append(" · Todos visibles ")
                .append(allVisible?"SÍ":"NO").append('\n');
            out.append(allSized&&allVisible?
                "ÉXITO: mosaico configurable verificado en Display 0.":
                "PARCIAL: revisá tamaños y visibilidad; "+
                "no se confirma mosaico completo.");
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            out.append("Interrumpido.");
        }catch(Exception ex){
            out.append("NO CONFIRMADO: ").append(compact(
                ex.getClass().getSimpleName()+": "+ex.getMessage()));
        }
        out.append("\nNo se modificaron tareas antiguas, HOME, "+
            "controles OEM ni Display 1. "+
            "Podés volver a DriveLab desde su icono.");
        return out.toString();
    }
}
