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
import android.provider.Settings;

/**
 * Opt-in background listener for a VERIFIED physical hard key.
 *
 * Evidence (actual T1Launcher-T1N134.apk / classes2.dex):
 * VozVolante$callback$1.onTransact handles transaction=3, with
 * key==1010 and action==100 -> VozVolante.escuchar(). The vendor
 * service and callback registration are the same interface used by
 * T1OemHardKeyBridge; code 1010 is NOT an invented Android KeyEvent.
 *
 * Explicit start/stop, no wakeword, no background mic recording. Its
 * only activity is launching DriveLab's one-tap speech UI after the
 * driver physically presses the key.
 */
public final class T1OemMicrophoneService extends Service {
    static final String PREFS="t1_oem_mic_button_v1";
    static final String ENABLED="enabled";
    static final String STATUS="status";
    static final String LAST_EVENT="last_event";
    static final String ACTION_START="drivelab.oem.mic.START";
    static final String ACTION_STOP="drivelab.oem.mic.STOP";
    private static final String CHANNEL="drivelab_t1_oem_mic";
    private static final int NOTIFICATION_ID=7801;
    private static final int KEY_MIC=1010;
    private T1OemHardKeyBridge bridge;
    private boolean started;
    private long lastKeyAt;

    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static void status(Context c,String value){
        prefs(c).edit().putString(STATUS,value).apply();
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            "DriveLab · botón de micrófono T1",NotificationManager.IMPORTANCE_LOW));
        bridge=new T1OemHardKeyBridge(this,new T1OemHardKeyBridge.Receiver(){
            @Override public void onHardKey(int key,int action,int extra){
                handleHardKey(key,action,extra);
            }
            @Override public void onStatus(String value){
                status(T1OemMicrophoneService.this,value);
            }
        });
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)||!prefs(this).getBoolean(ENABLED,false)){
            if(bridge!=null)bridge.stop();
            started=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!started){
            // The same callback registration may be exclusive on this ROM.
            // Do not compete with DriveLab's own Waze wheel OEM callback.
            boolean wheel=getSharedPreferences(T1WazeWheelService.PREFS,
                MODE_PRIVATE).getBoolean(
                    T1WazeWheelService.OEM_BINDER_ENABLED,false);
            if(wheel){
                status(this,"BLOQUEADO: desactivá primero el puente OEM de "+
                    "la ruedita Waze; el Binder podría admitir un solo receptor.");
                stopSelf();
                return START_NOT_STICKY;
            }
            startForeground(NOTIFICATION_ID,notification());
            started=true;
            bridge.start();
        }
        return START_STICKY;
    }
    private Notification notification(){
        Intent open=new Intent(this,T1ClusterWazeSettingsActivity.class);
        PendingIntent tap=PendingIntent.getActivity(this,7802,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DriveLab · micrófono del volante")
            .setContentText("Esperando pulsación física OEM 1010")
            .setContentIntent(tap).setOngoing(true).build();
    }
    private void handleHardKey(int code,int action,int extra){
        // The original APK invokes voice only for 1010 + action 100.
        // 101 is release; 1000-1003 stay reserved for wheel/OK/BACK.
        if(code!=KEY_MIC)return;
        prefs(this).edit().putString(LAST_EVENT,
            "key="+code+" action="+action+" extra="+extra).apply();
        if(action!=100||!started)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastKeyAt<1200L)return;
        lastKeyAt=now;
        Intent open=new Intent(this,T1VoiceRouteActivity.class)
            .setAction(T1VoiceRouteActivity.ACTION_SHORTCUT_GOOGLE_RECOGNITION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_SINGLE_TOP|
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try{
            // Background launch is OEM/Android-controlled. This works only
            // where the app is visible or overlay permission allows it.
            if(!ReceiverActivity.isVisible()&&!Settings.canDrawOverlays(this)){
                status(this,"MICRÓFONO 1010 RECIBIDO; Android no autoriza "+
                    "apertura en segundo plano. Permití Mostrar sobre otras "+
                    "apps para DriveLab, sin tocar permisos OEM.");
                notifyFallback(open);
                return;
            }
            startActivity(open);
            status(this,"MICRÓFONO 1010/100: reconocimiento Google "+
                "solicitado; verificar escucha en la pantalla.");
        }catch(RuntimeException ex){
            status(this,"Micrófono recibido, pero Android rechazó abrir "+
                "el reconocedor: "+ex.getClass().getSimpleName());
            notifyFallback(open);
        }
    }
    private void notifyFallback(Intent open){
        PendingIntent tap=PendingIntent.getActivity(this,7803,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification alert=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Micrófono DriveLab reconocido")
            .setContentText("Tocá para abrir reconocimiento si Android lo bloqueó")
            .setAutoCancel(true).setContentIntent(tap).build();
        getSystemService(NotificationManager.class).notify(7803,alert);
    }
    @Override public void onDestroy(){
        started=false;
        if(bridge!=null)bridge.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
