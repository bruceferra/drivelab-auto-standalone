package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * User-started trip: append-only private CSV every ~3 seconds, with OEM raw
 * readings and GPS (if permitted) or estimated OEM-speed distance.
 * Does not modify the stable DriveLab GPS database or Sender.
 */
public final class T1TripRecorderService extends Service {
    public static final String ACTION_START="t1.trip.START";
    public static final String ACTION_STOP="t1.trip.STOP";
    public static final String ACTION_RESTORE="t1.trip.RESTORE";
    public static final String ACTION_PAUSE="t1.trip.PAUSE";
    public static final String ACTION_RESUME="t1.trip.RESUME";
    public static final String PREFS="t1_trip_recording";
    public static final String RUNNING="running", STATUS="status", ID="trip_id",
        DISTANCE="distance_km", EV_DISTANCE="ev_distance_km",
        ICE_DISTANCE="ice_distance_km", UNKNOWN_DISTANCE="unknown_distance_km",
        GAS_L="gas_l_est", EV_KWH="hv_out_kwh",
        CHARGE_KWH="hv_in_kwh", LAST_FUEL="last_oem_fuel", SAMPLES="samples",
        DIST_SOURCE="distance_source", START_TIME="start_time", END_TIME="end_time",
        FUEL_PRICE="fuel_price", KWH_PRICE="kwh_price",
        SEG_FUEL_COUNT="seg_fuel_count", SEG_ELEC_COUNT="seg_elec_count",
        SEG_FUEL_LAST="seg_fuel_last", SEG_ELEC_LAST="seg_elec_last",
        SEG_STATUS="seg_status", SEG_FUEL_RAW_SUM="seg_fuel_raw_sum",
        SEG_ELEC_RAW_SUM="seg_elec_raw_sum", LAST_SAMPLE_TIME="last_sample_time",
        DESIRED="continuous_user_enabled",PAUSED="user_paused",PARKED_MS="parked_ms",
        OFFLINE_MS="offline_ms",RESTORE_STATUS="trip_restore_status";
    private static final String CHANNEL="t1_trip_recorder";
    private static final int NOTIF_ID=7527;
    private volatile boolean stop,running,restoring;
    private Thread worker;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastGood;
    private volatile double gpsKm=0;
    private volatile double distanceKm=0;
    private volatile boolean gpsMode=false;
    // Provisional road comparison: dashboard 22.9 km / raw integrated
    // 24.55 km, corroborated by the OEM 500 m event counts of 12 Nov.
    // Preserve original speed in the CSV and old trip history unchanged.
    private static final double OEM_SPEED_KM_FACTOR=22.9/24.55;

    @Override public IBinder onBind(Intent intent){return null;}
    private SharedPreferences p(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    public static File tripDir(Context context){
        File dir=new File(context.getFilesDir(),"t1_trips");
        if(!dir.exists())dir.mkdirs();
        return dir;
    }
    public static File currentFile(Context context){
        String id=context.getSharedPreferences(PREFS,MODE_PRIVATE).getString(ID,"");
        if(!id.matches("[0-9]{8}_[0-9]{6}"))return null;
        return new File(tripDir(context),"trip_"+id+".csv");
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?null:intent.getAction();
        if(ACTION_STOP.equals(action)){
            // Explicit TERMINAR closes the session even when it is paused.
            p().edit().putBoolean(DESIRED,false).putBoolean(PAUSED,false).commit();
            stop=true;
            if(running&&worker!=null&&worker.isAlive()){
                p().edit().putString(STATUS,
                    "Deteniendo TRIP · guardando CSV local…").apply();
                worker.interrupt();
            }else{
                // Android can kill the previous service while SharedPreferences
                // still says RUNNING. A STOP on a newly created service must
                // release that stale latch, without touching any CSV file.
                running=false;
                p().edit().putBoolean(RUNNING,false)
                    .putLong(END_TIME,System.currentTimeMillis())
                    .putString(STATUS,
                        "TRIP detenido · sesión anterior conservada")
                    .commit();
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if(ACTION_PAUSE.equals(action)){
            if(p().getBoolean(PAUSED,false))return START_NOT_STICKY;
            if(!p().getBoolean(DESIRED,false)&&
               !p().getBoolean(RUNNING,false))return START_NOT_STICKY;
            // Pause preserves same TRIP ID and CSV. It must never create a
            // second journey. Stop the worker, flush CSV, remain paused.
            p().edit().putBoolean(PAUSED,true).putBoolean(DESIRED,true)
                .putString(STATUS,"JORNADA EN PAUSA MANUAL · CSV conservado").commit();
            stop=true;
            if(running&&worker!=null&&worker.isAlive())worker.interrupt();
            else{
                running=false;
                p().edit().putBoolean(RUNNING,false)
                    .putString(RESTORE_STATUS,"PAUSA MANUAL · reanudar con botón")
                    .commit();
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if(intent==null)action=ACTION_RESTORE; // START_STICKY recreation
        if(ACTION_RESTORE.equals(action)&&p().getBoolean(PAUSED,false))
            return START_NOT_STICKY; // never silently resume a manual pause
        if(ACTION_START.equals(action)&&p().getBoolean(PAUSED,false))
            action=ACTION_RESUME;
        if(!ACTION_START.equals(action)&&!ACTION_RESTORE.equals(action)&&
            !ACTION_RESUME.equals(action))return START_NOT_STICKY;
        if(running)return START_STICKY;
        SharedPreferences before=p();
        boolean wasDesired=before.getBoolean(DESIRED,false)||
            (ACTION_RESUME.equals(action)&&before.getBoolean(PAUSED,false));
        // Recover pre-upgrade sessions that still had RUNNING=true.
        if(!before.contains(DESIRED)&&
            (before.getBoolean(RUNNING,false)||T1JourneyStore.active(this)||
                T1JourneyStore.pending(this)||T1JourneyStore2.active(this)||
                T1JourneyStore2.pending(this)))
            wasDesired=true;
        boolean oldFile=currentFile(this)!=null&&currentFile(this).isFile();
        // Explicit start only creates a new session after a user STOP.
        restoring=oldFile&&wasDesired;
        if(ACTION_RESUME.equals(action)&&!restoring){
            p().edit().putString(STATUS,"No se puede reanudar: falta CSV original").commit();
            stopSelf();return START_NOT_STICKY;
        }
        if(ACTION_RESUME.equals(action))
            before.edit().putBoolean(PAUSED,false).putBoolean(DESIRED,true).commit();
        if(ACTION_RESTORE.equals(action)&&!wasDesired)
            return START_NOT_STICKY;
        stop=false;running=true;lastGood=null;
        if(restoring){
            T1TripRecoveryState.State recovered;
            try{recovered=T1TripRecoveryState.read(currentFile(this));}
            catch(Exception ex){
                running=false;
                before.edit().putBoolean(RUNNING,false)
                    .putString(RESTORE_STATUS,
                        "No se recuperó CSV: "+ex.getClass().getSimpleName()+
                        ". Archivo intacto. Abrí RECUPERAR en el cockpit.")
                    .commit();
                stopSelf();
                return START_NOT_STICKY;
            }
            distanceKm=recovered.km;
            gpsKm=recovered.km;
            gpsMode=false;
            before.edit().putBoolean(DESIRED,true)
                .putBoolean(RUNNING,true)
                .putInt(SAMPLES,recovered.samples)
                .putLong(LAST_SAMPLE_TIME,recovered.timestamp)
                .putFloat(EV_KWH,(float)recovered.hvOut)
                .putFloat(CHARGE_KWH,(float)recovered.hvIn)
                .putFloat(DISTANCE,(float)recovered.km)
                .putString(RESTORE_STATUS,
                    "MISMO TRIP "+before.getString(ID,"")+
                    " · reanudado desde CSV, "+
                    recovered.samples+" muestras previas; "+
                    "intervalo sin GPS identificado.")
                .commit();
            return startRecording();
        }
        distanceKm=0;gpsKm=0;
        // Never overwrite a previous TRIP started during the same second.
        long started=System.currentTimeMillis();
        String name;
        do{
            name=new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.ROOT).format(new Date(started));
            started+=1000;
        }while(new File(tripDir(this),"trip_"+name+".csv").exists());
        p().edit().putString(ID,name).putBoolean(DESIRED,true)
            .putBoolean(PAUSED,false).putBoolean(RUNNING,true)
            .putLong(START_TIME,System.currentTimeMillis()).remove(END_TIME)
            .putLong(LAST_SAMPLE_TIME,0L)
            .putLong(PARKED_MS,0L).putLong(OFFLINE_MS,0L)
            .putString(RESTORE_STATUS,"Nueva jornada; solo CERRAR la finaliza")
            .putFloat(DISTANCE,0).putFloat(GAS_L,0).putFloat(EV_KWH,0)
            .putFloat(CHARGE_KWH,0).putFloat(EV_DISTANCE,0)
            .putFloat(ICE_DISTANCE,0).putFloat(UNKNOWN_DISTANCE,0)
            .putInt(SAMPLES,0)
            .putInt(SEG_FUEL_COUNT,0).putInt(SEG_ELEC_COUNT,0)
            .putFloat(SEG_FUEL_RAW_SUM,0f).putFloat(SEG_ELEC_RAW_SUM,0f)
            .remove(SEG_FUEL_LAST).remove(SEG_ELEC_LAST)
            .putString(SEG_STATUS,"Conectando callbacks OEM de 500 m…")
            .putString(STATUS,"TRIP iniciado · esperando primeras muestras").apply();
        T1TripSync.trigger(this,false);
        return startRecording();
    }
    private int startRecording(){
        try{
            foreground();
            attachGps();
            worker=new Thread(this::record,"T1-Trip-Samples");
            worker.start();
        }catch(Exception error){
            // An Android foreground-service/permission failure must not leave
            // RUNNING=true forever; otherwise INICIAR refuses every next trip.
            running=false;
            p().edit().putBoolean(RUNNING,false)
                .putLong(END_TIME,System.currentTimeMillis())
                .putString(STATUS,"No se pudo iniciar TRIP: "+
                    error.getClass().getSimpleName()).commit();
            try{stopForeground(STOP_FOREGROUND_REMOVE);}
            catch(Exception ignored){}
            stopSelf();
        }
        return START_STICKY;
    }
    private void foreground(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
            "Grabación de viajes T1",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,NOTIF_ID,
            new Intent(this,T1TankDashboardActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DriveLab · TRIP grabando")
            .setContentText("OEM, GPS y costos estimados · abrir para detener")
            .setContentIntent(open).setOngoing(true).build();
        int type=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==
            PackageManager.PERMISSION_GRANTED?
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION:
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        startForeground(NOTIF_ID,notification,type);
    }
    private void attachGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED)return;
        try{
            locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(locationManager==null||!locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER))return;
            gpsMode=true;
            locationListener=new LocationListener(){
                @Override public void onLocationChanged(Location location){
                    if(location==null||!location.hasAccuracy()||
                            location.getAccuracy()>40)return;
                    if(lastGood!=null){
                        float meters=lastGood.distanceTo(location);
                        long dt=location.getElapsedRealtimeNanos()-
                            lastGood.getElapsedRealtimeNanos();
                        if(dt>1000000000L&&dt<30000000000L&&meters>=0&&
                                meters<=(dt/1000000000.0)*55)
                            gpsKm+=meters/1000.0;
                    }
                    lastGood=location;
                    distanceKm=gpsKm;
                }
            };
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1000L,1.0f,locationListener,getMainLooper());
        }catch(Exception ex){gpsMode=false;}
    }
    private static String n(double x){
        return Double.isFinite(x)?String.format(Locale.US,"%.5f",x):"";
    }
    private static String ni(int x){
        return x==Integer.MIN_VALUE?"":Integer.toString(x);
    }
    private void record(){
        int sampleCount=restoring?p().getInt(SAMPLES,0):0;
        double hvOut=restoring?p().getFloat(EV_KWH,0f):0;
        double hvIn=restoring?p().getFloat(CHARGE_KWH,0f):0;
        long previousSample=restoring?p().getLong(LAST_SAMPLE_TIME,0):0;
        double parkedMs=p().getLong(PARKED_MS,0);
        long offlineMs=p().getLong(OFFLINE_MS,0);
        long previousActiveTick=0;
        double priorSpeed=Double.NaN;
        long priorGpsEpochNs=0L;
        double priorGpsBearing=Double.NaN;
        double classifiedKm=distanceKm;
        double evDistance=p().getFloat(EV_DISTANCE,0f);
        double iceDistance=p().getFloat(ICE_DISTANCE,0f);
        double unknownDistance=p().getFloat(UNKNOWN_DISTANCE,0f);
        String finalStatus="TRIP terminado";
        File path=currentFile(this);
        if(path==null){cleanup("TRIP sin identificador");return;}
        String tripId=p().getString(ID,"");
        T1OemConsumptionCallbacks segments=new T1OemConsumptionCallbacks(this);
        boolean segmentStarted=segments.startForTrip(tripId);
        p().edit().putString(SEG_STATUS,segmentStarted?
            "Grabando eventos OEM por tramo durante todo el TRIP":
            "Listener OEM no iniciado; TRIP normal continúa").apply();
        long lastTick=SystemClock.elapsedRealtime();
        boolean anchored=T1FuelCalibration.p(this).getBoolean(T1FuelCalibration.ANCHORED,false);
        double baselineKm=T1FuelCalibration.p(this).getFloat(T1FuelCalibration.SINCE_KM,0f);
        // Old open journeys must retain their ORIGINAL CSV column count.
        // New journeys get the richer physics/strategy schema. Never append
        // more columns than an older header declares after app updates.
        boolean extendedSchema=!restoring;
        if(restoring)try(java.io.BufferedReader head=new java.io.BufferedReader(
                new java.io.FileReader(path))){
            String header=head.readLine();
            extendedSchema=header!=null&&header.contains("mode_1043_raw");
        }catch(Exception ignored){}
        try(FileOutputStream out=new FileOutputStream(path,restoring)){
            if(!restoring)out.write(("timestamp_epoch_ms,gear_1033,speed_oem_raw,fuel_ci186_oem_l,"+
                "soc_1042_raw,soc_1062_raw,volt_1063_raw,amp_1064_raw,"+
                "counter_1048_raw,counter_1049_raw,recirc_1004,change_2101,"+
                "mirror_fold_2003,gps_lat,gps_lon,gps_accuracy_m,"+
                "distance_km_est,hv_gross_discharge_kwh_est,hv_charge_kwh_est,"+
                "gas_l_proxy,gas_cost_est,ev_cost_hv_proxy,notes,"+
                "rpm_1035_candidate,motor_on_rpm_candidate,"+
                "oem_1084_electric_net_kwh_per_100km_last50,"+
                "oem_1085_gas_l_per_100km_last50,"+
                "oem_1086_electric_average_last500m_raw,"+
                "oem_1087_fuel_average_last500m_raw,"+
                "rpm_valid_1034_raw,"+
                "oem_1086_electric_int_raw,oem_1087_fuel_int_raw,"+
                "hv_voltage_1063_int_raw,hv_current_1064_int_raw,"+
                "oem_1090_float_raw,oem_1090_int_raw,"+
                "oem_500m_fuel_event_count,oem_500m_electric_event_count,"+
                "oem_500m_fuel_last_raw,oem_500m_electric_last_raw,"+
                "oem_50km_fuel_callback_raw,oem_50km_electric_callback_raw,"+
                "mode_1043_raw,soc_strategy_1040_raw,"+
                "soc_target_1041_int_raw,soc_target_1041_float_raw,"+
                "regeneration_1046_raw,charge_1045_raw,"+
                "driving_2053_raw,footbrake_3011_raw,"+
                "fuel_pct_1079_raw,hv_enabled_1031_raw,"+
                "longitudinal_accel_speed_est_mps2,"+
                "lateral_accel_gps_est_mps2,gps_bearing_deg,gps_alt_m,"+
                "gps_fix_age_ms,engine_status_2073_raw\n")
                .getBytes(StandardCharsets.UTF_8));
            if(restoring&&path.length()>0){
                // A partial final line from sudden shutdown must never be
                // concatenated with the new sample.
                try(java.io.RandomAccessFile raf=new java.io.RandomAccessFile(
                    path,"r")){
                    raf.seek(raf.length()-1);
                    if(raf.readByte()!='\n')
                        out.write('\n');
                }
            }
            T1TripTelemetry oem=new T1TripTelemetry(this);
            while(!stop){
                T1TripTelemetry.Sample s=oem.snapshot();
                long tick=SystemClock.elapsedRealtime(),dt=tick-lastTick;
                lastTick=tick;
                // Wall-time gap is NOT distance or electrical consumption.
                // Keep it as interrupted/offline time, not fabricated motion.
                if(previousSample>0){
                    long gap=s.timestamp-previousSample;
                    if(gap>12000&&gap<2592000000L)offlineMs+=gap;
                }
                if(previousActiveTick>0&&dt>0&&dt<10000&&
                    Double.isFinite(s.speed)&&s.speed>=0&&s.speed<3)
                    parkedMs+=dt;
                previousActiveTick=tick;
                previousSample=s.timestamp;
                if(!gpsMode&&dt>0&&dt<=10000&&Double.isFinite(s.speed)&&
                        s.speed>=0&&s.speed<220)
                    distanceKm+=s.speed*dt/3600000.0*OEM_SPEED_KM_FACTOR;
                // Assign only observed incremental distance using RPM1035.
                // Missing RPM is unknown, never classified as electric.
                double intervalKm=dt>0&&dt<=10000?
                    Math.max(0,distanceKm-classifiedKm):0;
                classifiedKm=distanceKm;
                if(intervalKm>0){
                    if(s.motorOn==0)evDistance+=intervalKm;
                    else if(s.motorOn==1)iceDistance+=intervalKm;
                    else unknownDistance+=intervalKm;
                }
                hvOut+=T1TripMath.grossHvDischargeKwh(s.voltage,s.current,dt);
                hvIn+=T1TripMath.grossHvChargeKwh(s.voltage,s.current,dt);
                double km=Math.max(0,distanceKm);
                double liters=km*T1FuelCalibration.latestL100(this)/100.0;
                if(anchored)T1FuelCalibration.p(this).edit()
                    .putFloat(T1FuelCalibration.SINCE_KM,(float)(baselineKm+km)).apply();
                double gasPrice=p().getFloat(FUEL_PRICE,2129f);
                double kwhPrice=p().getFloat(KWH_PRICE,0f);
                double gasCost=liters*gasPrice,evCost=hvOut*kwhPrice;
                // Distinct from measured accelerometer signals: these are
                // derived from velocity and heading differences with a
                // validated time interval. No invented OEM accel IDs.
                double accelLong=Double.NaN;
                if(Double.isFinite(priorSpeed)&&Double.isFinite(s.speed)&&
                    priorSpeed>=0&&s.speed>=0&&dt>=500&&dt<=10000)
                    accelLong=(s.speed-priorSpeed)/(3.6*dt/1000.0);
                priorSpeed=Double.isFinite(s.speed)?s.speed:Double.NaN;
                Location latest=lastGood;
                double latAccel=Double.NaN,gpsBearing=Double.NaN,
                    gpsAlt=Double.NaN,gpsAgeMs=Double.NaN;
                if(latest!=null){
                    long ageNs=SystemClock.elapsedRealtimeNanos()-
                        latest.getElapsedRealtimeNanos();
                    if(ageNs>=0&&ageNs<20000000000L){
                        gpsAgeMs=ageNs/1000000.0;
                        if(latest.hasAltitude())gpsAlt=latest.getAltitude();
                        if(latest.hasBearing()&&latest.hasAccuracy()&&
                            latest.getAccuracy()<=25){
                            gpsBearing=latest.getBearing();
                            long ns=latest.getElapsedRealtimeNanos();
                            if(priorGpsEpochNs>0&&ns>priorGpsEpochNs&&
                                ns-priorGpsEpochNs<=12000000000L&&
                                Double.isFinite(priorGpsBearing)&&
                                Double.isFinite(s.speed)&&s.speed>5){
                                double change=(gpsBearing-priorGpsBearing+540)%360-180;
                                latAccel=(s.speed/3.6)*Math.toRadians(change)/
                                    ((ns-priorGpsEpochNs)/1.0e9);
                                if(Math.abs(latAccel)>9.81*1.5)
                                    latAccel=Double.NaN; // GPS jitter
                            }
                            priorGpsEpochNs=ns;
                            priorGpsBearing=gpsBearing;
                        }
                    }
                }
                String loc=latest==null?",,,":
                    n(latest.getLatitude())+","+n(latest.getLongitude())+","+
                        n(latest.getAccuracy())+",";
                T1OemConsumptionCallbacks.SegmentSnapshot seg=
                    segments.tripSegments();
                String line=s.timestamp+","+s.gear+","+n(s.speed)+","+
                    n(s.fuel)+","+n(s.socInternal)+","+n(s.socDisplay)+","+
                    n(s.voltage)+","+n(s.current)+","+
                    n(s.counter1048)+","+n(s.counter1049)+","+
                    s.recirc+","+s.recircChange+","+s.fold+","+loc+
                    n(km)+","+n(hvOut)+","+n(hvIn)+","+
                    n(liters)+","+n(gasCost)+","+n(evCost)+","+
                    (gpsMode?"GPS":"OEM_SPEED_CAL_0.9328_PRELIMINAR")+","+
                    n(s.rpm)+","+s.motorOn+","+
                    n(s.oemElectricRolling50)+","+n(s.oemFuelRolling50)+","+
                    n(s.oemElectricAverage500m)+","+n(s.oemFuelAverage500m)+","+
                    s.rpmValid1034+","+
                    ni(s.oemElectric500mInt)+","+ni(s.oemFuel500mInt)+","+
                    ni(s.voltage1063Int)+","+ni(s.current1064Int)+","+
                    n(s.voltageFloat1090)+","+ni(s.raw1090Int)+","+
                    seg.fuelCount+","+seg.electricCount+","+
                    n(seg.fuelLast)+","+n(seg.electricLast)+","+
                    n(seg.fuel50Last)+","+n(seg.electric50Last);
                if(extendedSchema)line+=","+s.powerMode+","+s.socMode+","+
                    ni(s.socTargetInt)+","+n(s.socTargetFloat)+","+
                    ni(s.regen1046)+","+ni(s.chargeSwitch1045)+","+
                    ni(s.driveMode2053)+","+ni(s.footBrake3011)+","+
                    ni(s.fuelPct1079)+","+ni(s.highVoltageReady1031)+","+
                    n(accelLong)+","+n(latAccel)+","+
                    n(gpsBearing)+","+n(gpsAlt)+","+n(gpsAgeMs)+","+
                    ni(s.engineStatus2073);
                line+="\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
                sampleCount++;
                if(sampleCount%5==0)out.flush();
                if(sampleCount%20==0){
                    T1TripSync.trigger(this,false);
                    T1ClientTripSync.trigger(this,false);
                }
                p().edit().putLong(LAST_SAMPLE_TIME,s.timestamp)
                    .putFloat(DISTANCE,(float)km)
                    .putFloat(EV_DISTANCE,(float)evDistance)
                    .putFloat(ICE_DISTANCE,(float)iceDistance)
                    .putFloat(UNKNOWN_DISTANCE,(float)unknownDistance)
                    .putFloat(GAS_L,(float)liters).putFloat(EV_KWH,(float)hvOut)
                    .putFloat(CHARGE_KWH,(float)hvIn)
                    .putFloat(LAST_FUEL,(float)s.fuel)
                    .putInt(SAMPLES,sampleCount)
                    .putLong(PARKED_MS,(long)parkedMs)
                    .putLong(OFFLINE_MS,offlineMs)
                    .putInt(SEG_FUEL_COUNT,seg.fuelCount)
                    .putInt(SEG_ELEC_COUNT,seg.electricCount)
                    .putFloat(SEG_FUEL_RAW_SUM,(float)seg.fuelRawSum)
                    .putFloat(SEG_ELEC_RAW_SUM,(float)seg.electricRawSum)
                    .putString(SEG_STATUS,segments.snapshot())
                    .putString(SEG_FUEL_LAST,n(seg.fuelLast))
                    .putString(SEG_ELEC_LAST,n(seg.electricLast))
                    .putString(DIST_SOURCE,gpsMode?"GPS":
                        "VELOCIDAD_OEM_CALIBRACION_PROVISIONAL_0_9328")
                    .putString(STATUS,"Grabando TRIP · "+sampleCount+
                        " muestras · tramos 500m nafta="+seg.fuelCount+
                        " electricidad="+seg.electricCount).apply();
                if(intervalKm>0){
                    T1JourneyStore.recordMovement(this,s.motorOn,intervalKm);
                    T1JourneyStore2.recordMovement(this,s.motorOn,intervalKm);
                }
                // A passenger may request a new journey before the first
                // sample. Resolve it here, never from an old persisted TRIP.
                T1JourneyStore.fulfillPending(this);
                T1JourneyStore2.fulfillPending(this);
                try{Thread.sleep(3000);}catch(InterruptedException e){if(stop)break;}
            }
            out.flush();
            T1TripSync.trigger(this,true);
            T1ClientTripSync.trigger(this,true);
        }catch(Exception ex){
            finalStatus="TRIP interrumpido · "+ex.getClass().getSimpleName();
        }finally{
            segments.stop();
            segments.awaitStopped(7000);
            File events=segments.lastLog();
            if(!segments.busy()&&events!=null&&events.isFile()&&
                events.getName().equals("trip_oem_"+tripId+".csv")&&
                T1CloudDiagnostics.active(this)){
                // Upload the tiny, GPS-free OEM segment log separately from
                // sanitized TRIP samples. Never upload raw GPS trip CSV.
                T1CloudDiagnostics.upload(this,events,null);
            }
            p().edit().putString(SEG_STATUS,
                segments.snapshot()+"\nRegistro OEM del viaje: "+
                (events==null?"sin archivo":events.getName())).apply();
            cleanup(finalStatus);
        }
    }
    private void cleanup(String message){
        running=false;
        if(locationManager!=null&&locationListener!=null){
            try{locationManager.removeUpdates(locationListener);}catch(Exception ignored){}
        }
        boolean desired=p().getBoolean(DESIRED,false);
        boolean paused=p().getBoolean(PAUSED,false);
        p().edit().putBoolean(RUNNING,false)
            .putLong(END_TIME,System.currentTimeMillis())
            .putString(STATUS,paused?
                "JORNADA PAUSADA MANUALMENTE · mismo CSV al reanudar":
                desired?
                "Sesión interrumpida por Android; recuperación pendiente":
                message)
            .putString(RESTORE_STATUS,paused?
                "PAUSA MANUAL · grabación detenida, CSV original conservado":
                desired?
                "INTERRUMPIDA · se conservaron CSV y checkpoints; reanudará"+
                " cuando vuelva el servicio":
                "JORNADA CERRADA SOLO POR USUARIO").commit();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy(){
        stop=true;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }
}
