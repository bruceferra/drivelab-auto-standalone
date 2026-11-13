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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** T1 cockpit: trips, manual OEM controls, offline road advisory and
 * EXPLICIT user-opted automated OEM1043 mode selection, delegated to an
 * independent foreground service with strict OEM preflight and readback. */
public final class T1PremiumCockpitActivity extends T1ImmersiveActivity {
    private static final int BG=0xFF091421,CARD=0xFF142639,WHITE=0xFFF3F8FD,
        MUTED=0xFF9BB0C1,TEAL=0xFF14AFA7,BLUE=0xFF2B789B,
        GOLD=0xFFFFC367,RED=0xFFB65754;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading=new AtomicBoolean(false);
    private boolean visible=false;
    private long lastTelem=0,lastTotals=0;
    private T1TripTelemetry.Sample lastSample;
    // Compact OEM gauges: keep fuel, SOC, engine and HV battery, no speedometer.
    private T1PremiumInstrumentView gauge;

    private TextView dayRecordingLamp,clientRecordingLamp,secondRecordingLamp;
    private Button dayStartButton,dayPauseButton,dayEndButton,
        clientStartButton,clientPauseButton,clientEndButton,
        secondStartButton,secondPauseButton,secondEndButton,clientRecoverButton;
    // 0=total; 1=Viaje 1; 2=Viaje 2. Selection only affects display,
    // never the independent recording checkpoint of either slot.
    private int selectedScope=1;
    private LinearLayout totalScopeCard,client1ScopeCard,client2ScopeCard;
    private TextView totalScopeTitle,totalScopeKm,secondScopeTitle,secondScopeKm,osmBanner;
    private LinearLayout secondary,historyPickList,historyMasterList,historyDetailRow;
    // Full master CSV scans are strictly BACKGROUND. Rendering history every
    // five seconds must never read large CSVs on the UI thread.
    private volatile ArrayList<T1MasterTripHistory.Entry> cachedMasterHistory=
        new ArrayList<>();
    private long lastHistoryAuditAt=0L;
    private TextView historyDetailTitle,historyRepairStatus;
    private String selectedHistoryKey="";
    private final AtomicBoolean historyRepairBusy=new AtomicBoolean(false);
    private TextView tripStatus,kmNow,evKmNow,consumptionNow,clientSyncStatus,
        fuelNow,electricNow,costNow,clientNow,
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
    private Button modeEvButton,modeHevButton,modeMaxButton,autoModeButton;
    private TextView autoModeLive;
    private TextView modeAtTop,osmStatus,vehicleModeStatus,rawSignalsStatus;
    private Button osmEnabledButton,osmSocButton;
    private TextView offlineMapStatus;
    private final AtomicBoolean mapDownloading=new AtomicBoolean(false);
    private T1OsmRoadAdvisor.Result osmLast;
    private final AtomicBoolean osmReading=new AtomicBoolean(false);
    private long lastOsmQuery=0;
    private final T1RoadTransitionGate roadTransitionGate=new T1RoadTransitionGate();
    private TextView roadTransitionPopup;
    private final Runnable hideRoadTransitionPopup=()->{
        if(roadTransitionPopup!=null){
            roadTransitionPopup.animate().cancel();
            roadTransitionPopup.animate().alpha(0f).setDuration(200L)
                .withEndAction(()->{
                    if(roadTransitionPopup!=null)
                        roadTransitionPopup.setVisibility(android.view.View.GONE);
                }).start();
        }
    };
    private static final String OSM_PREFS="t1_osm_advisory_opt_in";
    private static final String OSM_ENABLED="osm_lookup_enabled";
    private static final String OSM_FORCED="motorway_soc_forced";
    private T1CabinLightReadOnlyProbe lightProbe;
    private T1CabinLightReadOnlyProbe.Snapshot lightBefore;
    private T1RearSeatLightsProbe seatProbe;
    private T1RearSeatLightsProbe.Snapshot seatBefore;
    private String seatReport="";
    private String lightReport="";
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
                        if(gauge!=null)gauge.update(
                            valid(s.speed,0,220),valid(s.fuel,0,70),
                            valid(s.socDisplay,0,100),s.powerMode,
                            valid(s.rpm,0,12000),
                            valid(s.current,-1500,1500));
                        updateSoc();
                        updateAutonomy();
                        updateZone();
                        updateTopModes();
                        updateVehicleOemSignals();
                        if(osmEnabled()&&
                            System.currentTimeMillis()-lastOsmQuery>10000)
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
        selectedScope=Math.max(0,Math.min(2,getSharedPreferences(
            "t1_cockpit_selected_scope",MODE_PRIVATE).getInt("slot",1)));
        draw();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;lastTelem=0;lastTotals=0;
        T1OptimizedAutoService.ensureRunning(this);
        ensureContinuousTrip();
        // Recheck every closed client and JOURNADA on startup. Keep raw CSVs.
        runHistoryRepair(false);
        T1ClientTripSync.trigger(this,false);
        if(T1OfflineRoadMap.checkDue(this))updateOfflineMap(false);
        ui.removeCallbacks(ticker);ui.post(ticker);
        // Do not re-stamp a road observed before leaving the cockpit.
        if(osmEnabled()){
            if(lastOsmQuery==0||System.currentTimeMillis()-lastOsmQuery>45000L){
                osmLast=null;
                lastOsmQuery=0;
            }
            showOsmResult();
        }
    }
    private void ensureContinuousTrip(){
        SharedPreferences master=getSharedPreferences(
            T1TripRecorderService.PREFS,MODE_PRIVATE);
        boolean desired=master.getBoolean(T1TripRecorderService.DESIRED,
            master.getBoolean(T1TripRecorderService.RUNNING,false)||
                T1JourneyStore.active(this));
        if(!desired||master.getBoolean(T1TripRecorderService.PAUSED,false))return;
        long elapsed=System.currentTimeMillis()-
            master.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
        if(elapsed>=0&&elapsed<=15000&&
            master.getBoolean(T1TripRecorderService.RUNNING,false))return;
        try{
            startForegroundService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_RESTORE));
        }catch(RuntimeException ex){
            master.edit().putString(T1TripRecorderService.RESTORE_STATUS,
                "Android impidió el auto-reinicio: "+
                ex.getClass().getSimpleName()+
                ". Se mantiene abierta la jornada sin borrar datos.")
                .apply();
        }
    }
    @Override protected void onPause(){
        visible=false;
        ui.removeCallbacks(ticker);
        ui.removeCallbacks(hideRoadTransitionPopup);
        if(roadTransitionPopup!=null){
            roadTransitionPopup.animate().cancel();
            roadTransitionPopup.setVisibility(android.view.View.GONE);
        }
        super.onPause();
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
    private String getPackageVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "desconocida";}
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
            " (1043="+actual+"). "+
            (T1OptimizedAutoService.enabled(this)?
                "MODO OPTIMIZADO AUTO ON · controles OEM verificados":
                "MODO MANUAL · modo optimizado apagado"):
            "MODO OEM · sin lectura reciente; sin escrituras.");
        updateAutoModeUi();
    }
    /** One tap = one manual OEM request. No confirmation or result dialogs.
     * All direct OEM gear/speed/mode checks remain inside the pilot.
     */
    private void askTripleMode(int target){
        String targetName=T1ManualHevPolicy.currentMode(target);
        if(lastSample==null){
            modeAtTop.setText("SIN TELEMETRÍA OEM · no se envió "+targetName);
            return;
        }
        long age=System.currentTimeMillis()-lastSample.timestamp;
        if(age<0||age>10000||
            !T1DriveModeGate.allowed(lastSample.gear,lastSample.speed)){
            modeAtTop.setText("NO ENVIADO "+targetName+
                " · marcha/velocidad OEM no válidas ("+
                lastSample.gear+"/"+f(lastSample.speed,1)+")");
            return;
        }
        if(lastSample.powerMode==target){
            modeAtTop.setText("ACTUAL: "+targetName+" · sin cambios.");
            return;
        }
        if(threeModePilot.busy()||hevPilot.busy()){
            modeAtTop.setText("Otra orden OEM está en curso.");
            return;
        }
        modeAtTop.setText("ENVIANDO "+targetName+" · verificando 1043…");
        if(!threeModePilot.request(target,()->visible,
                result->ui.post(()->{
            if(!visible||isFinishing()||isDestroyed())return;
            modeAtTop.setText(result);
            lastTelem=0;
        })))modeAtTop.setText("Orden ocupada: "+targetName+" no enviado.");
    }
    private void updateAutoModeUi(){
        boolean enabled=T1OptimizedAutoService.enabled(this);
        if(autoModeButton!=null){
            autoModeButton.setText(enabled?"AUTO · ON":"AUTO · OFF");
            autoModeButton.setBackgroundTintList(
                ColorStateList.valueOf(enabled?TEAL:RED));
        }
        if(autoModeLive!=null)
            autoModeLive.setText(T1OptimizedAutoService.status(this));
    }
    private void toggleAutomaticMode(){
        if(T1OptimizedAutoService.enabled(this)){
            T1OptimizedAutoService.disable(this);
            updateAutoModeUi();
            showOsmResult();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("MODO OPTIMIZADO · ACTIVAR")
            .setMessage("DriveLab aplicará automáticamente EV, HEV o EV MAX "+
                "con el mapa OFFLINE y los perfiles guardados, incluso en "+
                "segundo plano. Requiere GPS preciso, tres lecturas "+
                "estables, modo OEM válido y doble comprobación de P/0 "+
                "o D con velocidad válida. SMART/FORZADO y regeneración "+
                "se mantienen como preferencias: NO escribe todavía "+
                "1040/1041/1046. Si 1043 no confirma, AUTO se desactiva "+
                "sin repetir órdenes. Podés apagarlo aquí o desde la "+
                "notificación del servicio.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                String result=T1OptimizedAutoService.activate(this);
                updateAutoModeUi();
                showOsmResult();
                message(result);
                if(T1OptimizedAutoService.enabled(this))
                    lookupOsm(true);
            }).show();
    }
    private void updateOsmControls(){
        if(osmEnabledButton==null)return;
        osmEnabledButton.setText(osmEnabled()?
            "OSM OFFLINE · DESACTIVAR":"OSM OFFLINE · ACTIVAR");
        osmSocButton.setText(motorwayForced()?
            "AUTOPISTA · HEV FORZADO":"AUTOPISTA · HEV SMART");
        if(osmBanner!=null){
            osmBanner.setVisibility(osmEnabled()?
                android.view.View.VISIBLE:android.view.View.GONE);
            if(osmEnabled()&&osmLast==null)
                osmBanner.setText("OSM ACTIVO · esperando GPS. "+
                    "Tocá para ver el motivo o guardar zona EV/HEV.");
        }
    }
    private void toggleOsm(){
        if(osmEnabled()){
            getSharedPreferences(OSM_PREFS,MODE_PRIVATE)
                .edit().putBoolean(OSM_ENABLED,false).apply();
            updateOsmControls();
            ui.removeCallbacks(hideRoadTransitionPopup);
            if(roadTransitionPopup!=null){
                roadTransitionPopup.animate().cancel();
                roadTransitionPopup.setVisibility(android.view.View.GONE);
            }
            osmStatus.setText("Asesor OSM desactivado. Sin envíos GPS.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("ASESOR OSM OFFLINE · SOLO SUGERENCIAS")
            .setMessage("Usa exclusivamente el mapa de Provincia y CABA "+
                "que descargás una vez desde el servidor de DriveLab Uber. "+
                "NO consulta Overpass ni envía tu GPS a terceros. "+
                "Si el mapa todavía no está instalado, presioná "+
                "DESCARGAR MAPA PBA+CABA en Diagnósticos. "+
                "Las sugerencias NO cambian EV/HEV ni SOC.")
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
            osmStatus.setText("OSM sin clasificación. El asesor NO guarda zonas "+
                "ni cambia el vehículo; usá GUARDAR ZONA EV/HEV debajo.");
            if(osmBanner!=null&&osmEnabled())
                osmBanner.setText("OSM ACTIVO · esperando clasificación de la vía");
            return;
        }
        T1OptimizedModePolicy.Road category;
        try{category=T1OptimizedModePolicy.Road.valueOf(osmLast.road.name());}
        catch(Exception ignored){category=T1OptimizedModePolicy.Road.UNKNOWN;}
        T1OptimizedModeStore.markReading(this,category,
            osmLast.roadTag,osmLast.detail);
        double soc=lastSample==null?Double.NaN:lastSample.socDisplay;
        T1OptimizedModePolicy.Decision decision=
            T1OptimizedModePolicy.evaluate(category,
                T1OptimizedModeStore.get(this,category),soc);
        boolean auto=T1OptimizedAutoService.enabled(this);
        String suggestion=auto?
            decision.summary().replace("SOLO SUGERENCIA",
                "PERFIL 1043 AUTOMÁTICO"):
            decision.summary();
        String road=T1OptimizedModePolicy.roadName(category);
        osmStatus.setText("VÍA: "+road+" · "+osmLast.explain()+
            "\nMODO OPTIMIZADO · "+suggestion+
            (auto?"\nAUTO ON: propulsión 1043 activa por mapa tras "+
                "confirmación OEM. SOC forzado/SMART y regeneración "+
                "NO se escriben todavía.\n"+
                T1OptimizedAutoService.status(this):
                "\nAUTO OFF · solo sugerencia; tocá AUTO ON para "+
                "aplicar EV/HEV/EV MAX en la Jetour."));
        if(osmBanner!=null&&osmEnabled())
            osmBanner.setText(category==T1OptimizedModePolicy.Road.UNKNOWN?
                "OSM · DESCONOCIDO · "+shortOsmProblem(osmLast.detail)+
                    " · TOCÁ PARA DIAGNÓSTICO":
                "OSM OFFLINE · "+road+" · "+suggestion+
                    " · TOCÁ PARA DETALLES");
    }
    private String shortOsmProblem(String detail){
        if(detail==null)return "SIN CAUSA";
        String why=detail.toLowerCase(Locale.ROOT);
        if(why.contains("no instalado"))return "FALTA DESCARGAR MAPA";
        if(why.contains("instalando")||why.contains("descargando"))
            return "MAPA DESCARGÁNDOSE";
        if(why.contains("permiso"))return "FALTA PERMISO GPS";
        if(why.contains("sin gps")||why.contains("gps reciente")||
            why.contains("gps desactivado")||why.contains("precisión"))
            return "REVISAR GPS / PRECISIÓN";
        if(why.contains("sqlite"))return "ERROR EN MAPA LOCAL";
        if(why.contains("sin calle")||why.contains("no se encontró"))
            return "SIN CALLE CERCA";
        if(why.contains("paralela")||why.contains("ambigua"))
            return "VÍAS AMBIGUAS";
        return "VER DETALLES";
    }
    /** Read-only road transition indicator. Stays at top even on scroll.
     * Never intercepts touch, never sends OEM commands, never needs an
     * Android overlay permission or opens a blocking dialog. */
    private void showRoadTransitionIfChanged(T1OsmRoadAdvisor.Result observed){
        if(!visible||!osmEnabled()||observed==null||
            roadTransitionPopup==null)return;
        T1OptimizedModePolicy.Road category;
        try{category=T1OptimizedModePolicy.Road.valueOf(observed.road.name());}
        catch(Exception ignored){category=T1OptimizedModePolicy.Road.UNKNOWN;}
        T1OptimizedModePolicy.Road entered=roadTransitionGate.observe(category);
        if(entered==null)return;
        String label=T1RoadTransitionGate.popupText(entered);
        // Short labels only; detailed UNKNOWN reasons stay available on
        // tapping the diagnostic OSM banner, not in the temporary popup.
        roadTransitionPopup.animate().cancel();
        ui.removeCallbacks(hideRoadTransitionPopup);
        roadTransitionPopup.setText(label);
        roadTransitionPopup.setBackground(bg(
            entered==T1OptimizedModePolicy.Road.UNKNOWN?0xFF4A5566:
            entered==T1OptimizedModePolicy.Road.MOTORWAY?0xFF15527C:
            entered==T1OptimizedModePolicy.Road.ROAD?0xFF6C4C1D:
            0xFF126358,17));
        roadTransitionPopup.setAlpha(1f);
        roadTransitionPopup.setVisibility(android.view.View.VISIBLE);
        roadTransitionPopup.bringToFront();
        roadTransitionPopup.announceForAccessibility(label);
        ui.postDelayed(hideRoadTransitionPopup,1700L);
    }
    private void openWhatIf(){
        startActivity(new Intent(this,T1WhatIfActivity.class));
    }
    private void openOptimizedMode(){
        Intent intent=new Intent(this,T1OptimizedModeActivity.class);
        if(lastSample!=null){
            intent.putExtra("soc",lastSample.socDisplay);
            intent.putExtra("soc_at",lastSample.timestamp);
        }
        startActivity(intent);
    }
    private void lookupOsm(boolean userTriggered){
        if(osmStatus==null||!visible)return;
        if(!userTriggered&&!osmEnabled())return;
        if(!osmReading.compareAndSet(false,true))return;
        lastOsmQuery=System.currentTimeMillis();
        android.location.Location loc=T1ModeZoneStore.location(this);
        osmStatus.setText("OSM offline · buscando GPS reciente; sin red.");
        if(osmBanner!=null&&osmEnabled())
            osmBanner.setText("OSM OFFLINE · localizando y clasificando vía…");
        if(!T1OfflineRoadMap.installed(this)){
            osmReading.set(false);
            osmLast=new T1OsmRoadAdvisor.Result(
                T1OsmRoadAdvisor.Road.UNKNOWN,"","",Double.NaN,
                mapDownloading.get()?"Mapa offline descargándose; "+
                    "se clasificará al terminar.":
                    "Mapa offline no instalado en Receiver. "+
                    "Entrá a ZONAS / DIAGNÓSTICOS y descargá PBA+CABA. "+
                    "La copia privada de Uber Driver no es accesible desde esta app.");
            showOsmResult();
            showRoadTransitionIfChanged(osmLast);
            return;
        }
        // Match offline-map tolerance, not the old 35m Overpass restriction.
        if(loc!=null&&loc.hasAccuracy()&&loc.getAccuracy()<=65f&&
            android.os.SystemClock.elapsedRealtimeNanos()-
                loc.getElapsedRealtimeNanos()<=45000000000L){
            runOsmQuery(loc);return;
        }
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=
                android.content.pm.PackageManager.PERMISSION_GRANTED){
            osmReading.set(false);
            osmLast=new T1OsmRoadAdvisor.Result(
                T1OsmRoadAdvisor.Road.UNKNOWN,"","",Double.NaN,
                "Falta permiso GPS de Receiver. Habilitá Ubicación "+
                "para esta app Android y probá CONSULTAR VÍA OSM.");
            showOsmResult();
            showRoadTransitionIfChanged(osmLast);
            return;
        }
        try{
            android.location.LocationManager mgr=(android.location.LocationManager)
                getSystemService(LOCATION_SERVICE);
            if(mgr==null)throw new IllegalStateException("Sin LocationManager");
            String provider=mgr.isProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER)?
                android.location.LocationManager.GPS_PROVIDER:
                mgr.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)?
                android.location.LocationManager.NETWORK_PROVIDER:null;
            if(provider==null)throw new IllegalStateException("GPS desactivado");
            final long attempt=lastOsmQuery;
            mgr.requestSingleUpdate(provider,new android.location.LocationListener(){
                @Override public void onLocationChanged(android.location.Location fresh){
                    if(!osmReading.get()||!visible||lastOsmQuery!=attempt)return;
                    runOsmQuery(fresh);
                }
            },getMainLooper());
            ui.postDelayed(()->{
                if(lastOsmQuery!=attempt||!osmReading.compareAndSet(true,false))
                    return;
                osmLast=new T1OsmRoadAdvisor.Result(
                    T1OsmRoadAdvisor.Road.UNKNOWN,"","",Double.NaN,
                    "Sin GPS reciente en 16 s. Revisá permisos Android y GPS "+
                    "y reintentá CONSULTAR VÍA OSM.");
                showOsmResult();
                showRoadTransitionIfChanged(osmLast);
            },16000L);
        }catch(Exception ex){
            osmReading.set(false);
            osmLast=new T1OsmRoadAdvisor.Result(
                T1OsmRoadAdvisor.Road.UNKNOWN,"","",Double.NaN,
                "GPS no disponible: "+ex.getClass().getSimpleName()+
                ". Revisá ubicación del vehículo.");
            showOsmResult();
            showRoadTransitionIfChanged(osmLast);
        }
    }
    private void runOsmQuery(android.location.Location loc){
        if(loc==null){osmReading.set(false);return;}
        final long attempt=lastOsmQuery;
        new Thread(()->{
            // Offline SQLite indexed road geometry ONLY: no Overpass calls.
            T1OsmRoadAdvisor.Result result=
                T1OfflineRoadMap.roadAt(getApplicationContext(),loc);
            ui.post(()->{
                if(attempt!=lastOsmQuery)return;
                osmReading.set(false);
                if(!visible||isFinishing()||isDestroyed())return;
                osmLast=result;
                showOsmResult();
                showRoadTransitionIfChanged(result);
            });
        },"T1-OSM-Offline-SQLite-ReadOnly").start();
    }
    private void updateOfflineMap(boolean manual){
        if(!manual&&!T1OfflineRoadMap.checkDue(this))return;
        if(!manual&&!T1OfflineRoadMap.automaticRetryAllowed(this))return;
        if(!mapDownloading.compareAndSet(false,true))return;
        if(!manual&&android.os.Build.VERSION.SDK_INT>=23){
            android.net.ConnectivityManager cm=(android.net.ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);
            if(cm==null||cm.getActiveNetwork()==null||cm.isActiveNetworkMetered()){
                mapDownloading.set(false);
                return; // No large automatic download on cellular data.
            }
        }
        if(!manual)T1OfflineRoadMap.markAutoAttempt(this);
        if(offlineMapStatus!=null)offlineMapStatus.setText(
            "MAPA · consultando manifiesto de DriveLab Uber…");
        new Thread(()->{
            String failure=null;
            try{
                T1OfflineRoadMap.install(getApplicationContext(),(msg,pct)->
                    ui.post(()->{
                        if(offlineMapStatus!=null)
                            offlineMapStatus.setText(msg+" · "+pct+" %");
                    }));
            }catch(Exception ex){
                failure=ex.getMessage();
                if(failure==null||failure.trim().isEmpty())
                    failure=ex.getClass().getSimpleName();
            }
            String outcome=failure;
            ui.post(()->{
                mapDownloading.set(false);
                if(offlineMapStatus!=null)offlineMapStatus.setText(outcome==null?
                    T1OfflineRoadMap.status(this):
                    "MAPA SIN ACTUALIZAR · "+outcome+
                    " · se conserva versión anterior si existía.");
                if(outcome==null&&osmEnabled()&&visible){
                    lastOsmQuery=0;
                    lookupOsm(false);
                }
            });
        },"T1-PBA-CABA-Map-Install").start();
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
    private TextView compactMetric(LinearLayout parent,String label,String value){
        LinearLayout container=tile(parent,CARD);
        container.setPadding(9,5,9,5);
        TextView descriptor=text(label,10,MUTED,false);
        descriptor.setSingleLine(true);
        descriptor.setEllipsize(android.text.TextUtils.TruncateAt.END);
        container.addView(descriptor);
        TextView number=text(value,20,WHITE,true);
        container.addView(number);
        return number;
    }
    private Button compactButton(LinearLayout parent,String caption,int color,Runnable run){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(caption);b.setTextSize(13);
        b.setTextColor(WHITE);
        // Disabled journey actions visibly stay GRAY; never look tappable.
        b.setBackgroundTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_enabled},
                        new int[]{-android.R.attr.state_enabled}},
            new int[]{color,0xFF45525E}));
        b.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,46,1f);
        lp.setMargins(2,1,2,1);parent.addView(b,lp);
        return b;
    }
    private LinearLayout controlCard(LinearLayout parent,String title,int slot){
        LinearLayout card=col();
        card.setPadding(5,3,5,3);
        card.setBackground(bg(CARD,13));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.setMargins(2,2,2,2);
        parent.addView(card,lp);
        return card;
    }
    private LinearLayout scopeCard(LinearLayout parent,int scope){
        LinearLayout card=col();
        card.setPadding(9,5,9,5);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.setMargins(2,2,2,2);
        parent.addView(card,lp);
        card.setOnClickListener(v->selectScope(scope));
        return card;
    }
    private void selectScope(int which){
        selectedScope=which;
        getSharedPreferences("t1_cockpit_selected_scope",MODE_PRIVATE)
            .edit().putInt("slot",which).apply();
        updateStats();
    }
    private void refreshScopeSelection(){
        if(totalScopeCard!=null)
            totalScopeCard.setBackground(bg(selectedScope==0?0xFF17546D:CARD,13));
        if(client1ScopeCard!=null)
            client1ScopeCard.setBackground(bg(selectedScope==1?0xFF17546D:CARD,13));
        if(client2ScopeCard!=null)
            client2ScopeCard.setBackground(bg(selectedScope==2?0xFF17546D:CARD,13));
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
        title.addView(text("COCKPIT · build OEM "+getPackageVersion(),12,MUTED,false));
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button microphone=new Button(this);
        microphone.setAllCaps(false);
        microphone.setTextSize(12);
        microphone.setText("🎤 DESTINO");
        microphone.setContentDescription("Dictar destino para Waze del clúster");
        microphone.setOnClickListener(v->startActivity(new Intent(this,
            T1VoiceRouteActivity.class)));
        top.addView(microphone,new LinearLayout.LayoutParams(-2,52));
        Button pendingTests=new Button(this);
        pendingTests.setAllCaps(false);
        pendingTests.setTextSize(12);
        pendingTests.setText("PENDIENTES");
        pendingTests.setOnClickListener(v->startActivity(new Intent(this,
            T1ValidationWizardActivity.class)));
        top.addView(pendingTests,new LinearLayout.LayoutParams(-2,52));
        Button displaySettings=new Button(this);
        displaySettings.setAllCaps(false);
        displaySettings.setTextSize(12);
        displaySettings.setText("PANTALLA");
        displaySettings.setOnClickListener(v->openDisplaySettings());
        top.addView(displaySettings,new LinearLayout.LayoutParams(-2,52));
        Button simulator=new Button(this);
        simulator.setAllCaps(false);
        simulator.setTextSize(12);
        simulator.setText("SIMULADOR");
        simulator.setOnClickListener(v->openWhatIf());
        top.addView(simulator,new LinearLayout.LayoutParams(-2,52));
        autoModeButton=new Button(this);
        autoModeButton.setAllCaps(false);
        autoModeButton.setTextSize(13);
        autoModeButton.setText("AUTO · OFF");
        autoModeButton.setOnClickListener(v->toggleAutomaticMode());
        top.addView(autoModeButton,new LinearLayout.LayoutParams(-2,52));
        Button optimized=new Button(this);
        optimized.setAllCaps(false);
        optimized.setTextSize(12);
        optimized.setText("MODO OPTIMIZADO");
        optimized.setOnClickListener(v->openOptimizedMode());
        top.addView(optimized,new LinearLayout.LayoutParams(-2,52));
        Button back=new Button(this);
        back.setText("VOLVER");back.setTextSize(12);
        back.setAllCaps(false);back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(-2,52));

        LinearLayout modes=row();root.addView(modes);
        modeEvButton=topModeButton(modes,"EV",1);
        modeHevButton=topModeButton(modes,"HEV",2);
        modeMaxButton=topModeButton(modes,"EV MAX",3);
        modeAtTop=text("MODO OEM: esperando 1043 · controles manuales P o D",
            14,MUTED,false);
        root.addView(modeAtTop);
        autoModeLive=text("MODO OPTIMIZADO OFF · sin comandos",13,TEAL,true);
        root.addView(autoModeLive);
        updateAutoModeUi();
        vehicleModeStatus=text("MODO CONDUCCIÓN · 2053: esperando OEM   |   "+
            "COMBUSTIBLE · 1079: esperando OEM",12,WHITE,true);
        vehicleModeStatus.setSingleLine(true);
        vehicleModeStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        vehicleModeStatus.setOnClickListener(v->showRawVehicleSignals());
        root.addView(vehicleModeStatus);
        osmBanner=text("OSM · esperando vía…",12,0xFFB3E9F1,true);
        osmBanner.setSingleLine(true);
        osmBanner.setEllipsize(android.text.TextUtils.TruncateAt.END);
        osmBanner.setOnClickListener(v->{
            String detail=osmLast==null?"Todavía no hay lectura del GPS.":
                osmLast.explain();
            String report=T1OfflineRoadMap.status(this)+"\n"+
                "Asesor: "+(osmEnabled()?"ACTIVADO":"DESACTIVADO")+
                "\nClasificación: "+(osmLast==null?"NO DISPONIBLE":
                    osmLast.road.name())+
                "\n"+detail+"\n"+
                "Modo optimizado solo lectura; sin comandos a vehículo. "+
                "Sin coordenadas GPS, nombres ni VIN.";
            new AlertDialog.Builder(this)
                .setTitle("DIAGNÓSTICO · OSM OFFLINE")
                .setMessage(report+"\n\n"+
                    "Si falta el mapa, descargalo por Wi-Fi. "+
                    "Tocá COMPARTIR para enviar el informe QR sin copiar nada.")
                .setNegativeButton("CERRAR",null)
                .setNeutralButton("COMPARTIR",(d,w)->
                    shareDiagnostic("osm_offline",report,offlineMapStatus))
                .setPositiveButton("DESCARGAR MAPA",(d,w)->updateOfflineMap(true))
                .show();
        });
        root.addView(osmBanner);
        osmBanner.setVisibility(osmEnabled()?
            android.view.View.VISIBLE:android.view.View.GONE);
        // Three independent button sets: JORNADA, VIAJE 1 and VIAJE 2.
        // Disabled is gray, only available actions retain colored tint.
        LinearLayout controls=row();root.addView(controls);
        LinearLayout clientControl=controlCard(controls,"",1);
        clientRecordingLamp=text("○ VIAJE 1 DETENIDO",13,MUTED,true);
        clientControl.addView(clientRecordingLamp);
        LinearLayout clientActions=row();clientControl.addView(clientActions);
        clientStartButton=compactButton(clientActions,"INICIAR",BLUE,this::askClient);
        clientPauseButton=compactButton(clientActions,"PAUSAR",GOLD,this::pauseClient);
        clientEndButton=compactButton(clientActions,"TERMINAR",RED,this::finishClient);

        LinearLayout dayControl=controlCard(controls,"",0);
        dayRecordingLamp=text("○ VIAJE TOTAL DETENIDO",13,MUTED,true);
        dayControl.addView(dayRecordingLamp);
        LinearLayout dayActions=row();dayControl.addView(dayActions);
        dayStartButton=compactButton(dayActions,"INICIAR",TEAL,this::startDay);
        dayPauseButton=compactButton(dayActions,"PAUSAR",GOLD,this::pauseDay);
        dayEndButton=compactButton(dayActions,"TERMINAR",RED,this::endDay);

        LinearLayout secondControl=controlCard(controls,"",2);
        secondRecordingLamp=text("○ VIAJE 2 DETENIDO",13,MUTED,true);
        secondControl.addView(secondRecordingLamp);
        LinearLayout secondActions=row();secondControl.addView(secondActions);
        secondStartButton=compactButton(secondActions,"INICIAR",BLUE,this::askSecond);
        secondPauseButton=compactButton(secondActions,"PAUSAR",GOLD,this::pauseSecond);
        secondEndButton=compactButton(secondActions,"TERMINAR",RED,this::finishSecond);

        // Restore the user's original instrument cards. Only remove the
        // oversize speedometer; the 4 non-speed gauges retain their live OEM
        // sources, colored bars, motor and HV battery flow indicators.
        gauge=new T1PremiumInstrumentView(this);
        gauge.setCompactWithoutSpeed(true);
        LinearLayout.LayoutParams meterParams=new LinearLayout.LayoutParams(-1,-2);
        meterParams.setMargins(0,3,0,3);
        root.addView(gauge,meterParams);

        LinearLayout powertrain=row();root.addView(powertrain);
        tempEngine=compactMetric(powertrain,"TEMP. MOTOR · RAW EXP.","SIN LECTURA");
        tempBattery=compactMetric(powertrain,"TEMP. BATERÍA HV · OEM","SIN LECTURA");
        socStrategy=text("SOC · ESPERANDO OEM",12,WHITE,true);
        root.addView(socStrategy);

        // Three selectable readings visible together. Selecting a card
        // switches trip-dependent values in the six compact tiles.
        LinearLayout overview=row();root.addView(overview);
        // Viaje TOTAL immediately to the RIGHT of Viaje 1.
        client1ScopeCard=scopeCard(overview,1);
        scopeTitle=text("VIAJE 1 · ESPERANDO",13,MUTED,true);
        client1ScopeCard.addView(scopeTitle);
        kmNow=text("0,00 km",27,0xFF5BE2D0,true);
        client1ScopeCard.addView(kmNow);
        scopeProgress=text("",10,MUTED,false);
        scopeProgress.setMaxLines(1);client1ScopeCard.addView(scopeProgress);

        totalScopeCard=scopeCard(overview,0);
        totalScopeTitle=text("VIAJE TOTAL · ESPERANDO",13,MUTED,true);
        totalScopeCard.addView(totalScopeTitle);
        totalScopeKm=text("0,00 km",27,0xFF5BE2D0,true);
        totalScopeCard.addView(totalScopeKm);

        client2ScopeCard=scopeCard(overview,2);
        secondScopeTitle=text("VIAJE 2 · ESPERANDO",13,MUTED,true);
        client2ScopeCard.addView(secondScopeTitle);
        secondScopeKm=text("0,00 km",27,0xFF5BE2D0,true);
        client2ScopeCard.addView(secondScopeKm);

        LinearLayout metrics=row();root.addView(metrics);
        consumptionNow=compactMetric(metrics,"CONSUMO · EXP.","SIN DATOS");
        evKmNow=compactMetric(metrics,"KM ELÉCTRICOS PUROS","0,00 km");
        fuelNow=compactMetric(metrics,"COMBUSTIBLE CONSUMIDO","—");
        electricNow=compactMetric(metrics,"ELECTRICIDAD CONSUMIDA · EXP.","—");
        costNow=compactMetric(metrics,"COSTO COMBUSTIBLE · EXP.","—");
        autonomyNow=compactMetric(metrics,"AUTONOMÍA TOTAL · EXP.","SIN DATOS");
        clientSyncStatus=text("CLIENTE QR · verificando pendientes",12,MUTED,false);
        clientSyncStatus.setSingleLine(true);
        clientSyncStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(clientSyncStatus);

        feedback=text("Listo · trayectos locales y CSV intactos.",
            12,0xFFB2DDD9,false);
        feedback.setMaxLines(3);root.addView(feedback);
        LinearLayout expandRow=row();root.addView(expandRow);
        compactButton(expandRow,"DETALLES / HISTORIAL",BLUE,()->
            secondary.setVisibility(secondary.getVisibility()==android.view.View.GONE?
                android.view.View.VISIBLE:android.view.View.GONE));
        compactButton(expandRow,"ZONAS / DIAGNÓSTICOS",BLUE,()->{
            advanced.setVisibility(advanced.getVisibility()==android.view.View.GONE?
                android.view.View.VISIBLE:android.view.View.GONE);
            updateZone();
        });

        secondary=col();secondary.setVisibility(android.view.View.GONE);
        secondary.setPadding(8,7,8,7);
        root.addView(secondary);
        LinearLayout rangeDetails=col();
        rangeDetails.setBackground(bg(CARD,14));
        secondary.addView(rangeDetails);
        rangeDetails.addView(text("AUTONOMÍA TOTAL · cálculo OEM EXPERIMENTAL",
            12,MUTED,true));
        autonomyDetail=text("Estimación OEM · calibración pendiente",
            11,MUTED,false);
        rangeDetails.addView(autonomyDetail);
        LinearLayout adjustRow=row();rangeDetails.addView(adjustRow);
        compactButton(adjustRow,"AJUSTAR AUTONOMÍA",BLUE,this::adjustAutonomy);
        LinearLayout electricTile=col();
        electricTile.setBackground(bg(CARD,14));secondary.addView(electricTile);
        electricTile.addView(text("MOTOR ELÉCTRICO",12,MUTED,false));
        electricMotorStatus=text("ESTADO SIN VALIDAR",17,GOLD,true);
        electricTile.addView(electricMotorStatus);
        electricMotorRpm=text("RPM: SIN LECTURA",12,WHITE,false);
        electricTile.addView(electricMotorRpm);
        LinearLayout thermalRow=row();secondary.addView(thermalRow);
        compactButton(thermalRow,"TEMPERATURAS / MOTOR · VER CÓDIGOS",
            BLUE,()->readThermal(true));
        secondary.addView(text("559940105 temperatura motor RAW: escala sin validar. "+
            "RPM térmicas=1035; sin RPM eléctrico confirmado.",
            11,MUTED,false));
        LinearLayout auxiliary=col();auxiliary.setBackground(bg(CARD,14));
        auxiliary.setPadding(9,5,9,5);secondary.addView(auxiliary);
        LinearLayout auxTitle=row();auxTitle.setGravity(Gravity.CENTER_VERTICAL);
        auxiliary.addView(auxTitle);
        LinearLayout auxText=col();
        auxText.addView(text("BATERÍA 12 V · IBS / DC-DC",13,MUTED,true));
        aux12vStatus=text("SIN MEDICIÓN VALIDADA",13,WHITE,false);
        auxText.addView(aux12vStatus);
        auxTitle.addView(auxText,new LinearLayout.LayoutParams(0,-2,1f));
        Button readAux=new Button(this);
        readAux.setText("LEER 12 V");readAux.setAllCaps(false);
        readAux.setOnClickListener(v->readAux12v());
        auxTitle.addView(readAux);
        auxiliary.addView(text("Valores brutos: no confundir IBS=0 con 0 V.",
            11,MUTED,false));
        energyNote=text("Km OEM estimados · nafta y electricidad por callbacks "+
            "500 m experimentales; la electricidad neta NO equivale a red comprada.",
            11,MUTED,false);
        secondary.addView(energyNote);
        tripStatus=text("Revisando TRIP OEM…",12,MUTED,false);
        secondary.addView(tripStatus);
        clientNow=text("Sin trayecto activo",13,WHITE,true);
        secondary.addView(clientNow);
        LinearLayout recoveryButtons=row();secondary.addView(recoveryButtons);
        clientRecoverButton=compactButton(recoveryButtons,
            "REPARAR CONTINUIDAD SIN CERRAR",RED,this::repairClient);
        clientRecoverButton.setVisibility(android.view.View.GONE);
        LinearLayout chargeActions=row();secondary.addView(chargeActions);
        compactButton(chargeActions,"CARGAS Y ECONOMÍA",BLUE,this::openChargeDialog);

        LinearLayout aggregate=row();secondary.addView(aggregate);
        dayNow=metric(tile(aggregate,CARD),"HOY · CLIENTES","—");
        monthNow=metric(tile(aggregate,CARD),"ESTE MES · CLIENTES","—");
        LinearLayout simulatorRow=row();secondary.addView(simulatorRow);
        compactButton(simulatorRow,"SIMULAR HEV SMART / SOC FORZADO",BLUE,
            this::openWhatIf);
        heading(secondary,"HISTORIAL · VIAJES Y JORNADAS");
        historyNow=text("Todavía no hay trayectos cerrados.",14,MUTED,false);
        historyNow.setBackground(bg(CARD,18));historyNow.setPadding(16,14,16,14);
        secondary.addView(historyNow);
        historyRepairStatus=text("Correcciones históricas: verificando…",
            12,MUTED,false);
        secondary.addView(historyRepairStatus);
        LinearLayout repairActions=row();secondary.addView(repairActions);
        compactButton(repairActions,"RECONSTRUIR KM DEL HISTORIAL",TEAL,
            this::runHistoryRepair);
        secondary.addView(text("IZQUIERDA: VIAJES 1/2 · DERECHA: "+
            "JORNADAS COMPLETAS. Elegí una tarjeta y verás los seis "+
            "indicadores a lo ancho, con original y corregido.",12,MUTED,true));
        LinearLayout historyColumns=row();
        secondary.addView(historyColumns,
            new LinearLayout.LayoutParams(-1,-2));
        LinearLayout historyLeft=tile(historyColumns,CARD);
        LinearLayout historyRight=tile(historyColumns,CARD);
        historyLeft.addView(text("VIAJES · 1 Y 2",17,WHITE,true));
        historyRight.addView(text("JORNADAS · TOTAL",17,WHITE,true));
        historyPickList=col();historyLeft.addView(historyPickList);
        historyMasterList=col();historyRight.addView(historyMasterList);
        historyDetailTitle=text("Elegí un viaje o una jornada.",14,WHITE,true);
        secondary.addView(historyDetailTitle);
        historyDetailRow=row();secondary.addView(historyDetailRow);
        advanced=col();advanced.setPadding(14,12,14,14);
        advanced.setBackground(bg(CARD,20));
        advanced.setVisibility(android.view.View.GONE);
        root.addView(advanced);
        heading(advanced,"SEÑALES OEM NUEVAS · SOLO LECTURA");
        advanced.addView(text("1079 porcentaje combustible · 2053 modo "+
            "0 Eco / 1 Normal / 2 Sport / 3 Snow (validado propietario) · "+
            "2204 freno de mano · "+
            "1031 alta tensión activada · 2073 estado general del motor "+
            "(NO RPM térmicas) · 1088 validez de velocidad · 2014 iluminación "+
            "ambiente · 2170 puerta acompañante. Sin escritura.",12,MUTED,false));
        rawSignalsStatus=text("Esperando lectura OEM para diagnosticar…",12,WHITE,false);
        advanced.addView(rawSignalsStatus);
        LinearLayout rawRow=row();advanced.addView(rawRow);
        button(rawRow,"COMPARTIR SEÑALES OEM 12/11",BLUE,()->
            shareDiagnostic("senales_oem_12nov",rawVehicleReport(),rawSignalsStatus));

        heading(advanced,"MODO OPTIMIZADO · CUATRO TIPOS DE VÍA");
        advanced.addView(text("Ciudad, carretera, autopista y desconocido "+
            "tienen configuraciones independientes. Cada perfil permite "+
            "EV/HEV/EV MAX, SMART o SOC FORZADO con porcentaje, umbral de "+
            "batería y regeneración baja/media/alta. El interruptor "+
            "AUTO ON aplica solo EV/HEV/EV MAX (1043) con OEM validado; "+
            "SOC forzado y regeneración continúan como preferencias.",
            13,MUTED,false));
        LinearLayout optimizerRow=row();advanced.addView(optimizerRow);
        button(optimizerRow,"CONFIGURAR 4 PERFILES",TEAL,this::openOptimizedMode);

        heading(advanced,"ASESOR OSM OFFLINE · PBA + CABA");
        advanced.addView(text("Cuatro clases: CIUDAD, CARRETERA, AUTOPISTA, "+
            "DESCONOCIDO. Cada una recomienda su perfil configurado. "+
            "Usa un índice SQLite del mapa descargado del mismo servidor "+
            "que DriveLab Uber: SIN Overpass, SIN transmitir coordenadas "+
            "de GPS a ningún servidor. Con AUTO OFF solo sugiere; "+
            "con AUTO ON el servicio aparte aplica 1043 tras validación. "+
            "Revisa la versión del mapa cada 24 h cuando la app está abierta "+
            "y hay Wi-Fi; con datos móviles solo descarga por acción manual.",
            13,MUTED,false));
        LinearLayout mapRow=row();advanced.addView(mapRow);
        button(mapRow,"DESCARGAR / ACTUALIZAR MAPA PBA+CABA",TEAL,
            ()->new AlertDialog.Builder(this)
                .setTitle("MAPA OFFLINE PROVINCIA + CABA")
                .setMessage("Se descarga de tu mismo servidor DriveLab Uber, "+
                    "con SHA-256 verificado. Recomendamos Wi-Fi y 200 MB "+
                    "libres. Los datos de Uber Driver quedan intactos. "+
                    "La versión instalada de Receiver se conserva "+
                    "si falla la actualización.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("DESCARGAR",(d,w)->updateOfflineMap(true))
                .show());
        offlineMapStatus=text(T1OfflineRoadMap.status(this),
            13,WHITE,false);
        advanced.addView(offlineMapStatus);
        LinearLayout osmButtons=row();advanced.addView(osmButtons);
        osmEnabledButton=button(osmButtons,
            "OSM · ACTIVAR ASESOR",BLUE,this::toggleOsm);
        osmSocButton=button(osmButtons,
            "AUTOPISTA · HEV SMART",TEAL,this::toggleOsmSoc);
        LinearLayout osmCheckRow=row();advanced.addView(osmCheckRow);
        button(osmCheckRow,"CONSULTAR VÍA OFFLINE · NO ESCRIBE",
            BLUE,()->lookupOsm(true));
        osmStatus=text("OSM offline no iniciado: sin clasificación de vía.",
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
        button(debugRow,"COMPARTIR INFORME",BLUE,this::showHevReport);
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
        button(ledButtons,"COMPARTIR LED",BLUE,
            ()->shareDiagnostic("led_frontal",ledReport,ledStatus));
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
        LinearLayout lightShare=row();advanced.addView(lightShare);
        button(lightShare,"COMPARTIR LUCES / BAÚL",BLUE,
            ()->shareDiagnostic("luces_baul",lightReport,lightStatus));
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
        button(seatCopy,"COMPARTIR LUCES ASIENTOS",BLUE,
            ()->shareDiagnostic("luces_asientos",seatReport,rearSeatStatus));
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
        button(beltCopy,"COMPARTIR CINTURONES",BLUE,
            ()->shareDiagnostic("cinturones_traseros",rearBeltReport,rearBeltStatus));
        rearBeltStatus=text("Sin comparación de cinturones todavía.",
            14,WHITE,false);
        advanced.addView(rearBeltStatus);
        LinearLayout extra=row();root.addView(extra);
        button(extra,"TABLERO TÉCNICO / CSV",BLUE,()->startActivity(
            new Intent(this,T1TankDashboardActivity.class)));
        button(extra,"DIAGNÓSTICO OEM",BLUE,()->startActivity(
            new Intent(this,ReceiverVehicleActivity.class)
                .putExtra("open_tab",3)));
        secondary.addView(text("Este cockpit no reemplaza el velocímetro homologado ni "+
            "el sistema Jetour. No usar para controlar EV/HEV en marcha. "+
            "No se guardan coordenadas de clientes, ni se envían las zonas a la nube.",
            12,MUTED,false));
        // The popup is pinned above scrolling content, but BELOW the fixed
        // voice-assistant clearance strip owned by T1ImmersiveActivity.
        FrameLayout cockpitFrame=new FrameLayout(this);
        cockpitFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        roadTransitionPopup=text("CIUDAD",26,WHITE,true);
        roadTransitionPopup.setGravity(Gravity.CENTER);
        int pad=Math.round(13*getResources().getDisplayMetrics().density);
        roadTransitionPopup.setPadding(pad,pad,pad,pad);
        roadTransitionPopup.setClickable(false);
        roadTransitionPopup.setFocusable(false);
        roadTransitionPopup.setElevation(
            12*getResources().getDisplayMetrics().density);
        roadTransitionPopup.setVisibility(android.view.View.GONE);
        FrameLayout.LayoutParams popupParams=new FrameLayout.LayoutParams(
            -1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        int side=Math.round(55*getResources().getDisplayMetrics().density);
        popupParams.setMargins(side,pad,side,0);
        cockpitFrame.addView(roadTransitionPopup,popupParams);
        setContentView(cockpitFrame);
        // Open the existing diagnostic panel directly when launched from
        // the wizard; retain its original actions and user-triggered probes.
        if(getIntent()!=null&&
            getIntent().getBooleanExtra("open_validation_advanced",false)){
            advanced.setVisibility(android.view.View.VISIBLE);
            scroll.post(()->scroll.smoothScrollTo(0,advanced.getTop()));
        }
    }
    private void updateStats(){
        String recovered=T1JourneyStore.reconcileInterrupted(this);
        String recovered2=T1JourneyStore2.reconcileInterrupted(this);
        if(feedback!=null){
            if(!recovered.isEmpty())feedback.setText("VIAJE 1 · "+recovered);
            if(!recovered2.isEmpty())feedback.setText("VIAJE 2 · "+recovered2);
        }
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
        boolean second=T1JourneyStore2.active(this),
            secondPending=T1JourneyStore2.pending(this);
        boolean dayPaused=m.getBoolean(T1TripRecorderService.PAUSED,false);
        boolean clientPaused=T1JourneyStore.paused(this);
        boolean secondPaused=T1JourneyStore2.paused(this);
        boolean dayDesired=m.getBoolean(T1TripRecorderService.DESIRED,false);
        T1JourneyMetrics.Result live=client?T1JourneyStore.running(this):null;
        T1JourneyMetrics.Result secondLive=second?T1JourneyStore2.running(this):null;
        boolean blink=(android.os.SystemClock.elapsedRealtime()/700L)%2==0;
        boolean clientLive=client&&!clientPaused&&fresh&&live!=null;
        boolean secondLiveNow=second&&!secondPaused&&fresh&&secondLive!=null;
        if(dayRecordingLamp!=null){
            dayRecordingLamp.setText(dayPaused?"Ⅱ VIAJE TOTAL PAUSADO":
                fresh?(blink?"● ":"◉ ")+"VIAJE TOTAL GRABANDO":
                running?"◉ TOTAL ESPERANDO OEM":
                dayDesired?"◉ RECUPERANDO TOTAL":"○ VIAJE TOTAL DETENIDO");
            dayRecordingLamp.setTextColor(dayPaused?GOLD:
                fresh?TEAL:running||dayDesired?GOLD:MUTED);
        }
        if(clientRecordingLamp!=null){
            clientRecordingLamp.setText(clientPaused?"Ⅱ VIAJE 1 PAUSADO":
                clientLive?(blink?"● ":"◉ ")+"VIAJE 1 GRABANDO":
                pending?"◉ INICIANDO VIAJE 1":
                T1JourneyStore.stuck(this)?"⚠ VIAJE 1 BLOQUEADO":
                client?"◉ VIAJE 1 ESPERANDO OEM":"○ VIAJE 1 DETENIDO");
            clientRecordingLamp.setTextColor(clientPaused?GOLD:
                clientLive?TEAL:(client||pending)?GOLD:MUTED);
        }
        if(secondRecordingLamp!=null){
            secondRecordingLamp.setText(secondPaused?"Ⅱ VIAJE 2 PAUSADO":
                secondLiveNow?(blink?"● ":"◉ ")+"VIAJE 2 GRABANDO":
                secondPending?"◉ INICIANDO VIAJE 2":
                T1JourneyStore2.stuck(this)?"⚠ VIAJE 2 BLOQUEADO":
                second?"◉ VIAJE 2 ESPERANDO OEM":"○ VIAJE 2 DETENIDO");
            secondRecordingLamp.setTextColor(secondPaused?GOLD:
                secondLiveNow?TEAL:(second||secondPending)?GOLD:MUTED);
        }
        // Independent button availability. The old client-dependent conditions
        // grayed out JORNADA PAUSAR/TERMINAR while Viaje 1 was active.
        if(dayStartButton!=null){
            dayStartButton.setText(dayPaused?"REANUDAR":"INICIAR");
            dayStartButton.setEnabled(!running&&(dayPaused||!dayDesired));
        }
        if(dayPauseButton!=null)dayPauseButton.setEnabled(
            !dayPaused&&running&&fresh);
        if(dayEndButton!=null)dayEndButton.setEnabled(dayDesired||dayPaused);
        if(clientStartButton!=null){
            clientStartButton.setText(clientPaused?"REANUDAR":"INICIAR");
            clientStartButton.setEnabled(
                (clientPaused&&!dayPaused&&!running?false:
                    clientPaused||(!client&&!pending)));
        }
        if(clientPauseButton!=null)
            clientPauseButton.setEnabled(client&&!clientPaused&&fresh&&!dayPaused);
        if(clientEndButton!=null)
            clientEndButton.setEnabled(client||pending);
        if(secondStartButton!=null){
            secondStartButton.setText(secondPaused?"REANUDAR":"INICIAR");
            secondStartButton.setEnabled(
                (secondPaused&&!dayPaused&&!running?false:
                    secondPaused||(!second&&!secondPending)));
        }
        if(secondPauseButton!=null)
            secondPauseButton.setEnabled(second&&!secondPaused&&fresh&&!dayPaused);
        if(secondEndButton!=null)
            secondEndButton.setEnabled(second||secondPending);
        if(clientRecoverButton!=null)
            clientRecoverButton.setVisibility(
                T1JourneyStore.stuck(this)?
                    android.view.View.VISIBLE:android.view.View.GONE);

        T1CockpitScope.Value total=T1CockpitScope.choose(
            fresh||dayPaused,false,false,null,km,nf,ne,fRaw,eRaw,price);
        T1CockpitScope.Value trip1=T1CockpitScope.choose(
            false,client,pending,live,0,0,0,0,0,price);
        T1CockpitScope.Value trip2=T1CockpitScope.choose(
            false,second,secondPending,secondLive,0,0,0,0,0,price);
        totalScopeTitle.setText(dayPaused?"Ⅱ VIAJE TOTAL PAUSADO":
            dayDesired?"● VIAJE TOTAL · JORNADA":"○ VIAJE TOTAL · SIN GRABAR");
        totalScopeKm.setText(f(total.km,2)+" km");
        scopeTitle.setText(clientPaused?"Ⅱ VIAJE 1 PAUSADO":
            client?"● VIAJE 1 · ACTIVO":
            pending?"◉ VIAJE 1 · PREPARANDO":"○ VIAJE 1 · SIN GRABAR");
        kmNow.setText(f(trip1.km,2)+" km");
        secondScopeTitle.setText(secondPaused?"Ⅱ VIAJE 2 PAUSADO":
            second?"● VIAJE 2 · ACTIVO":
            secondPending?"◉ VIAJE 2 · PREPARANDO":"○ VIAJE 2 · SIN GRABAR");
        secondScopeKm.setText(f(trip2.km,2)+" km");
        refreshScopeSelection();
        scopeProgress.setText(selectedScope==1?"SELECCIONADO · tocá otro para comparar":
            "TOCÁ VIAJE 1 PARA VER VALORES");

        T1CockpitScope.Value scoped=selectedScope==0?total:
            selectedScope==2?trip2:trip1;
        double electricRoadKm=selectedScope==0?
            (dayDesired||dayPaused?
                m.getFloat(T1TripRecorderService.EV_DISTANCE,0f):0):
            selectedScope==2?(second?T1JourneyStore2.electricKm(this):0):
            client?T1JourneyStore.electricKm(this):0;
        evKmNow.setText(f(electricRoadKm,2)+" km");
        // L/100 km = liters * 100 / kilometers; km/L = km / liters.
        // Neither ratio is meaningful with no verified fuel events, zero
        // liters, zero distance or a stale client scope.
        boolean hasConsumption=scoped.fuelKnown&&
            Double.isFinite(scoped.fuelLiters)&&scoped.fuelLiters>0.000001&&
            Double.isFinite(scoped.km)&&scoped.km>0.001;
        consumptionNow.setText(hasConsumption?
            f(100.0*scoped.fuelLiters/scoped.km,2)+" L/100 · "+
            f(scoped.km/scoped.fuelLiters,1)+" km/L":"SIN DATOS");
        consumptionNow.setTextSize(16);
        clientSyncStatus.setText("VIAJES QR · "+T1ClientTripSync.status(this));
        fuelNow.setText(scoped.fuelKnown?
            f(scoped.fuelLiters,3)+" L":"ESPERANDO");
        electricNow.setText(scoped.electricKnown?
            f(scoped.electricNetKwh,3)+" kWh":"ESPERANDO");
        costNow.setText(scoped.fuelKnown?
            "$ "+f(scoped.fuelCost,0):"SIN DATO");
        if(!running){
            tripStatus.setText((dayPaused?"Ⅱ JORNADA PAUSADA · TRIP ":"○ JORNADA DETENIDA · último TRIP ")+
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
        String continuity=m.getString(T1TripRecorderService.RESTORE_STATUS,"");
        if(!fresh&&m.getBoolean(T1TripRecorderService.DESIRED,false)&&
            !continuity.isEmpty())
            tripStatus.append("\n"+continuity);
        long parkedMs=m.getLong(T1TripRecorderService.PARKED_MS,0);
        long offlineMs=m.getLong(T1TripRecorderService.OFFLINE_MS,0);
        tripStatus.append("\nEstacionado: "+
            f(parkedMs/60000.0,0)+" min · "+
            "sin telemetría (apagado/reinicio): "+
            f(offlineMs/60000.0,0)+" min");
        if(client){
            clientNow.setText("● "+T1JourneyStore.activeLabel(this)+
                (live==null?" · esperando lectura reciente":
                    " · "+f(live.km,2)+" km · "+
                    (live.fuelAvailable?f(live.fuelLitersCandidate,3)+
                        " L nafta experimental":"esperando 500 m"))+
                "\nDuración (incluye paradas): "+
                    f(T1JourneyStore.elapsedMillis(this)/60000.0,0)+
                    " min · detenido conocido "+
                    f(T1JourneyStore.parkedMillis(this)/60000.0,0)+
                    " min · interrupción sin datos "+
                    f(T1JourneyStore.offlineMillis(this)/60000.0,0)+" min"+
                (T1JourneyStore.partialRecovery(this)?
                    "\nTRAMO ANTERIOR INCOMPLETO, sin inventar distancia":""));
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
                "Nafta: sin tramo OEM válido")+
            "\nElectricidad neta OEM experimental: "+
            (day.electricKnownTrips>0?f(day.electricNet,3)+" kWh":
                "SIN DATO")+
            " · no es electricidad comprada a la red");
        // Reuse background-verified master summaries. The previous
        // implementation reparsed every master CSV on the UI thread every
        // five seconds and showed OLD GPS-km totals after reconstruction.
        double dayMasterKm=0;
        int dayMasterCount=0;
        String todayMaster=T1JourneyStore.today().replace("-","");
        for(T1MasterTripHistory.Entry e:cachedMasterHistory)
            if(e.id.startsWith(todayMaster)){
                dayMasterKm+=e.km;dayMasterCount++;
            }
        if(dayMasterCount>0)
            dayNow.append("\nJORNADAS HOY: "+f(dayMasterKm,2)+
                " km en "+dayMasterCount+
                " sesiones, distancias verificadas o identificadas "+
                "como pendientes (sin sumar viajes individuales)");
        monthNow.setText(month.trips+" viajes · "+f(month.km,1)+" km\n"+
            (month.fuelKnownTrips>0?"$ "+f(month.fuelCost,0)+" nafta exp.":
                "Nafta: sin tramo OEM válido")+
            "\nRed comprada: "+f(month.gridKwh,1)+" kWh ($"+
            f(month.gridCost,0)+") · no imputada");

        ArrayList<HistoryRecord> all=new ArrayList<>();
        // Viaje Total is a separate MASTER CSV, never a sum of overlapping
        // client slots. Closed files are shown alongside Viaje 1/2.
        SharedPreferences recorder=getSharedPreferences(
            T1TripRecorderService.PREFS,MODE_PRIVATE);
        String currentMasterId=recorder.getString(T1TripRecorderService.ID,"");
        boolean masterOpen=recorder.getBoolean(
            T1TripRecorderService.RUNNING,false)||
            recorder.getBoolean(T1TripRecorderService.PAUSED,false);
        double currentFuelPrice=recorder.getFloat(
            T1TripRecorderService.FUEL_PRICE,2129f);
        for(T1MasterTripHistory.Entry e:cachedMasterHistory){
            double experimentalCost=Double.isFinite(e.liters)?
                e.liters*currentFuelPrice:Double.NaN;
            all.add(new HistoryRecord(0,e.id,
                "JORNADA "+e.id+" · "+e.status,
                e.started,e.ended,e.km,e.originalKm,e.evKm,e.liters,
                e.electricityKwh,experimentalCost,e.corrected,false,e.fuelEvents));
        }
        for(T1JourneyStore.Entry e:T1JourneyStore.history(this))
            all.add(new HistoryRecord(1,e.id,e.label,e.started,e.ended,
                e.km,e.originalKm,e.evKm,e.liters,e.electricNet,e.fuelCost,
                e.correctedKm,e.estimatedDistance,e.fuelEvents));
        for(T1JourneyStore2.Entry e:T1JourneyStore2.history(this))
            all.add(new HistoryRecord(2,e.id,e.label,e.started,e.ended,
                e.km,e.originalKm,e.evKm,e.liters,e.electricNet,e.fuelCost,
                e.correctedKm,e.estimatedDistance,e.fuelEvents));
        all.sort((a,b)->Long.compare(b.ended,a.ended));
        SimpleDateFormat dt=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault());
        StringBuilder summary=new StringBuilder();
        int totalClient=0,totalMaster=0;
        for(HistoryRecord item:all){
            if(item.slot==0)totalMaster++;else totalClient++;
        }
        for(int i=0;i<Math.min(3,all.size());i++){
            HistoryRecord e=all.get(i);
            summary.append(e.displayName()).append(" · ")
                .append(dt.format(new Date(e.started))).append(" · ")
                .append(f(e.km,2)).append(" km");
            if(e.corrected)summary.append(" · RECALCULADO OEM");
            summary.append("\n");
        }
        historyNow.setText(
            totalClient+" VIAJES CERRADOS · "+totalMaster+" JORNADAS CERRADAS\n"+
            (summary.length()==0?"Todavía no hay grabaciones":summary.toString().trim()));
        if(historyPickList!=null&&historyMasterList!=null){
            historyPickList.removeAllViews();
            historyMasterList.removeAllViews();
            int shownClient=0,shownMaster=0;
            for(HistoryRecord item:all){
                if(item.slot==0){
                    if(shownMaster++>=30)continue;
                    addHistoryButton(historyMasterList,item,dt);
                }else{
                    if(shownClient++>=30)continue;
                    addHistoryButton(historyPickList,item,dt);
                }
            }
            if(totalClient==0)
                historyPickList.addView(text("Sin viajes 1/2 cerrados",
                    12,MUTED,false));
            if(totalMaster==0)
                historyMasterList.addView(text(
                    historyRepairBusy.get()?
                        "Leyendo CSV de jornadas…":"Sin jornadas cerradas",
                    12,MUTED,false));
            HistoryRecord chosen=null;
            for(HistoryRecord h:all)
                if(selectedHistoryKey.equals(h.key())){chosen=h;break;}
            if(chosen==null&&!all.isEmpty()){
                chosen=all.get(0);selectedHistoryKey=chosen.key();
            }
            if(chosen!=null)showHistoryDetails(chosen);
            else{
                historyDetailTitle.setText("Sin viajes ni jornadas cerradas");
                historyDetailRow.removeAllViews();
            }
        }
        if(historyRepairStatus!=null&&!historyRepairBusy.get()){
            int corrected=0,review=0,estimated=0,protectedCount=0;
            for(T1HistoricalDistanceRepair.Entry e:
                T1HistoricalDistanceRepair.load(
                    T1TripRecorderService.tripDir(this)).values()){
                if(e.independentlyVerified())corrected++;
                else if(e.applied())estimated++;
                else if("PROTEGIDO_ULTIMO_VIAJE_2".equals(e.status))
                    protectedCount++;
                else if(e.status.startsWith("REVISAR"))review++;
            }
            historyRepairStatus.setText(
                corrected+" viajes verificados OEM · "+
                estimated+" estimaciones de velocidad · "+
                review+" para revisar · "+protectedCount+
                " Viaje 2 reciente protegido.");
        }
    }
    private void addHistoryButton(LinearLayout column,HistoryRecord item,
                                  SimpleDateFormat dateFormat){
        boolean selected=selectedHistoryKey.equals(item.key());
        String label=item.displayName()+" · "+
            dateFormat.format(new Date(item.started))+"\n"+
            f(item.km,2)+" km"+
            (item.corrected?" · "+(item.estimated?
                "ESTIMADO OEM":"CORREGIDO OEM"):"");
        TextView button=text((selected?"● ":"○ ")+label,
            14,selected?TEAL:WHITE,true);
        button.setPadding(12,10,12,10);
        button.setBackground(bg(selected?0xFF173B40:0xFF1C2C38,10));
        LinearLayout.LayoutParams params=
            new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(2,3,2,3);
        column.addView(button,params);
        button.setOnClickListener(v->{
            selectedHistoryKey=item.key();
            showHistoryDetails(item);
            // Next refresh also updates highlights in both columns.
            lastTotals=0;
        });
    }
    private static final class HistoryRecord {
        final int slot,fuelEvents;
        final String id,label;
        final long started,ended;
        final double km,oldKm,evKm,liters,kwh,cost;
        final boolean corrected,estimated;
        HistoryRecord(int slot,String id,String label,long started,long ended,
                      double km,double oldKm,double evKm,double liters,
                      double kwh,double cost,boolean corrected,
                      boolean estimated,int events){
            this.slot=slot;this.id=id;this.label=label;
            this.started=started;this.ended=ended;
            this.km=km;this.oldKm=oldKm;this.evKm=evKm;
            this.liters=liters;this.kwh=kwh;this.cost=cost;
            this.corrected=corrected;this.estimated=estimated;
            fuelEvents=events;
        }
        String key(){return slot+":"+id;}
        String displayName(){return slot==0?"VIAJE TOTAL":"VIAJE "+slot;}
    }
    private void showHistoryDetails(HistoryRecord x){
        if(historyDetailTitle==null||historyDetailRow==null)return;
        historyDetailTitle.setText(x.displayName()+" · "+
            new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault())
                .format(new Date(x.started))+
            " · "+f(x.km,2)+" km"+(x.corrected?
                " (antes "+f(x.oldKm,2)+" km · "+
                (x.estimated?"ESTIMACIÓN DE VELOCIDAD SIN VALIDACIÓN 500m":
                    "CORREGIDO OEM VERIFICADO")+")":"")+
            "\n"+x.label);
        historyDetailRow.removeAllViews();
        boolean fuelOk=x.km>0&&Double.isFinite(x.liters)&&x.liters>0;
        String consumption=fuelOk?
            f(100*x.liters/x.km,2)+" L/100 · "+
                f(x.km/x.liters,2)+" km/L"+
                (x.estimated?" · ESTIMADO":""):"SIN DATO";
        compactMetric(historyDetailRow,"CONSUMO",consumption);
        compactMetric(historyDetailRow,"KM ELÉCTRICOS PUROS",
            Double.isFinite(x.evKm)?f(x.evKm,2)+" km":"SIN DATO");
        compactMetric(historyDetailRow,"COMBUSTIBLE CONSUMIDO",
            Double.isFinite(x.liters)?f(x.liters,3)+" L exp.":"SIN DATO");
        compactMetric(historyDetailRow,"ELECTRICIDAD CONSUMIDA",
            Double.isFinite(x.kwh)?f(x.kwh,3)+" kWh exp.":"SIN DATO");
        compactMetric(historyDetailRow,"COSTO COMBUSTIBLE",
            Double.isFinite(x.cost)?"$ "+f(x.cost,0)+" exp.":"SIN DATO");
        // Historical end-of-trip range was not persisted. Never substitute
        // the current vehicle's autonomy into a completed trip.
        compactMetric(historyDetailRow,"AUTONOMÍA TOTAL","NO GUARDADA");
    }
    private void runHistoryRepair(){runHistoryRepair(true);}
    private void runHistoryRepair(boolean manual){
        if(!manual&&lastHistoryAuditAt>0&&
            System.currentTimeMillis()-lastHistoryAuditAt<120000L)return;
        if(!historyRepairBusy.compareAndSet(false,true))return;
        lastHistoryAuditAt=System.currentTimeMillis();
        if(historyRepairStatus!=null)
            historyRepairStatus.setText("Verificando viajes cerrados y "+
                "eventos OEM independientes…");
        new Thread(()->{
            String message;
            try{
                java.io.File folder=T1TripRecorderService.tripDir(this);
                java.util.ArrayList<T1HistoricalDistanceRepair.Entry> list=
                    T1HistoricalDistanceRepair.calculate(folder);
                T1HistoricalDistanceRepair.save(folder,list);
                SharedPreferences p=getSharedPreferences(
                    T1TripRecorderService.PREFS,MODE_PRIVATE);
                ArrayList<T1MasterTripHistory.Entry> master=
                    T1MasterTripHistory.closed(folder,
                        p.getString(T1TripRecorderService.ID,""),
                        p.getBoolean(T1TripRecorderService.RUNNING,false)||
                        p.getBoolean(T1TripRecorderService.PAUSED,false));
                cachedMasterHistory=master;
                int fixed=0,pending=0,estimated=0,masterFixed=0;
                for(T1HistoricalDistanceRepair.Entry e:list){
                    if(e.independentlyVerified())fixed++;
                    else if(e.applied())estimated++;
                    else if(e.status.startsWith("REVISAR"))pending++;
                }
                for(T1MasterTripHistory.Entry e:master)
                    if(e.corrected)masterFixed++;
                message="HISTORIAL REVISADO: "+fixed+
                    " viajes verificados OEM, "+estimated+
                    " estimados por velocidad (sin testigo 500m), "+
                    masterFixed+" jornadas corregidas y "+pending+
                    " viajes pendientes. CSV originales intactos.";
            }catch(Exception ex){
                message="No se pudo reconstruir: "+
                    ex.getClass().getSimpleName()+" · "+ex.getMessage();
            }
            final String report=message;
            ui.post(()->{
                historyRepairBusy.set(false);
                if(historyRepairStatus!=null)historyRepairStatus.setText(report);
                lastTotals=0;
                if(visible)updateHistory();
            });
        },"T1-ClosedTrips-OEM-Rebuild").start();
    }
    private void message(String m){feedback.setText(m);lastTotals=0;updateStats();}
    private void startDay(){
        SharedPreferences m=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE);
        boolean paused=m.getBoolean(T1TripRecorderService.PAUSED,false);
        if(paused&&m.getBoolean(T1TripRecorderService.RUNNING,false)){
            message("La jornada todavía se está pausando; esperá el cierre del CSV.");
            return;
        }
        if(paused){
            try{
                startForegroundService(new Intent(this,T1TripRecorderService.class)
                    .setAction(T1TripRecorderService.ACTION_RESUME));
                message("REANUDANDO JORNADA · mismo CSV y kilometraje acumulado.");
            }catch(Exception e){message("No se reanudó: "+e.getClass().getSimpleName());}
            return;
        }
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
    private void pauseDay(){
        // Never disable this button just because another slot is active.
        // Both independent client checkpoints must be paused BEFORE the
        // shared OEM recorder is stopped, otherwise the deltas could span
        // an unmeasured interval.
        if(T1JourneyStore.active(this)&&!T1JourneyStore.paused(this)){
            String result=T1JourneyStore.pause(this);
            if(!T1JourneyStore.paused(this)){message("Viaje 1: "+result);return;}
        }
        if(T1JourneyStore2.active(this)&&!T1JourneyStore2.paused(this)){
            String result=T1JourneyStore2.pause(this);
            if(!T1JourneyStore2.paused(this)){message("Viaje 2: "+result);return;}
        }
        try{
            startService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_PAUSE));
            message("VIAJE TOTAL PAUSADO · Viaje 1/2 abiertos en pausa. "+
                "Reanudá sus contadores por separado al reanudar Jornada.");
        }catch(Exception ex){message("No se pausó: "+ex.getClass().getSimpleName());}
    }
    private void pauseClient(){
        if(!T1JourneyStore.active(this)){
            message("No hay Viaje 1 activo.");return;
        }
        message(T1JourneyStore.pause(this));
    }
    private void pauseSecond(){
        if(!T1JourneyStore2.active(this)){
            message("No hay Viaje 2 activo.");return;
        }
        message(T1JourneyStore2.pause(this));
    }
    private void endDay(){
        // This global action is always available independently. Closing a
        // master containing open slots is explicitly confirmed and atomically
        // saves both client checkpoints before stopping their data source.
        boolean has1=T1JourneyStore.active(this);
        boolean has2=T1JourneyStore2.active(this);
        new AlertDialog.Builder(this).setTitle("TERMINAR VIAJE TOTAL")
            .setMessage((has1||has2?
                "Hay viajes individuales abiertos. Al cerrar el TOTAL "+
                "también se guardarán Viaje 1 y/o Viaje 2. "+
                "Podés cancelar y cerrarlos por separado.\n\n":"")+
                "Se conservan los CSV y se pide sincronización final QR.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("TERMINAR",(d,w)->{
                try{
                    if(T1JourneyStore.active(this)){
                        String result=T1JourneyStore.finish(this);
                        if(T1JourneyStore.active(this)){
                            message("No se cerró el total: Viaje 1 · "+result);
                            return;
                        }
                    }
                    if(T1JourneyStore2.active(this)){
                        String result=T1JourneyStore2.finish(this);
                        if(T1JourneyStore2.active(this)){
                            message("No se cerró el total: Viaje 2 · "+result);
                            return;
                        }
                    }
                    T1JourneyStore.cancelPending(this);
                    T1JourneyStore2.cancelPending(this);
                    startService(new Intent(this,T1TripRecorderService.class)
                        .setAction(T1TripRecorderService.ACTION_STOP));
                    message("Finalizando VIAJE TOTAL; Viajes 1/2 guardados "+
                        "si estaban abiertos. Sincronización QR en curso.");
                }catch(Exception ex){message("Error: "+ex.getClass().getSimpleName());}
            }).show();
    }
    private void askClient(){startSubTrip(1);}
    private void askSecond(){startSubTrip(2);}
    /** One touch only: never ask a passenger name. If the total recorder has
     * not produced a fresh OEM sample, queue the slot and generate its label
     * when the first genuine checkpoint arrives. */
    private void startSubTrip(int slot){
        boolean second=slot==2;
        if(second?T1JourneyStore2.paused(this):T1JourneyStore.paused(this)){
            message(second?T1JourneyStore2.resume(this):T1JourneyStore.resume(this));
            return;
        }
        if(second?(T1JourneyStore2.active(this)||T1JourneyStore2.pending(this)):
            (T1JourneyStore.active(this)||T1JourneyStore.pending(this))){
            message("Viaje "+slot+" ya está activo o iniciándose.");
            return;
        }
        String label=T1AutoTripLabel.token(slot);
        try{
            SharedPreferences p=getSharedPreferences(
                T1TripRecorderService.PREFS,MODE_PRIVATE);
            long age=System.currentTimeMillis()-
                p.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
            boolean live=p.getBoolean(T1TripRecorderService.RUNNING,false)&&
                age>=0&&age<=15000;
            if(live){
                message(second?T1JourneyStore2.start(this,label):
                    T1JourneyStore.start(this,label));
            }else{
                String status=second?T1JourneyStore2.queue(this,label):
                    T1JourneyStore.queue(this,label);
                startForegroundService(new Intent(this,
                    T1TripRecorderService.class).setAction(
                        T1TripRecorderService.ACTION_START));
                message(status+" · Viaje "+slot+" comienza con la primera "+
                    "muestra OEM; nombre automático por fecha y GPS disponible.");
            }
        }catch(Exception ex){
            if(second)T1JourneyStore2.cancelPending(this);
            else T1JourneyStore.cancelPending(this);
            message("No se inició Viaje "+slot+": "+ex.getMessage());
        }
    }
    private void repairClient(){
        if(!T1JourneyStore.stuck(this)){
            message("El trayecto ya está asociado a la jornada actual.");return;
        }
        String recovered=T1JourneyStore.reconcileInterrupted(this);
        if(!T1JourneyStore.stuck(this)){
            message(recovered);return;
        }
        new AlertDialog.Builder(this)
            .setTitle("REPARAR TRAYECTO · SIN FINALIZAR")
            .setMessage("El cliente seguirá ABIERTO con el mismo nombre "+
                "e inicio original. Los CSV anteriores no se borrarán. "+
                "El tramo que no se pudo recuperar quedará marcado como "+
                "incompleto, sin inventar km ni energía.\n\n"+
                T1JourneyStore.recoveryNote(this))
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CONTINUAR MISMO CLIENTE",
                (d,w)->message(T1JourneyStore.repairWithoutOldCsv(this)))
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
    private void finishSecond(){
        if(T1JourneyStore2.pending(this)){
            T1JourneyStore2.cancelPending(this);
            message("Se canceló el inicio: no había empezado el Viaje 2.");return;
        }
        if(!T1JourneyStore2.active(this)){message("No hay Viaje 2 activo.");return;}
        new AlertDialog.Builder(this).setTitle("Finalizar Viaje 2")
            .setMessage("Se guardará como subviaje sin interrumpir la jornada.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("GUARDAR",(d,w)->{
                try{message(T1JourneyStore2.finish(this));}
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
    private static String driveModeName2053(int raw){
        switch(raw){
            case 0:return "ECO";
            case 1:return "NORMAL";
            case 2:return "SPORT";
            case 3:return "SNOW";
            default:return raw==Integer.MIN_VALUE?"SIN DATO":"DESCONOCIDO";
        }
    }
    private static String rawCode(int raw){
        return raw==Integer.MIN_VALUE?"SIN DATO":Integer.toString(raw);
    }
    private void updateVehicleOemSignals(){
        if(lastSample==null)return;
        T1TripTelemetry.Sample x=lastSample;
        if(vehicleModeStatus!=null)
            vehicleModeStatus.setText("CONDUCCIÓN · "+driveModeName2053(x.driveMode2053)+
                " (2053="+rawCode(x.driveMode2053)+")"+
                "   |   COMBUSTIBLE · 1079="+rawCode(x.fuelPct1079)+
                (x.fuelPct1079>=0&&x.fuelPct1079<=100?" %":""));
        if(rawSignalsStatus!=null)
            rawSignalsStatus.setText(rawVehicleReport());
    }
    private String rawVehicleReport(){
        if(lastSample==null)return "SIN LECTURA OEM. No se pudo generar diagnóstico.";
        T1TripTelemetry.Sample x=lastSample;
        return "JETOUR T1 · SEÑALES OEM · SOLO LECTURA\n"+
            "Marca temporal: "+x.timestamp+"\n"+
            "1079 · COMBUSTIBLE % OEM: "+rawCode(x.fuelPct1079)+"\n"+
            "2053 · MODO CONDUCCIÓN: "+
                driveModeName2053(x.driveMode2053)+" (RAW "+
                rawCode(x.driveMode2053)+")\n"+
            "2204 · FRENO DE MANO: "+rawCode(x.handbrake2204)+"\n"+
            "1031 · ALTA TENSIÓN HABILITADA (estado, NO voltaje): "+
                rawCode(x.highVoltageReady1031)+"\n"+
            "1063 · VOLTAJE HV PACK (getter float): "+
                (Double.isFinite(x.voltage)?f(x.voltage,2):"SIN DATO")+"\n"+
            "1064 · CORRIENTE HV (A, NO voltios): "+
                (Double.isFinite(x.current)?f(x.current,2):"SIN DATO")+"\n"+
            "2073 · KEY ENGINE STS (NO INTERPRETAR COMO RPM): "+
                rawCode(x.engineStatus2073)+"\n"+
            "1035 · RPM MOTOR TÉRMICO: "+
                (Double.isFinite(x.rpm)?f(x.rpm,0):"SIN DATO")+"\n"+
            "1088 · SPEED VALID: "+rawCode(x.speedValid1088)+"\n"+
            "2014 · ATMOSPHERE LIGHT SWITCH: "+
                rawCode(x.atmosphereSwitch2014)+"\n"+
            "2170 · PASSENGER DOOR CEM2: "+
                rawCode(x.passengerDoorCem2_2170)+"\n"+
            "559940910 · EXT BATTERY VOLT: candidato HV, "+
                "getter separado en diagnósticos 12 V/IBS. "+
                "No atribuir a batería 12 V sin corroborar.\n"+
            "Sin escritura OEM; sin GPS, nombres, VIN o historial.";
    }
    private void showRawVehicleSignals(){
        new AlertDialog.Builder(this).setTitle("SEÑALES OEM · SOLO LECTURA")
            .setMessage(rawVehicleReport())
            .setNegativeButton("CERRAR",null)
            .setPositiveButton("COMPARTIR",(d,w)->
                shareDiagnostic("senales_oem_12nov",rawVehicleReport(),
                    vehicleModeStatus))
            .show();
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
    /** Reports are always private uploads through the already paired cloud.
     * Preserve the local CSV if the vehicle is offline. */
    private void shareDiagnostic(String type,String report,TextView output){
        if(report==null||report.trim().isEmpty()){
            if(output!=null)output.setText("Primero completá la captura.");
            return;
        }
        if(output!=null)output.setText("Compartiendo informe "+type+
            " con el servidor…");
        T1ReportShare.share(this,type,report,status->{
            if(isFinishing()||isDestroyed())return;
            if(output!=null)output.setText(status);
            else if(feedback!=null)feedback.setText(status);
        });
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
                    .setPositiveButton("COMPARTIR",
                        (d,w)->shareDiagnostic("bateria_12v",
                            aux12vReport,aux12vStatus)).show();
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
                .setMessage(report+"\n\nUsá COMPARTIR INFORME para subir "+
                    "las etapas y errores OEM al servidor.")
                .setPositiveButton("CERRAR",null).show();
        })))hevStatus.setText("No se pudo iniciar: diagnóstico ocupado.");
    }
    private void showHevReport(){
        String report=T1OemDiagnosticLog.report(this);
        shareDiagnostic("ev_hev_oem",report,hevStatus);
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
                        .setPositiveButton("COMPARTIR",(d,w)->
                            shareDiagnostic("motor_temperaturas",report,tempEngine))
                        .show();
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
                    "tocá COMPARTIR LED para subir.");
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
                    lightReport=T1CabinLightReadOnlyProbe.Snapshot.compare(
                        lightBefore,snap);
                    lightStatus.setText(lightReport);
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
