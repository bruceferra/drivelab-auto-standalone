package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicit opt-in for Waze-only steering key / route alternatives experiment. */
public final class T1WheelSettingsActivity extends T1ImmersiveActivity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView report,modeState,bridgeState,eventState;
    private Button selectionButton,zoomButton,bridgeOn,bridgeOff,panelA,panelB,genericControls;
    private boolean visible;
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            SharedPreferences p=prefs();
            refreshModes();
            report.setText(T1WazeWheelService.describe(
                T1WheelSettingsActivity.this)+
                "\n\nCONTROL DE LA APP: "+
                (p.getBoolean(T1WazeWheelService.ENABLED,false)?
                    "ACTIVO":"DESACTIVADO")+
                "\n\nFOCO PANTALLA: "+
                (hasWindowFocus()?"DRIVELAB VISIBLE":"VENTANA OEM ENCIMA")+
                "\nUn código sólo aparece si Android manda un KeyEvent "+
                "a DriveLab o Accesibilidad. El aviso 'Zoom' del "+
                "T1Launcher NO prueba que recibamos ese evento.");
            ui.postDelayed(this,750);
        }
    };
    private SharedPreferences prefs(){
        return getSharedPreferences(T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
    }
    /** Fixed indicators independent of the scrolling diagnostic report. */
    private void refreshModes(){
        if(modeState==null)return;
        SharedPreferences p=prefs();
        boolean zoom=p.getBoolean(T1WazeWheelService.ZOOM_MAP,false);
        boolean on=p.getBoolean(T1WazeWheelService.ENABLED,false);
        boolean link=p.getBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,false);
        boolean connected=T1WazeWheelService.isServiceConnected();
        modeState.setText("OBJETIVO: "+T1WheelTarget.name(this)+" · "+
            T1WheelTarget.application(this)+
            "\nMODO RUEDITA ACTUAL: "+
            (zoom?"ZOOM":"SELECCIÓN DE BOTONES"));
        if(panelA!=null)T1JetourTheme.button(panelA,
            T1WheelTarget.panelB(this)?T1JetourTheme.PANEL_HI:T1JetourTheme.GREEN_BUTTON);
        if(panelB!=null)T1JetourTheme.button(panelB,
            T1WheelTarget.panelB(this)?T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI);
        T1JetourTheme.button(selectionButton,zoom?
            T1JetourTheme.PANEL_HI:T1JetourTheme.GREEN_BUTTON);
        T1JetourTheme.button(zoomButton,zoom?
            T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI);
        bridgeState.setText("CONTROL "+T1WheelTarget.name(this)+" "+(on?"CONFIGURADO":"APAGADO")+
            " · PUENTE OEM "+(link?"HABILITADO":"APAGADO")+
            "\nACCESIBILIDAD: "+(connected?"CONECTADA":"SIN CONEXIÓN")+
            "\n"+T1WheelAccessibilityRecovery.permissionState(this)+
            "\n"+p.getString(T1WazeWheelService.OEM_BINDER_STATUS,
                "Sin conexión"));
        // A preference alone never paints a connected/working bridge green.
        T1JetourTheme.button(bridgeOn,on&&link&&connected?
            T1JetourTheme.GREEN_BUTTON:on&&link?
            T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI);
        T1JetourTheme.button(bridgeOff,!link?
            T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI);
        if(genericControls!=null){
            boolean other=T1WheelTarget.genericCandidate(this);
            genericControls.setVisibility(other?View.VISIBLE:View.GONE);
            genericControls.setText(T1WheelTarget.genericEnabled(this)?
                "DESACTIVAR CONTROLES · "+T1WheelTarget.application(this):
                "ACTIVAR CONTROLES · "+T1WheelTarget.application(this));
            T1JetourTheme.button(genericControls,
                T1WheelTarget.genericEnabled(this)?
                    T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI);
        }
        eventState.setText("ÚLTIMA TECLA: "+
            p.getString(T1WazeWheelService.OEM_LAST,"ninguna")+
            "\nÚLTIMA ACCIÓN: "+
            p.getString(T1WazeWheelService.LAST_STATUS,"ninguna")+
            "\nRECUPERACIÓN ACCESIBILIDAD: "+
            T1WheelAccessibilityRecovery.describe(this));
    }
    private TextView text(LinearLayout col,String title,int size){
        TextView t=new TextView(this);
        t.setText(title);t.setTextSize(size);
        t.setTextColor(T1JetourTheme.WHITE);
        t.setPadding(12,10,12,10);
        col.addView(t);return t;
    }
    private Button button(LinearLayout col,String name,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);
        T1JetourTheme.button(b,T1JetourTheme.PANEL_HI);
        b.setOnClickListener(v->action.run());
        col.addView(b,new LinearLayout.LayoutParams(-1,57));
        return b;
    }
    private void openAccessibilitySettings(){
        Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if(intent.resolveActivity(getPackageManager())==null){
            report.setText("La T1 no expone Ajustes de accesibilidad. "+
                "Si Android quitó el permiso, un botón de DriveLab NO "+
                "puede concedérselo sin intervención del propietario. "+
                "Usá WebADB. También sirve el ADB local ya autorizado.");
            return;
        }
        try{startActivity(intent);}
        catch(Exception ex){
            report.setText("Ajustes no disponibles: "+
                ex.getClass().getSimpleName()+
                ". El servicio requiere autorización Android.");
        }
    }
    private void learning(LinearLayout col,String label,String action){
        button(col,"APRENDER TECLA · "+label,()->{
            T1WazeWheelService.learn(this,action);
            report.setText("LISTO: "+label+
                " · Girá o pulsá la rueda AQUÍ MISMO. "+
                "No abras Waze ni salgas de DriveLab. Tenés 45 segundos. "+
                "Si aparece Zoom del vehículo y ningún código, "+
                "verificaremos el canal OEM por ADB.");
        });
    }
    // The base shell defaults to white for historical screens, while this
    // diagnostics page uses WHITE text. Keep the entire screen (including
    // the fixed header shell and empty scroll areas) on the Jetour dark palette.
    @Override protected int overlayClearBackgroundColor(){
        return T1JetourTheme.NIGHT;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        // Discard diagnostics left by the retired Nov17 wheel ADB fallback.
        // They must never appear as new attempts after it was disabled.
        prefs().edit().remove("adb_wheel_fallback_status").apply();
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(T1JetourTheme.NIGHT);
        scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(T1JetourTheme.NIGHT);
        body.setPadding(16,12,16,30);
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        text(body,"VOLANTE · WAZE / RUTAS ALTERNATIVAS",19);
        text(body,"PRUEBA DIRECTA: quedate en esta misma pantalla. "+
            "Tocá APRENDER TECLA y girá la ruedita sin abrir Waze. "+
            "Registramos una única tecla Android si la ROM la entrega; "+
            "no bloqueamos la acción Zoom ni otras funciones originales. "+
            "Si T1Launcher toma el mando antes, necesitamos investigar "+
            "sus eventos OEM. Probá únicamente estacionado.",13);
        report=text(body,"LEYENDO DIAGNÓSTICO DEL VOLANTE…",16);
        button(body,"VER ESTADO DEL SERVICIO ANDROID",()->{
            report.setText(T1WazeWheelService.describe(this));
        });
        button(body,"INSPECCIONAR CONTROLES · PANEL ACTIVO",()->{
            T1WazeWheelService.requestScan(this);
            report.setText("Inspección solicitada: aparecerá el Display seleccionado "+
                "y las ventanas Waze/Maps que realmente expone Android. "+
                "No toca botones ni modifica rutas.");
        });
        button(body,"RECONECTAR ACCESIBILIDAD · RUEDITA",()->{
            String reconnectMessage=T1WheelAccessibilityRecovery.requestManual(this);
            report.setText("RECONECTAR · "+reconnectMessage+
                "\n"+T1WheelAccessibilityRecovery.describe(this)+
                "\nEl estado se actualiza automáticamente abajo. "+
                "Si Android desactivó el permiso, tocá ABRIR AJUSTES.");
            // Do not claim success until Android invokes onServiceConnected.
            refreshModes();
        });
        button(body,"ABRIR AJUSTES DE ACCESIBILIDAD (SI EXISTEN)",()->{
            openAccessibilitySettings();
        });
        modeState=text(body,"MODO RUEDITA ACTUAL: SELECCIÓN DE BOTONES",20);
        panelA=button(body,"CONTROLAR PANEL A",()->{
            T1WheelTarget.select(this,false);refreshModes();
        });
        panelB=button(body,"CONTROLAR PANEL B",()->{
            T1WheelTarget.select(this,true);refreshModes();
        });
        text(body,"Los paneles usan la app que elegiste en CLÚSTER. "+
            "Waze y Google Maps mantienen sus perfiles probados. "+
            "Las otras apps requieren activación individual.",13);
        genericControls=button(body,"CONFIGURAR CONTROLES · OTRA APP",()->{
            if(!T1WheelTarget.genericCandidate(this))return;
            boolean next=!T1WheelTarget.genericEnabled(this);
            T1WheelTarget.setGenericEnabled(this,next);
            T1WazeWheelService.requestScan(this);
            report.setText(next?
                "Controles de "+T1WheelTarget.application(this)+
                " habilitados. Tocá INSPECCIONAR CONTROLES para "+
                "detectar sus menús y botones. Usá OK solo tras "+
                "seleccionar una opción con la rueda.":
                "Controles de esta aplicación desactivados, sin "+
                "modificar las configuraciones de Waze ni Google Maps.");
            refreshModes();
        });
        selectionButton=button(body,"SELECCIÓN DE BOTONES",()->{
            prefs().edit().putBoolean(T1WazeWheelService.ZOOM_MAP,false).apply();
            refreshModes();
        });
        zoomButton=button(body,"ZOOM DEL MAPA",()->{
            prefs().edit().putBoolean(T1WazeWheelService.ZOOM_MAP,true).apply();
            refreshModes();
        });
        text(body,"SELECCIÓN: solo botones internos de Waze; sin menú ni "+
            "botón útil no se selecciona nada. Maximizar/Cerrar de la "+
            "ventana no son botones de Waze.",13);
        bridgeState=text(body,"PUENTE OEM: consultando",16);
        bridgeOn=button(body,"ACTIVAR VOLANTE OEM → WAZE",()->{
            // ON is idempotent; touching twice does not disconnect Binder.
            prefs().edit().putBoolean(T1WazeWheelService.ENABLED,true)
                .putBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,true)
                .putString(T1WazeWheelService.OEM_BINDER_STATUS,
                    "Solicitado; esperando códigos de botones físicos")
                .apply();
            refreshModes();
        });
        bridgeOff=button(body,"DESACTIVAR VOLANTE OEM",()->{
            prefs().edit().putBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,false)
                .putString(T1WazeWheelService.OEM_BINDER_STATUS,
                    "Desconectado por usuario").apply();
            refreshModes();
        });
        eventState=text(body,"ÚLTIMA TECLA: ninguna",14);
        text(body,"La rueda OEM ya tiene sus cuatro códigos aprendidos "+
            "automáticamente (1000 / 1001 / 1002 / 1003). "+
            "No es necesario tocar APRENDER TECLA para Waze. "+
            "Desactivá VOLANTE → MAPA en T1Launcher durante la prueba "+
            "para evitar selecciones simultáneas.",13);
        text(body,"APRENDIZAJE SIN SALIR DE DRIVELAB: "+
            "puede capturar el evento la pantalla o el servicio "+
            "'DriveLab · Waze y volante'. Ver abajo los códigos; "+
            "los nombres guardados sólo se cambian si realmente "+
            "llegó una tecla. T1Launcher conserva sus acciones.",13);
        learning(body,"RUEDITA / SIGUIENTE",T1WazeWheelService.NEXT);
        learning(body,"RUEDITA / ANTERIOR",T1WazeWheelService.PREV);
        learning(body,"OK / CONFIRMAR",T1WazeWheelService.OK);
        learning(body,"ATRÁS / CANCELAR",T1WazeWheelService.BACK);
        learning(body,"ACCESO DIRECTO A RUTAS (OPCIONAL)",
            T1WazeWheelService.ROUTES);
        button(body,"RESTABLECER TECLAS DE ESTA APP (DPAD)",()->{
            prefs().edit()
                .putInt(T1WazeWheelService.settingKey(this,T1WazeWheelService.NEXT),
                    KeyEvent.KEYCODE_DPAD_RIGHT)
                .putInt(T1WazeWheelService.settingKey(this,T1WazeWheelService.PREV),
                    KeyEvent.KEYCODE_DPAD_LEFT)
                .putInt(T1WazeWheelService.settingKey(this,T1WazeWheelService.OK),
                    KeyEvent.KEYCODE_DPAD_CENTER)
                .putInt(T1WazeWheelService.settingKey(this,T1WazeWheelService.BACK),
                    KeyEvent.KEYCODE_BACK)
                .putInt(T1WazeWheelService.settingKey(this,T1WazeWheelService.ROUTES),-1)
                .remove(T1WazeWheelService.PENDING).apply();
        });
        button(body,"ABRIR WAZE Y PROBAR",()->{
            try{
                Intent launch=getPackageManager().getLaunchIntentForPackage("com.waze");
                if(launch==null)throw new IllegalStateException("Waze no instalado");
                startActivity(launch);
            }catch(Exception ex){
                report.setText("No se abrió Waze: "+ex.getClass().getSimpleName());
            }
        });
        text(body,"Waze y Google Maps: botones accesibles propios. "+
            "Otras aplicaciones: elegí Panel A/B y ACTIVAR CONTROLES, "+
            "después INSPECCIONAR CONTROLES. La ruedita recorre "+
            "elementos accesibles y OK confirma solo el seleccionado. "+
            "ATRÁS nunca envía BACK global. Probar detenido.",13);
        setContentView(scroll);
        refreshModes();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        // Additional diagnostic channel when the user has DriveLab open:
        // no activity switching; never steal OEM key functions.
        T1WazeWheelService.observeActivityKey(this,event);
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){
        super.onResume();visible=true;
        T1WheelAccessibilityRecovery.request(this);
        ui.removeCallbacks(ticker);ui.post(ticker);
    }
    @Override protected void onPause(){
        visible=false;ui.removeCallbacks(ticker);
        super.onPause();
    }
}