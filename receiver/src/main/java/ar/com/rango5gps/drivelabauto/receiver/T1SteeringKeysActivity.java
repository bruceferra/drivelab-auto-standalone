package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import java.util.Locale;

/**
 * Read-only Android KeyEvent diagnostic for Jetour T1 steering buttons.
 * T1Launcher has com.t1launcher.TeclasVolante and receives Android keys:
 * neither four-digit VehicleService nor HAL property IDs.
 * Captures only when this activity has focus and vehicle is safely in P.
 * NEVER turns a wheel press into a powertrain/vehicle command.
 */
public final class T1SteeringKeysActivity extends Activity {
    private TextView output;
    private final StringBuilder report=new StringBuilder(
        "DriveLab · botones volante Android · SOLO LECTURA\n");
    private boolean enabled;
    private long endsAt;
    private int count;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22,16,22,16);
        root.setBackgroundColor(0xFF091421);
        TextView heading=new TextView(this);
        heading.setText("VOLANTE T1 · CAPTURA KEYEVENT");
        heading.setTextSize(23);heading.setTextColor(Color.WHITE);
        root.addView(heading);
        TextView instructions=new TextView(this);
        instructions.setText("En P, 0 km/h: iniciá 90 segundos. Pulsá OK, Atrás, "+
            "ruedita izquierda + y −, de a uno. Se leen códigos de teclas Android, "+
            "sin tocar controles OEM. Solo llegan eventos que Android entregue "+
            "a esta pantalla; un botón reservado por el sistema puede no aparecer.");
        instructions.setTextSize(14);instructions.setTextColor(0xFFB9CDDB);
        root.addView(instructions);
        Button start=new Button(this);start.setText("INICIAR CAPTURA 90 S");
        start.setAllCaps(false);
        start.setOnClickListener(v->startCapture());
        root.addView(start);
        Button share=new Button(this);share.setText("COMPARTIR INFORME VOLANTE");
        share.setAllCaps(false);
        share.setOnClickListener(v->share());
        root.addView(share);
        output=new TextView(this);output.setTextColor(Color.WHITE);
        output.setTextSize(14);output.setPadding(8,10,8,10);
        output.setText("Sin captura. Poner el auto en P, velocidad 0.");
        ScrollView scroll=new ScrollView(this);scroll.addView(output);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        root.setFocusableInTouchMode(true);
        setContentView(root);root.requestFocus();
    }
    private void startCapture(){
        T1TripTelemetry.Sample s=new T1TripTelemetry(this).snapshot();
        if(s.gear!=1||!Double.isFinite(s.speed)||
            s.speed<0||s.speed>0.001){
            output.setText("NO INICIADO: únicamente P y 0 km/h. "+
                "1033="+s.gear+", velocidad 2148="+s.speed);
            return;
        }
        report.setLength(0);
        report.append("DriveLab · volante T1 · Android KeyEvent\n");
        report.append("inicio_epoch_ms=").append(System.currentTimeMillis()).append('\n');
        report.append("Marcha P=1; velocidad 0; sin comandos vehículo\n");
        enabled=true;count=0;
        endsAt=SystemClock.elapsedRealtime()+90000L;
        output.setText("GRABANDO · 90 s. Probá OK, Atrás, ruedita + y −.\n");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(enabled){
            if(SystemClock.elapsedRealtime()>endsAt){
                enabled=false;
                if(output!=null)output.append("\nCAPTURA FINALIZADA. Compartí el informe.");
            }else{
                final int action=event.getAction();
                // Keep down/up and repeated turns. Never consume system keys.
                String line=String.format(Locale.ROOT,
                    "%d;%s;keyCode=%d;name=%s;scanCode=%d;source=%d;device=%d;repeat=%d\n",
                    System.currentTimeMillis(),
                    action==KeyEvent.ACTION_DOWN?"DOWN":action==KeyEvent.ACTION_UP?"UP":"OTHER",
                    event.getKeyCode(),KeyEvent.keyCodeToString(event.getKeyCode()),
                    event.getScanCode(),event.getSource(),event.getDeviceId(),
                    event.getRepeatCount());
                if(count<350){
                    report.append(line);
                    if(output!=null)output.append(line);
                    count++;
                }else enabled=false;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void share(){
        enabled=false;
        if(count==0){
            output.setText("Sin teclas capturadas. No se subió informe vacío. "+
                "Si T1Launcher recibe el botón pero DriveLab no, "+
                "el sistema reserva ese evento o no lo entrega a nuestra Activity.");
            return;
        }
        report.append("total_eventos=").append(count).append('\n');
        output.append("\nSUBIENDO "+count+" eventos a Supabase…");
        T1ReportShare.share(this,"teclas_volante",report.toString(),
            status->{if(!isFinishing()&&!isDestroyed())output.append("\n"+status);});
    }
}
