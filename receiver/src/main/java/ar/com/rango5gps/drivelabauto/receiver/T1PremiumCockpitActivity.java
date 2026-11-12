package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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

/** T1 cockpit: trip recorder, compact manually confirmed OEM mode experiments
 * and read-only road suggestions. Powertrain writes never come from GPS.
 * Trips and costs remain estimated until OEM units are validated. */
public final class T1PremiumCockpitActivity extends T1ImmersiveActivity {
    private static final int BG=0xFF091421,CARD=0xFF142639,WHITE=0xFFF3F8FD,
        MUTED=0xFF9BB0C1,TEAL=0xFF14AFA7,BLUE=0xFF2B789B,
        GOLD=0xFFFFC367,RED=0xFFB65754;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading=new AtomicBoolean(false);
    private boolean visible=false;
    private long lastTelem=0,lastTotals=0;
    private T1TripTelemetry.Sample lastSample;
    private T1PremiumInstrumentView gauge;
    private TextView dayRecordingLamp,clientRecordingLamp;
    private Button dayStartButton,clientStartButton,clientContinueButton,
        clientRecoverButton;
    private TextView tripStatus,kmNow,fuelNow,electricNow,costNow,clientNow,
        dayNow,monthNow,historyNow,zoneStatus,feedback,energyNote,
        scopeTitle,scopeProgress,socStrategy,autonomyNow,autonomyDetail;
    private LinearLayout advanced;
    private TextView rearBeltStatus;
    private T1RearSeatBeltProbe rearBeltProbe;
    private T1RearSeatBeltProbe.Snapshot rearBeltBefore;
    private String rearBeltReport="";
    private TextView ledStatus;
    private String ledReport="";
    private T1HorizonLedReadOnlyProbe ledProbe;
    private final AtomicBoolean ledRecording=new AtomicBoolean(false);
    private TextView hevModeLive;
    private TextView hevStatus,lightStatus,aux12vStatus,rearSeatStatus,
        tempEngine,tempBattery,electricMotorStatus,electricMotorRpm;
    private T1ManualHevExperiment hevPilot;
    private T1ManualThreeModePilot threeModePilot;
    private Button modeEvButton,modeHevButton,modeMaxButton;
    private TextView modeAtTop,osmStatus;
    private Button osmEnabledButton,osmSocButton;
    private T1OsmRoadAdvisor.Result osmLast;
    private final AtomicBoolean osmReading=new AtomicBoolean(false);
    private long lastOsmQuery=0;
    private static final String OSM_PREFS="t1_osm_advisory_opt_in";
    private static final String OSM_ENABLED="osm_lookup_enabled";
    private static final String OSM_FORCED="motorway_soc_forced";
    private T1CabinLightReadOnlyProbe lightProbe;
    private T1CabinLightReadOnlyProbe.Snapshot lightBefore;
    private T1RearSeatLightsProbe seatProbe;
    private T1RearSeatLightsProbe.Snapshot seatBefore;
    private String seatReport="";
    private T1ThermalMotorProbe thermalProbe;
    private T1ThermalMotorProbe.Snapshot lastThermal;
    private T1Aux12vReadOnlyProbe aux12vProbe;
    private String aux12vReport="";
    private final AtomicBoolean lightReading=new AtomicBoolean(false),
        aux12vReading=new AtomicBoolean(false),
        seatReading=new AtomicBoolean(false),
        beltReading=new AtomicBoolean(false),
        thermalReading=new AtomicBoolean(false);
    private long lastThermalAttempt=0;
    private static final String RANGE_PREFS="t1_cockpit_range_settings";
    // Jetour voice assistant remains floating above the upper-left corner.
    // Keep the permanent empty strip the same dark color as the cockpit.
    @Override protected int overlayClearBackgroundColor(){return BG;}
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            updateStats();
            if(System.currentTimeMillis()-lastThermalAttempt>12000){
            lastThermalAttempt=System.currentTimeMillis();
            readThermal(false);
        }
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
                            valid(s.socDisplay,0,100),s.powerMode,
                            valid(s.rpm,0,12000),
                            valid(s.current,-1500,1500));
                        updateSoc();
                        updateAutonomy();
                        updateZone();
                        updateTopModes();
                        if(osmEnabled()&&
                            System.currentTimeMillis()-lastOsmQuery>95000)
                            lookupOsm(false);
                    });
                },"T1-Cockpit-ReadOnly").start();
            }
            ui.postDelayed(this,700);
        }
    };
    private static Double valid(double v,double min,double max){
        return Double.isFinite(v)&&v>=min&&v<=max?v:null;
    }
    private static String f(double v,int digits){return T1JourneyMetrics.fmt(v,digits);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        hevPilot=new T1ManualHevExperiment(this);
        threeModePilot=new T1ManualThreeModePilot(this);
        lightProbe=new T1CabinLightReadOnlyProbe(this);
        seatProbe=new T1RearSeatLightsProbe(this);
        rearBeltProbe=new T1RearSeatBeltProbe(this);
        ledProbe=new T1HorizonLedReadOnlyProbe(this);
        thermalProbe=new T1ThermalMotorProbe(this);
        aux12vProbe=new T1Aux12vReadOnlyProbe(this);
        draw();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;lastTelem=0;lastTotals=0;
        ui.removeCallbacks(ticker);ui.post(ticker);
    }
    @Override protected void onPause(){
        visible=false;ui.removeCallbacks(ticker);super.onPause();
    }
    private boolean osmEnabled(){
        return getSharedPreferences(OSM_PREFS,MODE_PRIVATE)
            .getBoolean(OSM_ENABLED,false);
    }
    private boolean motorwayForced(){
        return getSharedPreferences(OSM_PREFS,MODE_PRIVATE)
            .getBoolean(OSM_FORCED,false);
    }
    private void openDisplaySettings(){
        String[] choices={
            T1ImmersiveMode.enabled(this)?"MOSTRAR BARRAS":"PANTALLA COMPLETA",
            "ESPACIO JETOUR · "+clearanceDp()+" dp (ajustar)"
        };
        new AlertDialog.Builder(this).setTitle("PANTALLA T1")
            .setItems(choices,(dialog,index)->{
                if(index==0)T1ImmersiveMode.setEnabled(this,
                    !T1ImmersiveMode.enabled(this));
                if(index==1){
                    int value=cycleClearanceDp();
                    message("Espacio Jetour: "+value+" dp (ajustable).");
                }
            }).setNegativeButton("CERRAR",null).show();
    }
    private Button topModeButton(LinearLayout parent,String name,int target){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);b.setTextSize(18);
        b.setTypeface(null,android.graphics.Typeface.BOLD);
        b.setTextColor(WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        b.setOnClickListener(v->askTripleMode(target));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,57,1f);
        lp.setMargins(3,2,3,2);parent.addView(b,lp);
        return b;
    }
    private void updateTopModes(){
        if(modeEvButton==null||modeHevButton==null||modeMaxButton==null)
            return;
        int actual=lastSample==null?-1:lastSample.powerMode;
        long age=lastSample==null?Long.MAX_VALUE:
            System.currentTimeMillis()-lastSample.timestamp;
        boolean fresh=age>=0&&age<=10000;
        Button[] buttons={modeEvButton,modeHevButton,modeMaxButton};
        for(int i=0;i<buttons.length;i++){
            int expected=i+1;
            buttons[i].setBackgroundTintList(ColorStateList.valueOf(
                fresh&&actual==expected?TEAL:BLUE));
        }
        modeAtTop.setText(fresh?
            "ACTUAL: "+T1ManualHevPolicy.currentMode(actual)+
            " (1043="+actual+"). Cambios de DriveLab: "+
            "solo en P y 0 km/h; los tres modos probados en P.":
            "MODO OEM · sin lectura reciente. Sin escrituras.");
    }
    private void askTripleMode(int target){
        if(lastSample==null){
            modeAtTop.setText("SIN TELEMETRÍA OEM. No se envió comando.");
            return;
        }
        long age=System.currentTimeMillis()-lastSample.timestamp;
        if(age<0||age>10000||lastSample.gear!=1||
            !Double.isFinite(lastSample.speed)||lastSample.speed<0||
            lastSample.speed>0.001){
            new AlertDialog.Builder(this)
                .setTitle("CAMBIO MANUAL · SIN ESCRITURA")
                .setMessage("La Jetour permite cambios de modo desde su "+
                    "interfaz original en marcha. La transacción de DriveLab "+
                    "solo está probada estacionado, por lo que "+
                    "no se anulan sus comprobaciones. "+
                    "Lecturas: P/1033="+lastSample.gear+
                    ", velocidad="+f(lastSample.speed,2)+
                    " km/h; edad="+age+" ms.")
                .setPositiveButton("ENTENDIDO",null).show();
            return;
        }
        if(lastSample.powerMode==target){
            modeAtTop.setText("Ya está en "+
                T1ManualHevPolicy.currentMode(target)+
                " · no se envió nada.");
            return;
        }
        if(threeModePilot.busy()||hevPilot.busy()){
            modeAtTop.setText("Otro ensayo OEM está en curso.");
            return;
        }
        String targetName=T1ManualHevPolicy.currentMode(target);
        new AlertDialog.Builder(this)
            .setTitle("CAMBIO MANUAL → "+targetName)
            .setMessage("Intentar una vez 1043="+target+
                " con verificación doble de P/velocidad cero y lectura "+
                "posterior del modo. EV, HEV y EV MAX confirmados por "+
                "lectura 1043 desde DriveLab en P. El control Jetour "+
                "original puede operar en marcha, pero esta interfaz "+
                "Android NO fue validada circulando. "+
                "Sin cambios GPS automáticos.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR UNA VEZ",(dialog,which)->{
                modeAtTop.setText("Consultando OEM; verificando modo…");
                if(!threeModePilot.request(target,()->visible,
                        result->ui.post(()->{
                    if(!visible||isFinishing()||isDestroyed())return;
                    modeAtTop.setText(result);
                    lastTelem=0;
                    new AlertDialog.Builder(this)
                        .setTitle("RESULTADO MANUAL · "+targetName)
                        .setMessage(result)
                        .setPositiveButton("CERRAR",null).show();
                })))modeAtTop.setText("Ensayo ocupado. No se envió nada.");
            }).show();
    }
    private void updateOsmControls(){
        if(osmEnabledButton==null)return;
        osmEnabledButton.setText(osmEnabled()?
            "OSM · DESACTIVAR ASESOR":"OSM · ACTIVAR ASESOR");
        osmSocButton.setText(motorwayForced()?
            "AUTOPISTA · HEV FORZADO":"AUTOPISTA · HEV SMART");
    }
    private void toggleOsm(){
        if(osmEnabled()){
            getSharedPreferences(OSM_PREFS,MODE_PRIVATE)
                .edit().putBoolean(OSM_ENABLED,false).apply();
            updateOsmControls();
            osmStatus.setText("Asesor OSM desactivado. Sin envíos GPS.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("ASESOR OSM · SOLO RECOMENDACIONES")
            .setMessage("Al activarlo, mientras el cockpit esté abierto "+
                "se consultará cada ~95 segundos la clase de la vía "+
                "en Overpass/OpenStreetMap, enviando tus coordenadas "+
                "de ubicación al servicio público. NO cambiará EV/HEV "+
                "ni SOC; solo mostrará sugerencias. "+
                "Si falla GPS o el mapa es ambiguo, no recomienda nada.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                getSharedPreferences(OSM_PREFS,MODE_PRIVATE).edit()
                    .putBoolean(OSM_ENABLED,true).apply();
                lastOsmQuery=0;
                updateOsmControls();
                lookupOsm(false);
            }).show();
    }
    private void toggleOsmSoc(){
        boolean forced=!motorwayForced();
        getSharedPreferences(OSM_PREFS,MODE_PRIVATE).edit()
            .putBoolean(OSM_FORCED,forced).apply();
        updateOsmControls();
        showOsmResult();
    }
    private void showOsmResult(){
        if(osmStatus==null)return;
        if(osmLast==null){
            osmStatus.setText("OSM sin clasificación: ciudad → EV / "+
                "autopista → HEV "+(motorwayForced()?"FORZADO":"SMART")+
                ". SIN ESCRITURA.");
            return;
        }
        osmStatus.setText(osmLast.recommendation(motorwayForced())+
            "\n"+osmLast.explain()+
            "\nASESOR OSM: SOLO LECTURA, NO CAMBIA EL VEHÍCULO.");
    }
    private void lookupOsm(boolean userTriggered){
        if(osmStatus==null||!visible)return;
        if(!userTriggered&&!osmEnabled())return;
        if(!osmReading.compareAndSet(false,true))return;
        lastOsmQuery=System.currentTimeMillis();
        android.location.Location loc=T1ModeZoneStore.location(this);
        osmStatus.setText("Verificando vía OSM… "+
            "(consulta GPS; sin órdenes al vehículo)");
        new Thread(()->{
            T1OsmRoadAdvisor.Result result=T1OsmRoadAdvisor.lookup(loc);
            ui.post(()->{
                osmReading.set(false);
                if(!visible||isFinishing()||isDestroyed())return;
                osmLast=result;showOsmResult();
            });
        },"T1-OpenStreetMap-Road-ReadOnly").start();
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
        title.addView(text("DRIVELAB / T1",19,WHITE,true));
        title.addView(text("COCKPIT · v0.2.43",12,MUTED,false));
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button displaySettings=new Button(this);
        displaySettings.setAllCaps(false);
        displaySettings.setTextSize(12);
        displaySettings.setText("PANTALLA");
        displaySettings.setOnClickListener(v->openDisplaySettings());
        top.addView(displaySettings,new LinearLayout.LayoutParams(-2,52));
        Button back=new Button(this);
        back.setText("VOLVER");back.setTextSize(12);
        back.setAllCaps(false);back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(-2,52));

        LinearLayout modes=row();root.addView(modes);
        modeEvButton=topModeButton(modes,"EV",1);
        modeHevButton=topModeButton(modes,"HEV",2);
        modeMaxButton=topModeButton(modes,"EV MAX",3);
        modeAtTop=text("MODO OEM: esperando 1043 · control manual solo detenido en P",
            14,MUTED,false);
        root.addView(modeAtTop);
        LinearLayout recordingLamps=row();
        root.addView(recordingLamps);
        dayRecordingLamp=text("○ JORNADA DETENIDA",17,MUTED,true);
        clientRecordingLamp=text("○ CLIENTE DETENIDO",17,MUTED,true);
        recordingLamps.addView(dayRecordingLamp,
            new LinearLayout.LayoutParams(0,-2,1f));
        recordingLamps.addView(clientRecordingLamp,
            new LinearLayout.LayoutParams(0,-2,1f));

        gauge=new T1PremiumInstrumentView(this);
        LinearLayout.LayoutParams meterParams=new LinearLayout.LayoutParams(-1,-2);
        meterParams.setMargins(0,6,0,8);root.addView(gauge,meterParams);
        LinearLayout powertrain=row();root.addView(powertrain);
        tempEngine=metric(tile(powertrain,CARD),
            "TEMPERATURA MOTOR · 559940105 · EXP.","SIN LECTURA");
        tempBattery=metric(tile(powertrain,CARD),
            "TEMP. BATERÍA HV · OEM","SIN LECTURA");
        LinearLayout electricTile=tile(powertrain,CARD);
        electricTile.addView(text("MOTOR ELÉCTRICO",13,MUTED,false));
        electricMotorStatus=text("ESTADO SIN VALIDAR",20,GOLD,true);
        electricTile.addView(electricMotorStatus);
        electricMotorRpm=text("RPM: SIN LECTURA",14,WHITE,false);
        electricTile.addView(electricMotorRpm);
        LinearLayout thermalRow=row();root.addView(thermalRow);
        button(thermalRow,"TEMPERATURAS / MOTOR · VER CÓDIGOS",BLUE,
            ()->readThermal(true));
        root.addView(text("Temperatura motor: 559940105, lectura que "+
            "aumentó al calentarse en tu T1; escala aún sin confirmar "+
            "(RAW, no °C garantizados). Refrigerante AOSP y batería "+
            "han devuelto 0 sin datos útiles. RPM térmicas=1035; "+
            "motor eléctrico sin señal de RPM confirmada.",
            12,MUTED,false));
        socStrategy=text("GESTIÓN SOC: SIN LECTURA · OBJETIVO: SIN DATO",
            17,WHITE,true);root.addView(socStrategy);
        LinearLayout scope=col();
        scope.setBackground(bg(0xFF123950,22));
        scope.setPadding(20,13,20,13);
        LinearLayout.LayoutParams scopeParams=new LinearLayout.LayoutParams(-1,-2);
        scopeParams.setMargins(0,8,0,8);
        root.addView(scope,scopeParams);
        scopeTitle=text("JORNADA ACTUAL · SIN GRABACIÓN",16,0xFFB5DAEC,true);
        scope.addView(scopeTitle);
        kmNow=text("0,00 km",45,0xFF5BE2D0,true);scope.addView(kmNow);
        scopeProgress=text("Iniciá una jornada o un trayecto; acá verás el avance.",
            15,WHITE,false);
        scope.addView(scopeProgress);
        LinearLayout metrics=row();root.addView(metrics);
        fuelNow=metric(tile(metrics,CARD),"NAFTA 500 m · EXPERIMENTAL","—");
        electricNow=metric(tile(metrics,CARD),"ELECTRICIDAD NETA · EXP.","—");
        costNow=metric(tile(metrics,CARD),"COSTO NAFTA · EXP.","—");

        LinearLayout range=col();range.setBackground(bg(CARD,20));
        range.setPadding(14,9,14,12);
        LinearLayout.LayoutParams rangeParams=new LinearLayout.LayoutParams(-1,-2);
        rangeParams.setMargins(0,8,0,5);root.addView(range,rangeParams);
        LinearLayout rangeTop=row();rangeTop.setGravity(Gravity.CENTER_VERTICAL);
        range.addView(rangeTop);
        LinearLayout rangeInfo=col();
        rangeInfo.addView(text("AUTONOMÍA TOTAL · EXPERIMENTAL",15,MUTED,true));
        autonomyNow=text("SIN DATOS",30,WHITE,true);rangeInfo.addView(autonomyNow);
        rangeTop.addView(rangeInfo,new LinearLayout.LayoutParams(0,-2,1f));
        Button adjust=new Button(this);adjust.setText("AJUSTAR");
        adjust.setAllCaps(false);adjust.setOnClickListener(v->adjustAutonomy());
        rangeTop.addView(adjust);
        autonomyDetail=text("Combustible (CI186) + SOC eléctrico; pendiente de calibración",
            13,MUTED,false);range.addView(autonomyDetail);
        LinearLayout auxiliary=col();
        auxiliary.setBackground(bg(CARD,20));
        auxiliary.setPadding(14,10,14,12);
        LinearLayout.LayoutParams auxParams=new LinearLayout.LayoutParams(-1,-2);
        auxParams.setMargins(0,6,0,7);
        root.addView(auxiliary,auxParams);
        LinearLayout auxTitle=row();auxTitle.setGravity(Gravity.CENTER_VERTICAL);
        auxiliary.addView(auxTitle);
        LinearLayout auxText=col();
        auxText.addView(text("BATERÍA AUXILIAR 12 V · IBS / DC-DC",16,MUTED,true));
        aux12vStatus=text("SIN MEDICIÓN VALIDADA · pulsá LEER 12 V",15,WHITE,false);
        auxText.addView(aux12vStatus);
        auxTitle.addView(auxText,new LinearLayout.LayoutParams(0,-2,1f));
        Button readAux=new Button(this);
        readAux.setText("LEER 12 V");readAux.setAllCaps(false);
        readAux.setOnClickListener(v->readAux12v());
        auxTitle.addView(readAux);
        auxiliary.addView(text("IBS=0 NO confirma 0 V ni SOC de 12 V. "+
            "EXT BATT=315 (si son voltios) apunta a ALTA TENSIÓN HV, "+
            "NO a batería auxiliar. No mezclar con 1064 de tracción. "+
            "Lecturas brutas experimentales.",
            12,MUTED,false));
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
        dayStartButton=button(dayButtons,"▶ INICIAR JORNADA",TEAL,this::startDay);
        button(dayButtons,"■ CERRAR JORNADA",RED,this::endDay);
        heading(journey,"02  CLIENTE / TRAYECTO INDEPENDIENTE");
        clientNow=text("Sin trayecto activo",16,WHITE,true);journey.addView(clientNow);
        LinearLayout clientButtons=row();journey.addView(clientButtons);
        clientStartButton=button(clientButtons,
            "+ INICIAR TRAYECTO",BLUE,this::askClient);
        button(clientButtons,"✓ FINALIZAR TRAYECTO",TEAL,this::finishClient);
        LinearLayout recoveryButtons=row();journey.addView(recoveryButtons);
        clientContinueButton=button(recoveryButtons,
            "▶ CONTINUAR CLIENTE",TEAL,this::continueClient);
        clientRecoverButton=button(recoveryButtons,
            "⚠ RECUPERAR / LIBERAR CLIENTE",RED,this::repairClient);
        clientContinueButton.setVisibility(android.view.View.GONE);
        clientRecoverButton.setVisibility(android.view.View.GONE);

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
        heading(advanced,"REGLA CIUDAD / AUTOPISTA · OPENSTREETMAP");
        advanced.addView(text("CIUDAD → EV. AUTOPISTA → HEV SMART o HEV FORZADO. "+
            "La lectura de carretera OSM es aproximada y NO cambia "+
            "la propulsión: solo sugiere. Si activás el asesor, "+
            "se consultan coordenadas GPS en un servicio público "+
            "de OpenStreetMap mientras el cockpit está abierto.",
            13,MUTED,false));
        LinearLayout osmButtons=row();advanced.addView(osmButtons);
        osmEnabledButton=button(osmButtons,
            "OSM · ACTIVAR ASESOR",BLUE,this::toggleOsm);
        osmSocButton=button(osmButtons,
            "AUTOPISTA · HEV SMART",TEAL,this::toggleOsmSoc);
        LinearLayout osmCheckRow=row();advanced.addView(osmCheckRow);
        button(osmCheckRow,"CONSULTAR VÍA OSM · NO ESCRIBE",
            BLUE,()->lookupOsm(true));
        osmStatus=text("OSM no iniciado: sin clasificación de vía.",
            14,WHITE,false);
        advanced.addView(osmStatus);
        updateOsmControls();
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
        hevModeLive=text("ESTADO EV/HEV · esperando 1043 OEM…",
            16,TEAL,true);
        advanced.addView(hevModeLive);
        LinearLayout oneShot=row();advanced.addView(oneShot);
        button(oneShot,"PRUEBA MANUAL · EV → HEV · SOLO EN P",GOLD,
            this::askHevExperiment);
        advanced.addView(text("Comando único 1043=2, sin GPS ni automatismos. "+
            "Requiere P, 0 km/h, doble lectura y confirmación manual. "+
            "Puede ser rechazado por los permisos OEM.",13,MUTED,false));
        hevStatus=text("EV/HEV: aún sin diagnóstico. "+
            "Si el botón no funciona, tocá DIAGNOSTICAR para saber por qué.",14,GOLD,true);
        advanced.addView(hevStatus);
        LinearLayout debugRow=row();advanced.addView(debugRow);
        button(debugRow,"DIAGNOSTICAR · NO ESCRIBE",BLUE,this::diagnoseHev);
        button(debugRow,"VER / COPIAR INFORME",BLUE,this::showHevReport);
        heading(advanced,"PARRILLA LED DELANTERA · EFECTO EN P");
        advanced.addView(text("NUEVO · Registro SOLO LECTURA de 30 segundos "+
            "de la tira LED «horizonte»: LIGHT SHOW, DRL, WELCOME "+
            "y configuración de estacionamiento. Empieza solo en P "+
            "y velocidad 0. No cambia las luces ni la marcha. "+
            "Si comparás P con N, aplicá freno de estacionamiento, "+
            "mantené freno de pie y no toques la pantalla en N. "+
            "Cancelamos el registro si el vehículo se mueve.",
            13,MUTED,false));
        LinearLayout ledButtons=row();advanced.addView(ledButtons);
        button(ledButtons,"REGISTRAR LED 30 S · NO ESCRIBE",
            TEAL,this::recordHorizonLed);
        button(ledButtons,"COPIAR INFORME LED",BLUE,()->{
            if(ledReport.isEmpty()){
                ledStatus.setText("No existe informe. Empezá con REGISTRAR LED.");
                return;
            }
            ClipboardManager clipboard=(ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(
                ClipData.newPlainText("Jetour T1 tira LED P N",ledReport));
            ledStatus.setText("Informe de LED copiado. "+
                "Compartí el texto para comparar P y N.");
        });
        ledStatus=text("LED delantero: sin registro todavía. "+
            "Los códigos de show mode están identificados pero NO validados.",
            13,WHITE,false);
        advanced.addView(ledStatus);
        heading(advanced,"LUCES TRASERAS / BAÚL · SOLO LECTURA");
        advanced.addView(text("Buscamos diferencias entre dos lecturas OEM. "+
            "No se fuerza ninguna luz. Guardá CAPTURA A, accioná "+
            "el interruptor original de las luces o abrí una puerta "+
            "y después CAPTURA B, siempre estacionado.",13,MUTED,false));
        LinearLayout lightButtons=row();advanced.addView(lightButtons);
        button(lightButtons,"CAPTURA A · ANTES",BLUE,()->captureLights(false));
        button(lightButtons,"CAPTURA B · DESPUÉS",TEAL,()->captureLights(true));
        lightStatus=text("Esperando lectura del HAL de luces/puertas. "+
            "El resultado aparece acá; no hay comandos de iluminación habilitados.",
            13,WHITE,false);
        advanced.addView(lightStatus);
        heading(advanced,"LUCES DE LECTURA · SEGUNDA FILA POR ASIENTO");
        advanced.addView(text("NUEVA PRUEBA: área 16 izquierda / 32 centro / "+
            "64 derecha. Consultamos 356519683 (estado) y 356519684 "+
            "(interruptor) por asiento. SOLO LECTURA, sin encenderlas "+
            "por software. Primero APAGADAS; después ENCENDIDAS "+
            "con el botón original.",13,MUTED,false));
        LinearLayout seatButtons=row();advanced.addView(seatButtons);
        button(seatButtons,"ASIENTOS A · APAGADAS",BLUE,
            ()->captureRearSeats(false));
        button(seatButtons,"ASIENTOS B · ENCENDIDAS",TEAL,
            ()->captureRearSeats(true));
        LinearLayout seatCopy=row();advanced.addView(seatCopy);
        button(seatCopy,"COPIAR INFORME ASIENTOS",BLUE,()->{
            if(seatReport.isEmpty()){
                rearSeatStatus.setText("Primero completá A y B.");return;
            }
            ClipboardManager clipboard=(ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(
                ClipData.newPlainText("Jetour T1 luces lectura por asiento",seatReport));
            rearSeatStatus.setText("Informe de luces por asiento copiado. "+
                "Pegalo en el chat para mapear el control OEM.");
        });
        rearSeatStatus=text("Sin comparación por asiento todavía.",
            14,WHITE,false);
        advanced.addView(rearSeatStatus);
        heading(advanced,"CINTURONES TRASEROS · ESTADO Y AVISO OEM");
        advanced.addView(text("Nueva prueba SOLO LECTURA: cinturones "+
            "izquierdo, central y derecho; ocupación; aviso del asiento "+
            "central; volumen general de alarma y frecuencia de pitidos. "+
            "Con el vehículo en P, capturá A con cinturones sueltos, "+
            "abrochá uno y capturá B. Repetí por asiento. "+
            "NO desactiva ni reduce alarmas de seguridad. "+
            "Para cajas, usá el baúl o sujetalas correctamente en lugar "+
            "de anular la advertencia.",13,MUTED,false));
        LinearLayout beltButtons=row();advanced.addView(beltButtons);
        button(beltButtons,"CINTURONES A · SUELTOS",BLUE,
            ()->captureRearBelts(false));
        button(beltButtons,"CINTURONES B · ABROCHADO",TEAL,
            ()->captureRearBelts(true));
        LinearLayout beltCopy=row();advanced.addView(beltCopy);
        button(beltCopy,"COPIAR INFORME CINTURONES",BLUE,()->{
            if(rearBeltReport.isEmpty()){
                rearBeltStatus.setText("Primero completá las capturas A y B.");
                return;
            }
            ClipboardManager clipboard=(ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(
                ClipData.newPlainText("T1 cinturones traseros",rearBeltReport));
            rearBeltStatus.setText("Informe de cinturones copiado.");
        });
        rearBeltStatus=text("Sin comparación de cinturones todavía.",
            14,WHITE,false);
        advanced.addView(rearBeltStatus);
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
        String recovered=T1JourneyStore.reconcileInterrupted(this);
        if(!recovered.isEmpty()&&feedback!=null)
            feedback.setText(recovered);
        SharedPreferences m=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE);
        boolean running=m.getBoolean(T1TripRecorderService.RUNNING,false);
        long ms=System.currentTimeMillis()-m.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
        boolean fresh=running&&ms>=0&&ms<=15000&&
            m.getInt(T1TripRecorderService.SAMPLES,0)>0;
        double km=m.getFloat(T1TripRecorderService.DISTANCE,0f);
        int nf=m.getInt(T1TripRecorderService.SEG_FUEL_COUNT,0),
            ne=m.getInt(T1TripRecorderService.SEG_ELEC_COUNT,0);
        double fRaw=m.getFloat(T1TripRecorderService.SEG_FUEL_RAW_SUM,0f),
            eRaw=m.getFloat(T1TripRecorderService.SEG_ELEC_RAW_SUM,0f);
        double price=m.getFloat(T1TripRecorderService.FUEL_PRICE,2129f);
        boolean client=T1JourneyStore.active(this),
            pending=T1JourneyStore.pending(this);
        T1JourneyMetrics.Result live=client?T1JourneyStore.running(this):null;
        boolean blink=(android.os.SystemClock.elapsedRealtime()/700L)%2==0;
        boolean clientLive=client&&fresh&&live!=null;
        if(dayRecordingLamp!=null){
            dayRecordingLamp.setText(fresh?
                (blink?"● ":"○ ")+"JORNADA GRABANDO":
                running?"◉ ESPERANDO OEM":"○ JORNADA DETENIDA");
            dayRecordingLamp.setTextColor(fresh?
                (blink?RED:0xFF743737):
                running?GOLD:MUTED);
        }
        if(clientRecordingLamp!=null){
            clientRecordingLamp.setText(clientLive?
                (blink?"● ":"○ ")+"CLIENTE GRABANDO":
                pending?"◉ INICIANDO CLIENTE":
                T1JourneyStore.stuck(this)?
                    "⚠ CLIENTE BLOQUEADO":
                    client?"◉ CLIENTE SIN DATOS":
                    "○ CLIENTE DETENIDO");
            clientRecordingLamp.setTextColor(clientLive?
                (blink?RED:0xFF743737):
                (client||pending)?GOLD:MUTED);
        }
        if(dayStartButton!=null)
            dayStartButton.setBackgroundTintList(ColorStateList.valueOf(
                fresh?RED:TEAL));
        if(clientStartButton!=null)
            clientStartButton.setBackgroundTintList(ColorStateList.valueOf(
                clientLive?RED:BLUE));
        if(clientContinueButton!=null){
            String interrupted=T1JourneyStore.interruptedLabel(this);
            clientContinueButton.setVisibility(
                !client&&!pending&&fresh&&!interrupted.isEmpty()?
                    android.view.View.VISIBLE:android.view.View.GONE);
            if(!interrupted.isEmpty())
                clientContinueButton.setText("▶ CONTINUAR "+interrupted);
        }
        if(clientRecoverButton!=null)
            clientRecoverButton.setVisibility(
                T1JourneyStore.stuck(this)?
                    android.view.View.VISIBLE:android.view.View.GONE);
        T1CockpitScope.Value scoped=T1CockpitScope.choose(
            fresh,client,pending,live,km,nf,ne,fRaw,eRaw,price);
        switch(scoped.kind){
            case CLIENT:
                scopeTitle.setText("● CLIENTE EN CURSO · "+T1JourneyStore.activeLabel(this));
                break;
            case DAY:scopeTitle.setText("● JORNADA EN CURSO · SIN CLIENTE");break;
            case PREPARING:
                scopeTitle.setText("◉ PREPARANDO CLIENTE · "+
                    T1JourneyStore.pendingLabel(this));break;
            case UNSYNCED:
                scopeTitle.setText("⚠ DATOS OEM SIN ACTUALIZAR");break;
            default:scopeTitle.setText("○ JORNADA ACTUAL · SIN GRABACIÓN");
        }
        kmNow.setText(f(scoped.km,2)+" km");
        fuelNow.setText(scoped.fuelKnown?
            f(scoped.fuelLiters,3)+" L":"ESPERANDO");
        electricNow.setText(scoped.electricKnown?
            f(scoped.electricNetKwh,3)+" kWh":"ESPERANDO");
        costNow.setText(scoped.fuelKnown?
            "$ "+f(scoped.fuelCost,0):"SIN DATO");
        scopeProgress.setText(scoped.kind==T1CockpitScope.Kind.CLIENT||
            scoped.kind==T1CockpitScope.Kind.DAY?
            "AVANCE EN VIVO · nafta "+scoped.fuelEvents+
            " tramos / electricidad "+scoped.electricEvents+
            " tramos de 500 m · km por velocidad OEM sin calibrar":
            scoped.kind==T1CockpitScope.Kind.PREPARING?
            "0,00 km · esperando la primera muestra fresca de DriveLab (≈ 3 s)":
            scoped.kind==T1CockpitScope.Kind.UNSYNCED?
            "Sin muestras OEM recientes; comprobá que el grabador siga funcionando":
            "Iniciá jornada o trayecto. El último viaje NO se cuenta como actual.");
        if(!running){
            tripStatus.setText("○ JORNADA DETENIDA · último TRIP "+
                (m.getInt(T1TripRecorderService.SAMPLES,0)>0?
                    f(km,2)+" km guardados":"sin historial")+
                " · el nuevo contador está en cero.");
        }else{
            tripStatus.setText((fresh?"● GRABACIÓN CONTINUA":
                "⚠ ESPERANDO OEM · sin muestra fresca")+
                " · "+m.getInt(T1TripRecorderService.SAMPLES,0)+" muestras"+
                " · origen distancia "+m.getString(
                    T1TripRecorderService.DIST_SOURCE,"por verificar"));
        }
        if(client){
            clientNow.setText("● "+T1JourneyStore.activeLabel(this)+
                (live==null?" · esperando lectura reciente":
                    " · "+f(live.km,2)+" km · "+
                    (live.fuelAvailable?f(live.fuelLitersCandidate,3)+
                        " L nafta experimental":"esperando 500 m")));
        }else if(pending){
            clientNow.setText("◉ "+T1JourneyStore.pendingLabel(this)+
                " · 0,00 km · preparando grabación");
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
            (day.fuelKnownTrips>0?"$ "+f(day.fuelCost,0)+
                " nafta exp. ("+day.fuelKnownTrips+" tramos registrados)":
                "Nafta: sin tramo OEM válido"));
        monthNow.setText(month.trips+" viajes · "+f(month.km,1)+" km\n"+
            (month.fuelKnownTrips>0?"$ "+f(month.fuelCost,0)+" nafta exp.":
                "Nafta: sin tramo OEM válido")+
            "\nRed comprada: "+f(month.gridKwh,1)+" kWh ($"+
            f(month.gridCost,0)+") · no imputada");

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
        long age=System.currentTimeMillis()-
            m.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
        if(m.getBoolean(T1TripRecorderService.RUNNING,false)&&age>=0&&age<=15000){
            message("La jornada ya está grabando; no se reinició.");return;
        }
        try{
            startForegroundService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_START));
            message("Iniciando jornada. Esperá la primera muestra (3 s).");
        }catch(Exception ex){message("No se inició: "+ex.getClass().getSimpleName());}
    }
    private void endDay(){
        if(T1JourneyStore.active(this)||T1JourneyStore.pending(this)){
            message("Primero FINALIZÁ o cancelá el trayecto del cliente.");
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
            .setMessage("Empieza en 0,00 km para este cliente; no se reinicia "+
                "la grabación general. Si aún no hay jornada, se inicia también.")
            .setView(name).setNegativeButton("CANCELAR",null)
            .setPositiveButton("INICIAR",(d,w)->{
                if(T1JourneyStore.active(this)||T1JourneyStore.pending(this)){
                    message("Ya hay un trayecto activo o preparándose.");return;
                }
                try{
                    SharedPreferences p=getSharedPreferences(
                        T1TripRecorderService.PREFS,MODE_PRIVATE);
                    long age=System.currentTimeMillis()-
                        p.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
                    boolean live=p.getBoolean(T1TripRecorderService.RUNNING,false)
                        &&age>=0&&age<=15000;
                    if(live){
                        // Immediate checkpoint; do not reset the all-day trip.
                        message(T1JourneyStore.start(this,name.getText().toString()));
                    }else{
                        String status=T1JourneyStore.queue(this,
                            name.getText().toString());
                        startForegroundService(new Intent(this,
                            T1TripRecorderService.class).setAction(
                                T1TripRecorderService.ACTION_START));
                        message(status);
                    }
                }catch(Exception ex){
                    T1JourneyStore.cancelPending(this);
                    message("No se inició: "+ex.getMessage());
                }
            }).show();
    }
    private void continueClient(){
        if(T1JourneyStore.active(this)||T1JourneyStore.pending(this)){
            message("Ya tenés un trayecto activo.");return;
        }
        String label=T1JourneyStore.interruptedLabel(this);
        if(label.isEmpty()){
            message("No hay cliente pendiente para continuar.");return;
        }
        new AlertDialog.Builder(this).setTitle("CONTINUAR "+label)
            .setMessage("Se abre un NUEVO tramo en la jornada actual con "+
                "el mismo nombre. El segmento interrumpido queda guardado "+
                "por separado como PARCIAL. No se inventan kilómetros "+
                "ni energía durante el corte de Android.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CONTINUAR",(d,w)->{
                try{message(T1JourneyStore.resumeInterrupted(this));}
                catch(Exception ex){message("No se pudo continuar: "+
                    ex.getClass().getSimpleName());}
            }).show();
    }
    private void repairClient(){
        if(!T1JourneyStore.stuck(this)){
            message("No hay un cliente de otra jornada bloqueado.");return;
        }
        String recovered=T1JourneyStore.reconcileInterrupted(this);
        if(!T1JourneyStore.stuck(this)){
            message(recovered);return;
        }
        new AlertDialog.Builder(this)
            .setTitle("LIBERAR TRAYECTO INTERRUMPIDO")
            .setMessage("No pude reconstruir automáticamente los datos "+
                "del trayecto anterior. Podés liberar el bloqueo para "+
                "iniciar otro cliente; los archivos CSV anteriores "+
                "NO se borrarán, pero esa parte todavía NO quedará "+
                "contabilizada en el historial.\n\n"+
                T1JourneyStore.recoveryNote(this))
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("LIBERAR BLOQUEO",
                (d,w)->message(T1JourneyStore.releaseStuck(this)))
            .show();
    }
    private void finishClient(){
        if(T1JourneyStore.pending(this)){
            T1JourneyStore.cancelPending(this);
            message("Se canceló el inicio: no había empezado el trayecto.");return;
        }
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
    private void updateSoc(){
        if(socStrategy==null||lastSample==null)return;
        double goal=T1SocModePolicy.socTarget(lastSample.socTargetInt,
            lastSample.socTargetFloat);
        socStrategy.setText("GESTIÓN SOC: "+
            T1SocModePolicy.name(lastSample.socMode)+
            "    |    OBJETIVO: "+
            (Double.isFinite(goal)?f(goal,0)+" %":"SIN DATO")+
            "    |    SOC ACTUAL: "+
            (Double.isFinite(lastSample.socDisplay)?
                f(lastSample.socDisplay,1)+" %":"SIN DATO"));
    }
    private void updateAutonomy(){
        if(autonomyNow==null||lastSample==null)return;
        SharedPreferences p=getSharedPreferences(RANGE_PREFS,MODE_PRIVATE);
        double l100=p.getFloat("fuel_l100",(float)T1ExperimentalRange.DEFAULT_L100),
            fullEv=p.getFloat("full_ev_km",100f);
        T1ExperimentalRange.Estimate e=T1ExperimentalRange.estimate(
            lastSample.fuel,lastSample.socDisplay,l100,fullEv);
        autonomyNow.setText(Double.isFinite(e.totalKm)?
            "~ "+f(e.totalKm,0)+" km":"SIN ESTIMACIÓN TOTAL");
        autonomyDetail.setText("Nafta: "+(e.fuelOk?"~ "+f(e.fuelKm,0)+" km":
            "NO FIABLE / SIN DATO")+"  +  eléctrico: "+
            (e.electricOk?"~ "+f(e.electricKm,0)+" km":"SIN DATO")+
            " · cálculo EXPERIMENTAL con "+f(l100,2)+" L/100 km, "+
            f(fullEv,0)+" km EV a SOC 100 %, reserva 5,3 L. "+
            "No sustituye la autonomía original Jetour.");
    }
    private void adjustAutonomy(){
        LinearLayout form=col();form.setPadding(25,12,25,5);
        form.addView(text("Fórmula experimental: "+
            "km nafta=(litros CI186 − 5,3 L) × 100 / consumo L/100. "+
            "Km EV=(SOC actual / 100) × alcance EV al 100 %. "+
            "Total estimado=suma de ambos, supone utilización secuencial.",
            14,Color.DKGRAY,false));
        SharedPreferences p=getSharedPreferences(RANGE_PREFS,MODE_PRIVATE);
        EditText fuel=new EditText(this);
        fuel.setHint("L/100 km nafta (3 a 20)");
        fuel.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fuel.setText(String.format(Locale.ROOT,"%.2f",p.getFloat("fuel_l100",
            (float)T1ExperimentalRange.DEFAULT_L100)));
        form.addView(fuel);
        EditText ev=new EditText(this);
        ev.setHint("Autonomía EV con SOC 100 % (20 a 180 km)");
        ev.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ev.setText(String.format(Locale.ROOT,"%.0f",
            p.getFloat("full_ev_km",100f)));
        form.addView(ev);
        new AlertDialog.Builder(this).setTitle("AUTONOMÍA EXPERIMENTAL")
            .setView(form).setNegativeButton("CANCELAR",null)
            .setPositiveButton("GUARDAR",(d,w)->{
                try{
                    double l=Double.parseDouble(fuel.getText().toString().replace(",","."));
                    double e=Double.parseDouble(ev.getText().toString().replace(",","."));
                    if(!Double.isFinite(l)||l<3||l>20||
                        !Double.isFinite(e)||e<20||e>180)
                        throw new IllegalArgumentException("Fuera de rango");
                    p.edit().putFloat("fuel_l100",(float)l)
                        .putFloat("full_ev_km",(float)e).apply();
                    updateAutonomy();
                    message("Autonomía experimental ajustada. "+
                        "No modifica los datos del vehículo.");
                }catch(Exception ex){
                    message("No se aplicó: verificá consumo de 3–20 L/100 "+
                        "y autonomía eléctrica de 20–180 km.");
                }
            }).show();
    }
    private void askHevExperiment(){
        long age=lastSample==null?Long.MAX_VALUE:
            System.currentTimeMillis()-lastSample.timestamp;
        boolean permitted=lastSample!=null&&parked()&&
            T1ManualHevPolicy.canRequest(lastSample.gear,
                lastSample.speed,lastSample.powerMode);
        if(!permitted){
            int mode=lastSample==null?-1:lastSample.powerMode;
            String raw=lastSample==null?"sin telemetría OEM":
                "1033="+lastSample.gear+" (P=1); 2148="+
                f(lastSample.speed,2)+" km/h; 1043="+
                mode+" ("+T1ManualHevPolicy.currentMode(mode)+
                "); edad="+age+" ms";
            String reason=lastSample==null?
                "No hay telemetría OEM reciente.":
                T1ManualHevPolicy.preflightReason(
                    lastSample.gear,lastSample.speed,mode,age);
            String diagnostic=reason+"\n\nLECTURAS: "+raw+
                "\nNo hay transacción de escritura en este intento.";
            T1OemDiagnosticLog.begin(this,"Toque de botón EV→HEV; revisión de precondiciones");
            T1OemDiagnosticLog.add(this,
                mode==T1ManualHevPolicy.HEV?"YA_EN_HEV_SIN_ESCRITURA":
                    "BLOQUEO_EN_UI",diagnostic);
            hevStatus.setText(diagnostic);
            new AlertDialog.Builder(this)
                .setTitle(mode==T1ManualHevPolicy.HEV?
                    "YA ESTÁS EN HEV · NO HIZO FALTA ESCRIBIR":
                    "EV→HEV · NO SE ENVIÓ COMANDO")
                .setMessage(diagnostic+
                    "\n\nPara estudiar la transición real, podés "+
                    "usar ENSAYO EV/HEV · SOLO LECTURA y cambiar "+
                    "de modo desde el menú Jetour original.")
                .setPositiveButton("ENTENDIDO",null).show();
            return;
        }
        if(hevPilot.busy()){message("Ya hay una prueba en curso.");return;}
        new AlertDialog.Builder(this)
            .setTitle("EV → HEV · COMANDO EXPERIMENTAL")
            .setMessage("Solo estando ESTACIONADO en P, freno de "+
                "estacionamiento y velocidad cero. Se intentará UNA VEZ "+
                "la orden OEM 1043=2, ya comprobada en P en tu Jetour. "+
                "No está validada circulando. Se comprobará de nuevo "+
                "P, velocidad, EV/EV MAX y la lectura final HEV. "+
                "No realiza cambios por GPS ni en movimiento. "+
                "Si hay un rechazo o un estado inesperado, verificá "+
                "manualmente el modo original del auto.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR UNA VEZ",(d,w)->{
                hevStatus.setText("Intento confirmado. Leyendo VehicleService, "+
                    "P/1033, velocidad y modo; se registrará cada etapa…");
                message("Comprobando dos veces P, velocidad y modo OEM…");
                if(!hevPilot.request(()->visible,report->ui.post(()->{
                    if(!isFinishing()&&!isDestroyed()){
                        hevStatus.setText(report);
                        message(report);lastTelem=0;updateZone();
                        new AlertDialog.Builder(this)
                            .setTitle("RESULTADO EV→HEV · VERIFICADO POR ETAPAS")
                            .setMessage(report+"\n\nAbrí VER / COPIAR INFORME "+
                                "para conocer exactamente dónde se detuvo.")
                            .setPositiveButton("CERRAR",null).show();
                    }
                })))message("Prueba ocupada; no se envió otra orden.");
            }).show();
    }
    private void readAux12v(){
        if(!parked()){
            aux12vStatus.setText("Estacioná en P, velocidad cero y "+
                "esperá telemetría OEM reciente antes de leer 12 V.");
            return;
        }
        if(!aux12vReading.compareAndSet(false,true))return;
        aux12vStatus.setText("Consultando IBS, batería auxiliar y DC-DC…");
        new Thread(()->{
            T1Aux12vReadOnlyProbe.Result result=aux12vProbe.read();
            ui.post(()->{
                aux12vReading.set(false);
                if(isFinishing()||isDestroyed())return;
                aux12vReport=result.show();
                aux12vStatus.setText(result.headline());
                new AlertDialog.Builder(this).setTitle("12 V · LECTURA OEM")
                    .setMessage(aux12vReport)
                    .setNegativeButton("CERRAR",null)
                    .setPositiveButton("COPIAR INFORME",(d,w)->{
                        ClipboardManager clipboard=(ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                        if(clipboard!=null)clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Jetour T1 bateria 12V",aux12vReport));
                    }).show();
            });
        },"T1-12V-IBS-ReadOnly").start();
    }
    /** This button is read-only: uses the same OEM Binder but no setter. */
    private void diagnoseHev(){
        if(hevPilot.busy()){
            hevStatus.setText("Hay un diagnóstico o ensayo EV/HEV en curso.");
            return;
        }
        hevStatus.setText("Consultando 1033 / velocidad / 1043 en modo "+
            "SOLO LECTURA…");
        if(!hevPilot.inspect(()->visible,report->ui.post(()->{
            if(isFinishing()||isDestroyed())return;
            hevStatus.setText(report);
            new AlertDialog.Builder(this).setTitle("DIAGNÓSTICO EV/HEV")
                .setMessage(report+"\n\nSi el botón había fallado, "+
                    "VER / COPIAR INFORME muestra las etapas y errores OEM.")
                .setPositiveButton("CERRAR",null).show();
        })))hevStatus.setText("No se pudo iniciar: diagnóstico ocupado.");
    }
    private void showHevReport(){
        String report=T1OemDiagnosticLog.report(this);
        new AlertDialog.Builder(this).setTitle("INFORME EV/HEV")
            .setMessage(report)
            .setNegativeButton("CERRAR",null)
            .setPositiveButton("COPIAR",(d,w)->{
                ClipboardManager clipboard=(ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(
                    ClipData.newPlainText("DriveLab EV/HEV",report));
                hevStatus.setText("Informe copiado. No contiene "+
                    "coordenadas ni datos personales.");
            }).show();
    }
    private void readThermal(boolean showDetails){
        if(!visible||!thermalReading.compareAndSet(false,true))return;
        new Thread(()->{
            T1ThermalMotorProbe.Snapshot result=thermalProbe.read();
            ui.post(()->{
                thermalReading.set(false);
                if(!visible||isFinishing()||isDestroyed())return;
                lastThermal=result;
                tempEngine.setText(result.engineTemperatureDisplay());
                tempBattery.setText(result.batteryTempRaw);
                // No supported motor-electric RPM or confirmed ON/OFF signal.
                electricMotorStatus.setText("ESTADO SIN VALIDAR");
                electricMotorRpm.setText("RPM: SIN LECTURA");
                if(showDetails){
                    String report=result.report();
                    new AlertDialog.Builder(this)
                        .setTitle("Temperaturas y motor · lecturas OEM")
                        .setMessage(report)
                        .setNegativeButton("CERRAR",null)
                        .setPositiveButton("COPIAR",(d,w)->{
                            ClipboardManager clipboard=(ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                            if(clipboard!=null)clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Jetour T1 temperaturas y motor",report));
                        }).show();
                }
            });
        },"T1-Thermal-Motor-ReadOnly").start();
    }
    private void recordHorizonLed(){
        if(!parked()){
            ledStatus.setText("Registro de LED solo se inicia estacionado "+
                "en P con 0 km/h y telemetría reciente.");
            return;
        }
        if(!ledRecording.compareAndSet(false,true)){
            ledStatus.setText("Registro de LED ya está en curso.");
            return;
        }
        ledReport="";
        ledStatus.setText("REGISTRANDO 30 S · luces/parrilla OEM "+
            "y marcha 1033, sin comandos de escritura…");
        new Thread(()->{
            java.util.ArrayList<T1HorizonLedReadOnlyProbe.Snapshot> frames=
                new java.util.ArrayList<>();
            java.util.ArrayList<Integer> gears=new java.util.ArrayList<>();
            String issue="";
            long deadline=android.os.SystemClock.elapsedRealtime()+30000L;
            for(int i=0;i<16&&
                android.os.SystemClock.elapsedRealtime()<deadline;i++){
                if(!visible){issue="Pantalla cerrada: registro suspendido";break;}
                T1TripTelemetry.Sample reading=new T1TripTelemetry(this).snapshot();
                if(!Double.isFinite(reading.speed)||
                    reading.speed>0.5||reading.speed<0){
                    issue="Vehículo en movimiento o velocidad OEM no "+
                        "verificable; registro detenido sin modificar luces.";
                    break;
                }
                frames.add(ledProbe.read());
                gears.add(reading.gear);
                final int count=frames.size(),gear=reading.gear;
                ui.post(()->{
                    if(!isFinishing()&&!isDestroyed()&&ledStatus!=null)
                        ledStatus.setText("LED · "+count+
                            " muestras; marcha OEM 1033="+gear+
                            "; solamente leyendo. No toques pantalla en N.");
                });
                try{Thread.sleep(1300);}catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                    issue="Registro interrumpido";break;
                }
            }
            String report=T1HorizonLedReadOnlyProbe.Snapshot.transitions(
                frames,gears);
            if(!issue.isEmpty())report+="\n\nAVISO: "+issue;
            final String finalReport=report;
            ui.post(()->{
                ledRecording.set(false);
                ledReport=finalReport;
                if(!visible||isFinishing()||isDestroyed()||ledStatus==null)return;
                ledStatus.setText("LED · Registro completo ("+
                    frames.size()+" muestras). Volvé a P y "+
                    "tocá COPIAR INFORME LED para compartir.");
                new AlertDialog.Builder(this)
                    .setTitle("LED DELANTERO · REGISTRO P/N")
                    .setMessage("Lectura completada. "+
                        "No se modificaron luces ni marchas. "+
                        "Volvé a P antes de usar la pantalla.")
                    .setPositiveButton("CERRAR",null).show();
            });
        },"T1-Horizon-LED-ReadOnly").start();
    }
    private void captureRearBelts(boolean after){
        if(!parked()){
            rearBeltStatus.setText("Estacioná en P y velocidad cero. "+
                "Esta prueba nunca desactiva alertas ni abrocha cinturones.");
            return;
        }
        if(after&&rearBeltBefore==null){
            rearBeltStatus.setText("Primero hacé CINTURONES A · SUELTOS.");
            return;
        }
        if(!beltReading.compareAndSet(false,true))return;
        rearBeltStatus.setText("Leyendo cinturones OEM y ocupación "+
            "por asiento, sin escrituras…");
        new Thread(()->{
            T1RearSeatBeltProbe.Snapshot snap=rearBeltProbe.read();
            ui.post(()->{
                beltReading.set(false);
                if(isFinishing()||isDestroyed())return;
                if(!after){
                    rearBeltBefore=snap;
                    rearBeltReport="CAPTURA A:\n"+snap.format();
                    rearBeltStatus.setText("CAPTURA A guardada. Con el auto "+
                        "en P, abrochá el cinturón trasero a investigar "+
                        "y tocá CINTURONES B · ABROCHADO.");
                }else{
                    rearBeltReport=T1RearSeatBeltProbe.Snapshot.compare(
                        rearBeltBefore,snap);
                    rearBeltBefore=null;
                    rearBeltStatus.setText(rearBeltReport);
                }
            });
        },"T1-Rear-Belts-ReadOnly").start();
    }
    private void captureRearSeats(boolean after){
        if(!parked()){
            rearSeatStatus.setText("Esta prueba requiere P, velocidad cero "+
                "y lectura OEM reciente. Sin escrituras.");
            return;
        }
        if(after&&seatBefore==null){
            rearSeatStatus.setText("Primero capturá A con luces traseras APAGADAS.");
            return;
        }
        if(!seatReading.compareAndSet(false,true))return;
        rearSeatStatus.setText("Consultando estado e interruptores OEM "+
            "por área de asiento · SOLO LECTURA…");
        new Thread(()->{
            T1RearSeatLightsProbe.Snapshot snap=seatProbe.read();
            ui.post(()->{
                seatReading.set(false);
                if(isFinishing()||isDestroyed())return;
                if(!after){
                    seatBefore=snap;
                    seatReport="CAPTURA A:\n"+snap.summary();
                    rearSeatStatus.setText("CAPTURA A guardada. Ahora "+
                        "encendé las luces traseras con el interruptor original "+
                        "y tocá ASIENTOS B · ENCENDIDAS.");
                }else{
                    seatReport=T1RearSeatLightsProbe.Snapshot.compare(seatBefore,snap);
                    seatBefore=null;
                    rearSeatStatus.setText(seatReport);
                }
            });
        },"T1-Rear-Seats-ReadOnly").start();
    }
    private void captureLights(boolean after){
        if(!parked()){
            lightStatus.setText("Para la prueba de luces, primero estacioná "+
                "en P con velocidad 0 y esperá una lectura reciente.");
            return;
        }
        if(after&&lightBefore==null){
            lightStatus.setText("Primero hacé CAPTURA A con el botón OEM "+
                "en su estado inicial.");
            return;
        }
        if(!lightReading.compareAndSet(false,true))return;
        lightStatus.setText("Leyendo señales de luces y puertas del ALCarService… "+
            "(SIN escrituras)");
        new Thread(()->{
            T1CabinLightReadOnlyProbe.Snapshot snap=lightProbe.read();
            ui.post(()->{
                lightReading.set(false);
                if(isFinishing()||isDestroyed())return;
                if(!after){
                    lightBefore=snap;
                    lightStatus.setText("CAPTURA A GUARDADA · "+
                        "cambiá el interruptor de luz trasera/portón original "+
                        "y tocá CAPTURA B.\n"+snap.describe());
                }else{
                    lightStatus.setText(
                        T1CabinLightReadOnlyProbe.Snapshot.compare(
                            lightBefore,snap));
                    lightBefore=null;
                }
            });
        },"T1-Interior-Lighting-ReadOnly").start();
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
        if(hevModeLive!=null){
            long age=lastSample==null?Long.MAX_VALUE:
                System.currentTimeMillis()-lastSample.timestamp;
            String preflight=lastSample==null?
                "SIN DATOS OEM · se espera una muestra nueva":
                T1ManualHevPolicy.preflightReason(
                    lastSample.gear,lastSample.speed,mode,age);
            hevModeLive.setText("MODO ACTUAL: "+
                T1ManualHevPolicy.currentMode(mode)+
                " · 1043="+mode+"\n"+preflight);
        }
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
