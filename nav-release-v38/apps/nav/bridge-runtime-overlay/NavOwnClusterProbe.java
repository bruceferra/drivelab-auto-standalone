package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
import android.content.Intent;
import android.widget.Toast;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * NAV's OWN Cluster compositor: physical Jetour Display 1 Presentation,
 * NAV-owned VirtualDisplay, SurfaceTexture and TextureView.
 *
 * A manual 12-second in-place isolation test while NAV's central
 * Maps/Spotify virtual displays stay alive. It starts NO third-party app,
 * takes no input, and does not depend on any other launcher or renderer.
 *
 * Testing a third VD without launching Waze isolates whether display
 * creation (rather than activity launch) causes the LAB31 task regression.
 * DO NOT present this as live Waze or a completed Cluster implementation.
 */
final class NavOwnClusterProbe implements TextureView.SurfaceTextureListener {
    private static final int DPI=213;
    private static final int VD_FLAGS=
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private static final long DURATION_MS=12000L;
    private static final long WAZE_DURATION_MS=45000L;
    private static NavOwnClusterProbe active;
    private final Activity owner;
    private final long session;
    private boolean wazeTrial;
    private int ownDisplayId=-1;
    private volatile boolean launchInProgress,closeRequested,closingInProgress;
    private volatile String launchProblem;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private DisplayManager displays;
    private Presentation physicalLayer,scene;
    private TextureView texture;
    private VirtualDisplay ownVirtual;
    private Surface sink;
    private TextView marker;
    private int clusterWidth,clusterHeight,bandHeight,tick;
    private boolean ended;
    private boolean gotFrame;
    private volatile NavMosaicExitPolicy.Snapshot preflightTasks;
    private volatile boolean creationChecked;
    private final Runnable deadline=()->finishTrial(
        wazeTrial?"Prueba Waze NAV finalizada a los 45 segundos.":
        "Prueba de superficie NAV propia finalizada a los 12 segundos.");
    private final Runnable pulse=new Runnable(){
        @Override public void run(){
            if(ended||marker==null)return;
            tick++;
            marker.setText("DRIVELAB NAV · CLÚSTER PROPIO\n"+
                "SUPERFICIE INDEPENDIENTE\n"+
                "CONTADOR "+tick+"\n"+
                "SIN WAZE / SIN CAMBIOS DE TAREAS");
            marker.setBackgroundColor(tick%2==0?0xFF16465D:0xFF21605D);
            handler.postDelayed(this,650);
        }
    };

    private NavOwnClusterProbe(Activity owner,long token,boolean waze){
        this.owner=owner;session=token;wazeTrial=waze;
    }

    static void ask(Activity owner,long token){askInternal(owner,token,false);}
    static void askWaze(Activity owner,long token){askInternal(owner,token,true);}
    private static void askInternal(Activity owner,long token,boolean waze){
        if(active!=null){
            new AlertDialog.Builder(owner)
                .setTitle("CLÚSTER PROPIO · PRUEBA ACTIVA")
                .setMessage("Ya hay una superficie NAV en el tablero.")
                .setNegativeButton("VOLVER",null)
                .setPositiveButton("CERRAR PRUEBA",
                    (d,w)->releaseActive()).show();
            return;
        }
        NavMosaicSessionRegistry.Session current=
            NavMosaicSessionRegistry.snapshot(owner);
        if(current==null||current.token!=token){
            alert(owner,"CLÚSTER PROPIO · SIN MOSAICO",
                "Abrí primero Maps y Spotify en NAV.");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle(waze?"NAV · WAZE PROPIO · 45 SEGUNDOS":
                "NAV · CLÚSTER PROPIO · 12 SEGUNDOS")
            .setMessage(waze?
                "Sólo con la T1 ESTACIONADA. NAV abre Waze REAL "+
                "mediante ADB local en una tercera pantalla creada "+
                "POR NAV. Mantiene Maps y Spotify en sus displays. "+
                "Audita tareas antes/después; sin T1 Launcher, "+
                "sin volante, sin tocar OEM. Cierre automático a 45 s. "+
                "Si Waze ya está en otra pantalla NO se mueve. "+
                "La imagen real requiere verificación visual." :
                "Prueba con la T1 ESTACIONADA. Usa únicamente "+
                "el motor propio de NAV: una Presentation temporal "+
                "en Display 1 y su propia pantalla virtual. "+
                "NO abre Waze, NO transfiere Maps, NO cambia Spotify, "+
                "NO utiliza servicios externos ni toca el volante. "+
                "El indicador aparece solo en la franja inferior "+
                "y se retira a los 12 segundos. "+
                "¿Probar convivencia del tablero con el mosaico?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton(waze?"ABRIR WAZE PROPIO":
                "PROBAR CLÚSTER PROPIO",(d,w)->{
                NavMosaicSessionRegistry.Session now=
                    NavMosaicSessionRegistry.snapshot(owner);
                if(now==null||now.token!=token)return;
                NavOwnClusterProbe probe=new NavOwnClusterProbe(owner,token,waze);
                active=probe;
                probe.preflight();
            }).show();
    }
    static void releaseActive(){
        if(active!=null)active.finishTrial("Capa propia NAV liberada.");
    }
    static boolean hasActiveWazeTrial(){
        return active!=null&&!active.ended&&active.wazeTrial;
    }
    /** The only approved shell launch is tied to a LIVE NAV-created VD. */
    static boolean ownsLiveWazeDisplay(Context context,int id,long token){
        NavOwnClusterProbe a=active;
        if(a==null||a.ended||!a.wazeTrial||a.session!=token||
           a.ownVirtual==null||a.ownVirtual.getDisplay()==null||
           a.ownDisplayId!=id)return false;
        Display d=a.ownVirtual.getDisplay();
        if(d.getDisplayId()!=id||
           !"DriveLabNAVClusterOwn".equals(d.getName())||
           (d.getFlags()&Display.FLAG_PRIVATE)!=0)return false;
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        return live!=null&&live.token==token&&
            NavOwnClusterWazePolicy.ownedIds(
                id,live.displayId,live.mediaDisplayId)&&
            NavMosaicSessionRegistry.MAPS.equals(live.mapPackage);
    }

    /**
     * Confirm the already-running two central NAV tasks BEFORE attempting
     * another VirtualDisplay. No task is launched/moved during this probe.
     */
    private void preflight(){
        final Context app=owner.getApplicationContext();
        new Thread(()->{
            String error=null;
            NavMosaicExitPolicy.Snapshot verified=null;
            try{
                if(!current())throw new SecurityException("Sesión NAV vencida");
                NavMosaicSessionRegistry.Session live=
                    NavMosaicSessionRegistry.snapshot(app);
                if(live==null||live.token!=session)
                    throw new SecurityException("Sesión NAV no verificada");
                NavMosaicExitPolicy.Snapshot a=NavMosaicExitPolicy.parse(
                    BridgeLocalAdb.shell(app,"am stack list"),
                    live.displayId,live.mapPackage,live.mediaDisplayId);
                NavMosaicExitPolicy.Snapshot b=NavMosaicExitPolicy.parse(
                    BridgeLocalAdb.shell(app,"am stack list"),
                    live.displayId,live.mapPackage,live.mediaDisplayId);
                if(!a.same(b)||b.map==null||b.spotify==null||
                   (wazeTrial&&!NavMosaicSessionRegistry.MAPS.equals(
                       live.mapPackage)))
                    throw new SecurityException(
                        "Maps y Spotify NAV no son dos tareas estables");
                verified=b;
            }catch(Exception denied){
                error=denied.getClass().getSimpleName()+": "+
                    String.valueOf(denied.getMessage());
            }
            final String failure=error;
            final NavMosaicExitPolicy.Snapshot found=verified;
            owner.runOnUiThread(()->{
                if(ended||active!=this)return;
                if(failure!=null){
                    close("Inventario ADB previo inválido");
                    alert(owner,"CLÚSTER NAV · INICIO BLOQUEADO",
                        "No se creó otra pantalla: "+failure);
                    return;
                }
                preflightTasks=found;
                start();
            });
        },"NAV-Cluster-Own-Preflight").start();
    }
    private void compareCentralTasks(boolean afterClose){
        NavMosaicExitPolicy.Snapshot initial=preflightTasks;
        if(initial==null)return;
        final Context app=owner.getApplicationContext();
        final long token=session;
        new Thread(()->{
            String failure=null;
            try{
                NavMosaicSessionRegistry.Session live=
                    NavMosaicSessionRegistry.snapshot(app);
                if(live==null||live.token!=token)
                    throw new SecurityException("Sesión central cerrada");
                NavMosaicExitPolicy.Snapshot first=NavMosaicExitPolicy.parse(
                    BridgeLocalAdb.shell(app,"am stack list"),
                    live.displayId,live.mapPackage,live.mediaDisplayId);
                NavMosaicExitPolicy.Snapshot second=NavMosaicExitPolicy.parse(
                    BridgeLocalAdb.shell(app,"am stack list"),
                    live.displayId,live.mapPackage,live.mediaDisplayId);
                if(!first.same(second)||!initial.same(second))
                    throw new SecurityException(
                        "Maps o Spotify cambió de tarea/display/stack");
            }catch(Exception problem){
                failure=problem.getClass().getSimpleName()+": "+
                    String.valueOf(problem.getMessage());
            }
            final String error=failure;
            owner.runOnUiThread(()->{
                if(owner.isFinishing()||owner.isDestroyed())return;
                if(afterClose){
                    alert(owner,error==null?
                        "CLÚSTER NAV · TAREAS CENTRALES ESTABLES":
                        "CLÚSTER NAV · ALERTA DE REGRESIÓN",
                        error==null?
                        "Tras liberar la capa propia, Maps y Spotify "+
                        "conservan sus dos tareas y displays originales. "+
                        "Falta confirmar visualmente el tablero.":
                        "Android alteró o no pudo verificar el mosaico: "+
                        error+". No se abrió Waze.");
                }else if(!ended){
                    creationChecked=true;
                    if(error==null&&wazeTrial){
                        launchOwnWaze();
                    }
                    if(error!=null){
                        close("Regresión de tareas centrales detectada");
                        alert(owner,"CLÚSTER NAV · REGRESIÓN",
                            "Tras crear el display propio: "+error+
                            ". La capa de prueba se liberó.");
                    }
                }
            });
        },afterClose?
            "NAV-Cluster-Own-After":"NAV-Cluster-Own-During").start();
    }
    private void start(){
        try{
            if(ended||!current())throw new SecurityException(
                "La sesión propia de NAV cambió");
            displays=(DisplayManager)owner.getSystemService(
                Context.DISPLAY_SERVICE);
            if(displays==null)throw new IllegalStateException(
                "Android sin DisplayManager");
            Display hardware=displays.getDisplay(1);
            if(hardware==null||hardware.getDisplayId()!=1||
                hardware.getState()==Display.STATE_OFF||
                (hardware.getFlags()&Display.FLAG_PRIVATE)!=0)
                throw new SecurityException("Display 1 físico no autorizado");
            Point measured=new Point();
            hardware.getRealSize(measured);
            if(measured.x<1200||measured.y<480)
                throw new SecurityException(
                    "Geometría del tablero no reconocida");
            clusterWidth=measured.x;
            clusterHeight=measured.y;
            bandHeight=Math.round(clusterHeight*0.65f);
            physicalLayer=new Presentation(owner,hardware);
            FrameLayout host=new FrameLayout(physicalLayer.getContext());
            host.setBackgroundColor(Color.TRANSPARENT);
            texture=new TextureView(physicalLayer.getContext());
            texture.setOpaque(true);
            texture.setSurfaceTextureListener(this);
            host.addView(texture,new FrameLayout.LayoutParams(
                clusterWidth,bandHeight,Gravity.BOTTOM));
            physicalLayer.setContentView(host);
            configurePhysicalLayer();
            physicalLayer.setOnDismissListener(dialog->{
                if(!ended)handler.post(()->
                    close("Android retiró la Presentation propia de NAV"));
            });
            physicalLayer.show();
            configurePhysicalLayer();
            handler.postDelayed(deadline,wazeTrial?
                WAZE_DURATION_MS:DURATION_MS);
        }catch(Exception failed){
            close("Sin superficie propia NAV: "+
                failed.getClass().getSimpleName());
            alert(owner,"PRUEBA CLÚSTER PROPIO NO INICIADA",
                "Android rechazó la preparación: "+
                failed.getClass().getSimpleName()+
                ". Mosaico central sin comandos nuevos.");
        }
    }
    private boolean current(){
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(owner);
        return live!=null&&live.token==session;
    }
    private void configurePhysicalLayer(){
        Window w=physicalLayer==null?null:physicalLayer.getWindow();
        if(w==null)return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        w.setGravity(Gravity.TOP|Gravity.LEFT);
        w.setLayout(clusterWidth,clusterHeight);
    }
    @Override public void onSurfaceTextureAvailable(
            SurfaceTexture st,int width,int height){
        if(ended||ownVirtual!=null)return;
        try{
            if(!current())throw new SecurityException("NAV mosaico no vigente");
            st.setDefaultBufferSize(clusterWidth,bandHeight);
            sink=new Surface(st);
            ownVirtual=displays.createVirtualDisplay(
                "DriveLabNAVClusterOwn",clusterWidth,bandHeight,
                DPI,sink,VD_FLAGS);
            Display d=ownVirtual==null?null:ownVirtual.getDisplay();
            NavMosaicSessionRegistry.Session nav=
                NavMosaicSessionRegistry.snapshot(owner);
            if(d==null||nav==null||nav.token!=session||
               d.getDisplayId()<2||d.getDisplayId()>99||
               d.getDisplayId()==nav.displayId||
               d.getDisplayId()==nav.mediaDisplayId||
               (d.getFlags()&Display.FLAG_PRIVATE)!=0)
                throw new SecurityException("Display NAV propio no aislado");
            ownDisplayId=d.getDisplayId();
            if(wazeTrial){
                // Keep physical Presentation and TextureView, but NOT a
                // same-display Scene Presentation that would cover Waze.
                compareCentralTasks(false);
                return;
            }
            scene=new Presentation(owner,d);
            FrameLayout root=new FrameLayout(scene.getContext());
            root.setBackgroundColor(0xFF16465D);
            marker=new TextView(scene.getContext());
            marker.setGravity(Gravity.CENTER);
            marker.setTextColor(Color.WHITE);
            marker.setTextSize(18);
            root.addView(marker,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
            scene.setContentView(root);
            scene.show();
            handler.post(pulse);
            compareCentralTasks(false);
        }catch(Exception fail){
            close("Android no pudo crear compositor propio: "+
                fail.getClass().getSimpleName());
            alert(owner,"CLÚSTER NAV · SIN SUPERFICIE",
                "No se pudo iniciar la capa NAV: "+
                fail.getClass().getSimpleName());
        }
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){
        if(ended||gotFrame)return;
        gotFrame=true;
        // A frame only confirms renderer transport, NEVER OEM visibility.
    }
    @Override public void onSurfaceTextureSizeChanged(
        SurfaceTexture st,int width,int height){}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){
        finishTrial("Superficie independiente de NAV destruida.");
        return true;
    }
    /** No application start until the two central tasks and this own VD
     * each pass two independent ADB inventory reads.
     */
    private void launchOwnWaze(){
        if(ended||!wazeTrial||launchInProgress||closeRequested)return;
        launchInProgress=true;
        final Context app=owner.getApplicationContext();
        final int vid=ownDisplayId;
        final long nonce=session;
        new Thread(()->{
            String error=null;int taskId=-1;
            try{
                if(!ownsLiveWazeDisplay(app,vid,nonce))
                    throw new SecurityException("La pantalla NAV cambió");
                NavMosaicSessionRegistry.Session live=
                    NavMosaicSessionRegistry.snapshot(app);
                NavOwnClusterWazePolicy.State first=
                    NavOwnClusterWazePolicy.inspect(
                        BridgeLocalAdb.shell(app,"am stack list"),
                        vid,live.displayId,live.mediaDisplayId);
                NavOwnClusterWazePolicy.State second=
                    NavOwnClusterWazePolicy.inspect(
                        BridgeLocalAdb.shell(app,"am stack list"),
                        vid,live.displayId,live.mediaDisplayId);
                if(!first.same(second)||second.task!=-1)
                    throw new SecurityException(
                        "Waze ya está activo o la pantalla NAV no está vacía");
                Intent target=app.getPackageManager()
                    .getLaunchIntentForPackage("com.waze");
                if(target==null||target.getComponent()==null||
                   !"com.waze".equals(target.getComponent().getPackageName()))
                    throw new IllegalStateException("Waze no tiene Activity instalada");
                String component=target.getComponent().flattenToString();
                String reply=BridgeLocalAdb.startOwnClusterWaze(
                    app,vid,component,nonce);
                if(!NavOwnClusterWazePolicy.accepted(reply))
                    throw new SecurityException(
                        "Android no aceptó iniciar Waze en NAV");
                NavOwnClusterWazePolicy.State after1=
                    NavOwnClusterWazePolicy.inspect(
                        BridgeLocalAdb.shell(app,"am stack list"),
                        vid,live.displayId,live.mediaDisplayId);
                NavOwnClusterWazePolicy.State after2=
                    NavOwnClusterWazePolicy.inspect(
                        BridgeLocalAdb.shell(app,"am stack list"),
                        vid,live.displayId,live.mediaDisplayId);
                if(!after1.same(after2)||after2.task<=10)
                    throw new SecurityException(
                        "Android no confirmó Waze en la pantalla NAV propia");
                NavMosaicExitPolicy.Snapshot c1=NavMosaicExitPolicy.parse(
                    BridgeLocalAdb.shell(app,"am stack list"),
                    live.displayId,live.mapPackage,live.mediaDisplayId);
                NavMosaicExitPolicy.Snapshot c2=NavMosaicExitPolicy.parse(
                    BridgeLocalAdb.shell(app,"am stack list"),
                    live.displayId,live.mapPackage,live.mediaDisplayId);
                if(!c1.same(c2)||!preflightTasks.same(c2))
                    throw new SecurityException(
                        "ALERTA: Maps o Spotify cambió al lanzar Waze");
                taskId=after2.task;
            }catch(Exception failed){
                error=failed.getClass().getSimpleName()+": "+
                    String.valueOf(failed.getMessage());
            }
            final String failure=error;final int launchedId=taskId;
            owner.runOnUiThread(()->{
                launchInProgress=false;
                if(ended)return;
                if(failure!=null){
                    launchProblem=failure;
                    finishTrial("Waze NAV no confirmado");
                }else if(closeRequested){
                    finishTrial("Cierre de Waze NAV solicitado");
                }else{
                    Toast.makeText(owner,
                        "Waze tarea "+launchedId+" en pantalla NAV "+vid+
                        ". Verificá visualmente el clúster.",
                        Toast.LENGTH_LONG).show();
                }
            });
        },"NAV-Cluster-Waze-Own-Launch").start();
    }
    /**
     * Finish ONLY Waze task on the still-owned VirtualDisplay BEFORE
     * removing its Surface. Never use global force-stop or move-stack.
     */
    private void finishTrial(String reason){
        if(ended)return;
        if(!wazeTrial){close(reason);return;}
        closeRequested=true;
        if(launchInProgress||closingInProgress)return;
        if(ownVirtual==null||ownDisplayId<2){
            close(reason);return;
        }
        closingInProgress=true;
        final Context app=owner.getApplicationContext();
        final int vid=ownDisplayId;
        final NavMosaicExitPolicy.Snapshot central=preflightTasks;
        final long nonce=session;
        new Thread(()->{
            String problem=null;
            try{
                if(central==null)throw new SecurityException(
                    "Sin identidad central original");
                NavMosaicSessionRegistry.Session live=
                    NavMosaicSessionRegistry.snapshot(app);
                int map=central.map.displayId,media=central.spotify.displayId;
                if(!NavOwnClusterWazePolicy.ownedIds(vid,map,media)||
                   ownVirtual==null||ownVirtual.getDisplay()==null||
                   ownVirtual.getDisplay().getDisplayId()!=vid||
                   !"DriveLabNAVClusterOwn".equals(
                       ownVirtual.getDisplay().getName()))
                    throw new SecurityException("Pantalla NAV ya no es nuestra");
                // If central session was already closed, its IDs are from
                // the immutable preflight snapshot; never target 0/1.
                NavOwnClusterWazePolicy.State a=
                    NavOwnClusterWazePolicy.inspect(
                        BridgeLocalAdb.shell(app,"am stack list"),
                        vid,map,media);
                NavOwnClusterWazePolicy.State b=
                    NavOwnClusterWazePolicy.inspect(
                        BridgeLocalAdb.shell(app,"am stack list"),
                        vid,map,media);
                if(!a.same(b))
                    throw new SecurityException("Tarea Waze inestable al cerrar");
                if(b.task>10){
                    String cmd=NavOwnClusterWazePolicy.stopCommand(b,vid,map,media);
                    NavOwnClusterWazePolicy.State justBefore=
                        NavOwnClusterWazePolicy.inspect(
                            BridgeLocalAdb.shell(app,"am stack list"),
                            vid,map,media);
                    if(!b.same(justBefore))
                        throw new SecurityException(
                            "Waze cambió de tarea antes del cierre");
                    String result=BridgeLocalAdb.shell(app,cmd);
                    if(NavMosaicExitPolicy.rejected(result))
                        throw new SecurityException(
                            "Android rechazó cerrar tarea Waze de NAV");
                    NavOwnClusterWazePolicy.State after=
                        NavOwnClusterWazePolicy.inspect(
                            BridgeLocalAdb.shell(app,"am stack list"),
                            vid,map,media);
                    if(!after.valid||after.task!=-1)
                        throw new SecurityException(
                            "No se confirmó el cierre de Waze NAV");
                }
            }catch(Exception issue){
                problem=issue.getClass().getSimpleName()+": "+
                    String.valueOf(issue.getMessage());
            }
            final String failure=problem;
            owner.runOnUiThread(()->{
                if(ended)return;
                close(reason);
                if(!owner.isFinishing()&&!owner.isDestroyed()&&
                   (failure!=null||launchProblem!=null)){
                    alert(owner,"WAZE NAV · RESULTADO DE PRUEBA",
                        (launchProblem==null?"":launchProblem+"\n")+
                        (failure==null?"Cierre del display propio confirmado.":
                         "No se pudo verificar el cierre exacto de Waze: "+
                         failure)+
                        "\nNo se detuvo ninguna aplicación globalmente.");
                }
            });
        },"NAV-Cluster-Waze-Own-Cleanup").start();
    }
    private void close(String why){
        if(ended)return;
        ended=true;
        final boolean hadOwnDisplay=ownVirtual!=null;
        handler.removeCallbacks(deadline);
        handler.removeCallbacks(pulse);
        marker=null;
        if(scene!=null){
            try{scene.dismiss();}catch(RuntimeException ignored){}
            scene=null;
        }
        if(ownVirtual!=null){
            try{ownVirtual.release();}catch(RuntimeException ignored){}
            ownVirtual=null;
        }
        if(sink!=null){sink.release();sink=null;}
        if(physicalLayer!=null){
            try{physicalLayer.dismiss();}catch(RuntimeException ignored){}
            physicalLayer=null;
        }
        if(active==this)active=null;
        if(hadOwnDisplay&&preflightTasks!=null)
            compareCentralTasks(true);
        // Never clear the central map/Spotify registry or their tasks.
    }
    private static void alert(Activity owner,String title,String message){
        if(owner.isFinishing()||owner.isDestroyed())return;
        new AlertDialog.Builder(owner).setTitle(title)
            .setMessage(message).setPositiveButton("ENTENDIDO",null).show();
    }
}
