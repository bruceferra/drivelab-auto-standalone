package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owner-triggered, READ-ONLY real T1 task inventory. Runs exactly two
 * whitelisted 'am stack list' requests over already-authorized local ADB.
 * No starts, virtual displays, task manipulation, permission writes or
 * logging of raw dump (which can contain navigation information).
 */
final class NavClusterReadOnlyProbe {
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private NavClusterReadOnlyProbe(){}

    static void ask(Activity owner,int navMapId,int navSpotifyId){
        if(navMapId<2||navSpotifyId<2||navMapId==navSpotifyId){
            message(owner,"SIN MOSAICO ACTIVO",
                "Primero abrí Maps y Spotify en NAV. Nada cambió.");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("CLUSTER · IDENTIFICAR WAZE / MAPS")
            .setMessage("Lectura de tareas reales por ADB LOCAL, sólo " +
                "con la Jetour estacionada. No abre Waze ni Maps, " +
                "no mueve aplicaciones, no modifica el Cluster " +
                "ni activa el volante. El resultado no se guarda. " +
                "¿Leer las sesiones actuales?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("LEER TAREAS",(d,w)->
                inspect(owner,navMapId,navSpotifyId))
            .show();
    }
    private static void inspect(Activity owner,int mapId,int mediaId){
        if(!BUSY.compareAndSet(false,true)){
            message(owner,"DIAGNÓSTICO OCUPADO","Ya hay una lectura ADB activa.");
            return;
        }
        Context app=owner.getApplicationContext();
        new Thread(()->{
            String result;
            try{
                NavMosaicSessionRegistry.Session live=
                    NavMosaicSessionRegistry.snapshot(app);
                if(live==null||live.displayId!=mapId||
                   live.mediaDisplayId!=mediaId)
                    throw new SecurityException("El mosaico cambió; no se leyó ADB");
                String first=BridgeLocalAdb.shell(app,"am stack list");
                NavClusterTaskInventory.Snapshot one=
                    NavClusterTaskInventory.parse(first,mapId,mediaId);
                if(!one.valid)
                    throw new SecurityException(one.reason);
                String second=BridgeLocalAdb.shell(app,"am stack list");
                NavClusterTaskInventory.Snapshot two=
                    NavClusterTaskInventory.parse(second,mapId,mediaId);
                if(!one.same(two))
                    throw new SecurityException(
                        "Las tareas cambiaron entre las dos lecturas; "+
                        "no se identificó una sesión segura");
                live=NavMosaicSessionRegistry.snapshot(app);
                if(live==null||live.displayId!=mapId||
                   live.mediaDisplayId!=mediaId)
                    throw new SecurityException("El mosaico se cerró");
                result=report(app,two);
            }catch(Exception fail){
                result="SIN CAMBIOS: diagnóstico no confirmado ( "+
                    fail.getClass().getSimpleName()+" ). "+
                    "No se abrió Waze, no se movió ninguna tarea. "+
                    "Comprobá ADB local y el mosaico activo.";
            }finally{BUSY.set(false);}
            final String text=result;
            owner.runOnUiThread(()->message(owner,
                "CLUSTER · INVENTARIO SOLO LECTURA",text));
        },"NAV-Cluster-Task-Inventory-ReadOnly").start();
    }
    private static String report(Context app,
            NavClusterTaskInventory.Snapshot snapshot){
        List<NavClusterTaskInventory.Task> maps=
            snapshot.external(NavClusterTaskInventory.MAPS);
        List<NavClusterTaskInventory.Task> waze=
            snapshot.external(NavClusterTaskInventory.WAZE);
        StringBuilder b=new StringBuilder();
        b.append("Mosaico NAV sin cambios. Lectura ADB doble y estable.\n\n");
        b.append("WAZE fuera de NAV: ").append(waze.size())
         .append(" tarea(s) virtual(es) aislada(s).\n");
        for(NavClusterTaskInventory.Task t:waze)
            add(b,app,t);
        b.append("\nMAPS fuera de NAV: ").append(maps.size())
         .append(" tarea(s) virtual(es) aislada(s).\n");
        for(NavClusterTaskInventory.Task t:maps)
            add(b,app,t);
        b.append("\nTotal Waze en todos los displays: ")
         .append(snapshot.count(NavClusterTaskInventory.WAZE));
        b.append("\nTotal Maps en todos los displays: ")
         .append(snapshot.count(NavClusterTaskInventory.MAPS));
        if(waze.size()==1){
            NavClusterTaskInventory.Task target=waze.get(0);
            b.append("\n\nCANDIDATO WAZE: display ").append(target.displayId)
             .append(" / task ").append(target.taskId).append(". ");
            b.append("Esto NO acredita que sea T1Launcher ni que se vea ");
            b.append("en el Cluster. No hay entrega ni lanzamiento.");
        }else if(waze.size()>1){
            b.append("\n\nAMBIGUO: varias sesiones Waze externas. ");
            b.append("Prohibido seleccionar una automáticamente.");
        }else{
            b.append("\n\nNo se encontró una tarea Waze virtual externa ");
            b.append("aislada. No se iniciará otra sesión.");
        }
        b.append("\n\nPantallas físicas 0/1 nunca son destinos de NAV.");
        return b.toString();
    }
    private static void add(StringBuilder b,Context app,
            NavClusterTaskInventory.Task task){
        String name="desconocido";
        DisplayManager dm=(DisplayManager)app.getSystemService(
            Context.DISPLAY_SERVICE);
        Display d=dm==null?null:dm.getDisplay(task.displayId);
        if(d!=null&&d.getName()!=null){
            // Display name is informative only, NEVER an ownership proof.
            String n=d.getName().replaceAll("[^a-zA-Z0-9_ .-]","?");
            name=n.substring(0,Math.min(n.length(),50));
        }
        b.append(" • display ").append(task.displayId)
         .append(" (").append(name).append(")")
         .append(", pila ").append(task.stackId)
         .append(", tarea ").append(task.taskId).append("\n");
    }
    private static void message(Activity owner,String title,String body){
        if(owner.isFinishing()||owner.isDestroyed())return;
        new AlertDialog.Builder(owner)
            .setTitle(title).setMessage(body)
            .setPositiveButton("ENTENDIDO",null).show();
    }
}
