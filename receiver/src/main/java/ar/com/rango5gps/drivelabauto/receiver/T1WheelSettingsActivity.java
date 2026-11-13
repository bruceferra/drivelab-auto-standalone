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
                "\n\nEl código se aprende de una tecla REAL recibida por "+
                "Accesibilidad. Si queda igual, la ROM o T1Launcher pueden "+
                "interceptar ese mando antes de esta app.");
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
            report.setText("Captura preparada para "+label+
                ". Abrí Waze y pulsá esa tecla física antes de 45 segundos. "+
                "Si no se captura, no hay evento Android disponible.");
        });
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(16,12,16,30);
        scroll.addView(body);
        text(body,"VOLANTE · WAZE / RUTAS ALTERNATIVAS",19);
        text(body,"Prueba experimental en Jetour T1. Solo funciona "+
            "si Android entrega KeyEvents del mando y Waze expone "+
            "botones accesibles. No modifica T1Launcher. "+
            "Configurá y probá detenido.",13);
        button(body,"ABRIR AJUSTES DE ACCESIBILIDAD",()->{
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        button(body,"ACTIVAR / DESACTIVAR CONTROL EN WAZE",()->{
            SharedPreferences p=prefs();
            boolean active=!p.getBoolean(T1WazeWheelService.ENABLED,false);
            p.edit().putBoolean(T1WazeWheelService.ENABLED,active).apply();
        });
        text(body,"APRENDIZAJE DE TECLAS: activar primero el servicio "+
            "'DriveLab · Waze y volante' en Accesibilidad. El mapeo es "+
            "local y no cambia el del vehículo.",13);
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
        report=text(body,"Leyendo estado…",14);
        setContentView(scroll);
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