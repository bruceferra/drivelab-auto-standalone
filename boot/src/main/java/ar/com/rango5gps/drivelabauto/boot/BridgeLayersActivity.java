package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Jetour firmware display-layer vs Waze/Maps Display 1 forensic panel, ON CENTRAL DISPLAY.
 * Read only. An owner captures first when OEM MODE covers maps, again after
 * THEY reproduce the previously observed manual MAP SEND that makes the
 * map visible. MODE by itself does not show maps. The app NEVER
 * synthesizes steering keys, alters OEM windows or kills processes.
 */
public final class BridgeLayersActivity extends Activity {
    private static final int BG=0xFF0B1D2B,ACCENT=0xFF00A3B8,
        PANEL=0xFF142F40,WHITE=Color.WHITE;
    private BridgeModeLayerDiff.Snapshot covered,visible;
    private TextView result,savedStatus;
    private boolean busy;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        BridgeImmersive.apply(this);
        setTitle("DriveLab Bridge · Capas de Display 1");
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(22,18,22,24);
        scroll.addView(body);
        addText(body,"DRIVELAB BRIDGE",24,WHITE);
        addText(body,"DIAGNÓSTICO DE CAPAS · TABLERO (DISPLAY 1)",18,ACCENT);
        addText(body,
            "La interfaz nativa del tablero Jetour tapa a veces Waze/Maps. "+
            "Vamos a comparar dos estados del compositor sin abrir/cerrar "+
            "ninguna app ni tocar el firmware.",15,WHITE);
        addText(body,
            "CAMINO CORRECTO: con el vehículo estacionado, capturá "+
            "TABLERO ORIGINAL (1). Volvé a Bridge, mostrale Waze al "+
            "tablero, reabrí este diagnóstico y capturá MAPA VISIBLE "+
            "(2). AMBAS CAPTURAS SE GUARDAN aunque salgas de esta "+
            "pantalla o reinicies Bridge. Después tocá COMPARAR.",13,0xFFBFD1DA);
        savedStatus=addText(body,"CARGANDO CAPTURAS GUARDADAS…",
            15,0xFF54DCD5);
        button(body,"1 · CAPTURAR CUANDO JETOUR TAPA MAPAS",()->
            confirm("TAPADO"));
        button(body,"2 · CAPTURAR TRAS EL ENVÍO QUE MOSTRÓ MAPAS",()->
            confirm("VISIBLE"));
        button(body,"COMPARAR AMBAS CAPTURAS",this::compare);
        button(body,"BORRAR CAPTURAS GUARDADAS",this::askClear);
        result=addText(body,"Sin lecturas. La clave ADB de Bridge "+
            "se autoriza por separado, sólo si Android lo solicita.",
            14,WHITE);
        result.setTextIsSelectable(true);
        result.setBackgroundColor(PANEL);
        button(body,"VOLVER A BRIDGE",this::finish);
        setContentView(scroll);
        restoreCaptures();
    }
    @Override protected void onResume(){
        super.onResume();
        if(savedStatus!=null)restoreCaptures();
    }
    private static String stamp(long at){
        if(at<=0)return "sin hora";
        return DateFormat.getDateTimeInstance(
            DateFormat.SHORT,DateFormat.SHORT,
            new Locale("es","AR")).format(new Date(at));
    }
    private void restoreCaptures(){
        covered=BridgeLayerCaptureStore.load(this,"TAPADO");
        visible=BridgeLayerCaptureStore.load(this,"VISIBLE");
        showSavedStatus();
    }
    private void showSavedStatus(){
        if(savedStatus==null)return;
        String first=covered==null?"FALTA":
            "GUARDADA · "+stamp(BridgeLayerCaptureStore.timestamp(this,"TAPADO"));
        String second=visible==null?"FALTA":
            "GUARDADA · "+stamp(BridgeLayerCaptureStore.timestamp(this,"VISIBLE"));
        savedStatus.setText("1 · TABLERO ORIGINAL: "+first+
            "\n2 · MAPA VISIBLE: "+second+
            "\n"+(covered!=null&&visible!=null?
                "LISTO PARA COMPARAR. Tocá COMPARAR AMBAS CAPTURAS.":
                "Podés salir y volver; las capturas se conservan."));
    }
    private void askClear(){
        if(busy)return;
        new AlertDialog.Builder(this)
            .setTitle("BORRAR SÓLO LAS DOS CAPTURAS")
            .setMessage("Se borrarán únicamente estos diagnósticos "+
                "locales de capas. NO se modifica el mapa, QR, "+
                "rutas, volante ni configuraciones del vehículo.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("BORRAR",(d,w)->{
                if(!BridgeLayerCaptureStore.clear(this)){
                    result.setText("Android no permitió borrar las capturas.");
                    return;
                }
                restoreCaptures();
                result.setText("Capturas locales borradas; "+
                    "Bridge y los mapas no se modificaron.");
            }).show();
    }

    private static TextView addText(LinearLayout parent,String text,
                                    int size,int color){
        TextView t=new TextView(parent.getContext());
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(12,14,12,14);
        parent.addView(t);
        return t;
    }
    private void button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(ACCENT));
        parent.addView(b);
        b.setOnClickListener(v->action.run());
    }
    private void confirm(String phase){
        if(busy)return;
        new AlertDialog.Builder(this)
            .setTitle(phase.equals("TAPADO")?
                "CAPTURA A · JETOUR ENCIMA":"CAPTURA B · MAPAS VISIBLES")
            .setMessage("Confirmá que el vehículo está estacionado y "+
                "Display 1 está en el estado indicado. "+
                "Sólo se leerán ventanas, tareas y superficies. "+
                "No se modificará nada del vehículo.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CAPTURAR",(d,w)->capture(phase))
            .show();
    }
    private void capture(String phase){
        if(busy)return;
        busy=true;
        result.setText("LEYENDO "+phase+" · WindowManager, "+
            "ActivityManager y SurfaceFlinger · sin cambios");
        new Thread(()->{
            String windows=null,activities=null,sf=null,errors="";
            try{windows=BridgeAdbReadOnly.read(this,"dumpsys window windows");}
            catch(Exception ex){
                errors+="\nWM: "+ex.getClass().getSimpleName()+
                    " · "+String.valueOf(ex.getMessage());
            }
            try{activities=BridgeAdbReadOnly.read(
                this,"dumpsys activity activities");}
            catch(Exception ex){errors+="\nACT: "+ex.getClass().getSimpleName();}
            try{sf=BridgeAdbReadOnly.read(this,"dumpsys SurfaceFlinger");}
            catch(Exception ex){errors+="\nSF: "+ex.getClass().getSimpleName();}
            if(windows==null||windows.trim().isEmpty()||
               windows.startsWith("Permission Denial")||
               windows.startsWith("Error:")){
                final String failure="CAPTURA NO DISPONIBLE: WindowManager "+
                    "no permitió leer Display 1. "+errors+
                    "\nNo se guardó una captura falsa ni se cambió ninguna ventana.";
                runOnUiThread(()->{busy=false;result.setText(failure);});
                return;
            }
            BridgeModeLayerDiff.Snapshot state=
                BridgeModeLayerDiff.summarize(phase,windows,activities,sf);
            // Commit the sanitized capture to disk BEFORE leaving Activity.
            boolean saved=BridgeLayerCaptureStore.save(
                getApplicationContext(),state);
            String message=state.report()+errors;
            runOnUiThread(()->{
                busy=false;
                restoreCaptures();
                if(!saved){
                    result.setText("CAPTURA LEÍDA PERO NO GUARDADA. "+
                        "Android rechazó persistir el diagnóstico. "+
                        "No sigas con la segunda captura.\n\n"+message);
                    return;
                }
                result.setText("CAPTURA "+phase+" GUARDADA. "+
                    "Podés salir y volver sin perderla.\n\n"+
                    message+
                    "\n\nNo se ejecutaron acciones sobre ninguna ventana.");
            });
        },"Bridge-Jetour-MODE-Read-Only").start();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)BridgeImmersive.apply(this);
    }
    private void compare(){
        if(busy)return;
        // The user MUST leave the panel to show Waze on Display 1.
        restoreCaptures();
        result.setText(BridgeModeLayerDiff.compare(covered,visible)+
            "\n\n"+(covered==null?"":covered.report())+
            "\n\n"+(visible==null?"":visible.report()));
    }
}