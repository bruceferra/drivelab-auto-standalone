package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.ArrayDeque;

/**
 * Explicitly opt-in auto-recirculation: read 1004 every 3 seconds, request
 * 1004=1 only when previously observed 1004=2. 2101 is read-only diagnostic.
 *
 * Never writes 1004=2 or 2101. No direct ties to navigation or mirror motors.
 * If ECU/HVAC overrides repeatedly, stop writing, warn and require user reset.
 */
public final class RecirculationGuardService extends Service {
    public static final String PREFS="t1_recirc_control";
    public static final String MODE="mode",MODE_AUTO="AUTO",MODE_INTERIOR="INTERIOR";
    public static final String STATUS="status",LAST_VALUE="last_1004",LAST_EVENT="last_2101",
        PAUSED="paused";
    public static final String ACTION_INTERIOR="t1.recirc.INTERIOR";
    public static final String ACTION_AUTO="t1.recirc.AUTO";
    private static final int ID=7525;
    private static final String CHANNEL="t1_recirc_guard";
    private volatile boolean stop;
    private volatile boolean running;
    private Thread worker;
    private final ArrayDeque<Long> recentWrites=new ArrayDeque<>();
    private SharedPreferences pref(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    @Override public IBinder onBind(Intent i){return null;}

    @Override public int onStartCommand(Intent i,int flags,int startId){
        String action=i==null?null:i.getAction();
        if(ACTION_AUTO.equals(action)){
            pref().edit().putString(MODE,MODE_AUTO).putBoolean(PAUSED,false)
                .putString(STATUS,"AUTO · climatizador original decide").apply();
            stop=true;
            if(worker!=null)worker.interrupt();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if(ACTION_INTERIOR.equals(action)){
            pref().edit().putString(MODE,MODE_INTERIOR).putBoolean(PAUSED,false)
                .putString(STATUS,"SIEMPRE SIN AIRE EXTERIOR · verificando 1004").apply();
        }
        if(!MODE_INTERIOR.equals(pref().getString(MODE,MODE_AUTO))||
            pref().getBoolean(PAUSED,false)){
            stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_STICKY;
        running=true;stop=false;
        foreground();
        worker=new Thread(this::monitor,"T1-HVAC-Recirculation");
        worker.start();
        return START_STICKY;
    }
    private void foreground(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
            "Recirculación DriveLab",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,ID,
            new Intent(this,ReceiverActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(ID,new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("DriveLab · aire interior")
            .setContentText("Mantener recirculación; tocá para cambiar a AUTO")
            .setContentIntent(open).setOngoing(true).build());
    }
    private void monitor(){
        int failures=0;String finalStatus=null;
        try{
            while(!stop && MODE_INTERIOR.equals(pref().getString(MODE,MODE_AUTO))){
                if(pref().getBoolean(PAUSED,false))break;
                try(RecirculationOem vehicle=new RecirculationOem(this)){
                    int state=vehicle.read(1004);
                    if(!RecirculationPolicy.valid(state))
                        throw new IllegalStateException("1004 no reconocido: "+state);
                    int event=-1;
                    try{event=vehicle.read(2101);}catch(Exception ignored){}
                    pref().edit().putInt(LAST_VALUE,state).putInt(LAST_EVENT,event).apply();
                    failures=0;
                    if(RecirculationPolicy.needsActivation(state)){
                        long now=SystemClock.elapsedRealtime();
                        while(!recentWrites.isEmpty() &&
                            now-recentWrites.peekFirst()>60000)
                            recentWrites.removeFirst();
                        if(recentWrites.size()>=3){
                            pref().edit().putBoolean(PAUSED,true)
                                .putString(STATUS,"PAUSADO: OEM revirtió 1004 "+
                                    "3 veces/min. Posible desempañado; elegí AUTO "+
                                    "o reactivá el modo interior").apply();
                            break;
                        }
                        recentWrites.addLast(now);
                        vehicle.enableCabinRecirculation();
                        SystemClock.sleep(300);
                        if(stop)break;
                        int after=vehicle.read(1004);
                        pref().edit().putInt(LAST_VALUE,after)
                            .putString(STATUS,after==1?
                                "AIRE INTERIOR · 1004=1 confirmado":
                                "1004=1 enviado; OEM no confirmó el cambio").apply();
                    }else{
                        pref().edit().putString(STATUS,
                            "AIRE INTERIOR · 1004=1 confirmado").apply();
                    }
                }catch(Exception ex){
                    failures++;
                    pref().edit().putString(STATUS,"OEM 1004 sin lectura: "+
                        ex.getClass().getSimpleName()).apply();
                    if(failures>=4){
                        pref().edit().putBoolean(PAUSED,true)
                            .putString(STATUS,"PAUSADO: 4 errores OEM consecutivos. "+
                                "El aire exterior puede entrar; revisá el vehículo").apply();
                        break;
                    }
                }
                try{Thread.sleep(3000);}catch(InterruptedException interrupted){
                    if(stop)break;
                }
            }
        }finally{
            running=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    @Override public void onDestroy(){
        stop=true;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }
}
