package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
    private TextView liveStatus,rangeStatus,tripStatus,formStatus;
    private EditText gasolinePrice,energyPrice,refillLiters,refillKm,anchorKm;
    private File pendingCsv;
    private double lastFuel=Double.NaN;
    private long lastUiSample=0;
    private final Runnable update=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            updateTripStatus();
            if(!sampling.get()&&System.currentTimeMillis()-lastUiSample>=3500)
                readTelem();
            ui.postDelayed(this,1300);
        }
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    @Override protected void onResume(){
        super.onResume();visible=true;ui.removeCallbacks(update);ui.post(update);
    }
    @Override protected void onPause(){
        visible=false;ui.removeCallbacks(update);super.onPause();
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
    private void render(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=col();root.setPadding(18,14,18,30);
        scroll.addView(root);
        LinearLayout head=card(root,NAVY);
        head.addView(text("DRIVELAB · TABLERO T1",23,WHITE,true));
        head.addView(text("Instrumentos en vivo · surtidor · autonomía · TRIP",
            13,0xFFB4DADD,false));
        action(root,"← VOLVER",BLUE,this::finish);

        instruments=new T1InstrumentView(this);
        root.addView(instruments,new LinearLayout.LayoutParams(-1,-2));
        liveStatus=text("Esperando lecturas OEM…",13,GRAY,false);root.addView(liveStatus);
        action(root,"LEER SEÑALES AHORA",TEAL,this::readTelem);

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
        root.addView(text("GPS con permiso o distancia aproximada por velocidad OEM. "+
            "CSV privado con marcha, velocidad, CI186, SOC, HV, contadores "+
            "1048/1049, recirculación, plegado y ruta GPS disponible.",
            12,GRAY,false));
        action(root,"EXPORTAR ÚLTIMO TRIP CSV",BLUE,this::exportLastTrip);

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
                liveStatus.setText("CI186="+(fuel==null?"SIN LECTURA":fmt(fuel,1))+
                    " L indicador · SOC="+(soc==null?"--":fmt(soc,1))+
                    "% · velocidad OEM="+(speed==null?"--":fmt(speed,1))+
                    " · 1048="+fmt(s.counter1048,2)+
                    " /1049="+fmt(s.counter1049,2));
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
            "HV descarga estimada: "+fmt(hv,3)+" kWh · carga HV: "+
            fmt(charge,3)+" kWh\n"+
            (price>0?"HV proxy × $"+fmt(price,2)+
                " = ~$"+fmt(hv*price,0)+" (NO costo de red)":
                "Precio kWh no configurado; costo eléctrico pendiente")+
            "\n"+p.getString(T1TripRecorderService.STATUS,"Sin viaje"));
    }
    private void startTrip(){
        if(getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getBoolean(T1TripRecorderService.RUNNING,false)){
            formStatus.setText("Ya hay un TRIP activo");return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
                PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                ASK_GPS);
            formStatus.setText("Autorizá GPS para kilometraje independiente; "+
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
            formStatus.setText("TRIP iniciado; muestras privadas cada 3 segundos");
        }catch(Exception ex){formStatus.setText("No se inició TRIP: "+ex.getMessage());}
    }
    private void stopTrip(){
        try{
            startService(new Intent(this,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_STOP));
            formStatus.setText("Deteniendo TRIP y conservando CSV…");
        }catch(Exception ex){formStatus.setText("Error al detener: "+ex.getMessage());}
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
                fmt(lastFuel,1));
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
    private void exportLastTrip(){
        File[] all=T1TripRecorderService.tripDir(this).listFiles(
            file->file.isFile()&&file.getName().startsWith("trip_")&&
                file.getName().endsWith(".csv"));
        if(all==null||all.length==0){formStatus.setText("No hay TRIP para exportar");return;}
        Arrays.sort(all,Comparator.comparing(File::getName).reversed());
        exportFile(all[0]);
    }
    private void exportFile(File local){
        pendingCsv=local;
        try{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("text/csv");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,local.getName());
            startActivityForResult(i,SAVE_CSV);
        }catch(Exception e){formStatus.setText("Exportación no disponible: "+e.getMessage());}
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
