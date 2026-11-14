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
 * Owner-requested default auto-recirculation on app open: read 1004 every 3 seconds, request
 * 1004=1 only when previously observed 1004=2. 2101 is read-only diagnostic.
 *
 * Never writes 1004=2 or 2101. No direct ties to navigation or mirror motors.
 * If ECU/HVAC overrides repeatedly, stop writing, warn and require a manual
 * driver reset; windshield defogging/visibility must take priority.
 */
public final class RecirculationGuardService extends Service {
    public static final String PREFS="t1_recirc_control";
    public static final String MODE="mode",MODE_AUTO="AUTO",MODE_INTERIOR="INTERIOR";
    public static final String STATUS="status",LAST_VALUE="last_1004",LAST_EVENT="last_2101",
        PAUSED="paused",MONITORING="monitoring",LAST_POLL="last_poll",
        LAST_CONFIRMED="last_1004_confirmed";
    public static final String ACTION_INTERIOR="t1.recirc.INTERIOR";
    public static final String ACTION_AUTO="t1.recirc.AUTO";
    public static final String ACTION_RESTORE="t1.recirc.RESTORE";
    private static final int ID=7525;
    private static final String CHANNEL="t1_recirc_guard";
    private volatile boolean stop;
    private volatile boolean running;
    private Thread worker;
    private final ArrayDeque<Long> recentWrites=new ArrayDeque<>();
    private SharedPreferences pref(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    /** Old releases latched PAUSED forever after four read/IPC errors.
     * Migrate ONLY that exact communication error. A safety pause caused by
     * repeated OEM reversions must still require a driver action.
     */
    static void recoverLegacyReadError(android.content.Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        if(p.getBoolean(PAUSED,false)&&p.getString(STATUS,"").startsWith(
                "PAUSADO: 4 errores OEM consecutivos.")){
            p.edit().putBoolean(PAUSED,false).putInt(LAST_VALUE,-1)
                .putLong(LAST_CONFIRMED,0)
                .putString(STATUS,
                    "AIRE INTERIOR · reconectando tras fallas de lectura OEM")
                .apply();
        }
    }
    /**
     * Automatic cabin-air preference requested by the vehicle owner:
     * on a fresh DriveLab main-screen launch, select interior air and
     * start the dedicated foreground guard. No user tap is required.
     * A safety PAUSED state is NEVER cleared by re-opening the app.
     * The driver can still choose AIRE AUTO for the current session.
     */
    static void activateOnAppOpen(android.content.Context context){
        recoverLegacyReadError(context);
        SharedPreferences p=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        boolean safetyPaused=p.getBoolean(PAUSED,false);
        p.edit().putString(MODE,MODE_INTERIOR)
            .putString(STATUS,safetyPaused?
                "AIRE INTERIOR SOLICITADO · protección OEM pausó el monitor; "+
                    "revisar desempañado y reactivar manualmente":
                "AIRE INTERIOR AUTOMÁTICO · iniciando monitoreo OEM 1004")
            .apply();
        if(safetyPaused)return;
        try{
            context.startForegroundService(new Intent(context,
                RecirculationGuardService.class).setAction(ACTION_RESTORE));
        }catch(RuntimeException error){
            p.edit().putString(STATUS,
                "Android no permitió iniciar monitoreo de aire interior: "+
                error.getClass().getSimpleName()).apply();
        }
    }
    /** Resume watchdog when changing Receiver screens or returning from Waze.
     * An OEM safety PAUSED state is NEVER cleared automatically. */
    static void ensureRunning(android.content.Context context){
        recoverLegacyReadError(context);
        SharedPreferences p=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        if(!MODE_INTERIOR.equals(p.getString(MODE,MODE_AUTO))||
            p.getBoolean(PAUSED,false))return;
        long age=System.currentTimeMillis()-p.getLong(LAST_POLL,0L);
        if(p.getBoolean(MONITORING,false)&&age>=0&&age<8000)return;
        try{
            context.startForegroundService(new Intent(context,
                RecirculationGuardService.class).setAction(ACTION_RESTORE));
        }catch(RuntimeException ex){
            p.edit().putString(STATUS,
                "No se reanudó recirculación: "+ex.getClass().getSimpleName()).apply();
        }
    }
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
            // Only an explicit tap may clear an OEM safety pause.
            pref().edit().putString(MODE,MODE_INTERIOR).putBoolean(PAUSED,false)
                .putString(STATUS,"SIEMPRE SIN AIRE EXTERIOR · verificando 1004").apply();
        }else if(ACTION_RESTORE.equals(action)){
            // Read failures recover automatically; verified OEM override
            // safety pauses are retained across reboot/app reopening.
            recoverLegacyReadError(this);
            if(!MODE_INTERIOR.equals(pref().getString(MODE,MODE_AUTO))||
                pref().getBoolean(PAUSED,false)){
                stopSelf();return START_NOT_STICKY;
            }
        }
        if(!MODE_INTERIOR.equals(pref().getString(MODE,MODE_AUTO))||
            pref().getBoolean(PAUSED,false)){
            stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_STICKY;
        running=true;stop=false;
        foreground();
        pref().edit().putBoolean(MONITORING,true)
            .putLong(LAST_POLL,System.currentTimeMillis())
            .putLong(LAST_CONFIRMED,0L).putInt(LAST_VALUE,-1).apply();
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
        int failures=0;
        try{
            while(!stop && MODE_INTERIOR.equals(pref().getString(MODE,MODE_AUTO))){
                if(pref().getBoolean(PAUSED,false))break;
                pref().edit().putLong(LAST_POLL,System.currentTimeMillis()).apply();
                try(RecirculationOem vehicle=new RecirculationOem(this)){
                    int state=vehicle.read(1004);
                    if(!RecirculationPolicy.valid(state))
                        throw new IllegalStateException("1004 no reconocido: "+state);
                    int event=-1;
                    try{event=vehicle.read(2101);}catch(Exception ignored){}
                    pref().edit().putInt(LAST_VALUE,state)
                        .putInt(LAST_EVENT,event)
                        .putLong(LAST_CONFIRMED,state==1?
                            System.currentTimeMillis():0L).apply();
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
                            .putLong(LAST_CONFIRMED,after==1?
                                System.currentTimeMillis():0L)
                            .putString(STATUS,after==1?
                                "AIRE INTERIOR · 1004=1 confirmado":
                                "1004=1 enviado; OEM no confirmó el cambio").apply();
                    }else{
                        pref().edit().putString(STATUS,
                            "AIRE INTERIOR · 1004=1 confirmado").apply();
                    }
                }catch(Exception ex){
                    failures++;
                    // Unknown is NOT confirmed recirculation. OEM Binder may
                    // not yet be ready immediately after vehicle boot.
                    // Keep user preference AUTO, wait, retry without prompting.
                    // Do NOT write 1004 until a valid OEM read succeeds.
                    pref().edit().putInt(LAST_VALUE,-1)
                        .putLong(LAST_CONFIRMED,0)
                        .putString(STATUS,"AIRE AUTO · reconectando OEM 1004 ("+
                            failures+" lecturas fallidas · "+
                            ex.getClass().getSimpleName()+"). "+
                            "Estado de recirculación SIN CONFIRMAR").apply();
                }
                long delay=failures<4?3000L:
                    Math.min(60000L,10000L*(failures-2));
                try{Thread.sleep(delay);}catch(InterruptedException interrupted){
                    if(stop)break;
                }
            }
        }finally{
            running=false;
            pref().edit().putBoolean(MONITORING,false)
                .putLong(LAST_CONFIRMED,0).apply();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    @Override public void onDestroy(){
        pref().edit().putBoolean(MONITORING,false).apply();
        stop=true;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }
}
