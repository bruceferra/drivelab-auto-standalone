package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * User-confirmed, ONE-SHOT OEM EV/HEV/EV MAX selector (1043=1/2/3).
 * In P the original Owner tests confirmed all three commands and readbacks.
 * The user independently confirmed 1033=4 means D and OEM selector changes
 * modes while in D. D write from DriveLab remains an EXPERIMENT and the OEM
 * VehicleService may reject it; never claim D acceptance until observed.
 *
 * Two fresh direct OEM preflight readings are mandatory: stable 1033=1 and
 * exactly 0 km/h, OR stable 1033=4 with finite plausible speed 0..160 km/h.
 * No pedal requirement: 3011 brake status is diagnostic only (0/1).
 * May be invoked by the user's explicitly ON optimized-mode foreground
 * service as well as by the manual cockpit button; both use identical strict
 * double preflight, a shared single-command gate and verified OEM readback.
 * Never act on stale/unknown readings. The OEM remains final authority.
 */
final class T1ManualThreeModePilot {
    interface Callback { void complete(String result); }
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    private static final AtomicBoolean GLOBAL_BUSY=new AtomicBoolean(false);
    T1ManualThreeModePilot(Context c){app=c.getApplicationContext();}
    boolean busy(){return GLOBAL_BUSY.get();}
    boolean request(int target,BooleanSupplier screenVisible,Callback cb){
        if(target!=1&&target!=2&&target!=3)return false;
        // Another 1040/1041/1046 profile transaction could undo SMART
        // between disarming FORZADO and applying EV. Do not race it.
        if(target==1&&T1SocRegenPilot.oemSocWriteInProgress())return false;
        if(!GLOBAL_BUSY.compareAndSet(false,true))return false;
        T1OemDiagnosticLog.begin(app,
            "Control manual triple · objetivo 1043="+target+
            ". Preflight OEM doble: P/0 o D/velocidad válida; "+
            "control único compartido manual/optimizado.");
        new Thread(()->{
            String result;
            try{result=attempt(target,screenVisible);}
            catch(Exception e){
                String detail=e.getClass().getSimpleName()+
                    (e.getMessage()==null?"":" · "+e.getMessage());
                T1OemDiagnosticLog.add(app,"ERROR_TRIPLE",detail);
                result="NO CONFIRMADO · "+detail+
                    ". No reiterar a ciegas. Consultar modo OEM.";
            }
            T1OemDiagnosticLog.add(app,"FINAL_TRIPLE",result);
            GLOBAL_BUSY.set(false);
            cb.complete(result);
        },"T1-Manual-Three-Modes").start();
        return true;
    }
    private String attempt(int target,BooleanSupplier visible)throws Exception{
        if(!visible.getAsBoolean())
            throw new IllegalStateException("Cockpit fuera de primer plano");
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> link=new AtomicReference<>();
        ServiceConnection c=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                link.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){link.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){link.set(null);}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),c,0);
        T1OemDiagnosticLog.add(app,"BIND_TRIPLE","bound="+bound);
        if(!bound)throw new IllegalStateException("VehicleService no responde");
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Sin Binder OEM");
            IBinder b=link.get();
            if(b==null||!b.isBinderAlive()||
                    !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no reconocida");
            int start=readInt(b,1043);
            int startGear=checkDrivePosition(b,start,target,"PRIMERA_LECTURA");
            SystemClock.sleep(420);
            if(!visible.getAsBoolean()||!b.isBinderAlive())
                throw new IllegalStateException("Cockpit perdió el foco");
            int still=readInt(b,1043);
            if(still!=start)
                throw new IllegalStateException(
                    "Modo cambió durante comprobación: "+start+" a "+still);
            int confirmGear=checkDrivePosition(b,still,target,"SEGUNDA_LECTURA");
            if(startGear!=confirmGear)
                throw new IllegalStateException(
                    "Marcha cambió durante preflight: "+startGear+" -> "+confirmGear);
            if(!visible.getAsBoolean())throw new SecurityException("Sin foco");
            // Only EV (1043=1) needs to release a previous HEV FORZADO.
            // EV MAX (1043=3) does this by itself on the real T1 and must
            // never receive this additional 1040 write.
            ensureSmartBeforeEv(b,target,visible);
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(TOKEN);
                data.writeInt(1043);data.writeInt(target);
                data.writeString(app.getPackageName());
                T1OemDiagnosticLog.add(app,"ESCRITURA_TRIPLE",
                    "Binder transact(15): 1043="+target+", una sola vez");
                if(!b.transact(15,data,reply,0))
                    throw new IllegalStateException("Setter 1043 no implementado");
                reply.readException();
            }finally{data.recycle();reply.recycle();}
            long limit=SystemClock.elapsedRealtime()+3500L;
            while(SystemClock.elapsedRealtime()<limit&&
                    visible.getAsBoolean()&&b.isBinderAlive()){
                SystemClock.sleep(320);
                int actual=readInt(b,1043);
                T1OemDiagnosticLog.add(app,"READBACK_1043",
                    "esperado="+target+", actual="+actual);
                if(actual==target)
                    return "LECTURA CONFIRMADA: 1043="+target+
                        " ("+T1ManualHevPolicy.currentMode(target)+
                        "). Confirmá visualmente en la pantalla original.";
            }
            return "LA ORDEN SE ENVIÓ PERO 1043 NO CONFIRMÓ "+
                T1ManualHevPolicy.currentMode(target)+
                ". No reenviar sin revisar el log.";
        }finally{try{app.unbindService(c);}catch(Exception ignored){}}
    }
    /**
     * Before requesting EV from HEV FORZADO, the T1 requires SOC strategy
     * SMART=2. Same Binder link, single 1040 setter, verified readback and
     * another fresh drive-safety check before 1043=1. No SOC target 1041
     * or regeneration 1046 write is needed. EV MAX skips this entirely.
     */
    private void ensureSmartBeforeEv(IBinder b,int powerTarget,
                                     BooleanSupplier enabled)throws Exception{
        if(powerTarget!=1)return;
        int before=readInt(b,1040);
        T1OemDiagnosticLog.add(app,"EV_PRE_SMART_LECTURA",
            "1043 objetivo EV; 1040 estrategia actual="+before+
            (before==4?" (FORZADO)":""));
        if(!T1SocRegenWritePolicy.accepted(1040,before))
            throw new IllegalStateException(
                "No se puede comprobar SMART/FORZADO (1040="+before+
                "); EV no solicitado");
        if(!T1SocRegenWritePolicy.mustDisarmForcedBeforeEv(powerTarget,before)){
            T1OemDiagnosticLog.add(app,"EV_SMART_YA_ACTIVO",
                "1040=2 SMART; se solicita EV directamente, sin escritura 1040");
            return;
        }
        if(!enabled.getAsBoolean()||!b.isBinderAlive())
            throw new IllegalStateException("EV suspendido antes de pasar a SMART");
        SystemClock.sleep(180);
        int again=readInt(b,1040);
        if(again!=before)
            throw new IllegalStateException(
                "Gestión SOC cambió durante preflight "+before+
                "->"+again+"; no cambiar EV");
        int currentPower=readInt(b,1043);
        checkDrivePosition(b,currentPower,1,"EV_PRE_SMART_SEGURIDAD");
        if(!enabled.getAsBoolean())
            throw new IllegalStateException("EV cancelado antes de SMART");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(1040);data.writeInt(2); // SMART
            data.writeString(app.getPackageName());
            T1OemDiagnosticLog.add(app,"EV_SMART_WRITE_REQ",
                "Un solo Binder transact(15) 1040=2, antes de 1043=1");
            if(!b.transact(15,data,reply,0))
                throw new IllegalStateException(
                    "OEM no acepta cambio 1040=2; EV no solicitado");
            reply.readException();
            T1OemDiagnosticLog.add(app,"EV_SMART_BINDER_OK",
                "1040=2 recibido por Binder; pendiente confirmación OEM");
        }finally{data.recycle();reply.recycle();}
        long until=SystemClock.elapsedRealtime()+4000L;
        while(SystemClock.elapsedRealtime()<until&&enabled.getAsBoolean()&&
              b.isBinderAlive()){
            SystemClock.sleep(260);
            int actual=readInt(b,1040);
            T1OemDiagnosticLog.add(app,"EV_SMART_READBACK",
                "1040 esperado=2, obtenido="+actual);
            if(actual==2){
                // The second 1043 request is conditional on an *updated*
                // validated gear/speed reading, not the stale first preflight.
                int current=readInt(b,1043);
                checkDrivePosition(b,current,1,"EV_POST_SMART_SEGURIDAD");
                T1OemDiagnosticLog.add(app,"EV_SMART_CONFIRMADO",
                    "1040=2 OEM confirmado; habilitado 1043=1 EV");
                return;
            }
        }
        throw new IllegalStateException(
            "1040 SMART no confirmado; NO se envió EV (1043=1). "+
            "Revisar menú Jetour; sin reintento ni rollback automático");
    }

    private int checkDrivePosition(IBinder b,int mode,int target,String stage)
            throws Exception{
        int gear=readInt(b,1033);
        double speed;
        // Do NOT fall back to 1089: it has not been validated as an equal
        // vehicle-speed signal in D. A missing speed reading must fail closed.
        speed=readFloat(b,2148);
        String footBrake="sin lectura";
        try{footBrake=Integer.toString(readInt(b,3011));}
        catch(Exception ignored){}
        T1OemDiagnosticLog.add(app,stage,
            "1033="+gear+" ("+T1DriveModeGate.name(gear)+
            "); 2148="+speed+"; 3011_freno_lectura="+footBrake+
            " (diagnóstico, NO requisito); 1043="+mode+
            "; objetivo="+target);
        if(!T1DriveModeGate.allowed(gear,speed))
            throw new IllegalStateException(
                "Bloqueado: solo P/0 km/h o D con velocidad OEM válida "+
                "0-160 km/h. Lecturas 1033="+gear+", 2148="+speed);
        if(mode!=1&&mode!=2&&mode!=3)
            throw new IllegalStateException(
                "Modo actual 1043="+mode+" no validado");
        if(mode==target)throw new IllegalStateException(
            "Ya está en "+T1ManualHevPolicy.currentMode(target)+
            "; ninguna escritura necesaria");
        return gear;
    }
    private static int readInt(IBinder b,int id)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!b.transact(5,data,reply,0))
                throw new IllegalStateException("Getter "+id+" no disponible");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    private static float readFloat(IBinder b,int id)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!b.transact(6,data,reply,0))
                throw new IllegalStateException("Getter float "+id+" no disponible");
            reply.readException();return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
}
