package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Practical T1 cockpit: one all-day recorder + local passenger checkpoints.
 * No write/control Binder operation is reachable from this screen.
 * Trips and costs are explicitly estimated until OEM units are validated. */
public final class T1PremiumCockpitActivity extends Activity {
    private static final int BG=0xFF091421,CARD=0xFF142639,WHITE=0xFFF3F8FD,
        MUTED=0xFF9BB0C1,TEAL=0xFF14AFA7,BLUE=0xFF2B789B,
        GOLD=0xFFFFC367,RED=0xFFB65754;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading=new AtomicBoolean(false);
    private boolean visible=false;
    private long lastTelem=0,lastTotals=0;
    private T1TripTelemetry.Sample lastSample;
    private T1PremiumInstrumentView gauge;
    private TextView tripStatus,kmNow,fuelNow,electricNow,costNow,clientNow,
        dayNow,monthNow,historyNow,zoneStatus,feedback,energyNote;
    private LinearLayout advanced;
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            updateStats();
            if(System.currentTimeMillis()-lastTelem>3800&&
                reading.compareAndSet(false,true)){
                lastTelem=System.currentTimeMillis();
                new Thread(()->{
                    T1TripTelemetry.Sample s=new T1TripTelemetry(
                        T1PremiumCockpitActivity.this).snapshot();
                    ui.post(()->{
                        reading.set(false);
                        if(!visible||isFinishing()||isDestroyed())return;
                        lastSample=s;
                        gauge.update(valid(s.speed,0,220),valid(s.fuel,0,70),
                            valid(s.socDisplay,0,100),s.powerMode);
                        updateZone();
                    });
                },"T1-Cockpit-ReadOnly").start();
            }
            ui.postDelayed(this,1300);
        }
    };
    private static Double valid(double v,double min,double max){
        return Double.isFinite(v)&&v>=min&&v<=max?v:null;
    }
    private static String f(double v,int digits){return T1JourneyMetrics.fmt(v,digits);}
    @Override public void onCreate(Bundle b){super.onCreate(b);draw();}
    @Override protected void onResume(){
        super.onResume();visible=true;lastTelem=0;lastTotals=0;
        ui.removeCallbacks(ticker);ui.post(ticker);
    }
    @Override protected void onPause(){
        visible=false;ui.removeCallbacks(ticker);super.onPause();
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);
        g.setCornerRadius(radius);return g;
    }
    private LinearLayout col(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(color);t.setPadding(6,6,6,6);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        return t;
    }
    private LinearLayout tile(LinearLayout parent,int color){
        LinearLayout a=col();a.setBackground(bg(color,20));a.setPadding(14,12,14,12);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.setMargins(4,4,4,4);parent.addView(a,lp);return a;
    }
    private TextView metric(LinearLayout parent,String label,String placeholder){
        parent.addView(text(label,13,MUTED,false));
        TextView value=text(placeholder,24,WHITE,true);parent.addView(value);
        return value;
    }
    private Button button(LinearLayout parent,String caption,int color,Runnable run){
        Button b=new Button(this);b.setText(caption);b.setAllCaps(false);
        b.setTextSize(15);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,58,1f);
        lp.setMargins(3,3,3,3);parent.addView(b,lp);return b;
    }
    private void heading(LinearLayout c,String s){c.addView(text(s,19,WHITE,true));}
    private void draw(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=col();root.setPadding(18,10,18,28);
        scroll.addView(root);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        LinearLayout title=col();
        title.addView(text("DRIVELAB  /  JETOUR T1",22,WHITE,true));
        title.addView(text("COCKPIT · v0.2.34 · telemetría OEM",13,MUTED,false));
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button back=new Button(this);back.setText("VOLVER AL T1");
        back.setAllCaps(false);back.setOnClickListener(v->finish());top.addView(back);
        gauge=new T1PremiumInstrumentView(this);
        LinearLayout.LayoutParams meterParams=new LinearLayout.LayoutParams(-1,-2);
        meterParams.setMargins(0,6,0,12);root.addView(gauge,meterParams);

        LinearLayout metrics=row();root.addView(metrics);
        kmNow=metric(tile(metrics,CARD),"DISTANCIA · VELOCIDAD OEM","0,00 km");
        fuelNow=metric(tile(metrics,CARD),"NAFTA 500 m · EXPERIMENTAL","—");
        electricNow=metric(tile(metrics,CARD),"ELECTRICIDAD NETA · EXP.","—");
        costNow=metric(tile(metrics,CARD),"COSTO NAFTA · EXP.","—");
        energyNote=text("Km calculados desde velocidad OEM (sin calibrar). "+
            "Nafta y energía: hipótesis de unidades OEM 500 m. "+
            "Costo eléctrico total aún sin medidor validado.",12,MUTED,false);
        root.addView(energyNote);

        LinearLayout journey=col();journey.setBackground(bg(CARD,20));
        journey.setPadding(12,10,12,10);
        LinearLayout.LayoutParams pad=new LinearLayout.LayoutParams(-1,-2);
        pad.setMargins(0,10,0,12);root.addView(journey,pad);
        heading(journey,"01  JORNADA · GRABACIÓN CONTINUA");
        tripStatus=text("Revisando TRIP OEM…",15,MUTED,false);journey.addView(tripStatus);
        LinearLayout dayButtons=row();journey.addView(dayButtons);
        button(dayButtons,"▶ INICIAR JORNADA",TEAL,this::startDay);
        button(dayButtons,"■ CERRAR JORNADA",RED,this::endDay);
        heading(journey,"02  CLIENTE / TRAYECTO INDEPENDIENTE");
        clientNow=text("Sin trayecto activo",16,WHITE,true);journey.addView(clientNow);
        LinearLayout clientButtons=row();journey.addView(clientButtons);
        button(clientButtons,"+ INICIAR TRAYECTO",BLUE,this::askClient);
        button(clientButtons,"✓ FINALIZAR TRAYECTO",TEAL,this::finishClient);

        LinearLayout aggregate=row();root.addView(aggregate);
        dayNow=metric(tile(aggregate,CARD),"HOY · CLIENTES","—");
        monthNow=metric(tile(aggregate,CARD),"ESTE MES · CLIENTES","—");
        heading(root,"ÚLTIMOS TRAYECTOS");
        historyNow=text("Todavía no hay trayectos cerrados.",14,MUTED,false);
        historyNow.setBackground(bg(CARD,18));historyNow.setPadding(16,14,16,14);
        root.addView(historyNow);
        feedback=text("Los viajes se guardan localmente. "+
            "El TRIP continuo y sus CSV anteriores permanecen intactos.",
            14,0xFFB2DDD9,false);root.addView(feedback);

        LinearLayout expandRow=row();root.addView(expandRow);
        button(expandRow,"CARGAS Y ECONOMÍA",BLUE,()->openChargeDialog());
        button(expandRow,"ZONAS GPS · EV/HEV",BLUE,()->{
            advanced.setVisibility(advanced.getVisibility()==android.view.View.GONE?
                android.view.View.VISIBLE:android.view.View.GONE);
            updateZone();
        });
        advanced=col();advanced.setPadding(14,12,14,14);
        advanced.setBackground(bg(CARD,20));
        advanced.setVisibility(android.view.View.GONE);
        root.addView(advanced);
        heading(advanced,"ZONAS DE SUGERENCIA EV / HEV");
        advanced.addView(text("Experimental: detecta GPS solo con el panel abierto. "+
            "NO modifica la propulsión. La elección real se hace en controles Jetour.",
            13,MUTED,false));
        zoneStatus=text("Sin ubicación verificada",14,WHITE,false);
        advanced.addView(zoneStatus);
        LinearLayout zoneButtons=row();advanced.addView(zoneButtons);
        button(zoneButtons,"GUARDAR ZONA EV",TEAL,()->askZone(1));
        button(zoneButtons,"GUARDAR ZONA HEV",BLUE,()->askZone(2));
        LinearLayout modeButtons=row();advanced.addView(modeButtons);
        button(modeButtons,"ENSAYO EV/HEV · SOLO LECTURA",BLUE,this::readModeExperiment);
        button(modeButtons,"BORRAR ZONAS",RED,()->new AlertDialog.Builder(this)
            .setTitle("Borrar todas las zonas locales")
            .setMessage("No afecta el vehículo ni otros datos.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("BORRAR",(d,w)->{
                T1ModeZoneStore.clear(this);updateZone();
            }).show());
        LinearLayout extra=row();root.addView(extra);
        button(extra,"TABLERO TÉCNICO / CSV",BLUE,()->startActivity(
            new Intent(this,T1TankDashboardActivity.class)));
        button(extra,"DIAGNÓSTICO OEM",BLUE,()->startActivity(
            new Intent(this,ReceiverVehicleActivity.class)
                .putExtra("open_tab",3)));
        root.addView(text("Este cockpit no reemplaza el velocímetro homologado ni "+
            "el sistema Jetour. No usar para controlar EV/HEV en marcha. "+
            "No se guardan coordenadas de clientes, ni se envían las zonas a la nube.",
            12,MUTED,false));
        setContentView(scroll);
    }
    private void updateStats(){
        SharedPreferences m=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE);
        boolean running=m.getBoolean(T1TripRecorderService.RUNNING,false);
        double km=m.getFloat(T1TripRecorderService.DISTANCE,0f);
        int nf=m.getInt(T1TripRecorderService.SEG_FUEL_COUNT,0),
            ne=m.getInt(T1TripRecorderService.SEG_ELEC_COUNT,0);
        double fRaw=m.getFloat(T1TripRecorderService.SEG_FUEL_RAW_SUM,0f),
            eRaw=m.getFloat(T1TripRecorderService.SEG_ELEC_RAW_SUM,0f);
        double price=m.getFloat(T1TripRecorderService.FUEL_PRICE,2129f);
        double lit=nf>0?fRaw*0.005:Double.NaN;
        double eNet=ne>0?eRaw*0.005:Double.NaN;
        kmNow.setText(f(km,2)+" km");
        fuelNow.setText(nf>0?f(lit,3)+" L":"SIN TRAMOS");
        electricNow.setText(ne>0?f(eNet,3)+" kWh":"SIN TRAMOS");
        costNow.setText(nf>0?"$ "+f(lit*price,0):"SIN TRAMOS");
        tripStatus.setText((running?"● JORNADA ABIERTA":"○ JORNADA DETENIDA")+
            " · "+m.getInt(T1TripRecorderService.SAMPLES,0)+" muestras"+
            " · "+nf+" tramos OEM de nafta / "+ne+" eléctricos"+
            "\nFuente de km: "+m.getString(T1TripRecorderService.DIST_SOURCE,"sin dato"));
        if(T1JourneyStore.active(this)){
            T1JourneyMetrics.Result live=T1JourneyStore.running(this);
            clientNow.setText("● "+T1JourneyStore.activeLabel(this)+
                (live==null?" · comprobando señal…":
                    " · "+f(live.km,2)+" km · "+
                    (live.fuelAvailable?f(live.fuelLitersCandidate,3)+
                        " L experimentales":"esperando tramo de 500 m")));
        }else clientNow.setText("○ Sin cliente activo · listo para iniciar");
        if(System.currentTimeMillis()-lastTotals>5000){
            lastTotals=System.currentTimeMillis();
            updateHistory();
        }
    }
    private void updateHistory(){
        T1JourneyStore.Totals day=T1JourneyStore.totals(this,T1JourneyStore.today());
        T1JourneyStore.Totals month=T1JourneyStore.totals(this,T1JourneyStore.month());
        dayNow.setText(day.trips+" viajes · "+f(day.km,1)+" km\n"+
            "$ "+f(day.fuelCost,0)+" nafta exp.");
        monthNow.setText(month.trips+" viajes · "+f(month.km,1)+" km\n"+
            "$ "+f(month.fuelCost,0)+" nafta exp.");
        ArrayList<T1JourneyStore.Entry> all=T1JourneyStore.history(this);
        StringBuilder s=new StringBuilder();
        SimpleDateFormat dt=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault());
        for(int i=all.size()-1;i>=0&&i>=all.size()-5;i--){
            T1JourneyStore.Entry e=all.get(i);
            s.append(dt.format(new Date(e.started))).append(" · ")
                .append(e.label).append("\n")
                .append(f(e.km,2)).append(" km · ")
                .append(Double.isFinite(e.liters)?
                    f(e.liters,3)+" L exp.":"sin segmento completo")
                .append(" · $")
                .append(f(e.fuelCost,0)).append(" nafta exp.\n");
        }
        historyNow.setText(s.length()==0?
            "Sin trayectos de cliente guardados.":s.toString().trim());
    }
    private void message(String m){feedback.setText(m);lastTotals=0;updateStats();}
    private void startDay(){
        SharedPreferences m=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE);
        if(m.getBoolean(T1TripRecorderService.RUNNING,false)){
            message("La jornada ya está grabando; no se reinició.");return;
        }
        try{
            startForegroundService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_START));
            message("Iniciando jornada. Esperá la primera muestra (3 s).");
        }catch(Exception ex){message("No se inició: "+ex.getClass().getSimpleName());}
    }
    private void endDay(){
        if(T1JourneyStore.active(this)){
            message("Primero FINALIZÁ EL TRAYECTO del cliente para conservar su costo.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Cerrar la jornada")
            .setMessage("Se termina la grabación continua y se conservan CSV y trayectos.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CERRAR",(d,w)->{
                try{
                    startService(new Intent(this,T1TripRecorderService.class)
                        .setAction(T1TripRecorderService.ACTION_STOP));
                    message("Finalizando jornada; esperando cierre del CSV.");
                }catch(Exception ex){message("Error: "+ex.getClass().getSimpleName());}
            }).show();
    }
    private void askClient(){
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setHint("Ej.: Cliente, traslado, tramo de vuelta");
        new AlertDialog.Builder(this).setTitle("Iniciar trayecto")
            .setMessage("La jornada sigue grabando. Este tramo tendrá su propio costo.")
            .setView(name).setNegativeButton("CANCELAR",null)
            .setPositiveButton("INICIAR",(d,w)->{
                try{message(T1JourneyStore.start(this,name.getText().toString()));}
                catch(Exception ex){message("No se inició: "+ex.getMessage());}
            }).show();
    }
    private void finishClient(){
        if(!T1JourneyStore.active(this)){message("No hay trayecto activo.");return;}
        new AlertDialog.Builder(this).setTitle("Finalizar trayecto")
            .setMessage("Se guardará como subviaje sin interrumpir la jornada.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("GUARDAR",(d,w)->{
                try{message(T1JourneyStore.finish(this));}
                catch(Exception ex){message("No se guardó: "+ex.getMessage());}
            }).show();
    }
    private void openChargeDialog(){
        LinearLayout form=col();form.setPadding(24,8,24,4);
        form.addView(text("Registrar una carga REAL del medidor / cargador. "+
            "No se imputa automáticamente al costo de ningún cliente.",14,Color.DKGRAY,false));
        EditText k=new EditText(this);k.setHint("kWh comprados a la red");
        k.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(k);
        EditText price=new EditText(this);price.setHint("Precio del kWh (ARS)");
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float defaultPrice=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getFloat(T1TripRecorderService.KWH_PRICE,250f);
        price.setText(String.format(Locale.ROOT,"%.2f",defaultPrice));form.addView(price);
        new AlertDialog.Builder(this).setTitle("CARGA ELÉCTRICA")
            .setView(form).setNegativeButton("CANCELAR",null)
            .setPositiveButton("REGISTRAR",(d,w)->{
                try{
                    double kw=Double.parseDouble(k.getText().toString().replace(",","."));
                    double p=Double.parseDouble(price.getText().toString().replace(",","."));
                    T1JourneyStore.recordCharge(this,kw,p,"Carga de red");
                    getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
                        .edit().putFloat(T1TripRecorderService.KWH_PRICE,(float)p).apply();
                    T1JourneyStore.Totals m=T1JourneyStore.totals(this,T1JourneyStore.month());
                    message("Carga guardada: "+f(kw,2)+" kWh · $ "+f(kw*p,0)+
                        " · este mes "+f(m.gridKwh,2)+" kWh comprados ($"+
                        f(m.gridCost,0)+"). Sin doble conteo de recuperación.");
                }catch(Exception e){message("No se guardó la carga: revisá kWh y precio.");}
            }).show();
    }
    private boolean parked(){
        return lastSample!=null &&
            System.currentTimeMillis()-lastSample.timestamp<10000&&
            T1ZonePolicy.parked(lastSample.gear,lastSample.speed);
    }
    private void askZone(int mode){
        if(!parked()){
            message("Zonas solo se guardan detenido en P con telemetría reciente.");return;
        }
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setHint("Nombre de la zona (no dirección)");
        new AlertDialog.Builder(this).setTitle("Zona "+T1ZonePolicy.name(mode))
            .setMessage("GPS local · radio 300 m · solo SUGERENCIA, no cambia EV/HEV.")
            .setView(name).setNegativeButton("CANCELAR",null)
            .setPositiveButton("GUARDAR",(d,w)->{
                message(T1ModeZoneStore.add(this,name.getText().toString(),
                    mode,lastSample.gear,lastSample.speed));
                updateZone();
            }).show();
    }
    private void updateZone(){
        if(zoneStatus==null)return;
        int mode=lastSample==null?-1:lastSample.powerMode;
        zoneStatus.setText("Modo OEM 1043: "+T1ZonePolicy.name(mode)+
            "\n"+T1ModeZoneStore.suggestion(this,mode)+
            "\nZonas locales guardadas: "+T1ModeZoneStore.zones(this).size());
    }
    private void readModeExperiment(){
        if(!parked()){
            message("Ensayo de modos solo con vehículo en P y velocidad cero.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Ensayo EV / HEV · solo lectura")
            .setMessage("1043=1 EV; 2 HEV; 3 EV MAX. Grabaremos los cambios "+
                "por 3 minutos. Cambiá el modo solo desde la pantalla ORIGINAL "+
                "Jetour y comprobá la lectura en DIAGNÓSTICO. "+
                "Este botón NO envía comandos de propulsión.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("INICIAR REGISTRO",(d,w)->{
                try{
                    startForegroundService(new Intent(this,
                        T1PhevModeCaptureService.class).setAction(
                            T1PhevModeCaptureService.ACTION_START));
                    startActivity(new Intent(this,ReceiverVehicleActivity.class)
                        .putExtra("open_tab",3));
                }catch(Exception ex){message("Diagnóstico no iniciado: "+
                    ex.getClass().getSimpleName());}
            }).show();
    }
}
