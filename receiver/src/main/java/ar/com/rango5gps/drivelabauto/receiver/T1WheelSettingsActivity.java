package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicit opt-in for Waze-only steering key / route alternatives experiment. */
public final class T1WheelSettingsActivity extends T1ImmersiveActivity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView report;
    private boolean visible;
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            SharedPreferences p=prefs();
            report.setText(T1WazeWheelService.describe(
                T1WheelSettingsActivity.this)+
                "\n\nCONTROL WAZE: "+
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
    private TextView text(LinearLayout col,String title,int size){
        TextView t=new TextView(this);
        t.setText(title);t.setTextSize(size);
        t.setTextColor(T1JetourTheme.WHITE);
        t.setPadding(12,10,12,10);
        col.addView(t);return t;
    }
    private void button(LinearLayout col,String name,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);
        T1JetourTheme.button(b,T1JetourTheme.PANEL_HI);
        b.setOnClickListener(v->action.run());
        col.addView(b,new LinearLayout.LayoutParams(-1,57));
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
            report.setText(T1WazeWheelService.describe(this)+
                "\n\nSi figura SIN INICIO REGISTRADO o no hay KeyEvent, "+
                "verificá Accesibilidad por WebADB.");
        });
        button(body,"ABRIR AJUSTES DE ACCESIBILIDAD (SI EXISTEN)",()->{
            Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if(intent.resolveActivity(getPackageManager())==null){
                report.setText("La T1 no tiene pantalla de ajustes "+
                    "Accesibilidad disponible. Usá WebADB como ya hicimos.");
                return;
            }
            try{startActivity(intent);}
            catch(Exception ex){report.setText("Ajustes no disponibles: "+
                ex.getClass().getSimpleName()+". Usá WebADB.");}
        });
        button(body,"ACTIVAR / DESACTIVAR CONTROL EN WAZE",()->{
            SharedPreferences p=prefs();
            boolean active=!p.getBoolean(T1WazeWheelService.ENABLED,false);
            p.edit().putBoolean(T1WazeWheelService.ENABLED,active).apply();
        });
        text(body,"NUEVO · PUENTE OEM DEL VOLANTE AL CLÚSTER. "+
            "El APK original T1Launcher T1N134 recibe códigos 1000–1003 "+
            "desde IHardKeyCallback (Binder), NO como KeyEvent Android. "+
            "DriveLab busca los botones accesibles de Waze en PANTALLA 2, "+
            "y solicita conexión al mismo servicio OEM SOLO si activás "+
            "esta opción y Waze está visible. Google Maps queda intacto.",13);
        button(body,"CONECTAR / DESCONECTAR RUEDITA OEM → WAZE",()->{
            SharedPreferences p=prefs();
            boolean active=p.getBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,false);
            if(active){
                p.edit().putBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,false)
                    .putString(T1WazeWheelService.OEM_BINDER_STATUS,
                        "Desactivando enlace; no se usará en Google Maps")
                    .apply();
                report.setText("PUENTE OEM DESACTIVADO. La conexión se "+
                    "cierra en el próximo ciclo del servicio.");
                return;
            }
            new android.app.AlertDialog.Builder(this)
                .setTitle("VOLANTE OEM → WAZE · PRUEBA EN P")
                .setMessage("T1Launcher usa un servicio Binder del fabricante. "+
                    "Podemos conectarlo directamente para Waze en el clúster "+
                    "y reconocer giro 1000/1001, ATRÁS 1002 y OK 1003. "+
                    "\n\nIMPORTANTE: algunas ROM admiten UN solo receptor "+
                    "de teclas. Podría interrumpir temporalmente las "+
                    "funciones de volante de T1Launcher mientras Waze "+
                    "esté visible. Desactivá desde esta pantalla si sucede. "+
                    "\n\nNo se activará al inicio ni en Google Maps. "+
                    "Probalo únicamente con el auto detenido en P.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("ACTIVAR SOLO EN WAZE",(dialog,which)->{
                    p.edit().putBoolean(T1WazeWheelService.ENABLED,true)
                        .putBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,true)
                        .putString(T1WazeWheelService.OEM_BINDER_STATUS,
                            "Solicitado; esperando Waze y servicio Accesibilidad")
                        .apply();
                    report.setText("Puente solicitado. Abrí Waze en "+
                        "pantalla 2; al regresar consultá el ESTADO. "+
                        "Si el OEM rechaza la conexión aparecerá el motivo.");
                }).show();
        });
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
        button(body,"RESTABLECER TECLAS ANDROID DPAD",()->{
            prefs().edit()
                .putInt(T1WazeWheelService.NEXT,KeyEvent.KEYCODE_DPAD_RIGHT)
                .putInt(T1WazeWheelService.PREV,KeyEvent.KEYCODE_DPAD_LEFT)
                .putInt(T1WazeWheelService.OK,KeyEvent.KEYCODE_DPAD_CENTER)
                .putInt(T1WazeWheelService.BACK,KeyEvent.KEYCODE_BACK)
                .putInt(T1WazeWheelService.ROUTES,-1)
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
        text(body,"Uso previsto: en el mapa, OK intenta abrir 'Rutas "+
            "alternativas' si encuentra ese control accesible. Si configurás "+
            "una tecla RUTAS, la usa como acceso dedicado. Dentro de "+
            "alternativas, ruedita recorre rutas visibles, OK selecciona "+
            "y Atrás vuelve. Si Waze no expone los controles, no envía "+
            "pulsaciones inventadas. No operar el listado de rutas "+
            "mientras conducís.",13);
        setContentView(scroll);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        // Additional diagnostic channel when the user has DriveLab open:
        // no activity switching; never steal OEM key functions.
        T1WazeWheelService.observeActivityKey(this,event);
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){
        super.onResume();visible=true;
        ui.removeCallbacks(ticker);ui.post(ticker);
    }
    @Override protected void onPause(){
        visible=false;ui.removeCallbacks(ticker);
        super.onPause();
    }
}