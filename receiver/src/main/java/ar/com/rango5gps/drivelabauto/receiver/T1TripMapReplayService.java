package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User-initiated foreground service for THREE-MAP historical replay.
 * Separate from the Activity: leaving the tab cannot destroy this analysis.
 * Persists status/progress and finished aggregate text. Never auto-restarts,
 * modifies original trip CSV or controls any car signals.
 */
public final class T1TripMapReplayService extends Service {
    static final String PREFS="t1_trip_map_replay_job_v1";
    static final String ACTION_START="ar.com.rango5gps.drivelabauto.receiver.MAP_REPLAY";
    static final String EXTRA_ID="trip_id",EXTRA_FROM="range_from",EXTRA_UNTIL="range_until";
    static final String STATE="state",ID="id",FROM="from",UNTIL="until",
        PROGRESS="progress",RESULT="result",STAMP="updated";
    static final String RUNNING="RUNNING",DONE="DONE",ERROR="ERROR",INTERRUPTED="INTERRUPTED";
    private static final String CHANNEL="drivelab_trip_maps";
    private static final int NOTIFICATION_ID=7654;
    private static final AtomicBoolean ACTIVE=new AtomicBoolean(false);
    private volatile Thread worker;
    private volatile long lastNotificationMs;
    private String currentId="";
    private long currentStart,currentEnd;

    static SharedPreferences snapshot(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,MODE_PRIVATE);
    }
    /** Active means an in-process worker, not a stale preference from a killed APK. */
    static boolean isWorking(){return ACTIVE.get();}
    static void reconcile(Context c){
        SharedPreferences p=snapshot(c);
        if(RUNNING.equals(p.getString(STATE,""))&&!ACTIVE.get())
            p.edit().putString(STATE,INTERRUPTED)
                .putString(PROGRESS,"La app se cerró o actualizó y se "+
                    "interrumpió el análisis. Elegí el viaje para reintentarlo.")
                .putLong(STAMP,System.currentTimeMillis()).apply();
    }
    private void save(String state,String progress,String result){
        SharedPreferences.Editor e=snapshot(this).edit()
            .putString(STATE,state).putString(PROGRESS,progress)
            .putLong(STAMP,System.currentTimeMillis());
        if(result!=null)e.putString(RESULT,result);
        e.apply();
    }
    private Notification notification(String progress){
        Intent open=new Intent(this,T1TripMapAnalysisActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent go=PendingIntent.getActivity(this,NOTIFICATION_ID,open,
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String clipped=progress==null?"Analizando GPS local":progress;
        if(clipped.length()>120)clipped=clipped.substring(0,120);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DriveLab · OSM / Overture / IGN")
            .setContentText(clipped)
            .setStyle(new Notification.BigTextStyle().bigText(clipped))
            .setContentIntent(go).setOngoing(true).setOnlyAlertOnce(true)
            .build();
    }
    private void foreground(String message){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            CHANNEL,"Análisis de viajes (3 mapas)",
            NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID,notification(message));
        lastNotificationMs=SystemClock.elapsedRealtime();
    }
    private void progress(String message){
        if(!ACTIVE.get())return;
        save(RUNNING,message,null);
        long now=SystemClock.elapsedRealtime();
        if(now-lastNotificationMs>=4000){
            lastNotificationMs=now;
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm!=null)nm.notify(NOTIFICATION_ID,notification(message));
        }
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent request,int flags,int startId){
        if(request==null||!ACTION_START.equals(request.getAction())){
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        String id=request.getStringExtra(EXTRA_ID);
        long from=request.getLongExtra(EXTRA_FROM,0L);
        long until=request.getLongExtra(EXTRA_UNTIL,Long.MAX_VALUE);
        if(id==null||!id.matches("[0-9]{8}_[0-9]{6}")||
           from<0||until<from){
            save(ERROR,"Viaje o intervalo inválido; no se inició.",null);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if(!ACTIVE.compareAndSet(false,true)){
            // Do not restart or overwrite the currently running job.
            // Never stop the active foreground service from a second request.
            return START_NOT_STICKY;
        }
        currentId=id;currentStart=from;currentEnd=until;
        snapshot(this).edit().putString(ID,id).putLong(FROM,from)
            .putLong(UNTIL,until).putString(RESULT,"").apply();
        save(RUNNING,"Iniciando análisis del viaje "+id+"…",null);
        try{
            foreground("Analizando viaje "+id+" en segundo plano");
        }catch(RuntimeException ex){
            ACTIVE.set(false);
            save(ERROR,"No se pudo iniciar el servicio de primer plano: "+
                ex.getClass().getSimpleName(),null);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        worker=new Thread(()->{
            try{
                T1TripMapReplay.Report report=T1TripMapReplay.analyze(
                    getApplicationContext(),currentId,currentStart,currentEnd,
                    this::progress);
                save(DONE,"TERMINADO · "+currentId+" · "+
                    report.gpsSegments+" tramos GPS · "+
                    report.matchedBuckets+" consultas por mapa",report.text);
            }catch(Exception ex){
                String reason=ex.getMessage();
                save(ERROR,"Error de análisis "+currentId+": "+
                    ex.getClass().getSimpleName()+" · "+
                    (reason==null?"sin detalle":reason)+
                    ". Los viajes originales siguen intactos.",null);
            }finally{
                ACTIVE.set(false);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        },"DriveLab-Trip-3Maps-Foreground");
        worker.start();
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){
        Thread t=worker;
        if(ACTIVE.get()&&t!=null&&t.isAlive()){
            t.interrupt();
            save(INTERRUPTED,"Servicio interrumpido; el viaje original "+
                "se conserva. Abrí el historial para repetirlo.",null);
            ACTIVE.set(false);
        }
        super.onDestroy();
    }
}
