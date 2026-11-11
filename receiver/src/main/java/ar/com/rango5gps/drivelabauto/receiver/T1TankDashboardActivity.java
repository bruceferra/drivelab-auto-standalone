package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.content.ContentValues;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live cockpit and persistent trip hub, separate from navigation and mirror
 * control services. All raw OEM readings and private GPS trips are local.
 * Gasoline cost is explicitly a historical mixed-mode proxy; HV current is
 * NOT a metered charger or proof of grid-billed kWh.
 */
public final class T1TankDashboardActivity extends Activity {
    private static final int NAVY=0xFF102638,TEAL=0xFF087F7A,
        GRAY=0xFF647585,PALE=0xFFEFF4F7,RED=0xFFA44833,BLUE=0xFF24689A,
        WHITE=Color.WHITE,ASK_GPS=6021,SAVE_CSV=6022;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean sampling=new AtomicBoolean(false);
    private volatile boolean visible;
    private T1InstrumentView instruments;
    private TextView liveStatus,rangeStatus,tripStatus,tripActionStatus,exportStatus,formStatus,syncStatus,oemEventsStatus;
    private T1OemConsumptionCallbacks oemCallbacks;
    private EditText gasolinePrice,energyPrice,refillLiters,refillKm,anchorKm;
    private File pendingCsv;
    private double lastFuel=Double.NaN;
    private long lastUiSample=0;
    private final Runnable update=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            updateTripStatus();
            updateSyncStatus();
            if(oemEventsStatus!=null&&oemCallbacks!=null)
                oemEventsStatus.setText(oemCallbacks.snapshot());
            if(!sampling.get()&&System.currentTimeMillis()-lastUiSample>=3500)
                readTelem();
            ui.postDelayed(this,1300);
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        oemCallbacks=new T1OemConsumptionCallbacks(this);
        render();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;ui.removeCallbacks(update);ui.post(update);
        T1TripSync.trigger(this,false);
    }
    @Override protected void onPause(){
        visible=false;ui.removeCallbacks(update);super.onPause();
    }
    @Override protected void onDestroy(){
        if(oemCallbacks!=null)oemCallbacks.stop();
        super.onDestroy();
    }
    private GradientDrawable panelBg(int color){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(20);return bg;
    }
    private TextView text(String message,int size,int color,boolean strong){
        TextView t=new TextView(this);
        t.setText(message);t.setTextSize(size);t.setTextColor(color);
        if(strong)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setPadding(5,6,5,7);return t;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private void section(LinearLayout root,String title){
        TextView label=text(title,19,NAVY,true);label.setPadding(5,18,5,8);
        root.addView(label);
    }
    private LinearLayout card(LinearLayout root,int color){
        LinearLayout c=col();c.setPadding(16,14,16,14);
        c.setBackground(panelBg(color));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=12;root.addView(c,p);return c;
    }
    private Button action(LinearLayout root,String label,int tint,Runnable run){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setTextSize(14);
        b.setBackgroundTintList(ColorStateList.valueOf(tint));
        b.setOnClickListener(v->run.run());root.addView(b);
        return b;
    }
    private EditText number(LinearLayout root,String hint,double previous){
        EditText e=new EditText(this);e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|
            InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextSize(16);e.setHint(hint);
        if(Double.isFinite(previous))e.setText(String.format(Locale.US,"%.2f",previous));
        root.addView(e);return e;
    }
    private static double parse(EditText e){
        return Double.parseDouble(e.getText().toString().trim().replace(",","."));
    }
    private static String fmt(double x,int dp){
        return Double.isFinite(x)?String.format(Locale.forLanguageTag("es-AR"),
            "%."+dp+"f",x):"--";
    }
    private static String rawInt(int x){
        return x==Integer.MIN_VALUE?"SIN LECTURA":Integer.toString(x);
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=col();root.setPadding(18,14,18,30);
        scroll.addView(root);
        LinearLayout head=card(root,NAVY);
        head.addView(text("DRIVELAB · TABLERO T1",23,WHITE,true));
        head.addView(text("Instrumentos compactos · consumo OEM 500 m por TRIP",
            13,0xFFB4DADD,false));
        action(root,"← VOLVER",BLUE,this::finish);

        instruments=new T1InstrumentView(this);
        root.addView(instruments,new LinearLayout.LayoutParams(-1,-2));
        liveStatus=text("Esperando lecturas OEM…",13,GRAY,false);root.addView(liveStatus);
        action(root,"LEER SEÑALES AHORA",TEAL,this::readTelem);

        section(root,"PRUEBA OEM · EVENTOS 500 METROS Y 50 KM");
        LinearLayout callbackPanel=card(root,PALE);
        callbackPanel.addView(text("Escucha solo notificaciones de consumo OEM "+
            "durante 3 minutos. Comparación 500 m vs 50 km. Sin modificar "+
            "el vehículo ni calcular litros o kWh.",13,NAVY,false));
        action(callbackPanel,"ESCUCHAR EVENTOS OEM · 3 MIN",TEAL,()->{
            if(!oemCallbacks.start())
                oemEventsStatus.setText("Ya está escuchando. "+oemCallbacks.snapshot());
            else oemEventsStatus.setText(oemCallbacks.snapshot());
        });
        action(callbackPanel,"DETENER ESCUCHA OEM",RED,()->{
            oemCallbacks.stop();
            oemEventsStatus.setText("Cerrando inscripciones… "+oemCallbacks.snapshot());
        });
        action(callbackPanel,"EXPORTAR CSV EVENTOS OEM",BLUE,this::exportOemEvents);
        callbackPanel.addView(text("«REGISTRO APAGADO» significa que ahora no "+
            "está escuchando, NO que se haya perdido el CSV. Si ya grabaste, "+
            "tocá EXPORTAR para subir el último archivo. Para nuevos eventos, "+
            "tocá ESCUCHAR y dejá finalizar los 3 minutos.",12,GRAY,false));
        oemEventsStatus=text(oemCallbacks.snapshot(),14,NAVY,false);
        callbackPanel.addView(oemEventsStatus);

        section(root,"AUTONOMÍA PROPIA · NO OEM 1022 KM");
        LinearLayout range=card(root,PALE);
        rangeStatus=text("Calculando con el tanque de surtidor…",22,NAVY,true);
        range.addView(rangeStatus);
        range.addView(text("Primer corte: 66,865 L. Segundo: 66,230 L / 887 km "+
            "⇒ 7,47 L/100 km mixtos. El tope superior de 56 Ω se satura; "+
            "por debajo de 5,3 L OEM el térmico dejó de funcionar dos veces.",
            12,GRAY,false));
        action(root,"MARCAR TANQUE LLENO · REFERENCIA ACTUAL",TEAL,this::markFull);
        anchorKm=number(root,"Kilómetros desde la última carga (opcional)",Double.NaN);
        action(root,"AJUSTAR KM DESDE LLENADO",BLUE,this::setAnchorKm);
        root.addView(text("La referencia requiere una carga real. Si la lectura "+
            "CI186 ya bajó del nivel alto, no se permite fingir un llenado.",
            12,GRAY,false));

        section(root,"TRIP · GRABACIÓN PROPIA");
        LinearLayout trip=card(root,PALE);
        tripStatus=text("No hay viaje activo",18,NAVY,true);trip.addView(tripStatus);
        LinearLayout controls=row();
        Button start=new Button(this);start.setText("▶ INICIAR TRIP");
        start.setAllCaps(false);start.setTextColor(WHITE);
        start.setBackgroundTintList(ColorStateList.valueOf(TEAL));
        start.setOnClickListener(v->startTrip());
        controls.addView(start,new LinearLayout.LayoutParams(0,60,1));
        Button stop=new Button(this);stop.setText("■ DETENER TRIP");
        stop.setAllCaps(false);stop.setTextColor(WHITE);
        stop.setBackgroundTintList(ColorStateList.valueOf(RED));
        stop.setOnClickListener(v->stopTrip());
        controls.addView(stop,new LinearLayout.LayoutParams(0,60,1));
        root.addView(controls);
        tripActionStatus=text("Para cambiar de viaje: DETENER, luego INICIAR. "+
            "No requiere PIN ni sincronización.",14,NAVY,true);
        root.addView(tripActionStatus);
        root.addView(text("INICIAR TRIP también graba automáticamente todos los "+
            "eventos OEM de 500 metros hasta DETENER TRIP, incluso al abrir Waze "+
            "o la pantalla original. Los negativos eléctricos son valores crudos, "+
            "no kWh ni recuperación confirmada. El CSV de viaje y el de tramos "+
            "se conservan separados; el viaje incluye los contadores por tramo.",
            12,GRAY,false));
        action(root,"EXPORTAR ÚLTIMO TRIP CSV",BLUE,this::exportLastTrip);
        action(root,"EXPORTAR TRAMOS OEM 500 m DEL ÚLTIMO TRIP",BLUE,
            this::exportTripSegments);
        exportStatus=text("Exportación directa a Descargas/DriveLabAuto (sin selector). "+
            "El CSV original siempre se conserva.",13,NAVY,false);
        root.addView(exportStatus);

        section(root,"SINCRONIZACIÓN PRIVADA · TRIP");
        LinearLayout sync=card(root,PALE);
        sync.addView(text("Sin PIN: activá la nube una sola vez con un QR. "+
            "Los lotes TRIP se sincronizan automáticamente cuando hay Internet. "+
            "No se envían coordenadas GPS, VIN ni ubicación: solo telemetría "+
            "permitida. El CSV local se conserva.",
            13,NAVY,false));
        action(sync,"ACTIVAR NUBE CON QR · UNA VEZ",BLUE,()->{
            startActivity(new android.content.Intent(this,
                ReceiverVehicleActivity.class).putExtra("open_tab",3));
        });
        syncStatus=text("Sincronización desactivada",13,NAVY,true);
        sync.addView(syncStatus);
        action(root,"ACTIVAR SINCRONIZACIÓN PRIVADA",TEAL,this::enableSync);
        action(root,"SUBIR PENDIENTES AHORA",BLUE,this::syncNow);
        action(root,"PAUSAR SINCRONIZACIÓN",RED,this::disableSync);

        section(root,"PRECIOS DEL VIAJE · ARS");
        LinearLayout prices=card(root,PALE);
        float fuel=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getFloat(T1TripRecorderService.FUEL_PRICE,2129f);
        float kwh=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getFloat(T1TripRecorderService.KWH_PRICE,0f);
        prices.addView(text("Nafta por litro ($)",13,NAVY,true));
        gasolinePrice=number(prices,"Precio actual por litro",fuel);
        prices.addView(text("Electricidad por kWh ($)",13,NAVY,true));
        energyPrice=number(prices,"Tu tarifa por kWh",kwh);
        action(root,"GUARDAR PRECIOS",TEAL,this::savePrices);
        root.addView(text("Nafta = distancia TRIP × 7,47 L/100 km (histórico "+
            "mixto; NO consumo instantáneo real). Electricidad = descarga "+
            "HV bruta V×A (proxy NO equivalente a kWh facturados de red). "+
            "Se guardan también las señales para recalcular cuando estén validadas.",
            12,RED,false));

        section(root,"CALIBRAR CON PRÓXIMOS SURTIDORES");
        LinearLayout refill=card(root,PALE);
        refill.addView(text("Registrá solo una carga con litros y km Jetour "+
            "del ciclo equivalente comprobados. Los registros se anexan, no "+
            "se pisan los anteriores.",13,GRAY,false));
        refillLiters=number(refill,"Litros reales surtidor",Double.NaN);
        refillKm=number(refill,"Km Jetour del ciclo",Double.NaN);
        action(root,"REGISTRAR NUEVA CARGA REAL",BLUE,this::registerPump);

        section(root,"EVIDENCIA HISTÓRICA · DOS TANQUES");
        for(T1TankEvidence.Cycle c:new T1TankEvidence.Cycle[]{
                T1TankEvidence.PREVIOUS,T1TankEvidence.SECOND}){
            LinearLayout hist=card(root,PALE);
            hist.addView(text(c.label+" · "+c.cutoffDate,14,GRAY,true));
            hist.addView(text(fmt(c.lPer100(),2)+" L/100 km  ·  "+
                fmt(c.kmPerL(),2)+" km/L",20,TEAL,true));
            hist.addView(text(fmt(c.litersPump,3)+" L surtidor · "+
                (c.distanceApproximate?"~":"")+fmt(c.kmJetour,0)+
                " km Jetour",13,NAVY,false));
        }
        root.addView(text("Tres lecturas de distancia del segundo ciclo: "+
            "Jetour 887 km · T1Launcher 867 km · DriveLab GPS 895,089 km. "+
            "No se promedian; pueden tener períodos diferentes.",
            12,GRAY,false));
        root.addView(text("CSV del repostaje: 56 Ω ya a 60,5 L indicados; "+
            "al final 67 L OEM y 66,230 L realmente ingresados. "+
            "La diferencia OEM 61,7 L no mide el volumen físico. "+
            "El cálculo viejo de consumo T1Launcher permanece DESCARTADO.",
            12,RED,false));
        formStatus=text("",13,GRAY,false);root.addView(formStatus);
        Receiver3DButtons.apply(root);
        setContentView(scroll);
    }

    private File lastTripSegmentFile(){
        File[] all=T1TripRecorderService.tripDir(this).listFiles(
            file->file.isFile()&&file.getName().matches(
                "trip_oem_[0-9]{8}_[0-9]{6}\\.csv"));
        if(all==null||all.length==0)return null;
        Arrays.sort(all,Comparator.comparing(File::getName).reversed());
        return all[0];
    }
    private void exportTripSegments(){
        if(getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
                .getBoolean(T1TripRecorderService.RUNNING,false)){
            exportMessage("Detené TRIP antes de exportar los tramos OEM.");return;
        }
        File file=lastTripSegmentFile();
        if(file==null||file.length()==0){
            exportMessage("No hay tramos OEM guardados: primero INICIAR TRIP.");return;
        }
        exportMessage("Guardando tramos OEM de "+file.getName()+"…");
        new Thread(()->{
            String result;
            try{
                result="TRAMOS 500 m GUARDADOS: "+writeTripToDownloads(file)+
                    " · original local conservado";
                if(T1CloudDiagnostics.active(this)){
                    T1CloudDiagnostics.upload(this,file,(message,url)->
                        runOnUiThread(()->exportMessage(message)));
                    result+=" · envío HTTPS solicitado";
                }else result+=" · activá nube QR para enviarlos al servidor";
            }catch(Exception ex){
                result="No se pudo copiar a Descargas: "+
                    ex.getClass().getSimpleName()+"; CSV original conservado";
            }
            String done=result;
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())
                exportMessage(done);});
        },"T1-Trip-OEM-Export").start();
    }
    private void exportOemEvents(){
        if(oemCallbacks.busy()){
            oemEventsStatus.setText("Primero detené la escucha o completá los 3 minutos.");
            return;
        }
        File file=oemCallbacks.lastLog();
        if(file==null||!file.isFile()||file.length()==0){
            oemEventsStatus.setText("Todavía no existe un registro OEM para exportar.");
            return;
        }
        oemEventsStatus.setText("Exportando eventos OEM a Descargas/DriveLabAuto…");
        new Thread(()->{
            String result;
            try{
                result="EVENTOS EXPORTADOS: "+writeTripToDownloads(file)+
                    " · original local conservado";
                if(T1CloudDiagnostics.active(this)){
                    T1CloudDiagnostics.upload(this,file,(message,url)->{
                        if(oemEventsStatus!=null&&!isFinishing()&&!isDestroyed())
                            oemEventsStatus.setText(message+"\n"+oemCallbacks.snapshot());
                    });
                    result+=" · envío HTTPS iniciado";
                }else result+=" · nube sin activar (usá QR en DIAGNÓSTICO)";
            }catch(Exception e){
                result="No se pudo exportar eventos a Descargas ("+
                    e.getClass().getSimpleName()+"). Registro original conservado en "+
                    file.getAbsolutePath();
            }
            final String message=result;
            runOnUiThread(()->{
                if(!isFinishing()&&!isDestroyed())
                    oemEventsStatus.setText(message+"\n"+oemCallbacks.snapshot());
            });
        },"T1-OEM-Events-Export").start();
    }

    private void readTelem(){
        if(!sampling.compareAndSet(false,true))return;
        new Thread(()->{
            T1TripTelemetry.Sample s=new T1TripTelemetry(this).snapshot();
            runOnUiThread(()->{
                sampling.set(false);lastUiSample=System.currentTimeMillis();
                if(isFinishing()||isDestroyed())return;
                Double fuel=Double.isFinite(s.fuel)&&s.fuel>=0&&s.fuel<=70?s.fuel:null;
                Double soc=Double.isFinite(s.socDisplay)&&s.socDisplay>=0&&
                    s.socDisplay<=100?s.socDisplay:
                    (Double.isFinite(s.socInternal)?s.socInternal:null);
                Double speed=Double.isFinite(s.speed)&&s.speed>=0&&s.speed<=220?
                    s.speed:null;
                instruments.setTelemetry(speed,fuel,soc);
                lastFuel=fuel==null?Double.NaN:fuel;
                liveStatus.setText("CI186="+(fuel==null?"SIN LECTURA":T1FuelDisplay.liters(fuel))+
                    " L indicador · SOC="+(soc==null?"--":fmt(soc,1))+
                    "% · velocidad OEM="+(speed==null?"--":fmt(speed,1))+
                    " · 1048="+fmt(s.counter1048,2)+
                    " /1049="+fmt(s.counter1049,2)+
                    "\nOEM ÚLTIMOS 50 KM (ventana móvil, NO este TRIP): "+
                    "1085 nafta="+fmt(s.oemFuelRolling50,1)+" L/100 km · "+
                    "1084 eléctrica neta="+fmt(s.oemElectricRolling50,1)+
                    " kWh/100 km"+
                    "\nOEM 500 METROS (crudo, para validar): 1087 nafta="+
                    fmt(s.oemFuelAverage500m,2)+" · 1086 eléctrica="+
                    fmt(s.oemElectricAverage500m,2)+
                    " · RPM válidas 1034="+s.rpmValid1034+
                    "\nPRUEBA DE TIPOS 500 m: 1087 int="+rawInt(s.oemFuel500mInt)+
                    " / 1086 int="+rawInt(s.oemElectric500mInt)+
                    " (comparar con float; -1 = posiblemente sin dato)"+
                    "\nPRUEBA HV: 1063 float="+fmt(s.voltage,2)+
                    " / int="+rawInt(s.voltage1063Int)+
                    " · 1064 float="+fmt(s.current,2)+
                    " / int="+rawInt(s.current1064Int)+
                    " · 1090 float="+fmt(s.voltageFloat1090,2)+
                    " / int="+rawInt(s.raw1090Int)+
                    " (crudos, no convertir en kWh)");
                updateRange();
            });
        },"T1-Instrument-ReadOnly").start();
    }
    private void updateRange(){
        boolean anchored=T1FuelCalibration.p(this).getBoolean(
            T1FuelCalibration.ANCHORED,false);
        double l100=T1FuelCalibration.latestL100(this);
        double est=T1FuelCalibration.remainingKm(this,
            Double.isFinite(lastFuel)?lastFuel:null);
        if(anchored){
            float usedKm=T1FuelCalibration.p(this).getFloat(
                T1FuelCalibration.SINCE_KM,0f);
            rangeStatus.setText("~"+fmt(est,0)+" km hasta corte térmico (estimado)\n"+
                "Desde carga: "+fmt(usedKm,1)+" km · referencia "+
                fmt(l100,2)+" L/100 km"+
                (T1TripMath.floatUpperSaturated(Double.isFinite(lastFuel)?
                    lastFuel:null)?"\nZona alta: flotante saturado cerca de 56 Ω":"")+
                (Double.isFinite(lastFuel)&&lastFuel>5.3&&lastFuel<60.5?
                    "\nReferencia secundaria por nivel OEM (baja confianza): ~"+
                    fmt(T1TripMath.rawGaugeRangeLowConfidence(lastFuel),0)+
                    " km, sin curva física validada":"")+
                (Double.isFinite(lastFuel)&&lastFuel<=5.3?
                    "\nCORTE OEM 5,3 L: 0 km nafta hasta corte":""));
        }else{
            rangeStatus.setText("~"+fmt(T1TripMath.PUMP_LITERS*100/l100,0)+
                " km CON TANQUE LLENO (referencia)\n"+
                "Autonomía actual sin ancla de llenado: indeterminada. "+
                "Usá MARCAR TANQUE LLENO o ingresá distancia recorrida.");
        }
    }
    private void updateTripStatus(){
        android.content.SharedPreferences p=getSharedPreferences(
            T1TripRecorderService.PREFS,MODE_PRIVATE);
        double km=p.getFloat(T1TripRecorderService.DISTANCE,0);
        double l=p.getFloat(T1TripRecorderService.GAS_L,0);
        double hv=p.getFloat(T1TripRecorderService.EV_KWH,0);
        double charge=p.getFloat(T1TripRecorderService.CHARGE_KWH,0);
        double fuelPrice=p.getFloat(T1TripRecorderService.FUEL_PRICE,2129);
        double price=p.getFloat(T1TripRecorderService.KWH_PRICE,0);
        tripStatus.setText((p.getBoolean(T1TripRecorderService.RUNNING,false)?
            "● GRABANDO TRIP":"TRIP detenido")+" · "+p.getString(
            T1TripRecorderService.ID,"ninguno")+"\n"+
            fmt(km,2)+" km ("+p.getString(T1TripRecorderService.DIST_SOURCE,"--")+")"+
            " · muestras: "+p.getInt(T1TripRecorderService.SAMPLES,0)+"\n"+
            "Nafta PROXY: "+fmt(l,3)+" L × $"+fmt(fuelPrice,2)+
            " = ~$"+fmt(l*fuelPrice,0)+"\n"+
            "OEM 500 m · tramos: nafta "+
            p.getInt(T1TripRecorderService.SEG_FUEL_COUNT,0)+
            " (último "+p.getString(T1TripRecorderService.SEG_FUEL_LAST,"—")+")"+
            " · electricidad "+
            p.getInt(T1TripRecorderService.SEG_ELEC_COUNT,0)+
            " (último "+p.getString(T1TripRecorderService.SEG_ELEC_LAST,"—")+")"+
            " · valores crudos, NO litros/kWh\n"+
            "HV descarga estimada: "+fmt(hv,3)+" kWh · carga HV: "+
            fmt(charge,3)+" kWh\n"+
            (price>0?"HV proxy × $"+fmt(price,2)+
                " = ~$"+fmt(hv*price,0)+" (NO costo de red)":
                "Precio kWh no configurado; costo eléctrico pendiente")+
            "\n"+p.getString(T1TripRecorderService.STATUS,"Sin viaje"));
    }
    private void updateSyncStatus(){
        if(syncStatus==null)return;
        android.content.SharedPreferences p=T1TripSync.prefs(this);
        boolean enabled=p.getBoolean(T1TripSync.ENABLED,false);
        syncStatus.setText((enabled?"● SINCRONIZACIÓN ACTIVA":"○ SINCRONIZACIÓN PAUSADA")+
            "\n"+p.getString(T1TripSync.STATUS,"Sin envíos")+
            "\nMuestras confirmadas: "+p.getInt(T1TripSync.SENT,0));
    }
    private void enableSync(){
        try{
            T1TripSync.configure(this);
            T1TripSync.trigger(this,true);
            formStatus.setText("Sincronización activada; enviando pendientes si hay Internet");
            updateSyncStatus();
        }catch(Exception ex){formStatus.setText("No se pudo activar: "+ex.getMessage());}
    }
    private void syncNow(){
        if(!T1TripSync.prefs(this).getBoolean(T1TripSync.ENABLED,false)){
            if(T1CloudDiagnostics.active(this)){
                enableSync();return;
            }
            formStatus.setText("Primero activá la nube con el QR de DIAGNÓSTICO");
            return;
        }
        T1TripSync.trigger(this,true);
        formStatus.setText("Se solicitó la subida; la confirmación aparece en estado.");
        updateSyncStatus();
    }
    private void disableSync(){
        T1TripSync.disable(this);
        formStatus.setText("Subida pausada. Los TRIP y CSV locales no se borraron.");
        updateSyncStatus();
    }
    private void showTripMessage(String message){
        if(tripActionStatus!=null)tripActionStatus.setText(message);
        if(formStatus!=null)formStatus.setText(message);
    }
    private void startTrip(){
        if(getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getBoolean(T1TripRecorderService.RUNNING,false)){
            showTripMessage("Hay un TRIP marcado activo. Tocá DETENER TRIP "+
                "para cerrarlo (conserva el CSV); luego INICIAR TRIP.");
            return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
                PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                ASK_GPS);
            showTripMessage("Autorizá GPS para kilometraje independiente; "+
                "si lo rechazás se usará velocidad OEM estimada.");
            return;
        }
        launchTrip();
    }
    @Override public void onRequestPermissionsResult(int requestCode,
        String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==ASK_GPS)launchTrip(); // fallback to OEM if denied
    }
    private void launchTrip(){
        try{
            startForegroundService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_START));
            showTripMessage("Solicitando TRIP; verificá arriba que aumenten "+
                "las muestras. PIN no necesario.");
        }catch(Exception ex){showTripMessage("No se inició TRIP: "+ex.getMessage());}
    }
    private void stopTrip(){
        try{
            startService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_STOP));
            showTripMessage("Deteniendo TRIP y conservando CSV… "+
                "Al mostrar TRIP detenido, podés iniciar otro.");
        }catch(Exception ex){showTripMessage("Error al detener: "+ex.getMessage());}
    }
    private void savePrices(){
        try{
            double fuel=parse(gasolinePrice),kwh=parse(energyPrice);
            if(!Double.isFinite(fuel)||fuel<=0||fuel>10000000||
                    !Double.isFinite(kwh)||kwh<0||kwh>10000000)
                throw new IllegalArgumentException("Precios fuera de rango");
            getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE).edit()
                .putFloat(T1TripRecorderService.FUEL_PRICE,(float)fuel)
                .putFloat(T1TripRecorderService.KWH_PRICE,(float)kwh).apply();
            formStatus.setText("Precios guardados · Nafta $"+fmt(fuel,2)+
                "/L · electricidad $"+fmt(kwh,2)+"/kWh");
            updateTripStatus();
        }catch(Exception ex){formStatus.setText("Precio inválido: "+ex.getMessage());}
    }
    private void markFull(){
        if(getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getBoolean(T1TripRecorderService.RUNNING,false)){
            formStatus.setText("Detené el TRIP antes de marcar carga");return;
        }
        try{
            T1FuelCalibration.markFull(this,lastFuel);
            formStatus.setText("Referencia tanque lleno guardada con lectura OEM "+
                T1FuelDisplay.liters(lastFuel));
            updateRange();
        }catch(Exception ex){
            formStatus.setText("No se registró tanque lleno: "+ex.getMessage());
        }
    }
    private void setAnchorKm(){
        try{
            if(!T1FuelCalibration.p(this).getBoolean(
                    T1FuelCalibration.ANCHORED,false))
                throw new IllegalStateException("Marcá un tanque lleno real primero");
            if(getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
                .getBoolean(T1TripRecorderService.RUNNING,false))
                throw new IllegalStateException("Detené el TRIP primero");
            double km=parse(anchorKm);
            if(!Double.isFinite(km)||km<0||km>2500)
                throw new IllegalArgumentException("Distancia fuera de rango");
            T1FuelCalibration.p(this).edit().putFloat(
                T1FuelCalibration.SINCE_KM,(float)km).apply();
            updateRange();
            formStatus.setText("Km desde llenado guardados: "+fmt(km,1));
        }catch(Exception ex){formStatus.setText(ex.getMessage());}
    }
    private void registerPump(){
        try{
            double liters=parse(refillLiters),km=parse(refillKm);
            T1FuelCalibration.registerPump(this,liters,km);
            formStatus.setText("Surtidor REAL registrado: "+fmt(liters,3)+
                " L / "+fmt(km,1)+" km = "+fmt(100*liters/km,2)+
                " L/100 km. Conservado en CSV local");
            updateRange();
        }catch(Exception ex){formStatus.setText("No se registró: "+ex.getMessage());}
    }
    private void exportMessage(String message){
        if(exportStatus!=null)exportStatus.setText(message);
        if(formStatus!=null)formStatus.setText(message);
    }
    private void exportLastTrip(){
        File[] all=T1TripRecorderService.tripDir(this).listFiles(
            file->file.isFile()&&file.getName().startsWith("trip_")&&
                file.getName().endsWith(".csv"));
        if(all==null||all.length==0){
            exportMessage("No hay TRIP para exportar");return;
        }
        Arrays.sort(all,Comparator.comparing(File::getName).reversed());
        final File local=all[0];
        if(local.length()==0){
            exportMessage("CSV vacío: cerrá DETENER TRIP y volvé a intentar");return;
        }
        exportMessage("Exportando "+local.getName()+" a Descargas/DriveLabAuto…");
        // Android 11 Jetour can lack DocumentsUI. Save directly to Downloads
        // through MediaStore (scoped storage), without any file chooser.
        new Thread(()->{
            String status;
            try{
                String exported=writeTripToDownloads(local);
                status="CSV GUARDADO: "+exported+
                    " · original privado conservado.";
                if(T1CloudDiagnostics.active(this)){
                    if(!T1TripSync.prefs(this).getBoolean(T1TripSync.ENABLED,false))
                        T1TripSync.configure(this);
                    T1TripSync.trigger(this,true);
                    File segmentFile=lastTripSegmentFile();
                    if(segmentFile!=null && !getSharedPreferences(
                        T1TripRecorderService.PREFS,MODE_PRIVATE)
                            .getBoolean(T1TripRecorderService.RUNNING,false))
                        T1CloudDiagnostics.upload(this,segmentFile,(msg,url)->
                            runOnUiThread(()->exportMessage(msg)));
                    status+=" · TRIP a sincronización QR sin GPS; tramos OEM si disponibles";
                }else status+=" · nube sin activar (usá QR en DIAGNÓSTICO)";
            }catch(Exception primary){
                // Some OEM builds also lack the standard Downloads provider.
                // Fallback is app-owned external storage, accessible by adb pull.
                try{
                    File ext=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                    if(ext==null)throw new IllegalStateException("Sin almacenamiento externo");
                    File folder=new File(ext,"DriveLabAuto");
                    if(!folder.isDirectory()&&!folder.mkdirs())
                        throw new IllegalStateException("No se pudo crear carpeta");
                    File target=new File(folder,local.getName());
                    try(InputStream in=new FileInputStream(local);
                        OutputStream out=new FileOutputStream(target,false)){
                        copyStream(in,out);
                    }
                    status="CSV GUARDADO POR RUTA ALTERNATIVA: "+
                        target.getAbsolutePath()+
                        " · descargalo con WebADB. Original conservado.";
                }catch(Exception secondary){
                    status="No se pudo exportar (Descargas: "+
                        primary.getClass().getSimpleName()+
                        "; alternativa: "+secondary.getClass().getSimpleName()+
                        "). El CSV original sigue guardado.";
                }
            }
            final String result=status;
            runOnUiThread(()->{
                if(!isFinishing()&&!isDestroyed())exportMessage(result);
            });
        },"T1-Trip-Export-Downloads").start();
    }
    private static void copyStream(InputStream in,OutputStream out)
        throws java.io.IOException{
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1)out.write(buf,0,n);
        out.flush();
    }
    private String writeTripToDownloads(File local)throws Exception{
        ContentValues info=new ContentValues();
        info.put(MediaStore.MediaColumns.DISPLAY_NAME,local.getName());
        info.put(MediaStore.MediaColumns.MIME_TYPE,"text/csv");
        info.put(MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS+"/DriveLabAuto");
        info.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri dest=getContentResolver().insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,info);
        if(dest==null)throw new java.io.IOException("MediaStore sin respuesta");
        boolean committed=false;
        try{
            try(InputStream in=new FileInputStream(local);
                OutputStream out=getContentResolver().openOutputStream(dest,"w")){
                if(out==null)throw new java.io.IOException("Destino sin escritura");
                copyStream(in,out);
            }
            ContentValues done=new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(getContentResolver().update(dest,done,null,null)!=1)
                throw new java.io.IOException("No se confirmó la copia");
            committed=true;
            return "Descargas/DriveLabAuto/"+local.getName();
        }finally{
            if(!committed)try{getContentResolver().delete(dest,null,null);}
                catch(Exception ignored){}
        }
    }
    @Override protected void onActivityResult(int req,int code,Intent intent){
        super.onActivityResult(req,code,intent);
        if(req!=SAVE_CSV||code!=RESULT_OK||intent==null||
            intent.getData()==null||pendingCsv==null)return;
        try(InputStream in=new FileInputStream(pendingCsv);
            OutputStream out=getContentResolver().openOutputStream(intent.getData())){
            if(out==null)throw new IllegalStateException("Destino no accesible");
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            out.flush();
            formStatus.setText("TRIP exportado; original local conservado");
        }catch(Exception ex){formStatus.setText("Error al exportar: "+ex.getMessage());}
        finally{pendingCsv=null;}
    }
}
