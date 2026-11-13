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
 * Changes OEM 1043 (EV/HEV/EV MAX) using proven double-preflight/readback.
 * The owner's explicitly enabled four-profile optimizer also attempts
 * OEM 1040/1041/1046 changes while moving in D, using double live OEM
 * preflight, one write per differing value, readback, cooldown and fail-stop.
 * If 1041's integer getter is opaque/stale, the setter is attempted only
 * with allowed 20..70 multiples of ten. An unconfirmed readback stops the
 * current automation; never write a guessed original value to roll back.
 * The original dashboard can change regen in motion; this remains experimental.
 *
 * OEM refusal disables the controller: do not blindly retry/fallback-write.
 */
public final class T1OptimizedAutoService extends Service {
    static final String PREFS="t1_optimized_auto_v1";
    static final String ENABLED="enabled",STATUS="status",LAST_ROAD="last_road";
    // Distinct from the unverified GPS category shown by the OSM advisor.
    // The first-row chip may say MODO CIUDAD only after the three road votes,
    // battery condition and both OEM write/readback phases are verified.
    private static final String VERIFIED_ROAD="verified_applied_road",
        VERIFIED_PROFILE="verified_applied_profile",
        VERIFIED_TIME="verified_applied_at",DISPLAY_REASON="verified_display_reason";
    private static final long VERIFIED_TTL_MS=25000L;
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
    private T1SocRegenPilot socRegenPilot;
    private final T1SocRegenTransitionGate socProfileGate=
        new T1SocRegenTransitionGate();
    private long auxLastWriteAt=-1;
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
    private static String profileSignature(T1OptimizedModePolicy.Rule rule){
        if(rule==null)return "";
        return rule.power+":"+rule.strategy+":"+rule.forcedSoc+":"+
            rule.regeneration+":"+rule.minEnabled+":"+rule.minBattery+":"+
            rule.maxEnabled+":"+rule.maxBattery;
    }
    /** ONLY a fully verified active profile is allowed to paint the chip green.
     * Unlike T1OptimizedModeStore.lastRoad, this is NOT a GPS guess.
     */
    static T1OptimizedModePolicy.Road verifiedAppliedRoad(Context c){
        if(!enabled(c))return null;
        SharedPreferences p=prefs(c);
        String roadName=p.getString(VERIFIED_ROAD,"");
        if(roadName.isEmpty())return null;
        long age=System.currentTimeMillis()-p.getLong(VERIFIED_TIME,0L);
        if(age<0||age>VERIFIED_TTL_MS)return null;
        T1OptimizedModePolicy.Road road;
        try{road=T1OptimizedModePolicy.Road.valueOf(roadName);}
        catch(Exception ignored){return null;}
        if(road==T1OptimizedModePolicy.Road.UNKNOWN||
            road!=T1OptimizedModeStore.lastRoad(c))return null;
        String stored=p.getString(VERIFIED_PROFILE,"");
        if(!stored.equals(profileSignature(T1OptimizedModeStore.get(c,road))))
            return null; // changed user configuration needs fresh validation
        return road;
    }
    static String roadDisplayReason(Context c){
        if(!enabled(c))return "OPTIMIZADO OFF";
        String reason=prefs(c).getString(DISPLAY_REASON,"MODO · VERIFICANDO");
        // Never return a green/verified label based on stale SharedPreferences.
        if("CONFIRMADO".equals(reason)&&verifiedAppliedRoad(c)==null)
            return "MODO · VERIFICANDO";
        return reason;
    }
    private static void clearVerified(SharedPreferences.Editor edit,String reason){
        edit.putString(VERIFIED_ROAD,"")
            .putString(VERIFIED_PROFILE,"")
            .putLong(VERIFIED_TIME,0L)
            .putString(DISPLAY_REASON,reason);
    }
    private void pendingRoad(String reason){
        SharedPreferences p=pref();
        if(p.getString(VERIFIED_ROAD,"").isEmpty()&&
            reason.equals(p.getString(DISPLAY_REASON,"")))return;
        SharedPreferences.Editor edit=p.edit();
        clearVerified(edit,reason);
        edit.apply();
    }
    private void verifiedRoad(T1OptimizedModePolicy.Road road,
                              T1OptimizedModePolicy.Rule rule){
        if(stopping||!enabled(this))return;
        pref().edit().putString(VERIFIED_ROAD,road.name())
            .putString(VERIFIED_PROFILE,profileSignature(rule))
            .putLong(VERIFIED_TIME,System.currentTimeMillis())
            .putString(DISPLAY_REASON,"CONFIRMADO")
            .apply();
    }
    static String activate(Context c){
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED)
            return "NO ACTIVADO: habilitá ubicación precisa para Receiver";
        if(!T1OfflineRoadMap.installed(c))
            return "NO ACTIVADO: primero descargá el mapa offline PBA+CABA";
        // Only after explicit user ON. No automatic powertrain writes on boot.
        SharedPreferences.Editor activation=prefs(c).edit()
            .putBoolean(ENABLED,true)
            .putString(STATUS,"AUTO ON · esperando 3 lecturas de vía fiables");
        clearVerified(activation,"MODO · VERIFICANDO");
        activation.apply();
        // The on-screen OSM advisor should show the same map classification
        // as the independent foreground auto controller.
        c.getSharedPreferences("t1_osm_advisory_opt_in",Context.MODE_PRIVATE)
            .edit().putBoolean("osm_lookup_enabled",true).apply();
        try{
            c.startForegroundService(new Intent(c,
                T1OptimizedAutoService.class).setAction(ACTION_ON));
            return "MODO OPTIMIZADO ON · EV/HEV/EV MAX, SMART/FORZADO, "+
                "SOC FORZADO 20-70 (de 10 en 10) y regeneración por vía. "+
                "1040/1041/1046 siguen activos; lectura de vuelta "+
                "obligatoria, sin restauraciones a valores desconocidos.";
        }catch(RuntimeException e){
            SharedPreferences.Editor error=prefs(c).edit()
                .putBoolean(ENABLED,false)
                .putString(STATUS,"AUTO NO INICIADO: "+
                    e.getClass().getSimpleName());
            clearVerified(error,"OPTIMIZADO OFF");error.apply();
            return status(c);
        }
    }
    static void disable(Context c){
        SharedPreferences.Editor off=prefs(c).edit()
            .putBoolean(ENABLED,false)
            .putString(STATUS,"MODO OPTIMIZADO OFF · control OEM manual");
        clearVerified(off,"OPTIMIZADO OFF");off.apply();
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
        // A restarted service does not inherit an unverified green chip.
        pendingRoad("MODO · VERIFICANDO");
        stopping=false;
        pilot=new T1ManualThreeModePilot(this);
        socRegenPilot=new T1SocRegenPilot(this);
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
            pendingRoad("VÍA SIN IDENTIFICAR");
            gate.examine(null,null,Double.NaN,Double.NaN,-1,-1,
                SystemClock.elapsedRealtime(),false,false);
            note("AUTO ON · sin GPS preciso reciente; sin cambios");
            return;
        }
        long fixNanos=gps.getElapsedRealtimeNanos();
        if(fixNanos<=usedGpsNanos){
            // No new fix: do not renew VERIFIED_TIME. The UI will expire
            // if no fresh, verified profile arrives within the TTL.
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
        // Publish the CURRENT classified road from the live optimizer to
        // the shared first-row read-only chip on every Receiver screen.
        // Uncertain distance/GPS must never leave stale CIUDAD/AUTOPISTA
        // highlighted; UNKNOWN selects the fallback profile label.
        T1OptimizedModeStore.markReading(this,
            confident?road:T1OptimizedModePolicy.Road.UNKNOWN,
            found.roadTag,found.detail);
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
        T1OptimizedModePolicy.Decision conditions=
            T1OptimizedModePolicy.evaluate(road,rule,battery);
        if(!conditions.conditionSatisfied){
            // Critically, HEV fallback with 13% and a configured 15% CITY
            // minimum MUST NOT be represented as "MODO CIUDAD".
            pendingRoad("SOC NO CUMPLE");
        }else if(!confident||road==T1OptimizedModePolicy.Road.UNKNOWN){
            pendingRoad("VÍA SIN IDENTIFICAR");
        }else if(decision.reason!=T1OptimizedAutoGate.Reason.SAME_MODE){
            pendingRoad(T1OptimizedAppliedRoadPolicy.label(
                T1OptimizedAppliedRoadPolicy.evaluate(
                    road,rule,battery,
                    decision.reason!=T1OptimizedAutoGate.Reason.WAIT_STABLE,
                    false,false)));
        }
        if(!decision.ready()){
            if(decision.reason==T1OptimizedAutoGate.Reason.SAME_MODE){
                maybeApplySocRegen(road,rule,battery,snapshot,
                    confident,fresh);
            }else{
                note("AUTO ON · "+decision.text);
            }
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
                        "SOC/regeneración se sincronizan en el "+
                        "siguiente ciclo estable con readback OEM.");
                }else{
                    // No blind retries and no second fallback write if the
                    // OEM declined the first request.
                    pendingRoad("MODO · OEM NO CONFIRMADO");
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
    private void maybeApplySocRegen(
            T1OptimizedModePolicy.Road road,
            T1OptimizedModePolicy.Rule rule,double battery,
            T1TripTelemetry.Sample snapshot,
            boolean confident,boolean fresh){
        boolean eligible=confident&&fresh&&
            T1SocRegenWritePolicy.regenDriveEligible(
                snapshot.gear,snapshot.speed);
        T1SocRegenWritePolicy.Targets targets=
            T1SocRegenWritePolicy.desired(rule,battery);
        if(!T1OptimizedModePolicy.evaluate(road,rule,battery).conditionSatisfied){
            pendingRoad("SOC NO CUMPLE");
            // Existing fallback vehicle policy stays untouched, but that
            // fallback cannot be shown as the selected road profile.
        }
        if(!eligible){
            pendingRoad("MODO · VERIFICANDO");
            socProfileGate.examine(targets,false,false);
            note("AUTO ON · SOC/regeneración: esperando lecturas "+
                "OEM fiables de marcha/velocidad en P o D");
            return;
        }
        if(!targets.valid()){
            pendingRoad("SOC/REGEN PENDIENTE");
            socProfileGate.examine(targets,false,false);
            note("AUTO ON · SOC objetivo fuera de los seis presets "+
                "20,30,40,50,60,70. Sin escritura.");
            return;
        }
        // The raw integer 1041 getter can be opaque (e.g. 56) although
        // the original T1 UI shows 20. Accept only a supported float
        // confirmation, never interpret 56 as a previous/target percent.
        boolean socMatches=!targets.needsTarget()||
            snapshot.socTargetInt==targets.forcedSoc||
            (Double.isFinite(snapshot.socTargetFloat)&&
                Math.abs(snapshot.socTargetFloat-targets.forcedSoc)<0.05);
        boolean currentMatches=snapshot.socMode==targets.strategy&&
            snapshot.regen1046==targets.regen&&socMatches;
        T1SocRegenTransitionGate.Decision d=
            socProfileGate.examine(targets,true,currentMatches);
        if(d.reason==T1SocRegenTransitionGate.Reason.APPLIED||
           d.reason==T1SocRegenTransitionGate.Reason.OEM_MATCHES){
            // All three GPS observations have already passed via SAME_MODE.
            // A cached 1041 readback is only usable if the current OEM
            // strategy and regen remain aligned. Never infer applied from
            // a fallback or mere road classification.
            boolean baseMatches=snapshot.socMode==targets.strategy&&
                snapshot.regen1046==targets.regen;
            boolean auxVerified=(d.reason==
                T1SocRegenTransitionGate.Reason.OEM_MATCHES&&currentMatches)||
                (d.reason==T1SocRegenTransitionGate.Reason.APPLIED&&baseMatches);
            T1OptimizedAppliedRoadPolicy.Reason display=
                T1OptimizedAppliedRoadPolicy.evaluate(
                    road,rule,battery,true,true,auxVerified&&!targets.fallback);
            if(display==T1OptimizedAppliedRoadPolicy.Reason.APPLIED){
                verifiedRoad(road,rule);
                note("AUTO ON · MODO "+T1OptimizedModePolicy.roadName(road)+
                    " APLICADO · 3/3, condición SOC y 1043/1040/1046 confirmados");
            }else{
                pendingRoad(T1OptimizedAppliedRoadPolicy.label(display));
                note("AUTO ON · "+T1OptimizedModePolicy.roadName(road)+
                    " detectada; perfil NO aplicado: "+display);
            }
            return;
        }
        if(d.reason==T1SocRegenTransitionGate.Reason.WAIT_STABLE){
            pendingRoad("MODO · VERIFICANDO");
            note("AUTO ON · transición SOC/regen nueva "+
                d.matches+"/3 · "+targets.summary());
            return;
        }
        pendingRoad("SOC/REGEN PENDIENTE");
        long now=SystemClock.elapsedRealtime();
        if(auxLastWriteAt>=0&&now-auxLastWriteAt<120000L){
            note("AUTO ON · cambio de perfil SOC/regen en espera anti-oscilación");
            return;
        }
        if(pilot.busy()||socRegenPilot.busy()||
            !changing.compareAndSet(false,true)){
            note("AUTO ON · esperando que termine otra orden OEM");
            return;
        }
        auxLastWriteAt=now;
        T1OemDiagnosticLog.add(this,"OPT_SOC_TRANSICION_3_DE_3",
            "VIA="+road+" PERFIL="+targets.summary()+
            " - cambio nuevo; no se consulta antiguo SOC 1041");
        note("AUTO ON · transición confirmada 3/3 · solicitando "+
            targets.summary()+" · diagnóstico 1041 activo");
        boolean submitted=socRegenPilot.apply(targets,
            ()->!stopping&&enabled(T1OptimizedAutoService.this),
            result->{
                changing.set(false);
                if(stopping||!enabled(T1OptimizedAutoService.this))return;
                if(result.startsWith("LECTURA CONFIRMADA:")){
                    socProfileGate.recordConfirmed(targets);
                    note("AUTO ON · "+result);
                }else{
                    // No repeated actuation after uncertain OEM readback.
                    pendingRoad("SOC/REGEN SIN CONFIRMAR");
                    pref().edit().putBoolean(ENABLED,false)
                        .putString(STATUS,"AUTO DESACTIVADO · "+
                            result).apply();
                    stopping=true;stopSelf();
                }
            });
        if(!submitted){
            changing.set(false);
            note("AUTO ON · perfil SOC/regen no enviado: "+
                "piloto ocupado o perfil inválido");
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
