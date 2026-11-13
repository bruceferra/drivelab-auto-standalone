package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * One-shot, guarded automatic OEM write for strategy 1040, target 1041 and
 * regeneration 1046. SAME VehicleService transact(15) contract as confirmed
 * 1043, but 1040/1041/1046 write acceptance must be confirmed on the T1.
 *
 * No separate unlock wizard: the owner explicitly enables MODO OPTIMIZADO
 * once. The first automatic write is an experiment with direct OEM readback.
 * Every apply uses two fresh gear/speed reads, individually checked values,
 * one attempt per id, readback, and best-effort rollback of confirmed
 * strategy/regen changes only. 1041 is NEVER rolled back on stale raw
 * readback: the actual OEM UI must be checked before any next attempt.
 * D with valid 2148 speed is eligible, as is P/0; R/N/unknown are denied.
 */
final class T1SocRegenPilot {
    interface Callback { void complete(String message); }
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());

    T1SocRegenPilot(Context context){app=context.getApplicationContext();}
    boolean busy(){return BUSY.get();}

    boolean apply(T1SocRegenWritePolicy.Targets desired,
                  BooleanSupplier stillEnabled,Callback callback){
        if(desired==null||!desired.valid()||
            stillEnabled==null||!stillEnabled.getAsBoolean())return false;
        if(!BUSY.compareAndSet(false,true))return false;
        new Thread(()->{
            String result;
            try{
                result=tryApply(desired,stillEnabled);
            }catch(Exception ex){
                result="NO CONFIRMADO: "+describe(ex)+
                    " · AUTO se desactiva, comprobar ajustes Jetour.";
                T1OemDiagnosticLog.add(app,"SOC_REGEN_AUTO_ERROR",result);
            }finally{BUSY.set(false);}
            final String answer=result;
            main.post(()->callback.complete(answer));
        },"T1-OEM-SOC-REGEN-Auto-Readback").start();
        return true;
    }

    private interface Work { String run(IBinder b)throws Exception; }
    private String withBinder(Work work)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder binder){
                remote.set(binder);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                remote.set(null);
            }
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){remote.set(null);}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),connection,0);
        if(!bound)throw new IllegalStateException("Sin enlace VehicleService");
        try{
            if(!ready.await(2800,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Sin respuesta Binder OEM");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
                !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz IVehicleControl no válida");
            return work.run(b);
        }finally{
            try{app.unbindService(connection);}catch(Exception ignored){}
        }
    }

    private static int readInt(IBinder b,int id)throws Exception{
        if(id!=1040&&id!=1041&&id!=1046&&id!=1033)
            throw new SecurityException("Getter OEM no autorizado: "+id);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            if(!b.transact(5,data,reply,0))
                throw new IllegalStateException("Getter OEM "+id+" rechazado");
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    /** Independent candidate getter for target 1041: the int getter can
     * return an opaque/raw 56 while the OEM UI shows a target of 20%.
     * This is only confirmation if the float getter itself equals a
     * supported requested value. Never reinterpret raw 56 as a SOC preset.
     */
    private static double readSocTargetFloat(IBinder b)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(1041);
            if(!b.transact(6,data,reply,0))
                throw new IllegalStateException("Float OEM 1041 rechazado");
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
    private static float readSpeed(IBinder b)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(2148);
            if(!b.transact(6,data,reply,0))
                throw new IllegalStateException("Velocidad OEM 2148 no legible");
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }

    private int preflight(IBinder b,String stage)throws Exception{
        int gear=readInt(b,1033);
        float speed=readSpeed(b);
        T1OemDiagnosticLog.add(app,stage,
            "1033="+gear+" 2148="+speed);
        if(!T1SocRegenWritePolicy.regenDriveEligible(gear,speed))
            throw new IllegalStateException(
                "Marcha/velocidad no seguras (solo P/0 o D/0..160): "+
                gear+" / "+speed);
        return gear;
    }
    private void doublePreflight(IBinder b)throws Exception{
        int first=preflight(b,"SOC_REGEN_PREFLIGHT_1");
        SystemClock.sleep(400);
        int second=preflight(b,"SOC_REGEN_PREFLIGHT_2");
        if(first!=second)
            throw new IllegalStateException("Marcha cambió durante preflight");
    }
    private void set(IBinder b,int id,int target,
                     BooleanSupplier enabled)throws Exception{
        if(!T1SocRegenWritePolicy.accepted(id,target))
            throw new SecurityException("Valor no permitido "+id+"="+target);
        if(enabled!=null&&!enabled.getAsBoolean())
            throw new IllegalStateException("AUTO apagado antes de escribir");
        preflight(b,"SOC_REGEN_ANTES_"+id);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            data.writeInt(target);
            data.writeString(app.getPackageName());
            T1OemDiagnosticLog.add(app,"SOC_REGEN_ESCRITURA",
                "Binder transact(15) id="+id+" valor="+target);
            if(!b.transact(15,data,reply,0))
                throw new IllegalStateException("Setter "+id+" rechazado");
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }
    private void verify(IBinder b,int id,int target,
                        BooleanSupplier enabled)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+3000;
        while(SystemClock.elapsedRealtime()<deadline){
            if(enabled!=null&&!enabled.getAsBoolean())
                throw new IllegalStateException("AUTO apagado; lectura abortada");
            int actual=readInt(b,id);
            double asFloat=Double.NaN;
            if(id==1041){
                try{asFloat=readSocTargetFloat(b);}catch(Exception ignored){}
            }
            T1OemDiagnosticLog.add(app,"SOC_REGEN_READBACK",
                "id="+id+" esperado="+target+" intRaw="+actual+
                " floatRaw="+asFloat);
            if(actual==target||
                (id==1041&&Double.isFinite(asFloat)&&
                    Math.abs(asFloat-target)<0.05))return;
            SystemClock.sleep(300);
        }
        throw new IllegalStateException(
            "OEM no confirmó por getters id="+id+" objetivo="+target+
            ". La escritura puede HABERSE APLICADO; no repetir automáticamente.");
    }

    private String tryApply(T1SocRegenWritePolicy.Targets targets,
                            BooleanSupplier active)throws Exception{
        if(!active.getAsBoolean())
            throw new IllegalStateException("AUTO ya está apagado");
        T1OemDiagnosticLog.add(app,"SOC_REGEN_PERFIL",
            "SOLICITADO "+targets.summary()+"; comprobando OEM");
        return withBinder(b->{
            doublePreflight(b);
            if(!active.getAsBoolean())
                throw new IllegalStateException("AUTO ya está apagado");
            LinkedHashMap<Integer,Integer> wanted=new LinkedHashMap<>();
            // Set target BEFORE entering FORZADO so the correct SOC target
            // already exists when the ECU receives strategy=4.
            if(targets.needsTarget())wanted.put(1041,targets.forcedSoc);
            wanted.put(1040,targets.strategy);
            wanted.put(1046,targets.regen);
            LinkedHashMap<Integer,Integer> original=new LinkedHashMap<>();
            for(Map.Entry<Integer,Integer> item:wanted.entrySet()){
                int id=item.getKey(),now=readInt(b,id);
                if(id!=1041&&!T1SocRegenWritePolicy.accepted(id,now))
                    throw new IllegalStateException(
                        "Estado OEM "+id+"="+now+" no reconocido; sin escritura");
                // 1041 is a raw getter, NOT an authorized former preset.
                // Preserve it for diagnostics but never use it as rollback.
                if(id==1041&&!T1SocRegenWritePolicy.accepted(id,now))
                    T1OemDiagnosticLog.add(app,"SOC_REGEN_1041_RAW",
                        "getterInt="+now+" no representa preset validado; "+
                        "solo se escribirá objetivo de perfil permitido");
                original.put(id,now);
            }
            int writes=0;
            LinkedHashMap<Integer,Integer> changed=new LinkedHashMap<>();
            try{
                for(Map.Entry<Integer,Integer> item:wanted.entrySet()){
                    int id=item.getKey(),target=item.getValue();
                    if(original.get(id)==target)continue;
                    if(!active.getAsBoolean())
                        throw new IllegalStateException("AUTO apagado");
                    // Mark prior state BEFORE sending. OEM may apply a
                    // command even if Binder declines to report success.
                    changed.put(id,original.get(id));
                    set(b,id,target,active);
                    verify(b,id,target,active);
                    writes++;
                }
            }catch(Exception failure){
                StringBuilder restored=new StringBuilder();
                boolean allRestored=true;
                Integer[] changedIds=changed.keySet().toArray(new Integer[0]);
                for(int n=changedIds.length-1;n>=0;n--){
                    int id=changedIds[n],previous=changed.get(id);
                    if(id==1041){
                        // The ECU may have applied the requested 20 while
                        // readInt still reports opaque raw 56. A readback
                        // mismatch MUST NOT cause a second SOC-target write
                        // to a guessed prior value. Driver verifies OEM UI.
                        allRestored=false;
                        restored.append("1041 SIN ROLLBACK; objetivo solicitado=")
                            .append(targets.forcedSoc)
                            .append(", valor físico por confirmar en Jetour; ");
                        continue;
                    }
                    try{
                        // Best-effort restoration is allowed even after AUTO
                        // is disabled, but ONLY if OEM position remains P/D
                        // with valid 2148 speed at the time of restoration.
                        set(b,id,previous,null);
                        verify(b,id,previous,null);
                        restored.append(id).append(" restaurado; ");
                    }catch(Exception restoreError){
                        allRestored=false;
                        restored.append(id).append(" NO restaurado: ")
                            .append(describe(restoreError)).append("; ");
                    }
                }
                throw new IllegalStateException("Fallo parcial: "+
                    describe(failure)+" · recuperación "+
                    (allRestored?"CONFIRMADA":"NO CONFIRMADA")+" · "+
                    restored+" · revisar OEM inmediatamente");
            }
            String result="LECTURA CONFIRMADA: "+targets.summary()+
                " · "+writes+" escrituras, OEM readback. "+
                "Ajuste realizado sin detenerse si la T1 estaba en D.";
            T1OemDiagnosticLog.add(app,"SOC_REGEN_PERFIL_OK",result);
            return result;
        });
    }
    private static String describe(Exception e){
        String s=e.getClass().getSimpleName();
        if(e.getMessage()!=null)s+=" · "+e.getMessage();
        return s.length()>330?s.substring(0,330):s;
    }
}