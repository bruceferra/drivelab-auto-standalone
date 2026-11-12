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
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicitly enabled, foreground-backed road mode controller.
 *
 * Changes ONLY OEM property 1043 (1 EV, 2 HEV, 3 EV MAX) using the same
 * double-preflight/readback pilot as the manual OEM controls. Road/SOC
 * strategy is not an automotive safety sensor; require repeated precise,
 * fresh GPS matches before one command. Never write SOC setpoint,
 * Smart/Forced or regeneration (their setters are not validated).
 *
 * OEM refusal disables the controller: do not blindly retry/fallback-write.
 */
public final class T1OptimizedAutoService extends Service {
    static final String PREFS="t1_optimized_auto_v1";
    static final String ENABLED="enabled",STATUS="status",LAST_ROAD="last_road";
    static final String ACTION_ON="t1.optimized.ON",
        ACTION_OFF="t1.optimized.OFF";
    private static final int NOTIFICATION=7543;
    private static final String CHANNEL="t1_optimized_drive";
    private static final long INTERVAL_MS=10000L;
    private volatile boolean stopping=false;
    private volatile Location lastGps;
    private LocationManager locations;
    private Thread worker;
    private final AtomicBoolean changing=new AtomicBoolean(false);
    private final T1OptimizedAutoGate gate=new T1OptimizedAutoGate();
    private T1ManualThreeModePilot pilot;
    private long usedGpsNanos=0;

    private SharedPreferences pref(){return prefs(this);}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(
            PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context c){return prefs(c).getBoolean(ENABLED,false);}
    static String status(Context c){
        return prefs(c).getString(STATUS,"MODO OPTIMIZADO OFF · sin órdenes OEM");
    }
    static String activate(Context c){
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED)
            return "NO ACTIVADO: habilitá ubicación precisa para Receiver";
        if(!T1OfflineRoadMap.installed(c))
            return "NO ACTIVADO: primero descargá el mapa offline PBA+CABA";
        // Only after explicit user ON. No automatic powertrain writes on boot.
        prefs(c).edit().putBoolean(ENABLED,true)
            .putString(STATUS,"AUTO ON · esperando 3 lecturas de vía fiables")
            .apply();
        // The on-screen OSM advisor should show the same map classification
        // as the independent foreground auto controller.
        c.getSharedPreferences("t1_osm_advisory_opt_in",Context.MODE_PRIVATE)
            .edit().putBoolean("osm_lookup_enabled",true).apply();
        try{
            c.startForegroundService(new Intent(c,
                T1OptimizedAutoService.class).setAction(ACTION_ON));
            return "MODO OPTIMIZADO ON · EV/HEV/EV MAX automáticos por zona; "+
                "SOC forzado/regeneración aún no se escriben";
        }catch(RuntimeException e){
            prefs(c).edit().putBoolean(ENABLED,false)
                .putString(STATUS,"AUTO NO INICIADO: "+
                    e.getClass().getSimpleName()).apply();
            return status(c);
        }
    }
    static void disable(Context c){
        prefs(c).edit().putBoolean(ENABLED,false)
            .putString(STATUS,"MODO OPTIMIZADO OFF · control OEM manual")
            .apply();
        try{
            c.startService(new Intent(c,T1OptimizedAutoService.class)
                .setAction(ACTION_OFF));
        }catch(RuntimeException ignored){}
    }
    static void ensureRunning(Context c){
        // Resume only after the previously explicit ON, while screen visible.
        if(enabled(c))try{
            c.startForegroundService(new Intent(c,
                T1OptimizedAutoService.class).setAction(ACTION_ON));
        }catch(RuntimeException e){
            prefs(c).edit().putString(STATUS,
                "AUTO no restaurado: "+e.getClass().getSimpleName()).apply();
        }
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_OFF.equals(action)||!enabled(this)){
            stopping=true;
            if(worker!=null)worker.interrupt();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if(worker!=null&&worker.isAlive())return START_STICKY;
        stopping=false;
        pilot=new T1ManualThreeModePilot(this);
        showNotification();
        try{
            locations=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(locations==null)throw new IllegalStateException("Sin GPS");
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
                PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Sin permiso GPS preciso");
            // Continued while cockpit is hidden: location-type FGS was
            // started via an explicit on-screen tap.
            for(String provider:new String[]{
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER}){
                if(!locations.isProviderEnabled(provider))continue;
                // No minimum movement: a parked P/0 vehicle still receives
                // independent fresh fixes and can reach three stable
                // observations. Earlier 3m filter stalled at P.
                locations.requestLocationUpdates(provider,2500,0f,
                    listener,Looper.getMainLooper());
            }
        }catch(Exception ex){
            pref().edit().putBoolean(ENABLED,false)
                .putString(STATUS,"AUTO DETENIDO: GPS "+
                    ex.getClass().getSimpleName()).apply();
            stopSelf();return START_NOT_STICKY;
        }
        worker=new Thread(this::loop,"T1-Optimized-OEM-Drive");
        worker.start();
        return START_STICKY;
    }
    private final LocationListener listener=new LocationListener(){
        @Override public void onLocationChanged(Location loc){
            if(loc!=null)lastGps=loc;
        }
    };
    private void showNotification(){
        NotificationManager mgr=getSystemService(NotificationManager.class);
        mgr.createNotificationChannel(new NotificationChannel(CHANNEL,
            "DriveLab · modo optimizado",
            NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,T1PremiumCockpitActivity.class);
        PendingIntent tap=PendingIntent.getActivity(this,NOTIFICATION,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent disable=new Intent(this,T1OptimizedAutoService.class)
            .setAction(ACTION_OFF);
        PendingIntent stop=PendingIntent.getService(this,NOTIFICATION+1,disable,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DriveLab · modo optimizado ON")
            .setContentText("EV / HEV / EV MAX automáticos · tocar para abrir")
            .setContentIntent(tap)
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DESACTIVAR AUTOMÁTICO",stop).build())
            .setOngoing(true).build();
        startForeground(NOTIFICATION,n);
    }
    private void note(String message){
        if(!enabled(this)||stopping)return;
        pref().edit().putString(STATUS,message).apply();
    }
    private Location current(){
        Location chosen=lastGps;
        if(chosen==null)return null;
        long age=SystemClock.elapsedRealtimeNanos()-
            chosen.getElapsedRealtimeNanos();
        if(age<0||age>15000000000L||!chosen.hasAccuracy()||
            chosen.getAccuracy()>30f)return null;
        return chosen;
    }
    private void loop(){
        try{
            while(!stopping&&enabled(this)){
                try{tick();}
                catch(Exception ex){
                    note("AUTO: lectura sin cambio ("+
                        ex.getClass().getSimpleName()+")");
                }
                Thread.sleep(INTERVAL_MS);
            }
        }catch(InterruptedException ignored){}
        finally{
            if(!stopping&&!enabled(this))stopSelf();
        }
    }
    private void tick(){
        if(changing.get())return;
        Location gps=current();
        if(gps==null){
            gate.examine(null,null,Double.NaN,Double.NaN,-1,-1,
                SystemClock.elapsedRealtime(),false,false);
            note("AUTO ON · sin GPS preciso reciente; sin cambios");
            return;
        }
        long fixNanos=gps.getElapsedRealtimeNanos();
        if(fixNanos<=usedGpsNanos){
            note("AUTO ON · esperando nueva posición GPS");
            return;
        }
        usedGpsNanos=fixNanos;
        T1OsmRoadAdvisor.Result found=T1OfflineRoadMap.roadAt(this,gps);
        T1OptimizedModePolicy.Road road=
            T1OptimizedModePolicy.Road.valueOf(found.road.name());
        boolean confident=road!=T1OptimizedModePolicy.Road.UNKNOWN&&
            Double.isFinite(found.distanceMeters)&&
            found.distanceMeters>=0&&found.distanceMeters<=35;
        T1TripTelemetry.Sample snapshot=new T1TripTelemetry(this).snapshot();
        long age=System.currentTimeMillis()-snapshot.timestamp;
        boolean fresh=age>=0&&age<=10000L;
        double battery=Double.isFinite(snapshot.socDisplay)&&
            snapshot.socDisplay>=0&&snapshot.socDisplay<=100?
            snapshot.socDisplay:snapshot.socInternal;
        T1OptimizedModePolicy.Rule rule=T1OptimizedModeStore.get(this,road);
        T1OptimizedAutoGate.Decision decision=gate.examine(
            road,rule,battery,snapshot.speed,snapshot.gear,
            snapshot.powerMode,SystemClock.elapsedRealtime(),confident,fresh);
        if(!decision.ready()){
            note("AUTO ON · "+decision.text);
            return;
        }
        if(pilot.busy()||!changing.compareAndSet(false,true)){
            note("AUTO ON · espera: control OEM ocupado");return;
        }
        int target=decision.target;
        final String mode=target==1?"EV":target==2?"HEV":"EV MAX";
        note("AUTO ON · "+T1OptimizedModePolicy.roadName(road)+
            " · solicitando "+mode+" (1043="+target+")");
        gate.recordCommand(SystemClock.elapsedRealtime(),target);
        boolean submitted=pilot.request(target,
            ()->!stopping&&enabled(T1OptimizedAutoService.this),
            result->{
                changing.set(false);
                if(stopping||!enabled(T1OptimizedAutoService.this))return;
                if(result.startsWith("LECTURA CONFIRMADA:")){
                    note("AUTO ON · "+mode+" CONFIRMADO por OEM 1043. "+
                        "SOC forzado y regeneración NO aplicados.");
                }else{
                    // No blind retries and no second fallback write if the
                    // OEM declined the first request.
                    pref().edit().putBoolean(ENABLED,false)
                        .putString(STATUS,"AUTO DESACTIVADO · "+
                            "OEM no confirmó "+mode+" · "+result).apply();
                    stopping=true;
                    stopSelf();
                }
            });
        if(!submitted){
            changing.set(false);
            note("AUTO ON · piloto OEM ocupado; espera");
        }
    }
    @Override public void onDestroy(){
        stopping=true;
        if(worker!=null)worker.interrupt();
        if(locations!=null)try{locations.removeUpdates(listener);}
        catch(Exception ignored){}
        super.onDestroy();
    }
}
