package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit, parked owner-requested experiment. Reuse ONLY a T1Launcher
 * VirtualDisplay already named "T1Mapa". Never create a third NAV display,
 * redirect the center map, move tasks, or touch OEM/physical cluster 0/1.
 *
 * The T1Launcher renderer is still required and controls visibility. ADB
 * task creation is not proof of cluster rendering or user input.
 */
final class NavExistingT1MapaWaze {
    private static final String WAZE="com.waze";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private NavExistingT1MapaWaze(){}

    static void ask(Activity owner,long token){
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(owner);
        if(live==null||live.token!=token){
            message(owner,"WAZE · SIN SESIÓN NAV",
                "Primero abrí el mosaico Maps + Spotify.");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("WAZE CLÚSTER · USAR T1MAPA EXISTENTE")
            .setMessage("Sólo con la T1 ESTACIONADA. Esta prueba busca "+
                "la pantalla virtual T1Mapa ya creada por T1Launcher "+
                "y solicita abrir Waze allí mediante ADB local. "+
                "NO crea otra pantalla virtual ni cambia Maps/Spotify "+
                "de NAV. Si T1Launcher no está ejecutando su módulo "+
                "de mapas, NO abrirá Waze. La visibilidad del clúster "+
                "debe verificarse físicamente. ¿Probar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR WAZE EN T1MAPA",
                (d,w)->run(owner,token)).show();
    }
    static int existingT1Mapa(Context app,int map,int spotify){
        DisplayManager manager=(DisplayManager)
            app.getSystemService(Context.DISPLAY_SERVICE);
        if(manager==null)return -1;
        int target=-1;
        for(Display d:manager.getDisplays()){
            if(d==null||!"T1Mapa".equals(d.getName()))continue;
            int id=d.getDisplayId();
            if(id<2||id>99||id==map||id==spotify||
               (d.getFlags()&Display.FLAG_PRIVATE)!=0||
               d.getState()==Display.STATE_OFF)
                return -1;
            if(target!=-1)return -1;
            target=id;
        }
        return target;
    }
    static boolean ownsVirtualDisplay(Context app,int id,long token){
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(app);
        return live!=null&&live.token==token&&
            existingT1Mapa(app,live.displayId,live.mediaDisplayId)==id;
    }
    private static void run(Activity owner,long token){
        if(!BUSY.compareAndSet(false,true)){
            message(owner,"WAZE · PRUEBA ACTIVA",
                "Ya hay una solicitud al clúster en ejecución.");
            return;
        }
        Context app=owner.getApplicationContext();
        new Thread(()->{
            String result;
            try{
                NavMosaicSessionRegistry.Session nav=
                    NavMosaicSessionRegistry.snapshot(app);
                if(nav==null||nav.token!=token)
                    throw new SecurityException("La sesión NAV ya no existe");
                int target=existingT1Mapa(app,nav.displayId,nav.mediaDisplayId);
                if(target<2)
                    throw new IllegalStateException(
                        "NO existe T1Mapa accesible. Activá primero "+
                        "el módulo de mapas de T1Launcher; no se "+
                        "creó otra pantalla ni se abrió Waze.");
                Intent intent=app.getPackageManager()
                    .getLaunchIntentForPackage(WAZE);
                if(intent==null||intent.getComponent()==null||
                   !WAZE.equals(intent.getComponent().getPackageName()))
                    throw new IllegalStateException(
                        "Waze no está instalado o no tiene Activity de inicio");
                String component=intent.getComponent().flattenToString();
                String report1=BridgeLocalAdb.shell(app,"am stack list");
                String report2=BridgeLocalAdb.shell(app,"am stack list");
                NavMosaicExitPolicy.Snapshot nav1=
                    NavMosaicExitPolicy.parse(report1,nav.displayId,
                        nav.mapPackage,nav.mediaDisplayId);
                NavMosaicExitPolicy.Snapshot nav2=
                    NavMosaicExitPolicy.parse(report2,nav.displayId,
                        nav.mapPackage,nav.mediaDisplayId);
                NavExistingT1MapaPolicy.State t1a=
                    NavExistingT1MapaPolicy.parse(report1,target,
                        nav.displayId,nav.mediaDisplayId);
                NavExistingT1MapaPolicy.State t1b=
                    NavExistingT1MapaPolicy.parse(report2,target,
                        nav.displayId,nav.mediaDisplayId);
                if(!nav1.same(nav2)||nav2.map==null||nav2.spotify==null||
                   !t1a.same(t1b))
                    throw new SecurityException(
                        "Mosaico NAV o T1Mapa inestable; no se abrió Waze");
                if(t1b.wazeTask>0){
                    result="Waze YA TIENE tarea "+t1b.wazeTask+
                        " en T1Mapa display "+target+
                        ". No se inició otra sesión. Verificá el clúster.";
                }else{
                    // Final fail-closed identity guard is repeated by the
                    // narrowly scoped ADB method before the command.
                    String output=BridgeLocalAdb.startWazeInExistingT1Mapa(
                        app,target,component,token);
                    if(!NavExistingT1MapaPolicy.accepted(output))
                        throw new SecurityException(
                            "Android no confirmó inicio de Waze en T1Mapa");
                    String after1=BridgeLocalAdb.shell(app,"am stack list");
                    String after2=BridgeLocalAdb.shell(app,"am stack list");
                    NavMosaicExitPolicy.Snapshot navAfter1=
                        NavMosaicExitPolicy.parse(after1,nav.displayId,
                            nav.mapPackage,nav.mediaDisplayId);
                    NavMosaicExitPolicy.Snapshot navAfter2=
                        NavMosaicExitPolicy.parse(after2,nav.displayId,
                            nav.mapPackage,nav.mediaDisplayId);
                    NavExistingT1MapaPolicy.State t1After1=
                        NavExistingT1MapaPolicy.parse(after1,target,
                            nav.displayId,nav.mediaDisplayId);
                    NavExistingT1MapaPolicy.State t1After2=
                        NavExistingT1MapaPolicy.parse(after2,target,
                            nav.displayId,nav.mediaDisplayId);
                    if(!navAfter1.same(navAfter2)||
                       !nav2.same(navAfter2))
                        throw new SecurityException(
                            "ALERTA: cambiaron tareas Maps/Spotify NAV "+
                            "tras abrir Waze. Comprobá ambos paneles.");
                    if(!t1After1.same(t1After2)||
                       t1After2.wazeTask<=10)
                        throw new SecurityException(
                            "Android aceptó el inicio, pero NO confirmó "+
                            "una tarea Waze estable en T1Mapa.");
                    result="Waze iniciado como tarea "+
                        t1After2.wazeTask+" en T1Mapa display "+target+
                        ". Maps y Spotify NAV mantienen sus tareas. "+
                        "IMPORTANTE: sólo la foto del clúster confirma "+
                        "que Waze es visible; no se modificó el volante.";
                }
            }catch(Exception problem){
                result="NO SE CONFIRMÓ WAZE EN CLÚSTER: "+
                    problem.getClass().getSimpleName()+": "+
                    String.valueOf(problem.getMessage())+
                    ". No se movió Maps ni se creó pantalla adicional.";
            }finally{BUSY.set(false);}
            final String report=result;
            owner.runOnUiThread(()->message(owner,
                "RESULTADO · WAZE EN T1MAPA",report));
        },"NAV-T1Mapa-Waze-Manual").start();
    }
    private static void message(Activity a,String title,String text){
        if(a.isFinishing()||a.isDestroyed())return;
        new AlertDialog.Builder(a).setTitle(title).setMessage(text)
            .setPositiveButton("ENTENDIDO",null).show();
    }
}
