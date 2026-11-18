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
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * User-requested AUTO TRIP: every observed OEM ignition, no start button and
 * no existing manual trip required. Separate SQLite journal and GPS point table.
 * Foreground service started from boot/reopening app; can't retrospectively
 * record travel before the Android service is active or OEM is readable.
 */
public final class T1AutoJourneyService extends Service {
    static final String ACTION_START="t1.auto.journey.START",
        ACTION_STOP="t1.auto.journey.STOP";
    private static final int NOTIF=7532;
    private static final String CHANNEL="t1_auto_ignition_journeys";
    private static final long ACTIVE_MS=4000L,IDLE_MS=15000L;
    private volatile boolean stopped,running;
    private Thread worker;
    private LocationManager manager;
    private LocationListener listener;
    private volatile Location lastFix;
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(T1AutoJourneyStore.PREFS,Context.MODE_PRIVATE);
    }
    static void startIfEnabled(Context c){
        // Feature is ON by default because the user explicitly requested
        // always-on ignition recording. The user can turn it OFF permanently.
        if(!prefs(c).getBoolean(T1AutoJourneyStore.ENABLED,true))return;
        try{
            c.startForegroundService(new Intent(c,T1AutoJourneyService.class)
                .setAction(ACTION_START));
        }catch(RuntimeException ex){
            prefs(c).edit().putString(T1AutoJourneyStore.STATUS,
                "ARRANQUE AUTO NO PERMITIDO POR ANDROID: "+
                ex.getClass().getSimpleName()+
                " · abrí DriveLab al encender").commit();
        }
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopped=true;
            if(worker!=null)worker.interrupt();
            stopSelf();return START_NOT_STICKY;
        }
        if(!prefs(this).getBoolean(T1AutoJourneyStore.ENABLED,true)){
            stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_STICKY;
        foreground();
        running=true;stopped=false;
        attachLocation();
        worker=new Thread(this::observe,"T1-Ignition-Auto-Journeys");
        worker.start();
        return START_STICKY;
    }
    private void foreground(){
        NotificationManager n=getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel(CHANNEL,
            "Viajes automáticos por encendido",
            NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,NOTIF,
            new Intent(this,T1AutoJourneyActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DriveLab · viajes automáticos")
            .setContentText("Busca encendido OEM y registra recorridos en SQLite")
            .setContentIntent(open).setOngoing(true).build();
        int type=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==
            PackageManager.PERMISSION_GRANTED?
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC |
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION:
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        try{startForeground(NOTIF,notification,type);}
        catch(SecurityException ex){
            // Android can forbid while-in-use LOCATION from a boot receiver.
            // Remain a read-only OEM logger without claiming GPS recording.
            startForeground(NOTIF,notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
    }
    private void attachLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED)return;
        try{
            manager=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(manager==null||
                !manager.isProviderEnabled(LocationManager.GPS_PROVIDER))return;
            listener=new LocationListener(){
                @Override public void onLocationChanged(Location fix){
                    if(fix!=null&&fix.hasAccuracy()&&fix.getAccuracy()<=40)
                        lastFix=new Location(fix);
                }
            };
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1500L,2.0f,listener,getMainLooper());
        }catch(Exception ex){lastFix=null;}
    }
    private Location validGps(){
        Location f=lastFix;
        if(f==null||!f.hasAccuracy()||f.getAccuracy()>40)return null;
        long elapsed=SystemClock.elapsedRealtimeNanos()-
            f.getElapsedRealtimeNanos();
        if(elapsed<0||elapsed>12000000000L)return null;
        return new Location(f);
    }
    private void status(String x,T1AutoDriveOem.Point p,long id){
        SharedPreferences.Editor e=prefs(this).edit()
            .putString(T1AutoJourneyStore.STATUS,x)
            .putLong(T1AutoJourneyStore.SESSION,id);
        if(p!=null)e.putLong(T1AutoJourneyStore.LAST_OEM,p.wallMs)
            .putInt(T1AutoJourneyStore.LAST_READY,p.ready);
        e.apply();
    }
    private void observe(){
        long session=0;
        int offStreak=0,errors=0;
        T1AutoDriveOem oem=null;
        try(T1AutoJourneyStore db=new T1AutoJourneyStore(this)){
            T1AutoJourneyStore.Journey previous=db.openJourney();
            if(previous!=null){
                long now=System.currentTimeMillis(),uptime=SystemClock.elapsedRealtime();
                if(now<previous.last ||
                   now-previous.last>T1AutoJourneyStore.GAP_MS ||
                   uptime<previous.lastElapsed){
                    db.end(previous.id,
                        "CIERRE POR CORTE OEM / REINICIO / GAP SIN MUESTRAS");
                }else session=previous.id;
            }
            while(!stopped&&
                prefs(this).getBoolean(T1AutoJourneyStore.ENABLED,true)){
                T1AutoDriveOem.Point p=null;
                try{
                    if(oem==null)oem=new T1AutoDriveOem(this);
                    p=oem.read();
                    errors=0;
                    boolean start=T1AutoDrivePolicy.start(
                        p.ready,p.cable,p.gear,p.speed);
                    boolean ending=T1AutoDrivePolicy.stop(
                        p.ready,p.cable,p.gear,p.speed);
                    if(session>0){
                        // No counting while cable connected or OEM ready OFF.
                        if(ending){
                            offStreak++;
                            if(offStreak>=3){
                                db.end(session,p.cable==1?
                                    "CABLE CONECTADO / FIN VIAJE":
                                    "OFF OEM 1031 · FIN ENCENDIDO");
                                session=0;offStreak=0;
                            }
                        }else{
                            offStreak=0;
                            db.append(session,p,validGps());
                        }
                    }else if(start){
                        session=db.begin(p,validGps());
                        offStreak=0;
                    }
                    if(session>0)status("GRABANDO AUTO · SESIÓN #"+session+
                        " · GPS "+(validGps()!=null?"OK":"SIN POSICIÓN")+
                        " · ready1031="+p.ready+" · marcha="+p.gear,p,session);
                    else status("ESPERANDO ENCENDIDO · ready1031="+p.ready+
                        " · cable1060="+p.cable,p,0);
                }catch(Exception ex){
                    errors++;
                    if(oem!=null){oem.close();oem=null;}
                    status("OEM no disponible ("+errors+") · sin inventar "+
                        "tiempo, km ni energía: "+ex.getClass().getSimpleName(),
                        null,session);
                }
                long delay=session>0?ACTIVE_MS:IDLE_MS;
                try{Thread.sleep(delay);}
                catch(InterruptedException ex){if(stopped)break;}
            }
            if(stopped&&session>0){
                // STOP is an explicit user action; avoid reopening a disabled
                // session at the next ignition.
                db.end(session,"AUTO DETENIDA POR USUARIO");
            }
        }catch(Exception ex){
            status("ERROR AUTO SQLite: "+
                ex.getClass().getSimpleName(),null,session);
        }finally{
            if(oem!=null)oem.close();
            running=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    @Override public void onDestroy(){
        stopped=true;
        if(worker!=null)worker.interrupt();
        if(manager!=null&&listener!=null)
            try{manager.removeUpdates(listener);}catch(Exception ignored){}
        super.onDestroy();
    }
}
