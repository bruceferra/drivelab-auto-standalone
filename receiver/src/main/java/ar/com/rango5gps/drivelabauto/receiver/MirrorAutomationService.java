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
 * Auto: one transition to R at <=0.5 km/h => tilt; low-speed R/P/D shuffle
 * does not repeat/restore. Restore ONLY at stationary P >30s or D >5km/h.
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
        LAST_ERROR="last_error",LAST_TRANSITION="last_transition",
        SAFETY_HOLD="safety_hold_until_manual_inspection",
        PENDING_LEFT="pending_left",PENDING_RIGHT="pending_right",
        PULSE_IN_FLIGHT="pulse_in_flight";
    public static final String ACTION_AUTO="t1.mirror.AUTO";
    public static final String ACTION_MANUAL="t1.mirror.MANUAL";
    public static final String ACTION_DOWN="t1.mirror.MANUAL_DOWN";
    public static final String ACTION_UP="t1.mirror.MANUAL_UP";
    public static final String ACTION_RECOVER="t1.mirror.RECOVER_TEN_UP"; // now exact logged pending pulses, NOT ten blind pulses
    public static final String ACTION_STOP="t1.mirror.STOP";
    /** Explicit driver confirmation AFTER physically resetting mirrors with OEM switch.
     * Never an angle sensor; is a visual acknowledgement and never actuates the mirrors. */
    public static final String ACTION_CONFIRM_NORMAL="t1.mirror.CONFIRM_NORMAL";
    static final String ACTION_BOOT_RECOVER="t1.mirror.BOOT_RECOVER_LOGGED";
    static final String BOOT_RECOVERY="boot_recovery_in_progress";
    private static final String CHANNEL="t1_mirror_parking";
    private static final int NOTIF_ID=7526;
    // One quick wheel-notch-like OEM press per step; ALWAYS release 2068/2069
    // to zero before the next press. Old 500ms ON + 180ms gap was ~27s for
    // 20 pulses on each side and acted more like a long hold than quick ticks.
    // These are experimental conservative values pending physical validation:
    // 140ms asserted, 20ms neutral after explicit OEM release, never parallel writes to both mirrors.
    private static final long POLL_MS=250L, PULSE_MS=140L, PAUSE_MS=20L;
    private volatile boolean running,stop,referenceConfirming;
    private volatile int pendingManual; // 1=down, 2=up, 3=manual 10-up recovery
    private Thread worker;

    @Override public IBinder onBind(Intent ignored){return null;}
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    /**
     * Driver preference: reverse tilt should be requested every time the
     * DriveLab process starts, including a new ignition.  Never erase a
     * physically unresolved pulse ledger or execute a command at this point.
     * The OEM worker waits for fresh gear/speed and a transition INTO R.
     */
    private static boolean unresolved(SharedPreferences p){
        return p.getBoolean(PULSE_IN_FLIGHT,false)||
            p.getInt(PENDING_LEFT,0)!=0||p.getInt(PENDING_RIGHT,0)!=0;
    }
    static void activateOnAppOpen(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long age=System.currentTimeMillis()-p.getLong(LAST_POLL,0L);
        if(p.getBoolean(SERVICE_ACTIVE,false)&&age>=0&&age<8000L)
            return; // live motion must not be interrupted by another activity
        if(unresolved(p)){
            int left=p.getInt(PENDING_LEFT,0),right=p.getInt(PENDING_RIGHT,0);
            if(MirrorBootRecoveryPolicy.recoverable(left,right,
                    p.getBoolean(PULSE_IN_FLIGHT,false))){
                boolean saved=p.edit().putBoolean(AUTO,false)
                    .putBoolean(SAFETY_HOLD,true).putBoolean(BOOT_RECOVERY,true)
                    .putString(POSITION,"INCIERTA · recuperando pulsos al arrancar")
                    .putString(STATUS,"ESPEJOS NARANJA · retorno en P pendiente "+
                        "IZQ "+left+" DER "+right+" · esperando P estable")
                    .commit();
                if(saved)try{
                    context.startForegroundService(new Intent(context,
                        MirrorAutomationService.class)
                        .setAction(ACTION_BOOT_RECOVER));
                }catch(RuntimeException ex){
                    p.edit().putBoolean(BOOT_RECOVERY,false)
                        .putString(STATUS,
                            "Retorno al arrancar NO disponible · "+
                            ex.getClass().getSimpleName()+
                            "; usar RECUPERAR ESPEJOS").commit();
                }
                return;
            }
            p.edit().putBoolean(AUTO,false).putBoolean(SAFETY_HOLD,true)
                .putBoolean(BOOT_RECOVERY,false)
                .putString(STATUS,"ESPEJOS NARANJA · pulso incierto o "+
                    "registro inválido; revisar espejos con mandos originales")
                .commit();
            return;
        }
        if(p.getBoolean(SAFETY_HOLD,false)&&
            p.getString(POSITION,"").startsWith("INCIERTA")){
            p.edit().putBoolean(AUTO,false)
                .putString(STATUS,"ESPEJOS NARANJA · contador perdido, "+
                    "revisión visual obligatoria").commit();
            return;
        }
        // Only a truly clear, safe ledger can re-arm the next R.
        p.edit().putBoolean(SAFETY_HOLD,false)
            .putBoolean(AUTO,true)
            .putBoolean(SERVICE_ACTIVE,false)
            .putString(POSITION,"AUTO · referencia visual del conductor")
            .putString(STATUS,"AUTO solicitado al abrir · esperando datos OEM")
            .apply();
        try{
            context.startForegroundService(new Intent(context,
                MirrorAutomationService.class).setAction(ACTION_AUTO));
        }catch(RuntimeException ex){
            p.edit().putBoolean(AUTO,false)
                .putString(STATUS,"AUTO no iniciado: "+
                    ex.getClass().getSimpleName()).apply();
        }
    }
    /** Restart a stale worker only with an empty motion ledger. */
    static void ensureRunning(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.getBoolean(AUTO,false)||unresolved(p))return;
        long age=System.currentTimeMillis()-p.getLong(LAST_POLL,0L);
        if(p.getBoolean(SERVICE_ACTIVE,false)&&age>=0&&age<3000L)return;
        activateOnAppOpen(context);
    }
    private int steps(String key){
        return Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,prefs().getInt(key,2)));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // A restarted process cannot know the actual manual D pose.
        // Require conscious re-arming before any new automatic movement.
        if(intent==null){
            prefs().edit().putBoolean(SERVICE_ACTIVE,false).apply();
            stopSelf();return START_NOT_STICKY;
        }
        String action=intent.getAction();
        if(ACTION_CONFIRM_NORMAL.equals(action)){
            // The running reverse monitor previously REJECTED confirmation,
            // leaving an orange INCIERTA even with 0 pending pulses.
            // Disarm the monitor FIRST, wait for its in-flight pulse to
            // release, then read OEM P/0 and save the owner's visual ACK.
            // Never claim NORMAL from the pending ledger alone.
            if(referenceConfirming){
                prefs().edit().putString(STATUS,
                    "CONFIRMANDO REFERENCIA: lectura P/0 en curso").apply();
                return START_NOT_STICKY;
            }
            referenceConfirming=true;
            Thread prior=running?worker:null;
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"Confirmando posición visual sin mover espejos. "+
                    "Esperando que se detenga la reversa automática.")
                .apply();
            stop=true;
            if(prior!=null)prior.interrupt();
            foreground();
            Thread acknowledge=new Thread(()->{
                MirrorAutomationOem check=null;
                try{
                    if(prior!=null){
                        prior.join(4500L);
                        if(prior.isAlive())
                            throw new IllegalStateException(
                                "Servicio anterior sigue moviendo espejos; "+
                                "no se puede confirmar");
                    }
                    // No physical movement on reference acknowledgement.
                    // It is valid in P or D only after the driver VISUALLY
                    // verifies the mirrors. Never erase a pending pulse ledger.
                    if(prefs().getBoolean(PULSE_IN_FLIGHT,false)||
                       prefs().getInt(PENDING_LEFT,0)!=0||
                       prefs().getInt(PENDING_RIGHT,0)!=0)
                        throw new IllegalStateException(
                            "Hay pulsos sin compensar o inciertos.");
                    // This confirms the user's manual VISUAL reference only.
                    // After stopping the old monitor, no automatic pulses are
                    // emitted and no movement of either mirror is requested.
                    boolean saved=prefs().edit()
                        .putBoolean(AUTO,false)
                        .putBoolean(SAFETY_HOLD,false)
                        .putBoolean(SERVICE_ACTIVE,false)
                        .putInt(PENDING_LEFT,0).putInt(PENDING_RIGHT,0)
                        .putBoolean(PULSE_IN_FLIGHT,false)
                        .putString(POSITION,
                            "NORMAL · referencia visual confirmada por conductor")
                        .putString(STATUS,
                            "POSICIÓN VISUAL CONFIRMADA · REVERSA MANUAL. "+
                            "Sin pulsos pendientes. Tocá REVERSA para AUTO.")
                        .remove(LAST_ERROR).commit();
                    if(!saved)
                        throw new IllegalStateException(
                            "Android no pudo guardar referencia");
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                    prefs().edit().putString(STATUS,
                        "NO SE CONFIRMÓ: interrupción al detener el monitor")
                        .apply();
                }catch(Exception ex){
                    String reason=ex.getMessage()==null?
                        ex.getClass().getSimpleName():ex.getMessage();
                    prefs().edit().putBoolean(AUTO,false)
                        .putString(STATUS,"NO SE CONFIRMÓ: "+reason+
                            ". Espejos permanecen sin referencia validada.")
                        .apply();
                }finally{
                    if(check!=null)check.close();
                    referenceConfirming=false;
                    running=false;
                    prefs().edit().putBoolean(SERVICE_ACTIVE,false).apply();
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf(startId);
                }
            },"T1-Mirror-Reference-Acknowledge");
            worker=acknowledge;
            acknowledge.start();
            return START_NOT_STICKY;
        }
        if(referenceConfirming){
            prefs().edit().putString(STATUS,
                "Confirmación de espejos en curso; no se inicia otra orden")
                .apply();
            return START_NOT_STICKY;
        }
        if(ACTION_STOP.equals(action)||ACTION_MANUAL.equals(action)){
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"MANUAL · automatización desactivada").apply();
            stop=true;if(worker!=null)worker.interrupt();
            if(!running){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
            return START_NOT_STICKY;
        }
        if(ACTION_BOOT_RECOVER.equals(action)){
            if(running)return START_STICKY;
            int l=prefs().getInt(PENDING_LEFT,0);
            int r=prefs().getInt(PENDING_RIGHT,0);
            if(!MirrorBootRecoveryPolicy.recoverable(l,r,
                    prefs().getBoolean(PULSE_IN_FLIGHT,false))){
                prefs().edit().putBoolean(AUTO,false)
                    .putBoolean(SAFETY_HOLD,true)
                    .putBoolean(BOOT_RECOVERY,false)
                    .putString(STATUS,"ESPEJOS NARANJA · recuperación "+
                        "al arrancar bloqueada, contador incierto").commit();
                stopSelf();return START_NOT_STICKY;
            }
            pendingManual=4; // strictly P, zero speed, original logged pulses
            prefs().edit().putBoolean(AUTO,false)
                .putBoolean(SAFETY_HOLD,true)
                .putBoolean(BOOT_RECOVERY,true)
                .putString(STATUS,"ESPEJOS NARANJA · esperando P estable "+
                    "para subir "+l+" IZQ / "+r+" DER").commit();
        }else if(ACTION_AUTO.equals(action)){
            // Some Activity/watchdog paths resend ACTION_AUTO while a 20-pulse
            // tilt or its P+30s return is ALREADY RUNNING. Do NOT mistake the
            // correctly outstanding pulse ledger for an unsafe re-arm and
            // never stop the live service: that previously stranded mirrors.
            if(running)return START_STICKY;
            // A physical "normal" position has no readback sensor. Do not
            // rearm after software update/reboot/partial return without the
            // driver's explicit reference confirmation in P at 0 km/h.
            if(unresolved(prefs())){
                prefs().edit().putBoolean(AUTO,false)
                    .putString(STATUS,"AUTO BLOQUEADA: primero revisá la "+
                        "posición de ambos espejos con los mandos originales "+
                        "en P y confirmá la referencia.")
                    .apply();
                stopSelf();return START_NOT_STICKY;
            }
            // First activation after a stopped/failed service is permitted
            // only with a known, visually acknowledged normal reference.
            String physical=prefs().getString(POSITION,"");
            if((physical.startsWith("INCIERTA")||physical.startsWith("R · inclinados"))&&
               unresolved(prefs())){
                prefs().edit().putBoolean(AUTO,false)
                    .putString(STATUS,"AUTO BLOQUEADO: la app no sabe si los espejos "+
                        "siguen bajos. Ajustá con mandos OEM, detenido en P, "+
                        "y confirmá su posición en la barra.").apply();
                stopSelf();return START_NOT_STICKY;
            }
            prefs().edit().putBoolean(AUTO,true)
                .putBoolean(SAFETY_HOLD,false)
                .putString(POSITION,"AUTO armado · referencia de ángulo NO medida")
                .putString(STATUS,"AUTO armado · esperando transición nueva a R").apply();
        }else if(ACTION_RECOVER.equals(action)){
            // One explicit control for P or D; never blindly send ten UP.
            // Recover only confirmed, logged, uncompensated commands.
            prefs().edit().putBoolean(AUTO,false)
                .putString(STATUS,"RECUPERAR ESPEJOS · subiendo solo los pulsos registrados en P o D").apply();
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
                        // Relative ANGLE adjustment in D is manually requested
                        // by the driver; folding has its own <=5 km/h guard.
                        if(manual==4){
                            // After restart: require 2.5 s of real OEM P/0.
                            // A parked, stable boot must be verified anew;
                            // no motor command in R or D during startup.
                            for(int stable=0;stable<10&&!stop;stable++){
                                if(!MirrorBootRecoveryPolicy.parkedForRestore(
                                        oem.gear(),oem.speedKmh()))
                                    throw new IllegalStateException(
                                        "P estable no confirmado al encender");
                                SystemClock.sleep(250L);
                            }
                            if(stop)throw new IllegalStateException(
                                "Recuperación de arranque interrumpida");
                        }else if(rawGear!=1&&rawGear!=4)
                            throw new IllegalStateException(
                                "Ajuste manual de orientación: solo P o D");
                        int l=(manual==3||manual==4)?
                            prefs().getInt(PENDING_LEFT,0):steps(LEFT);
                        int r=(manual==3||manual==4)?
                            prefs().getInt(PENDING_RIGHT,0):steps(RIGHT);
                        if((manual==3||manual==4) && (l+r==0)){
                            prefs().edit().putString(STATUS,
                                "SIN PULSOS REGISTRADOS: versión anterior perdió "+
                                "la cuenta. Ajustá con mandos originales en P.")
                                .putBoolean(SAFETY_HOLD,true).apply();
                            break;
                        }
                        if((manual==3||manual==4) &&
                           prefs().getBoolean(PULSE_IN_FLIGHT,false))
                            throw new IllegalStateException(
                                "Pulso previo incierto; usar mando original");
                        movementStarted=true;
                        int[] counts=runPulses(oem,manual==1,l,r,
                            manual==4,manual==3||manual==4,manual==4);
                        if(counts[0]!=l||counts[1]!=r)
                            throw new IllegalStateException("Movimiento manual incompleto");
                        if(manual==4){
                            if(!prefs().edit().putBoolean(BOOT_RECOVERY,false)
                                .putBoolean(SAFETY_HOLD,false)
                                .putBoolean(AUTO,true)
                                .putString(POSITION,
                                    "P · pulsos guardados compensados; "+
                                    "ángulo físico sin sensor")
                                .putString(STATUS,"ESPEJOS · recuperados "+
                                    counts[0]+" IZQ / "+counts[1]+" DER. "+
                                    "AUTO listo para próxima R; revisar "+
                                    "posición visual antes de circular")
                                .commit())throw new IllegalStateException(
                                    "No se guardó retorno al encender");
                        }else if(manual==3){
                            prefs().edit().putString(POSITION,
                                    "P · retorno manual de pulsos registrado")
                                .putString(STATUS,"RECUPERADOS izq="+counts[0]+
                                    " der="+counts[1]+
                                    " pulsos; ajustar si el ángulo no coincide.")
                                .putBoolean(AUTO,false).apply();
                        }else{
                            prefs().edit().putString(STATUS,
                                    "MANUAL "+(manual==1?"BAJAR":"SUBIR")+
                                    " · izq="+l+" der="+r+" órdenes enviadas; "+
                                    "posición física no confirmada")
                                .putString(POSITION,"INCIERTA · ajuste manual sin "+
                                    "comprobación de ángulo")
                                .putBoolean(SAFETY_HOLD,true).apply();
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
                                    "· vuelve SOLO en P detenido >30s o D >5km/h",
                                    gear,speed,speedRead.source,
                                    speedRead.raw2148,speedRead.raw1089)).apply();
                        }
                        if(action==MirrorParkingPolicy.Action.TILT_ON_R){
                            // Mark uncertain BEFORE first actuator command.
                            // Process death mid-movement must never look normal.
                            prefs().edit().putString(POSITION,
                                "INCIERTA · bajada en curso")
                                .putString(STATUS,
                                "AUTO · bajando espejos al entrar en R")
                                .putString(LAST_TRANSITION,
                                    "R detectada · iniciada bajada: "+
                                    System.currentTimeMillis()).apply();
                            // Crash-safe pulse ledger starts at ZERO, and
                            // each neutralized 2068/2069 command is committed.
                            if(!prefs().edit().putInt(PENDING_LEFT,0)
                                .putInt(PENDING_RIGHT,0)
                                .putBoolean(PULSE_IN_FLIGHT,false).commit())
                                throw new IllegalStateException("No se pudo iniciar contador");
                            movementStarted=true;
                            int[] n=runPulses(oem,true,policy.requestedLeft(),
                                policy.requestedRight(),true,true,false);
                            policy.completeTilt(n[0],n[1]);
                            prefs().edit().putString(POSITION,
                                policy.phase()==MirrorParkingPolicy.Phase.TILTED?
                                "R · inclinados (pulsos registrados)":
                                "P · bajada cancelada antes del primer pulso")
                                .putString(STATUS,"AUTO bajada registrada: izq="+
                                    n[0]+" der="+n[1]+
                                    ". Si está en P, vuelve después de 30 segundos.")
                                .apply();
                            movementStarted=false;
                        }else if(action==MirrorParkingPolicy.Action.RESTORE_AFTER_NON_R_SPEED){
                            // Policy requires >30s in P while stopped OR D >5km/h.
                            String startGear=gear==MirrorParkingPolicy.Gear.P?"P":"D";
                            prefs().edit().putString(POSITION,
                                "INCIERTA · retorno en curso")
                                .putString(STATUS,
                                "AUTO · recuperando espejos en "+startGear)
                                .putString(LAST_TRANSITION,
                                    startGear+" estable · retorno iniciado: "+
                                    System.currentTimeMillis()).apply();
                            movementStarted=true;
                            int[] n=runPulses(oem,false,policy.leftIssued(),
                                policy.rightIssued(),true,true,false);
                            policy.completeRestore(n[0],n[1]);
                            prefs().edit().putString(POSITION,
                                policy.phase()==MirrorParkingPolicy.Phase.NORMAL?
                                startGear+" · retorno de pulsos completado":
                                "R · inclinados (retorno pendiente)")
                                .putString(STATUS,policy.phase()==MirrorParkingPolicy.Phase.NORMAL?
                                    "RETORNO COMPLETADO · izq="+n[0]+
                                        " der="+n[1]+
                                        " (movimiento relativo, no ángulo medido)":
                                    "RETORNO PARCIAL · faltan izq="+
                                        policy.leftIssued()+" der="+policy.rightIssued()+
                                        ". Nuevo P detenido 30 s para continuar.")
                                .apply();
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
                    if(manual==4){
                        prefs().edit().putBoolean(AUTO,false)
                            .putBoolean(BOOT_RECOVERY,false)
                            .putBoolean(SAFETY_HOLD,true)
                            .putString(POSITION,"INCIERTA · retorno al arrancar "+
                                "pendiente de inspección")
                            .putString(STATUS,"ESPEJOS NARANJA · "+
                                "retorno al arrancar incompleto: "+reason+
                                " · corregir en P antes de circular")
                            .commit();
                        break; // never retry an interrupted OEM movement
                    }
                    if(prefs().getBoolean(AUTO,false)){
                        // Never automatically retry a possibly partial movement.
                        prefs().edit().putBoolean(AUTO,false)
                            .putBoolean(SAFETY_HOLD,true)
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
            // A reference confirmation may have asked us to terminate.
            // It owns the foreground service until it has checked OEM P/0
            // and stored the new NORMAL state; don't destroy it here.
            if(!referenceConfirming){
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }
    /** Exactly one logged counter change AFTER releasing each pulse.
     * If P interrupts a long R downward sequence, return PARTIAL COUNTS,
     * not an exception. A shift out of P mid-restore likewise preserves
     * the remaining counts for another 30-second stationary P dwell.
     * An in-flight Binder failure stays uncertain and is NEVER retried.
     */
    private int[] runPulses(MirrorAutomationOem oem,boolean down,
                    int left,int right,boolean automatic,
                    boolean trackLedger,boolean bootParkedOnly)throws Exception{
        synchronized(OEM_MIRROR_MUTEX){
            int[] counts={0,0};
            int max=Math.max(left,right);
            for(int step=0;step<max;step++){
                if(stop)return counts;
                for(int side=0;side<2;side++){
                    if(step >= (side==0?left:right))continue;
                    if(stop)return counts;
                    int gear=oem.gear();
                    double speed=oem.speedKmh();
                    if(mapGear(gear)==MirrorParkingPolicy.Gear.UNKNOWN||
                       !Double.isFinite(speed)||speed<0||speed>200)
                        throw new IllegalStateException("Marcha o velocidad OEM inválida");
                    if(bootParkedOnly &&
                        !MirrorBootRecoveryPolicy.parkedForRestore(gear,speed))
                        return counts; // leave remaining pulses journaled
                    if(automatic){
                        // Controlled gear change is not a failed OEM movement.
                        if(down && (gear!=2||speed>8))return counts;
                        // Once triggered by P or D, continue in safe P or D,
                        // even if D speed drops under the trigger threshold.
                        if(!down && !(gear==4||(gear==1&&speed<=0.5)))
                            return counts;
                    }else if(gear!=1&&gear!=4){
                        return counts;
                    }
                    // Recover only exactly logged pulses in P or D.
                    if(trackLedger && !automatic && gear!=1&&gear!=4)
                        return counts;
                    int id=side==0?2068:2069;
                    if(trackLedger &&
                       !prefs().edit().putBoolean(PULSE_IN_FLIGHT,true).commit())
                        throw new IllegalStateException("Sin almacenamiento de pulso");
                    try{
                        oem.write(id,0);
                        oem.write(id,down?2:1);
                        long end=SystemClock.elapsedRealtime()+PULSE_MS;
                        while(!stop&&SystemClock.elapsedRealtime()<end)
                            SystemClock.sleep(15);
                    }finally{
                        // Always release; a failed release leaves IN_FLIGHT.
                        oem.write(id,0);
                    }
                    counts[side]++;
                    if(trackLedger){
                        String key=side==0?PENDING_LEFT:PENDING_RIGHT;
                        int pending=prefs().getInt(key,0)+(down?1:-1);
                        if(pending<0||pending>MirrorParkingPolicy.MAX_PULSES_PER_SIDE)
                            throw new IllegalStateException("Contador de espejo inválido");
                        if(!prefs().edit().putInt(key,pending)
                            .putBoolean(PULSE_IN_FLIGHT,false).commit())
                            throw new IllegalStateException("No se guardó pulso terminado");
                    }
                    SystemClock.sleep(PAUSE_MS);
                }
            }
            return counts;
        }
    }
    @Override public void onDestroy(){
        stop=true;if(worker!=null)worker.interrupt();
        if(prefs().getBoolean(AUTO,false)){
            boolean hasUnresolved=unresolved(prefs());
            SharedPreferences.Editor edit=prefs().edit()
                .putBoolean(AUTO,false)
                .putBoolean(SERVICE_ACTIVE,false)
                .putBoolean(SAFETY_HOLD,hasUnresolved);
            if(hasUnresolved)
                edit.putString(POSITION,
                    "INCIERTA · movimiento interrumpido")
                    .putString(STATUS,
                        "Movimiento de espejo pendiente; corrección manual disponible");
            else edit.putString(POSITION,"AUTO · sin pulsos pendientes")
                .putString(STATUS,
                    "AUTO detenido; se solicitará de nuevo al iniciar DriveLab");
            edit.apply();
        }
        super.onDestroy();
    }
}
