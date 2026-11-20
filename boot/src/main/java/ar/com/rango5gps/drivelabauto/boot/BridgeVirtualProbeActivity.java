package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Isolated first-stage T1N145 architecture probe. It draws ONLY our own
 * test pattern: VirtualDisplay -> SurfaceTexture -> TextureView, while
 * Presentation renders in that private virtual display.
 *
 * No map is started; no Display 1/OEM layer is touched; no Receiver or ADB
 * is used. Owner must explicitly open this while PARKED. Fail closed.
 *
 * This proves surface delivery, NOT Waze task migration, Android's display
 * permissions for third-party apps, or real cluster visibility.
 */
public final class BridgeVirtualProbeActivity extends Activity
        implements TextureView.SurfaceTextureListener {
    private static final int WIDTH=960,HEIGHT=540,DENSITY_DPI=213;
    private static final long MAX_SESSION_MS=120000L;
    private static final String PREFS="bridge_virtual_probe_diagnostic_v1";
    private static final int FLAGS=
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable expiry=()->stopProbe(
        "Prueba terminada automáticamente a los 2 minutos.");
    private TextureView preview;
    private TextView state;
    private VirtualDisplay virtualDisplay;
    private Surface sink;
    private Presentation presentation;
    private boolean closed;
    private boolean receivedFrame;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22,20,22,22);
        root.setBackgroundColor(0xFF101B25);
        TextView heading=new TextView(this);
        heading.setText("BRIDGE · PRUEBA DE PANTALLA VIRTUAL");
        heading.setTextSize(22);
        heading.setTextColor(0xFF32DBD5);
        root.addView(heading);
        TextView warning=new TextView(this);
        warning.setText("Solo con el vehículo estacionado. " +
            "El patrón se genera dentro de Bridge y se muestra aquí, " +
            "en la pantalla CENTRAL. No se envían mapas ni contenido " +
            "a Display 1, no se abre DriveLab Auto y no se controla el vehículo.");
        warning.setTextSize(15);
        warning.setTextColor(Color.WHITE);
        warning.setPadding(0,12,0,18);
        root.addView(warning);
        preview=new TextureView(this);
        preview.setOpaque(true);
        preview.setSurfaceTextureListener(this);
        root.addView(preview,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,440));
        state=new TextView(this);
        state.setTextSize(15);
        state.setTextColor(Color.WHITE);
        state.setPadding(0,18,0,15);
        root.addView(state);
        Button close=new Button(this);
        close.setText("CERRAR PRUEBA · LIBERAR PANTALLA VIRTUAL");
        close.setAllCaps(false);
        close.setOnClickListener(v->{
            stopProbe("Prueba cerrada por el propietario.");
            finish();
        });
        root.addView(close);
        setContentView(root);
        report("Esperando textura Android · no se creó ninguna pantalla.");
    }

    private void report(String text){
        if(state!=null)state.setText(text);
        getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("last_result",text).apply();
    }

    @Override public void onSurfaceTextureAvailable(
            SurfaceTexture texture,int width,int height){
        if(closed||virtualDisplay!=null)return;
        try{
            texture.setDefaultBufferSize(WIDTH,HEIGHT);
            sink=new Surface(texture);
            DisplayManager manager=(DisplayManager)
                getSystemService(Context.DISPLAY_SERVICE);
            if(manager==null)throw new IllegalStateException("DisplayManager no disponible");
            // Snapshot before creation: Android may assign ID 1 to a
            // private virtual display on devices with no physical second
            // screen. Never infer physical/vIRTUAL identity from number.
            Display[] existing=manager.getDisplays();
            virtualDisplay=manager.createVirtualDisplay(
                "DriveLabBridgeProbe",WIDTH,HEIGHT,DENSITY_DPI,sink,FLAGS);
            if(virtualDisplay==null||virtualDisplay.getDisplay()==null)
                throw new IllegalStateException("Android negó VirtualDisplay");
            Display target=virtualDisplay.getDisplay();
            if(target.getDisplayId()==Display.DEFAULT_DISPLAY||
               (target.getFlags()&Display.FLAG_PRIVATE)==0)
                throw new IllegalStateException(
                    "Pantalla virtual no aislada: "+target.getDisplayId());
            for(Display previous:existing)
                if(previous.getDisplayId()==target.getDisplayId())
                    throw new IllegalStateException(
                        "Pantalla existente no autorizada: "+target.getDisplayId());

            presentation=new Presentation(this,target);
            LinearLayout scene=new LinearLayout(this);
            scene.setGravity(Gravity.CENTER);
            scene.setOrientation(LinearLayout.VERTICAL);
            scene.setBackgroundColor(0xFF153447);
            TextView mark=new TextView(this);
            mark.setText("DRIVELAB BRIDGE\nVIRTUAL DISPLAY DE PRUEBA");
            mark.setGravity(Gravity.CENTER);
            mark.setTextSize(28);
            mark.setTextColor(0xFF31E9D3);
            scene.addView(mark);
            TextView note=new TextView(this);
            note.setText("Contenido local · 960 × 540\nNO es Waze ni el tablero OEM");
            note.setTextSize(17);
            note.setGravity(Gravity.CENTER);
            note.setTextColor(Color.WHITE);
            scene.addView(note);
            presentation.setContentView(scene);
            presentation.show();
            report("Pantalla virtual privada ID "+target.getDisplayId()+
                " y Presentation creadas. Esperando primer cuadro real…");
            main.postDelayed(expiry,MAX_SESSION_MS);
        }catch(RuntimeException error){
            String message="Android no permitió la prueba: "+
                error.getClass().getSimpleName()+". No se abrió ningún mapa.";
            stopProbe(message);
        }
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){
        if(closed||receivedFrame||virtualDisplay==null)return;
        receivedFrame=true;
        report("CUADRO RECIBIDO: Display virtual privado → Surface → "+
            "TextureView en Display 0. Verificá el patrón en pantalla. "+
            "La visibilidad en Display 1 sigue NO PROBADA.");
    }

    @Override public void onSurfaceTextureSizeChanged(
            SurfaceTexture texture,int width,int height){
        // Only the preview rectangle changes; the virtual display and
        // its presentation keep their original resolution and session.
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
        stopProbe("Superficie cerrada. Recursos de Bridge liberados.");
        return true; // TextureView owns and frees SurfaceTexture itself.
    }

    private void stopProbe(String message){
        if(closed)return;
        closed=true;
        main.removeCallbacks(expiry);
        if(presentation!=null){
            try{presentation.dismiss();}catch(RuntimeException ignored){}
            presentation=null;
        }
        if(virtualDisplay!=null){
            try{virtualDisplay.release();}catch(RuntimeException ignored){}
            virtualDisplay=null;
        }
        if(sink!=null){sink.release();sink=null;}
        report(message);
        if(preview!=null)preview.setVisibility(android.view.View.GONE);
    }

    @Override protected void onStop(){
        // Never leave a virtual display alive behind another app or while
        // the owner may resume driving.
        stopProbe("Prueba cerrada al salir de la pantalla.");
        super.onStop();
    }

    @Override protected void onDestroy(){
        stopProbe("Actividad destruida. Sin pantalla virtual activa.");
        super.onDestroy();
    }
}
