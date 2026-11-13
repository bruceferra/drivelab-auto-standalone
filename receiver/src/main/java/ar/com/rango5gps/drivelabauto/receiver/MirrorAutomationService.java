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
 * does not repeat. Stable D for 800ms => restore once, without waiting to stop.
 * Manual down/up can run in P/R/N/D only while vehicle stopped and
 * DISARM auto. A successful explicit 10-UP recovery resets the policy and
 * REARMS AUTO for the next fresh R transition, without assuming measured angle.
 *
 * Commands: 2068/2069=2 down, =1 up, always release each with =0.
 * Default 2 pulses per side, editable 1..20. No absolute angle claim.
 */
public final class MirrorAutomationService extends Service {
    // OEM 2068/2069 bursts and fold 2003 must never write concurrently.
    static final Object OEM_MIRROR_MUTEX = new Object();
    public static final String PREFS="t1_mirror_auto";
    public static final String AUTO="auto", STATUS="status",LEFT="steps_left",
        RIGHT="steps_right",POSITION="position",LAST_GEAR="last_gear",LAST_SPEED="last_speed",
        SERVICE_ACTIVE="service_active",LAST_RECONNECT="last_reconnect",
        LAST_POLL="auto_last_poll_ms",
        LAST_ERROR="last_error",LAST_TRANSITION="last_transition";
    public static final String ACTION_AUTO="t1.mirror.AUTO";
    public static final String ACTION_MANUAL="t1.mirror.MANUAL";
    public static final String ACTION_DOWN="t1.mirror.MANUAL_DOWN";
    public static final String ACTION_UP="t1.mirror.MANUAL_UP";
    public static final String ACTION_RECOVER="t1.mirror.RECOVER_TEN_UP";
    public static final String ACTION_STOP="t1.mirror.STOP";
    private static final String CHANNEL="t1_mirror_parking";
    private static final int NOTIF_ID=7526;
    private static final long POLL_MS=250L, PULSE_MS=500L, PAUSE_MS=180L;
    private volatile boolean running,stop;
    private volatile int pendingManual; // 1=down, 2=up, 3=manual 10-up recovery
    private Thread worker;

    @Override public IBinder onBind(Intent ignored){return null;}
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    /** User-selected default for this Jetour: enable on a fresh Receiver open.
     * Never claim to know the physical mirror angle after a process restart.
     * An already-healthy foreground service is left untouched. */
    static void activateOnAppOpen(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long age=System.currentTimeMillis()-p.getLong(LAST_POLL,0L);
        if(p.getBoolean(AUTO,false)&&p.getBoolean(SERVICE_ACTIVE,false)&&
            age>=0&&age<=3000)return;
        // Do not rearm a possibly interrupted mirror movement.
        if(p.getString(POSITION,"").startsWith("INCIERTA")){
            p.edit().putString(STATUS,
                "ESPEJOS AUTO SOLICITADO, PAUSADO POR POSICIÓN INCIERTA; "+
                "revisar físicamente y rearmar manualmente").apply();
            return;
        }
        p.edit().putString(STATUS,"ESPEJOS AUTO · iniciando servicio de fondo")
            .apply();
        try{
            context.startForegroundService(new Intent(context,
                MirrorAutomationService.class).setAction(ACTION_AUTO));
        }catch(RuntimeException ex){
            p.edit().putString(STATUS,"No se pudo iniciar espejos AUTO: "+
                ex.getClass().getSimpleName()).apply();
        }
    }
    /** Recovery watchdog; only for an already armed preference.
     * Never re-arms after a manual fold or an uncertain actuator result. */
    static void ensureRunning(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        if(!p.getBoolean(AUTO,false)||p.getString(POSITION,"")
            .startsWith("INCIERTA"))return;
        long age=System.currentTimeMillis()-p.getLong(LAST_POLL,0L);
        if(p.getBoolean(SERVICE_ACTIVE,false)&&age>=0&&age<=3000)return;
        activateOnAppOpen(context);
    }
    private int steps(String key){
        return Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,prefs().getInt(key,2)));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // A restarted process cannot know the actual manual D pose.
        // Require conscious re-arming before any new automatic movement.
        if(intent==null){
            prefs().edit().putBoolean(AUTO,false)
                .putBoolean(SERVICE_ACTIVE,false)
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
                .putString(POSITION,"AUTO armado · referencia de ángulo NO medida")
                .putString(STATUS,"AUTO armado · esperando transición nueva a R").apply();
        }else if(ACTION_RECOVER.equals(action)){
            // Pause AUTO for a stationary 10-UP recovery. It must be rearmed
            // only after ALL commands finish; failed/partial recovery stays MANUAL.
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"RECUPERAR ESPEJOS · AUTO pausado hasta terminar").apply();
            pendingManual=3;
        }else if(ACTION_DOWN.equals(action)||ACTION_UP.equals(action)){
            // Ordinary manual adjustments intentionally disarm AUTO.
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"AUTO detenido por operación manual").apply();
            pendingManual=ACTION_DOWN.equals(action)?1:2;
        }
        boolean armed=prefs().getBoolean(AUTO,false);
        if(!armed && pendingManual==0){stopSelf();return START_NOT_STICKY;}
        if(running)return armed?START_STICKY:START_NOT_STICKY;
        stop=false;running=true;
        foreground();
        prefs().edit().putBoolean(SERVICE_ACTIVE,true).apply();
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
        int errors=0,readErrors=0;
        boolean movementStarted=false;
        MirrorAutomationOem oem=null;
        try{
            while(!stop){
                boolean auto=prefs().getBoolean(AUTO,false);
                prefs().edit().putLong(LAST_POLL,System.currentTimeMillis()).apply();
                int manual=pendingManual;
                if(!auto&&manual==0)break;
                try{
                    // ONE persistent Binder connection: do not bind/unbind
                    // VehicleService every 250 ms. This monitor runs as a
                    // foreground SERVICE independently of any Activity.
                    if(oem==null)oem=new MirrorAutomationOem(this);
                    int rawGear=oem.gear();
                    MirrorAutomationOem.SpeedSample speedRead=oem.speedSample();
                    double speed=speedRead.kmh;
                    if(!Double.isFinite(speed)||speed<0||speed>200)
                        throw new IllegalStateException("Velocidad inválida");
                    MirrorParkingPolicy.Gear gear=mapGear(rawGear);
                    if(gear==MirrorParkingPolicy.Gear.UNKNOWN)
                        throw new IllegalStateException("Marcha desconocida="+rawGear);
                    prefs().edit().putInt(LAST_GEAR,rawGear)
                        .putString(LAST_SPEED,String.format(java.util.Locale.US,
                            "%.1f (%s; 2148=%.1f; 1089=%.1f)",
                            speed,speedRead.source,speedRead.raw2148,
                            speedRead.raw1089)).apply();
                    errors=0;readErrors=0;
                    if(manual!=0){
                        pendingManual=0;
                        // No movement at speed; P/R/N/D allowed when stopped.
                        if(speed>0.5)
                            throw new IllegalStateException("Manual solo detenido: "+speed+" km/h");
                        int l=manual==3?10:steps(LEFT);
                        int r=manual==3?10:steps(RIGHT);
                        movementStarted=true;
                        int[] counts=runPulses(oem,manual==1,l,r,false);
                        if(counts[0]!=l||counts[1]!=r)
                            throw new IllegalStateException("Movimiento manual incompleto");
                        if(manual==3){
                            if(!MirrorParkingPolicy.completedManualRecovery(
                                    counts[0],counts[1],l,r))
                                throw new IllegalStateException(
                                    "Recuperación incompleta: AUTO no reactivado");
                            // Discard the previous TILTED/RESTORING bookkeeping.
                            // A fresh policy needs a known non-R sample before
                            // another R can command a tilt.
                            policy=new MirrorParkingPolicy(steps(LEFT),steps(RIGHT));
                            prefs().edit().putString(POSITION,
                                    "Referencia D tras recuperar; ángulo sin sensor")
                                .putString(STATUS,"RECUPERAR 10 ARRIBA completado "+
                                    "· AUTO REACTIVADO · próximo R nuevo; "+
                                    "posición física no medida")
                                .putBoolean(AUTO,true).apply();
                        }else{
                            prefs().edit().putString(STATUS,
                                    "MANUAL "+(manual==1?"BAJAR":"SUBIR")+
                                    " · izq="+l+" der="+r+" órdenes enviadas; "+
                                    "posición física no confirmada")
                                .putString(POSITION,"Manual: posición sin medir").apply();
                        }
                        movementStarted=false;
                    }else if(auto){
                        MirrorParkingPolicy.Action action=policy.observe(gear,speed,
                            SystemClock.elapsedRealtime(),true);
                        // Expose live diagnostics for field issue: R tilt succeeds,
                        // but restore sometimes never starts when entering D.
                        if(policy.phase()==MirrorParkingPolicy.Phase.TILTED &&
                                action==MirrorParkingPolicy.Action.NONE &&
                                gear!=MirrorParkingPolicy.Gear.R){
                            prefs().edit().putString(STATUS,
                                String.format(java.util.Locale.US,
                                    "AUTO esperando retorno · marcha %s "+
                                    "· velocidad %.1f (%s; 2148=%.1f / 1089=%.1f) "+
                                    "· retorno habilitado en D estable 800ms",
                                    gear,speed,speedRead.source,
                                    speedRead.raw2148,speedRead.raw1089)).apply();
                        }
                        if(action==MirrorParkingPolicy.Action.TILT_ON_R){
                            prefs().edit().putString(STATUS,
                                "AUTO · bajando espejos al entrar en R")
                                .putString(LAST_TRANSITION,
                                    "R detectada · iniciada bajada: "+
                                    System.currentTimeMillis()).apply();
                            movementStarted=true;
                            int[] n=runPulses(oem,true,policy.requestedLeft(),
                                policy.requestedRight(),true);
                            policy.completeTilt(n[0],n[1]);
                            prefs().edit().putString(POSITION,
                                policy.phase()==MirrorParkingPolicy.Phase.TILTED?
                                "R · inclinados por órdenes relativas":
                                "INCIERTA · bajada interrumpida").apply();
                            if(policy.phase()!=MirrorParkingPolicy.Phase.TILTED)
                                throw new IllegalStateException("Bajada incompleta");
                            movementStarted=false;
                        }else if(action==MirrorParkingPolicy.Action.RESTORE_AFTER_NON_R_SPEED){
                            prefs().edit().putString(STATUS,
                                "AUTO · recuperando posición D con marcha estable")
                                .putString(LAST_TRANSITION,
                                    "D estable · retorno iniciado: "+
                                    System.currentTimeMillis()).apply();
                            movementStarted=true;
                            int[] n=runPulses(oem,false,policy.leftIssued(),
                                policy.rightIssued(),true);
                            policy.completeRestore(n[0],n[1]);
                            prefs().edit().putString(POSITION,
                                policy.phase()==MirrorParkingPolicy.Phase.NORMAL?
                                "D · retorno relativo terminado":
                                "INCIERTA · retorno interrumpido").apply();
                            if(policy.phase()!=MirrorParkingPolicy.Phase.NORMAL)
                                throw new IllegalStateException("Retorno incompleto");
                            movementStarted=false;
                        }
                    }
                }catch(Exception ex){
                    String reason=ex.getMessage()==null?
                        ex.getClass().getSimpleName():ex.getMessage();
                    // On READ-only Binder errors keep the known policy state
                    // and reconnect. Never retry an interrupted actuator run.
                    if(oem!=null){oem.close();oem=null;}
                    if(prefs().getBoolean(AUTO,false)&&!movementStarted&&
                            pendingManual==0&&!stop){
                        readErrors++;
                        prefs().edit().putString(LAST_ERROR,
                            "LECTURA OEM · "+reason+" · "+
                            System.currentTimeMillis()).apply();
                        prefs().edit().putString(STATUS,
                            "AUTO SIN DATOS OEM · reconectando servicio en segundo plano "+
                            "(fallos lectura "+readErrors+") · "+reason)
                            .putLong(LAST_RECONNECT,System.currentTimeMillis()).apply();
                        try{Thread.sleep(Math.min(2500L,400L*readErrors));}
                        catch(InterruptedException e){if(stop)break;}
                        continue;
                    }
                    movementStarted=false;
                    if(pendingManual!=0)pendingManual=0;
                    if(prefs().getBoolean(AUTO,false)){
                        // Never automatically retry a possibly partial movement.
                        prefs().edit().putBoolean(AUTO,false)
                            .putString(POSITION,"INCIERTA · calibrar manualmente")
                            .putString(LAST_ERROR,"AUTO DETENIDO · "+reason+
                                " · "+System.currentTimeMillis())
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
            if(oem!=null)oem.close();
            running=false;
            prefs().edit().putBoolean(SERVICE_ACTIVE,false).apply();
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
        synchronized(OEM_MIRROR_MUTEX){
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
                    if(!down && gear!=4)
                        throw new IllegalStateException(
                            "Retorno automático interrumpido: dejó marcha D");
                    // For the upward return to normal rear visibility, do
                    // NOT gate on a speed threshold or require the driver to
                    // brake. The OEM speed remains validated at each step.
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
    }
    @Override public void onDestroy(){
        stop=true;if(worker!=null)worker.interrupt();
        if(prefs().getBoolean(AUTO,false))
            prefs().edit().putBoolean(AUTO,false)
                .putBoolean(SERVICE_ACTIVE,false)
                .putString(STATUS,"AUTO detenido por Android; rearmar desde la app").apply();
        super.onDestroy();
    }
}
