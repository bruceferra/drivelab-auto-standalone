package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
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
    private static NavOwnClusterProbe active;
    private final Activity owner;
    private final long session;
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
    private final Runnable deadline=()->close(
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

    private NavOwnClusterProbe(Activity owner,long token){
        this.owner=owner;session=token;
    }

    static void ask(Activity owner,long token){
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
            .setTitle("NAV · CLÚSTER PROPIO · 12 SEGUNDOS")
            .setMessage("Prueba con la T1 ESTACIONADA. Usa únicamente "+
                "el motor propio de NAV: una Presentation temporal "+
                "en Display 1 y su propia pantalla virtual. "+
                "NO abre Waze, NO transfiere Maps, NO cambia Spotify, "+
                "NO utiliza servicios externos ni toca el volante. "+
                "El indicador aparece solo en la franja inferior "+
                "y se retira a los 12 segundos. "+
                "¿Probar convivencia del tablero con el mosaico?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR CLÚSTER PROPIO",(d,w)->{
                NavMosaicSessionRegistry.Session now=
                    NavMosaicSessionRegistry.snapshot(owner);
                if(now==null||now.token!=token)return;
                NavOwnClusterProbe probe=new NavOwnClusterProbe(owner,token);
                active=probe;
                probe.preflight();
            }).show();
    }
    static void releaseActive(){
        if(active!=null)active.close("Capa de prueba NAV liberada.");
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
                if(!a.same(b)||b.map==null||b.spotify==null)
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
            handler.postDelayed(deadline,DURATION_MS);
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
        close("Superficie independiente de NAV destruida.");
        return true;
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
