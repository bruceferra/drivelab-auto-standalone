package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Parked-only PROOF of the same independent compositor pipeline used by
 * T1Launcher: VirtualDisplay -> SurfaceTexture -> TextureView hosted by a
 * Presentation on PHYSICAL Display 1.
 *
 * Draws only a tiny diagnostic test pattern; NEVER a map, never fullscreen.
 * Does not modify/cover OEM windows, SystemUI, critical warnings or task
 * stacks. Foreground owner action ONLY, automatic release after 15 seconds,
 * onStop and display removal. A received frame proves transport, NOT Z-order:
 * the owner must confirm whether the small pattern appeared over MODE.
 */
public final class BridgeClusterSurfaceProbeActivity extends Activity
        implements TextureView.SurfaceTextureListener {
    private static final int WIDTH=480,HEIGHT=240,DPI=213;
    private static final long MAX_MS=15000L;
    private static final String PREFS="bridge_display1_surface_probe_v1";
    private static final int FLAGS=
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->stop("PRUEBA FINALIZADA "+
        "AUTOMÁTICAMENTE tras 15 segundos. Capa liberada.");
    private DisplayManager displays;
    private Display physical;
    private Presentation cluster,pattern;
    private TextureView receiver;
    private VirtualDisplay virtual;
    private Surface surface;
    private TextView status;
    private boolean closed,frame;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        BridgeImmersive.apply(this);
        LinearLayout body=new LinearLayout(this);
        body.setPadding(24,22,24,24);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(0xFF101C27);
        TextView title=new TextView(this);
        title.setText("BRIDGE · COMPROBAR CAPA REAL EN DISPLAY 1");
        title.setTextSize(19); title.setTextColor(0xFF42E3D0);
        body.addView(title);
        TextView instructions=new TextView(this);
        instructions.setText("SÓLO CON EL VEHÍCULO ESTACIONADO. "+
            "Mostrará durante 15 segundos un recuadro pequeño de prueba "+
            "en el centro de Display 1, sin tocar los controles ni el "+
            "firmware Jetour. NO es Waze. NO cubre todo el tablero. "+
            "Fijate si aparece por delante de Radio/Bluetooth. "+
            "Se libera automáticamente al salir o al terminar.");
        instructions.setTextColor(Color.WHITE);
        instructions.setTextSize(15);
        instructions.setPadding(0,18,0,20);
        body.addView(instructions);
        status=new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setText("Preparando prueba sin modificar el tablero…");
        body.addView(status);
        Button close=new Button(this);
        close.setAllCaps(false);
        close.setText("TERMINAR AHORA · LIBERAR CAPA");
        close.setOnClickListener(v->{stop("Cerrada manualmente.");finish();});
        body.addView(close);
        setContentView(body);

        // Owner has already confirmed that the Jetour is parked in
        // BootActivity. Never run from a receiver, boot or background.
        main.postDelayed(timeout,MAX_MS);
        main.post(this::startProbe);
    }

    private void report(String message){
        if(status!=null)status.setText(message);
        getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("last_result",message).apply();
    }
    static String lastResult(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString("last_result","Sin prueba de Presentation en Display 1");
    }
    private void startProbe(){
        if(closed)return;
        try{
            displays=(DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
            if(displays==null)throw new IllegalStateException(
                "DisplayManager no disponible");
            physical=displays.getDisplay(1);
            if(physical==null||physical.getDisplayId()==Display.DEFAULT_DISPLAY||
               physical.getState()==Display.STATE_OFF)
                throw new IllegalStateException(
                    "No está disponible el Display 1 físico");
            if((physical.getFlags()&Display.FLAG_PRIVATE)!=0)
                throw new SecurityException(
                    "Display 1 pertenece al sistema y es privado");
            Point p=new Point();physical.getRealSize(p);
            if(p.x<1200||p.y<480)
                throw new IllegalStateException(
                    "Pantalla no coincide con la del tablero");
            // Presentation uses Android's own TYPE_PRESENTATION for the
            // real Display 1. Never request overlay/system window types.
            cluster=new Presentation(this,physical);
            LinearLayout host=new LinearLayout(cluster.getContext());
            host.setBackgroundColor(Color.TRANSPARENT);
            host.setGravity(Gravity.CENTER);
            receiver=new TextureView(cluster.getContext());
            receiver.setOpaque(true);
            receiver.setSurfaceTextureListener(this);
            host.addView(receiver,new LinearLayout.LayoutParams(
                WIDTH,HEIGHT));
            cluster.setContentView(host);
            configureWindow(cluster);
            cluster.setOnDismissListener(dialog->{
                if(!closed)main.post(()->stop(
                    "Android retiró la Presentation del tablero"));
            });
            cluster.show();
            // Stay small even on OEM builds that reset Dialog layout
            // when a presentation's window first attaches.
            configureWindow(cluster);
            report("Presentation real solicitada en Display 1. "+
                "Creando TextureView/VirtualDisplay…");
        }catch(Exception issue){
            stop("NO SE PUDO PRESENTAR EN DISPLAY 1: "+
                issue.getClass().getSimpleName()+
                ". Jetour puede restringir sus capas OEM.");
        }
    }
    private static void configureWindow(Presentation p){
        Window window=p.getWindow();
        if(window==null)return;
        window.setBackgroundDrawableResource(
            android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setGravity(Gravity.CENTER);
        window.setLayout(WIDTH,HEIGHT);
    }
    @Override public void onSurfaceTextureAvailable(
            SurfaceTexture texture,int width,int height){
        if(closed||virtual!=null)return;
        try{
            texture.setDefaultBufferSize(WIDTH,HEIGHT);
            surface=new Surface(texture);
            virtual=displays.createVirtualDisplay("BridgeDisplay1LayerProbe",
                WIDTH,HEIGHT,DPI,surface,FLAGS);
            if(virtual==null||virtual.getDisplay()==null)
                throw new IllegalStateException("VirtualDisplay no creado");
            Display d=virtual.getDisplay();
            if(d.getDisplayId()==0||d.getDisplayId()==1||
               (d.getFlags()&Display.FLAG_PRIVATE)==0)
                throw new SecurityException(
                    "Pantalla virtual no está aislada");
            pattern=new Presentation(this,d);
            LinearLayout scene=new LinearLayout(pattern.getContext());
            scene.setOrientation(LinearLayout.VERTICAL);
            scene.setGravity(Gravity.CENTER);
            scene.setBackgroundColor(0xFF145E70);
            TextView label=new TextView(pattern.getContext());
            label.setText("BRIDGE · CAPA DE PRUEBA\n15 s · NO ES WAZE");
            label.setGravity(Gravity.CENTER);
            label.setTextColor(Color.WHITE);label.setTextSize(19);
            scene.addView(label);
            pattern.setContentView(scene);
            pattern.show();
            report("Presentaciones real y virtual creadas; "+
                "esperando cuadro. Comprobá si se ve el recuadro "+
                "sobre Radio/Bluetooth.");
        }catch(Exception issue){
            stop("Jetour bloqueó la superficie virtual: "+
                issue.getClass().getSimpleName());
        }
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){
        if(closed||frame)return;
        frame=true;
        report("CUADRO VIRTUAL RECIBIDO EN DISPLAY 1. "+
            "La GPU entregó píxeles, pero sólo VOS podés "+
            "confirmar si Radio/Bluetooth quedó detrás.");
    }
    @Override public void onSurfaceTextureSizeChanged(
        SurfaceTexture texture,int width,int height){}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
        stop("Superficie cerrada; prueba terminada.");
        return true;
    }
    private void stop(String message){
        if(closed)return;
        closed=true;
        main.removeCallbacks(timeout);
        if(pattern!=null){
            try{pattern.dismiss();}catch(Exception ignored){}
            pattern=null;
        }
        if(virtual!=null){try{virtual.release();}catch(Exception ignored){}
            virtual=null;}
        if(surface!=null){surface.release();surface=null;}
        if(cluster!=null){
            try{cluster.dismiss();}catch(Exception ignored){}
            cluster=null;
        }
        // TextureView owns the SurfaceTexture. Do not release it here.
        report(message);
    }
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);
        if(focus)BridgeImmersive.apply(this);
    }
    @Override protected void onStop(){
        stop("Prueba finalizada al salir. No quedan capas de Bridge.");
        super.onStop();
    }
    @Override protected void onDestroy(){
        stop("Activity cerrada; no quedan capas de Bridge.");
        super.onDestroy();
    }
}
