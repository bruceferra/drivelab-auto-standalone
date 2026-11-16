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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Four-window manual group controls, using the user's tested OEM Binder interface.
 * 2004..2007 only. CLOSE(1) skips already-closed windows.
 * Explicit ventilation (3) is requested from any readable state 0..4;
 * every OEM target is checked before moving to the next window.
 *
 * Manual foreground tap; proven sequential dispatch, never writes all motors together.
 * No root/ADB, no background/boot commands, no anti-pinch override, no ECU reset.
 * Stop only prevents NEXT commands; it cannot stop a moving window motor.
 */
public final class VehicleWindowGroup {
    public interface Callback { void update(Progress progress); }
    /** Read-only status. null positions means OEM reading failed; do not
     * present green for an unverified group.
     */
    public interface StateCallback {
        void update(int[] positions,String error);
    }
    public static final class Progress {
        public final boolean running,completed;
        public final String message;
        public final List<String> events;
        Progress(boolean running,boolean completed,String message,List<String> events){
            this.running=running;this.completed=completed;this.message=message;
            this.events=new ArrayList<>(events);
        }
    }
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final long STEP_TIMEOUT_MS=12000;
    private static final int SMART_TOGGLE=-7; // internal sentinel; never sent to vehicle
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private volatile boolean foreground;

    public VehicleWindowGroup(Context context){app=context.getApplicationContext();}
    public void setForeground(boolean state){foreground=state;if(!state)stop.set(true);}
    public boolean busy(){return busy.get();}
    /** Cancels subsequent commands; physical OEM switch controls a motor already moving. */
    public void cancel(){stop.set(true);}
    public boolean start(int operation,Callback callback){
        return startSelected(WindowGroupPolicy.IDS,operation,callback);
    }
    public boolean smartVentilation(Callback callback){
        return startSelected(WindowGroupPolicy.IDS,SMART_TOGGLE,callback);
    }
    /** Foreground readback, used to update button color after manual changes. */
    public boolean inspectState(StateCallback callback){
        if(!foreground||!busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            int[] values=null;
            String problem=null;
            ServiceConnection conn=null;
            boolean bound=false;
            try{
                requireForeground();
                CountDownLatch latch=new CountDownLatch(1);
                AtomicReference<IBinder> remote=new AtomicReference<>();
                conn=new ServiceConnection(){
                    @Override public void onServiceConnected(ComponentName c,IBinder b){
                        remote.set(b);latch.countDown();
                    }
                    @Override public void onServiceDisconnected(ComponentName c){stop.set(true);}
                    @Override public void onNullBinding(ComponentName c){latch.countDown();}
                    @Override public void onBindingDied(ComponentName c){stop.set(true);}
                };
                bound=app.bindService(new Intent().setComponent(
                    new ComponentName(PKG,CLS)),conn,0);
                if(!bound||!latch.await(4,TimeUnit.SECONDS))
                    throw new IllegalStateException("Sin lectura OEM de ventanillas");
                IBinder b=remote.get();
                if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))
                    throw new SecurityException("Interfaz OEM inesperada");
                values=new int[WindowGroupPolicy.IDS.length];
                for(int i=0;i<values.length;i++){
                    requireForeground();
                    values[i]=read(b,WindowGroupPolicy.IDS[i]);
                }
            }catch(Exception ex){
                problem=ex.getMessage()==null?ex.getClass().getSimpleName():
                    ex.getMessage();
            }finally{
                if(bound&&conn!=null)
                    try{app.unbindService(conn);}catch(RuntimeException ignored){}
                busy.set(false);
                final int[] positions=values;
                final String error=problem;
                main.post(()->callback.update(positions,error));
            }
        },"T1-Windows-OEM-ReadOnly").start();
        return true;
    }
    public boolean startOne(int id,int operation,Callback callback){
        if(!WindowGroupPolicy.validId(id))return false;
        return startSelected(new int[]{id},operation,callback);
    }
    private boolean startSelected(int[] ids,int operation,Callback callback){
        if((operation!=WindowGroupPolicy.CLOSE&&
            operation!=WindowGroupPolicy.VENT_QUARTER&&
            operation!=SMART_TOGGLE)||!foreground)return false;
        if(!busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->run(ids,operation,callback),"T1-Windows-Sequential").start();
        return true;
    }
    private void emit(Callback cb,boolean running,boolean completed,String status,List<String> events){
        Progress snapshot=new Progress(running,completed,status,events);
        main.post(()->cb.update(snapshot));
    }
    private void requireForeground(){
        if(stop.get()||!foreground)
            throw new IllegalStateException("Secuencia detenida; no hay más órdenes");
    }
    private void run(int[] ids,int operation,Callback cb){
        ArrayList<String> events=new ArrayList<>();
        ServiceConnection conn=null;
        boolean bound=false,completed=false;
        String status="Sin ejecutar";
        try{
            requireForeground();
            CountDownLatch latch=new CountDownLatch(1);
            AtomicReference<IBinder> remote=new AtomicReference<>();
            conn=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName c,IBinder b){
                    remote.set(b);latch.countDown();
                }
                @Override public void onServiceDisconnected(ComponentName c){stop.set(true);}
                @Override public void onNullBinding(ComponentName c){latch.countDown();}
                @Override public void onBindingDied(ComponentName c){stop.set(true);}
            };
            bound=app.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),conn,0);
            if(!bound||!latch.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("Servicio OEM no disponible");
            IBinder b=remote.get();
            if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM inesperada");

            // One snapshot of FOUR positions before any actuation.
            // Any nonquarter window (including one lowered at the gate)
            // => restore all quarter; only all-quarter => close all.
            int command=operation;
            if(operation==SMART_TOGGLE||
                    operation==WindowGroupPolicy.VENT_QUARTER){
                int[] initial=new int[ids.length];
                for(int i=0;i<ids.length;i++){
                    requireForeground();
                    initial[i]=read(b,ids[i]);
                    events.add(WindowGroupPolicy.name(ids[i])+
                        " · estado inicial "+initial[i]);
                }
                if(operation==SMART_TOGGLE){
                    if(ids.length!=WindowGroupPolicy.IDS.length)
                        throw new IllegalStateException(
                            "Modo ventilación necesita cuatro estados OEM");
                    command=WindowGroupPolicy.smartOperation(initial);
                    events.add("Ventilación: "+
                        (command==WindowGroupPolicy.CLOSE?
                        "cuatro a 1/4 → CIERRE TOTAL":
                        "alguna fuera de 1/4 → TODAS A 1/4"));
                }
            }

            int sent=0,skipped=0;
            for(int id:ids){
                requireForeground();
                int before=read(b,id);
                int decision=WindowGroupPolicy.decision(command,before);
                if(decision==WindowGroupPolicy.SKIP){
                    skipped++;
                    events.add(WindowGroupPolicy.name(id)+" · ya en estado "+before+
                        " · sin escritura");
                    emit(cb,true,false,"Omitida "+WindowGroupPolicy.name(id),events);
                    continue;
                }
                if(decision!=WindowGroupPolicy.SEND)
                    throw new IllegalStateException(WindowGroupPolicy.name(id)+
                        ": estado "+before+" no apto; se detuvo la secuencia");
                requireForeground();
                // Exactly one OEM Binder transaction per selected window.
                write(b,id,command);
                sent++;
                events.add(WindowGroupPolicy.name(id)+" · "+
                    before+" → orden "+command+" enviada");
                emit(cb,true,false,"Verificando "+WindowGroupPolicy.name(id),events);
                int after=awaitTarget(b,id,command,STEP_TIMEOUT_MS);
                events.add(WindowGroupPolicy.name(id)+" · respuesta de lectura "+after);
                if(after!=command)
                    throw new IllegalStateException(WindowGroupPolicy.name(id)+
                        ": no se confirmó estado "+command+
                        " tras 12 s; no se ejecutan otras órdenes");
                // The state can change before motion ends; avoid issuing the next
                // command immediately when the state first matches.
                SystemClock.sleep(1400);
                requireForeground();
            }
            completed=true;
            status=WindowGroupPolicy.operation(command)+" · "+
                sent+" órdenes enviadas, "+skipped+" ventanas ya en destino; "+
                "los estados OEM respondieron, verificar físicamente";
        }catch(Exception ex){
            String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            status="DETENIDO · "+detail;
            events.add(status);
        }finally{
            if(bound&&conn!=null)try{app.unbindService(conn);}catch(Exception ignored){}
            busy.set(false);
            emit(cb,false,completed,status,events);
        }
    }
    private int awaitTarget(IBinder b,int id,int expected,long maxMs)throws Exception{
        long start=SystemClock.elapsedRealtime();
        do{
            requireForeground();
            int value=read(b,id);
            if(value==expected)return value;
            SystemClock.sleep(500);
        }while(SystemClock.elapsedRealtime()-start<maxMs);
        return read(b,id);
    }
    private static int read(IBinder b,int id)throws Exception{
        if(!WindowGroupPolicy.validId(id))throw new SecurityException("ID no permitido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!b.transact(5,data,reply,0))
                throw new IllegalStateException("Lectura OEM rechazada");
            reply.readException();
            int value=reply.readInt();
            if(!WindowGroupPolicy.validState(value))
                throw new IllegalStateException("Lectura OEM inválida "+value);
            return value;
        }finally{data.recycle();reply.recycle();}
    }
    private void write(IBinder b,int id,int command)throws Exception{
        if(!WindowGroupPolicy.validId(id)||
            (command!=WindowGroupPolicy.CLOSE&&command!=WindowGroupPolicy.VENT_QUARTER))
            throw new SecurityException("Escritura OEM no permitida");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            data.writeInt(command);
            data.writeString(app.getPackageName());
            if(!b.transact(15,data,reply,0))
                throw new IllegalStateException("Escritura OEM rechazada");
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }
}
