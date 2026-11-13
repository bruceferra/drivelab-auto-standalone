package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

/**
 * Experimental SOC/regen OEM writer with physical owner verification gate.
 *
 * Prior logs validate that 1040/1041/1046 change FROM ORIGINAL OEM UI,
 * not a Receiver setter. All writes use the known VehicleService binder
 * contract (setter transaction 15); P and 0 km/h are mandatory for every
 * stage. An explicit manual test changes ONE key, verifies getter readback,
 * restores the original value, and verifies that restoration; only then is
 * that individual key unlocked for an automatic parked-only apply.
 *
 * Never writes SOC or regen while D/driving. Never retries a refused write.
 */
final class T1SocRegenPilot {
    interface Callback { void complete(String message); }
    private static final String PREFERENCES="t1_soc_regen_verified_writer_v1";
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());

    T1SocRegenPilot(Context c){app=c.getApplicationContext();}
    boolean busy(){return BUSY.get();}
    private SharedPreferences flags(){
        return app.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
    }
    static boolean confirmed(Context c,int id){
        if(id!=1040&&id!=1041&&id!=1046)return false;
        return c.getApplicationContext().getSharedPreferences(
            PREFERENCES,Context.MODE_PRIVATE).getBoolean("ok_"+id,false);
    }
    static String status(Context c){
        return "Escrituras verificadas desde DriveLab · 1040 "+
            (confirmed(c,1040)?"OK":"PENDIENTE")+" · 1041 "+
            (confirmed(c,1041)?"OK":"PENDIENTE")+" · 1046 "+
            (confirmed(c,1046)?"OK":"PENDIENTE")+
            ". AUTO SOC/regeneración se limita a P/0; no actúa en D.";
    }
    boolean test(int id,Callback callback){
        if(!T1SocRegenWritePolicy.accepted(id,
            id==1040?2:id==1041?56:1))return false;
        if(!BUSY.compareAndSet(false,true))return false;
        new Thread(()->{
            String result;
            try{result=tryTest(id);}
            catch(Exception ex){
                flags().edit().putBoolean("ok_"+id,false).apply();
                result="PRUEBA "+id+" NO VALIDADA: "+describe(ex)+
                    ". Revisá los estados en el menú OEM.";
                T1OemDiagnosticLog.add(app,"SOC_REGEN_TEST_ERROR",result);
            }finally{BUSY.set(false);}
            final String answer=result;
            main.post(()->callback.complete(answer));
        },"T1-OEM-SOC-REGEN-Manual-Test").start();
        return true;
    }

    boolean apply(T1SocRegenWritePolicy.Targets desired,Callback callback){
        if(desired==null||!desired.valid()||
           !confirmed(app,1040)||!confirmed(app,1046)||
           (desired.needsTarget()&&!confirmed(app,1041)))return false;
        if(!BUSY.compareAndSet(false,true))return false;
        new Thread(()->{
            String result;
            try{result=tryApply(desired);}
            catch(Exception ex){
                result="NO APLICADO / FALLÓ: "+describe(ex)+
                    ". Detener automatización; comprobar pantalla OEM.";
                T1OemDiagnosticLog.add(app,"SOC_REGEN_AUTO_ERROR",result);
            }finally{BUSY.set(false);}
            final String answer=result;
            main.post(()->callback.complete(answer));
        },"T1-OEM-SOC-REGEN-Auto-Parked").start();
        return true;
    }

    private interface OnBinder { String work(IBinder binder)throws Exception; }
    private String withBinder(OnBinder action)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<IBinder> ref=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                ref.set(b);latch.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                ref.set(null);latch.countDown();
            }
            @Override public void onNullBinding(ComponentName n){latch.countDown();}
            @Override public void onBindingDied(ComponentName n){latch.countDown();}
        };
        boolean bound=app.bindService(
            new Intent().setComponent(new ComponentName(PKG,CLS)),connection,0);
        if(!bound)throw new IllegalStateException("Sin vínculo VehicleService");
        try{
            if(!latch.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Timeout Binder");
            IBinder b=ref.get();
            if(b==null||!b.isBinderAlive()||
                !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Binder no corresponde a IVehicleControl");
            return action.work(b);
        }finally{
            try{app.unbindService(connection);}catch(Exception ignored){}
        }
    }
    private static int readInt(IBinder b,int id)throws Exception{
        if(id!=1040&&id!=1041&&id!=1046&&id!=1033)
            throw new SecurityException("Getter no autorizado: "+id);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!b.transact(5,data,reply,0))
                throw new IllegalStateException("Getter "+id+" sin transacción");
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    private static float readSpeed(IBinder b)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(2148);
            if(!b.transact(6,data,reply,0))
                throw new IllegalStateException("Getter velocidad 2148 no responde");
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
    // Kept in instance context, not global state: each test uses one owner.
    private void preflight(IBinder b,String stage)throws Exception{
        int gear=readInt(b,1033);
        float speed=readSpeed(b);
        T1OemDiagnosticLog.add(app,stage,
            "1033="+gear+", velocidad 2148="+speed);
        if(!T1SocRegenWritePolicy.parked(gear,speed))
            throw new IllegalStateException("Requiere P/0 km/h: 1033="+
                gear+", 2148="+speed);
    }
    private void doublePreflight(IBinder b)throws Exception{
        preflight(b,"SOC_REGEN_PREFLIGHT_1");
        SystemClock.sleep(450);
        preflight(b,"SOC_REGEN_PREFLIGHT_2");
    }
    private void set(IBinder b,int id,int value)throws Exception{
        if(!T1SocRegenWritePolicy.accepted(id,value))
            throw new SecurityException("Valor fuera de whitelist "+
                id+"="+value);
        preflight(b,"ANTES_ESCRITURA_"+id);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);data.writeInt(value);
            data.writeString(app.getPackageName());
            T1OemDiagnosticLog.add(app,"SOC_REGEN_SET",
                "transact(15) · "+id+"="+value+"; único intento");
            if(!b.transact(15,data,reply,0))
                throw new IllegalStateException("Setter rechazado en "+id);
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }
    private void verify(IBinder b,int id,int target)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+2800;
        do{
            if(readInt(b,id)==target){
                T1OemDiagnosticLog.add(app,"SOC_REGEN_CONFIRMED",
                    id+"="+target);
                return;
            }
            SystemClock.sleep(270);
        }while(SystemClock.elapsedRealtime()<deadline);
        throw new IllegalStateException(
            "No confirmado por readback "+id+"="+target+
            "; consultar configuración OEM");
    }
    private String tryTest(int id)throws Exception{
        flags().edit().putBoolean("ok_"+id,false).apply();
        T1OemDiagnosticLog.begin(app,
            "PRUEBA MANUAL P/0 SOC/REGEN "+id+
            " · escribir valor alternativo, leer, RESTAURAR y confirmar");
        return withBinder(b->{
            doublePreflight(b);
            int initial=readInt(b,id);
            int alternative=T1SocRegenWritePolicy.testAlternative(id,initial);
            if(alternative<0)
                throw new IllegalStateException(
                    "Valor inicial no reconocido "+id+"="+initial);
            T1OemDiagnosticLog.add(app,"SOC_REGEN_INITIAL",
                id+"="+initial+" · ensayo="+alternative);
            boolean attempted=false;
            try{
                preflight(b,"SOC_REGEN_PREPARE_TEST_SET");
                // Even if transact(15) later throws, OEM might have applied
                // the request: make one best-effort restore to original.
                attempted=true;
                set(b,id,alternative);
                verify(b,id,alternative);
            }finally{
                // The OEM may apply a write despite Binder/readback failure.
                // Always attempt a single return to initial if attempted.
                if(attempted){
                    try{
                        set(b,id,initial);
                        verify(b,id,initial);
                    }catch(Exception restore){
                        flags().edit().putBoolean("ok_"+id,false).apply();
                        throw new IllegalStateException(
                            "RESTAURACIÓN NO CONFIRMADA de "+id+
                            ". Usá control OEM ORIGINAL para restituir "+
                            initial+": "+describe(restore),restore);
                    }
                }
            }
            flags().edit().putBoolean("ok_"+id,true).apply();
            String msg="VALIDADO POR ESCRITURA Y RESTITUCIÓN: "+
                id+" "+initial+" → "+alternative+" → "+initial+
                ". Desbloqueado para AUTO solo en P/0.";
            T1OemDiagnosticLog.add(app,"SOC_REGEN_TEST_OK",msg);
            return msg;
        });
    }
    private String tryApply(T1SocRegenWritePolicy.Targets targets)
        throws Exception{
        T1OemDiagnosticLog.add(app,"SOC_REGEN_AUTO_BEGIN",
            targets.summary());
        return withBinder(b->{
            doublePreflight(b);
            LinkedHashMap<Integer,Integer> before=new LinkedHashMap<>();
            LinkedHashMap<Integer,Integer> requested=new LinkedHashMap<>();
            requested.put(1040,targets.strategy);
            if(targets.needsTarget())requested.put(1041,targets.forcedSoc);
            requested.put(1046,targets.regen);
            for(Map.Entry<Integer,Integer> entry:requested.entrySet()){
                int original=readInt(b,entry.getKey());
                if(!T1SocRegenWritePolicy.accepted(
                    entry.getKey(),original))
                    throw new IllegalStateException("Lectura no confiable "+
                        entry.getKey()+"="+original);
                before.put(entry.getKey(),original);
            }
            // No blind rollback inside auto when the ECU refuses a write;
            // owner can use stock UI. Stop further writes immediately.
            int done=0;
            LinkedHashMap<Integer,Integer> changed=new LinkedHashMap<>();
            try{
                for(Map.Entry<Integer,Integer> entry:requested.entrySet()){
                    int id=entry.getKey(),value=entry.getValue();
                    if(before.get(id)==value)continue;
                    // Record the old value BEFORE transact because OEM can
                    // apply a change even when the Binder result is absent.
                    changed.put(id,before.get(id));
                    set(b,id,value);
                    verify(b,id,value);
                    done++;
                }
            }catch(Exception applyFailure){
                boolean restored=true;
                StringBuilder recovery=new StringBuilder();
                Integer[] changedIds=changed.keySet().toArray(new Integer[0]);
                for(int n=changedIds.length-1;n>=0;n--){
                    int id=changedIds[n],initial=changed.get(id);
                    try{
                        set(b,id,initial);
                        verify(b,id,initial);
                        recovery.append(id).append(" RESTAURADO; ");
                    }catch(Exception re){
                        restored=false;
                        recovery.append(id).append(" SIN RESTAURAR: ")
                            .append(describe(re)).append("; ");
                    }
                }
                throw new IllegalStateException(
                    "Perfil incompleto; "+describe(applyFailure)+
                    ". Restitución "+(restored?"CONFIRMADA":"NO CONFIRMADA")+
                    " · "+recovery+
                    (restored?"":" · usar de inmediato ajustes OEM originales."),
                    applyFailure);
            }
            return "LECTURA CONFIRMADA: "+targets.summary()+
                " · "+done+" órdenes OEM. Aplicación SOLO en P/0.";
        });
    }
    private static String describe(Exception ex){
        String s=ex.getClass().getSimpleName();
        if(ex.getMessage()!=null)s+=" · "+ex.getMessage();
        return s.length()>260?s.substring(0,260):s;
    }
}