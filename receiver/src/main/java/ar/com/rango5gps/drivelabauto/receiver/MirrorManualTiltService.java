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
 * ONE TAP / ONE DIRECTION manual reverse tilt, entirely independent of
 * P/R/D transition tracking. The driver taps DOWN and later UP when wanted.
 * Every successful OEM command is journaled and exactly inverted. No angle
 * sensor is available, so 'up' never claims exact physical return.
 *
 * Actions are explicit foreground button requests ONLY; no boot or reverse.
 * Requires physically P and 0 km/h at start and before EVERY motor pulse.
 * Uses the same OEM mirror mutex and ledger as legacy AUTO; no concurrency.
 */
public final class MirrorManualTiltService extends Service {
    static final String ACTION_TOGGLE="t1.mirror.TOGGLE_MANUAL_TILT";
    static final String ACTION_REFERENCE="t1.mirror.MANUAL_REFERENCE_CONFIRMED";
    static final String SELECTED="manual_tilt_selected";
    static final String RUNNING="manual_tilt_running";
    static final String RUNNING_AT="manual_tilt_started_at";
    static final String LAST_RESULT="manual_tilt_last_result";
    static final String REFERENCE="manual_tilt_reference_confirmed";
    static final int DEFAULT_PULSES=15;
    private static final int CHANNEL_ID=7528;
    private static final String CHANNEL="drivelab_mirror_manual";
    private static final long PULSE_MS=140L,PAUSE_MS=20L;
    private static final AtomicBoolean ACTIVE=new AtomicBoolean(false);
    private volatile boolean stopping;
    private volatile Thread worker;

    static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(
            MirrorAutomationService.PREFS,Context.MODE_PRIVATE);
    }
    static boolean selected(Context c){return prefs(c).getBoolean(SELECTED,false);}
    static boolean busy(Context c){
        SharedPreferences p=prefs(c);
        long age=System.currentTimeMillis()-p.getLong(RUNNING_AT,0L);
        return p.getBoolean(RUNNING,false)&&age>=0&&age<90000L;
    }
    static boolean unverified(Context c){
        SharedPreferences p=prefs(c);
        return p.getBoolean(MirrorAutomationService.PULSE_IN_FLIGHT,false)||
            p.getBoolean(MirrorAutomationService.SAFETY_HOLD,false)&&
                p.getString(MirrorAutomationService.POSITION,"").startsWith("INCIERTA")||
            p.getBoolean(RUNNING,false)&&!busy(c);
    }
    static int left(Context c){
        return Math.max(0,prefs(c).getInt(MirrorAutomationService.PENDING_LEFT,0));
    }
    static int right(Context c){
        return Math.max(0,prefs(c).getInt(MirrorAutomationService.PENDING_RIGHT,0));
    }
    static boolean lowered(Context c){return left(c)>0||right(c)>0;}
    static String label(Context c){
        if(busy(c))return "ESPEJOS · MOVIENDO…";
        if(unverified(c))return "ESPEJOS · REVISAR";
        return lowered(c)?"SUBIR ESPEJOS · MANUAL":"BAJAR ESPEJOS · MANUAL";
    }
    static String summary(Context c){
        return "MANUAL "+(selected(c)?"SELECCIONADO":"NO SELECCIONADO")+
            " · IZQ "+left(c)+" / DER "+right(c)+" pulsos sin compensar"+
            " · "+prefs(c).getString(LAST_RESULT,"Sin maniobra manual");
    }

    private SharedPreferences store(){return prefs(this);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        final boolean reference=ACTION_REFERENCE.equals(intent.getAction());
        if(!reference&&!ACTION_TOGGLE.equals(intent.getAction())){
            stopSelf(startId);return START_NOT_STICKY;
        }
        if(!ACTIVE.compareAndSet(false,true)){
            store().edit().putString(LAST_RESULT,
                "Orden ignorada: espejos ya en movimiento").apply();
            return START_NOT_STICKY;
        }
        stopping=false;
        foreground();
        // Permanently prefer manual until driver EXPLICITLY selects AUTO.
        // The old reverse monitor is asked to stop; we wait for its worker,
        // not just for its AUTO flag.
        store().edit().putBoolean(SELECTED,true)
            .putBoolean(MirrorAutomationService.AUTO,false)
            .putBoolean(RUNNING,true)
            .putLong(RUNNING_AT,System.currentTimeMillis())
            .putString(LAST_RESULT,"Solicitada lectura OEM P/0; sin pulsos aún")
            .commit();
        try{
            startService(new Intent(this,MirrorAutomationService.class)
                .setAction(MirrorAutomationService.ACTION_MANUAL));
        }catch(Exception ignored){}
        worker=new Thread(()->{
            try{perform(reference);}
            finally{
                ACTIVE.set(false);
                store().edit().putBoolean(RUNNING,false)
                    .putLong(RUNNING_AT,0L).commit();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
        },"T1-Mirror-Manual-OnOff");
        worker.start();
        return START_NOT_STICKY;
    }
    private void foreground(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL,"DriveLab · espejos manuales",
            NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,CHANNEL_ID,
            new Intent(this,ReceiverActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(CHANNEL_ID,new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DriveLab · movimiento manual de espejos")
            .setContentText("P / 0 km/h; liberación después de cada pulso")
            .setContentIntent(open).setOngoing(true).build());
    }
    private void result(String text){
        store().edit().putString(LAST_RESULT,text)
            .putString(MirrorAutomationService.STATUS,text).commit();
    }
    private void waitForAutoStop()throws Exception{
        long deadline=SystemClock.elapsedRealtime()+6500L;
        while(SystemClock.elapsedRealtime()<deadline){
            SharedPreferences p=store();
            if(!p.getBoolean(MirrorAutomationService.SERVICE_ACTIVE,false))
                return;
            SystemClock.sleep(75L);
        }
        throw new IllegalStateException(
            "REVERSA AUTO todavía activa: sin movimiento manual");
    }
    private static void parked(MirrorAutomationOem oem)throws Exception{
        int gear=oem.gear();
        double speed=oem.speedKmh();
        if(gear!=1||!Double.isFinite(speed)||speed<0||speed>0.5)
            throw new IllegalStateException(
                "P y 0 km/h requeridos: marcha="+gear+" velocidad="+speed);
    }
    private void perform(boolean reference){
        try{
            waitForAutoStop();
            synchronized(MirrorAutomationService.OEM_MIRROR_MUTEX){
                try(MirrorAutomationOem oem=new MirrorAutomationOem(this)){
                    parked(oem);
                    if(reference){
                        // Only an explicit human confirmation after physical
                        // adjustment using original controls can clear old
                        // uncertain/unknown relative counts. No movement.
                        if(store().getBoolean(MirrorAutomationService.PULSE_IN_FLIGHT,false))
                            throw new IllegalStateException(
                                "Pulso OEM incierto: verificar liberación antes de confirmar");
                        if(!store().edit()
                            .putInt(MirrorAutomationService.PENDING_LEFT,0)
                            .putInt(MirrorAutomationService.PENDING_RIGHT,0)
                            .putBoolean(MirrorAutomationService.AUTO,false)
                            .putBoolean(MirrorAutomationService.SAFETY_HOLD,false)
                            .putBoolean(REFERENCE,true)
                            .putString(MirrorAutomationService.POSITION,
                                "MANUAL · referencia visual ajustada por conductor")
                            .commit())
                            throw new IllegalStateException("Referencia no guardada");
                        result("Referencia manual confirmada en P, sin mover "+
                            "espejos. Próximo toque: BAJAR.");
                        return;
                    }
                    if(unverified(this))
                        throw new IllegalStateException(
                            "Posición anterior incierta: ajustar con mandos "+
                            "Jetour originales y confirmar referencia manual");
                    int pendingLeft=left(this),pendingRight=right(this);
                    boolean down=pendingLeft==0&&pendingRight==0;
                    // Returning uses EXACT successful downward pulses, not
                    // the current step setting (which may have changed).
                    int l=down?steps(MirrorAutomationService.LEFT):pendingLeft;
                    int r=down?steps(MirrorAutomationService.RIGHT):pendingRight;
                    if(!down && (pendingLeft>MirrorParkingPolicy.MAX_PULSES_PER_SIDE||
                                 pendingRight>MirrorParkingPolicy.MAX_PULSES_PER_SIDE))
                        throw new IllegalStateException("Contador anterior inválido");
                    result((down?"BAJANDO":"SUBIENDO")+" manual · objetivo "+
                        "IZQ "+l+" / DER "+r+" · P estacionado");
                    for(int n=0;n<Math.max(l,r);n++){
                        for(int side=0;side<2;side++){
                            if(stopping)throw new IllegalStateException(
                                "Movimiento interrumpido; registro conservado");
                            int limit=side==0?l:r;
                            if(n>=limit)continue;
                            parked(oem); // NO P/R/D automatic transition logic
                            int id=side==0?2068:2069;
                            int old=side==0?left(this):right(this);
                            if(!store().edit().putBoolean(
                                MirrorAutomationService.PULSE_IN_FLIGHT,true).commit())
                                throw new IllegalStateException("No se guardó pulso pendiente");
                            try{
                                oem.write(id,0);
                                oem.write(id,down?2:1);
                                SystemClock.sleep(PULSE_MS);
                            }finally{
                                oem.write(id,0);
                            }
                            int next=old+(down?1:-1);
                            if(next<0||next>MirrorParkingPolicy.MAX_PULSES_PER_SIDE)
                                throw new IllegalStateException("Contador inválido");
                            String key=side==0?MirrorAutomationService.PENDING_LEFT:
                                MirrorAutomationService.PENDING_RIGHT;
                            if(!store().edit().putInt(key,next)
                                .putBoolean(MirrorAutomationService.PULSE_IN_FLIGHT,false)
                                .putString(MirrorAutomationService.POSITION,
                                    "MANUAL · "+(down?"bajada":"subida")+
                                    " por pulsos, ángulo no medido")
                                .commit())
                                throw new IllegalStateException("No se guardó pulso");
                            SystemClock.sleep(PAUSE_MS);
                        }
                    }
                    result((down?"BAJADA":"SUBIDA")+
                        " MANUAL COMPLETADA · ejecutados IZQ "+l+
                        " / DER "+r+"; pendientes IZQ "+left(this)+
                        " / DER "+right(this)+
                        ". Verificar posición física; no hay sensor de ángulo.");
                }
            }
        }catch(Exception e){
            String reason=e.getMessage()==null?
                e.getClass().getSimpleName():e.getMessage();
            store().edit().putBoolean(MirrorAutomationService.AUTO,false)
                .putBoolean(MirrorAutomationService.SAFETY_HOLD,true)
                .putString(MirrorAutomationService.POSITION,
                    "INCIERTA · manual incompleto o no verificado")
                .putString(MirrorAutomationService.LAST_ERROR,
                    "MANUAL: "+reason+" · "+System.currentTimeMillis()).commit();
            result("MANUAL NO CONFIRMADO: "+reason+
                ". Sin reintentos automáticos; comprobar ambos espejos.");
        }
    }
    private int steps(String side){
        return Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,
            store().getInt(side,DEFAULT_PULSES)));
    }
    @Override public void onDestroy(){
        stopping=true;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }
}
