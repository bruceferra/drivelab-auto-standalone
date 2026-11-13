package ar.com.rango5gps.drivelabauto.receiver;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
public final class T1SteeringKeysActivity extends T1ImmersiveActivity {
    @Override protected int overlayClearBackgroundColor(){return 0xFF091421;}
    private TextView output,countdown;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable timer=new Runnable(){
        @Override public void run(){
            if(!enabled)return;
            long left=Math.max(0,endsAt-SystemClock.elapsedRealtime());
            if(countdown!=null)countdown.setText("CAPTURA ACTIVA · "+
                ((left+999)/1000)+" s restantes · "+count+
                " eventos capturados. Un cero significa sin teclas recibidas, "+
                "no que el botón de iniciar falló.");
            if(left==0){
                enabled=false;
                report.append("FIN_90_SEGUNDOS;eventos=").append(count).append('\n');
                if(output!=null)output.append("\nFIN DEL REGISTRO · "+count+
                    " eventos. Compartí el informe, incluso con cero.");
            }else ui.postDelayed(this,1000L);
        }
    };
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
        countdown=new TextView(this);
        countdown.setTextColor(0xFFB9CDDB);countdown.setTextSize(16);
        countdown.setText("Detenido · ningún registro iniciado");
        root.addView(countdown);
        Button selfTest=new Button(this);
        selfTest.setText("COMPROBAR BOTÓN TÁCTIL (SIN VOLANTE)");
        selfTest.setAllCaps(false);
        selfTest.setOnClickListener(v->{
            output.setText("OK: este botón de la pantalla funciona. " +
                "Si los botones físicos no llegan al contador, la ROM OEM " +
                "puede interceptar los KeyEvent de la app.");
            countdown.setText(enabled?"CAPTURA SIGUE ACTIVA · "+count+
                " eventos recibidos":"TOQUE EN PANTALLA RECIBIDO · listo para iniciar");
        });
        root.addView(selfTest);
        Button share=new Button(this);share.setText("COMPARTIR INFORME VOLANTE");
        share.setAllCaps(false);
        share.setOnClickListener(v->share());
        root.addView(share);
        output=new TextView(this);output.setTextColor(Color.WHITE);
        output.setTextSize(14);output.setPadding(8,10,8,10);
        output.setText("Poné el auto en P y 0 km/h. Iniciar lectura no exige que "+
            "la señal OEM 1033/2148 esté disponible: es SOLO KEYEVENT.");
        ScrollView scroll=new ScrollView(this);scroll.addView(output);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        root.setFocusableInTouchMode(true);
        setContentView(root);root.requestFocus();
    }
    private void startCapture(){
        // Never block the touch handler with an OEM Binder call.
        // The old synchronous snapshot could make INICIAR look dead.
        ui.removeCallbacks(timer);
        report.setLength(0);
        report.append("DriveLab · volante T1 · Android KeyEvent\n");
        report.append("inicio_epoch_ms=").append(System.currentTimeMillis()).append('\n');
        enabled=true;count=0;
        endsAt=SystemClock.elapsedRealtime()+90000L;
        output.setText("● GRABANDO · 90 segundos iniciados desde la pantalla. " +
            "Pulsa cada tecla del volante de a una.\n");
        countdown.setText("CAPTURA ACTIVA · 90 s · 0 eventos");
        ui.post(timer);
        new Thread(()->{
            T1TripTelemetry.Sample s=new T1TripTelemetry(this).snapshot();
            String verified=(s.gear==1&&Double.isFinite(s.speed)&&
                s.speed>=0&&s.speed<1)?"P y 0 km/h por OEM":
                "Estado OEM no confirmado (1033="+s.gear+
                ", 2148="+s.speed+"). Usar solo en P, detenido.";
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                report.append("Verificación diferida: ").append(verified)
                    .append("; sin comandos vehículo\n");
                if(output!=null)output.append("\nEstado OEM: "+verified+"\n");
            });
        },"T1-Steering-Async-ReadOnly").start();
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
        ui.removeCallbacks(timer);
        report.append("total_eventos=").append(count).append('\n');
        if(count==0)report.append("NINGUN_KEYEVENT_RECIBIDO; " +
            "verificar si botones son exclusivos del servicio OEM\n");
        countdown.setText("CAPTURA DETENIDA · "+count+" eventos");
        output.append("\nGUARDANDO "+count+" eventos; si el total es " +
            "cero, el informe igualmente es útil para diagnosticar.\n");
        T1ReportShare.share(this,"teclas_volante",report.toString(),
            status->{if(!isFinishing()&&!isDestroyed())output.append("\n"+status);});
    }
    @Override protected void onPause(){
        if(enabled){
            enabled=false;
            report.append("PAUSA_PANTALLA;eventos=").append(count).append('\n');
        }
        ui.removeCallbacks(timer);
        super.onPause();
    }
}
