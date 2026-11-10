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

/**
 * Opt-in automatic reverse tilt. Dedicated OEM foreground service, independent
 * from navigation, windows, AVAS, HVAC and trip logger.
 *
 * Auto: one transition to R at <=0.5 km/h => tilt; R/non-R parking shuffle
 * does not repeat. Non-R and >10 km/h stable 800ms => restore once.
 * Manual down/up can run in P/R/N/D only while vehicle stopped, and they
 * DISARM auto to avoid stale relative-position bookkeeping.
 *
 * Commands: 2068/2069=2 down, =1 up, always release each with =0.
 * Default 2 pulses per side, editable 1..10. No absolute angle claim.
 */
public final class MirrorAutomationService extends Service {
    public static final String PREFS="t1_mirror_auto";
    public static final String AUTO="auto", STATUS="status",LEFT="steps_left",
        RIGHT="steps_right",POSITION="position",LAST_GEAR="last_gear",LAST_SPEED="last_speed";
    public static final String ACTION_AUTO="t1.mirror.AUTO";
    public static final String ACTION_MANUAL="t1.mirror.MANUAL";
    public static final String ACTION_DOWN="t1.mirror.MANUAL_DOWN";
    public static final String ACTION_UP="t1.mirror.MANUAL_UP";
    public static final String ACTION_STOP="t1.mirror.STOP";
    private static final String CHANNEL="t1_mirror_parking";
    private static final int NOTIF_ID=7526;
    private static final long POLL_MS=250L, PULSE_MS=500L, PAUSE_MS=180L;
    private volatile boolean running,stop;
    private volatile int pendingManual; // 1=down, 2=up
    private Thread worker;

    @Override public IBinder onBind(Intent ignored){return null;}
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    private int steps(String key){
        return Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,prefs().getInt(key,2)));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // A restarted process cannot know the actual manual D pose.
        // Require conscious re-arming before any new automatic movement.
        if(intent==null){
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"AUTO suspendido tras reiniciar app; "+
                    "ajustá espejos D y volvé a activar AUTO").apply();
            stopSelf();return START_NOT_STICKY;
        }
        String action=intent.getAction();
        if(ACTION_STOP.equals(action)||ACTION_MANUAL.equals(action)){
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"MANUAL · automatización desactivada").apply();
            stop=true;if(worker!=null)worker.interrupt();
            if(!running){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
            return START_NOT_STICKY;
        }
        if(ACTION_AUTO.equals(action)){
            prefs().edit().putBoolean(AUTO,true)
                .putString(POSITION,"D · referencia ajustada por conductor")
                .putString(STATUS,"AUTO armado · esperando transición nueva a R").apply();
        }else if(ACTION_DOWN.equals(action)||ACTION_UP.equals(action)){
            // Explicit manual control always overrides auto.
            prefs().edit().putBoolean(AUTO,false).apply();
            pendingManual=ACTION_DOWN.equals(action)?1:2;
        }
        boolean armed=prefs().getBoolean(AUTO,false);
        if(!armed && pendingManual==0){stopSelf();return START_NOT_STICKY;}
        if(running)return armed?START_STICKY:START_NOT_STICKY;
        stop=false;running=true;
        foreground();
        worker=new Thread(this::monitor,"T1-ReverseMirror-Automation");
        worker.start();
        return armed?START_STICKY:START_NOT_STICKY;
    }
    private void foreground(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
            "Espejos de estacionamiento T1",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,NOTIF_ID,
            new Intent(this,ReceiverActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(NOTIF_ID,new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("DriveLab · espejos de reversa")
            .setContentText("AUTO opcional; detener desde Receiver")
            .setContentIntent(open).setOngoing(true).build());
    }
    private static MirrorParkingPolicy.Gear mapGear(int gear){
        switch(gear){
            case 1:return MirrorParkingPolicy.Gear.P;
            case 2:return MirrorParkingPolicy.Gear.R;
            case 3:return MirrorParkingPolicy.Gear.N;
            case 4:return MirrorParkingPolicy.Gear.D;
            default:return MirrorParkingPolicy.Gear.UNKNOWN;
        }
    }
    private void monitor(){
        MirrorParkingPolicy policy=new MirrorParkingPolicy(steps(LEFT),steps(RIGHT));
        int errors=0;
        try{
            while(!stop){
                boolean auto=prefs().getBoolean(AUTO,false);
                int manual=pendingManual;
                if(!auto&&manual==0)break;
                try(MirrorAutomationOem oem=new MirrorAutomationOem(this)){
                    int rawGear=oem.gear();
                    double speed=oem.speedKmh();
                    if(!Double.isFinite(speed)||speed<0||speed>200)
                        throw new IllegalStateException("Velocidad inválida");
                    MirrorParkingPolicy.Gear gear=mapGear(rawGear);
                    if(gear==MirrorParkingPolicy.Gear.UNKNOWN)
                        throw new IllegalStateException("Marcha desconocida="+rawGear);
                    prefs().edit().putInt(LAST_GEAR,rawGear)
                        .putString(LAST_SPEED,String.format(java.util.Locale.US,"%.1f",speed))
                        .apply();
                    errors=0;
                    if(manual!=0){
                        pendingManual=0;
                        // No movement at speed; P/R/N/D allowed when stopped.
                        if(speed>0.5)
                            throw new IllegalStateException("Manual solo detenido: "+speed+" km/h");
                        int l=steps(LEFT),r=steps(RIGHT);
                        int[] counts=runPulses(oem,manual==1,l,r,false);
                        if(counts[0]!=l||counts[1]!=r)
                            throw new IllegalStateException("Movimiento manual incompleto");
                        prefs().edit().putString(STATUS,
                            "MANUAL "+(manual==1?"BAJAR":"SUBIR")+
                            " · izq="+l+" der="+r+" órdenes enviadas")
                            .putString(POSITION,"Manual: posición sin medir").apply();
                    }else if(auto){
                        MirrorParkingPolicy.Action action=policy.observe(gear,speed,
                            SystemClock.elapsedRealtime(),true);
                        if(action==MirrorParkingPolicy.Action.TILT_ON_R){
                            prefs().edit().putString(STATUS,
                                "AUTO · bajando espejos al entrar en R").apply();
                            int[] n=runPulses(oem,true,policy.requestedLeft(),
                                policy.requestedRight(),true);
                            policy.completeTilt(n[0],n[1]);
                            prefs().edit().putString(POSITION,
                                policy.phase()==MirrorParkingPolicy.Phase.TILTED?
                                "R · inclinados por órdenes relativas":
                                "INCIERTA · bajada interrumpida").apply();
                            if(policy.phase()!=MirrorParkingPolicy.Phase.TILTED)
                                throw new IllegalStateException("Bajada incompleta");
                        }else if(action==MirrorParkingPolicy.Action.RESTORE_AFTER_NON_R_SPEED){
                            prefs().edit().putString(STATUS,
                                "AUTO · recuperando posición D al superar 10 km/h").apply();
                            int[] n=runPulses(oem,false,policy.leftIssued(),
                                policy.rightIssued(),true);
                            policy.completeRestore(n[0],n[1]);
                            prefs().edit().putString(POSITION,
                                policy.phase()==MirrorParkingPolicy.Phase.NORMAL?
                                "D · retorno relativo terminado":
                                "INCIERTA · retorno interrumpido").apply();
                            if(policy.phase()!=MirrorParkingPolicy.Phase.NORMAL)
                                throw new IllegalStateException("Retorno incompleto");
                        }
                    }
                }catch(Exception ex){
                    String reason=ex.getMessage()==null?
                        ex.getClass().getSimpleName():ex.getMessage();
                    if(pendingManual!=0)pendingManual=0;
                    if(prefs().getBoolean(AUTO,false)){
                        // Never automatically retry a possibly partial movement.
                        prefs().edit().putBoolean(AUTO,false)
                            .putString(POSITION,"INCIERTA · calibrar manualmente")
                            .putString(STATUS,"AUTO DETENIDO · "+reason).apply();
                        break;
                    }
                    errors++;
                    prefs().edit().putString(STATUS,"MANUAL NO COMPLETADO · "+reason).apply();
                    if(errors>=2)break;
                }
                if(!prefs().getBoolean(AUTO,false)&&pendingManual==0)break;
                try{Thread.sleep(POLL_MS);}catch(InterruptedException interrupted){
                    if(stop)break;
                }
            }
        }finally{
            running=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    /**
     * A command is counted ONLY after the OEM setter and neutralizing release
     * both return successfully. That is not proof of angular position.
     */
    private int[] runPulses(MirrorAutomationOem oem,boolean down,
                            int left,int right,boolean automatic)throws Exception{
        int[] counts={0,0};
        int max=Math.max(left,right);
        for(int step=0;step<max;step++){
            if(stop)throw new IllegalStateException("Servicio detenido");
            for(int side=0;side<2;side++){
                if(step >= (side==0?left:right))continue;
                if(stop)throw new IllegalStateException("Secuencia cancelada");
                int gear=oem.gear();
                double speed=oem.speedKmh();
                if(mapGear(gear)==MirrorParkingPolicy.Gear.UNKNOWN)
                    throw new IllegalStateException("Marcha OEM no mapeada");
                if(automatic){
                    if(down && (gear!=2 || speed>8))
                        throw new IllegalStateException("Salió de R / velocidad alta durante bajada");
                    if(!down && (gear==2 || speed<=10 || speed>75))
                        throw new IllegalStateException("Retorno interrumpido por marcha/velocidad");
                }else if(speed>0.5){
                    throw new IllegalStateException("Vehículo dejó de estar detenido");
                }
                int id=side==0?2068:2069;
                boolean released=false;
                try{
                    oem.write(id,0); // neutralize previous latched command
                    oem.write(id,down?2:1);
                    long end=SystemClock.elapsedRealtime()+PULSE_MS;
                    while(!stop&&SystemClock.elapsedRealtime()<end)
                        SystemClock.sleep(15);
                }finally{
                    // Even if gear changes or user disables auto, attempt release.
                    oem.write(id,0);
                    released=true;
                }
                if(released)counts[side]++;
                SystemClock.sleep(PAUSE_MS);
            }
        }
        return counts;
    }
    @Override public void onDestroy(){
        stop=true;if(worker!=null)worker.interrupt();
        if(prefs().getBoolean(AUTO,false))
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"AUTO detenido por Android; rearmar desde la app").apply();
        super.onDestroy();
    }
}
