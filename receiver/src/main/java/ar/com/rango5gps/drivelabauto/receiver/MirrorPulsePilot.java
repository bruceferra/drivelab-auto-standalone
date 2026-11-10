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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Experimental, foreground-only, ONE SHORT PULSE mirror test from T1N134 DEX.
 * NEVER used by BootReceiver/ReceiverService. No reverse auto-tilt.
 *
 * 1033: reverse-gear candidate (T1N134 uses 1 for P, 2 for R).
 * 2069: command from EspejoRService (1/2 = opposite motions, 0 = release).
 * 2148 float / 1089 int: candidate speed readbacks (units to validate).
 *
 * This feature is UNVERIFIED in the Jetour Receiver UID. Only test while the
 * vehicle is physically in P, stationary, on level ground and supervised.
 * Android cannot guarantee motor stop after a crash or a dead Binder link.
 */
public final class MirrorPulsePilot {
    public interface Callback { void done(String status); }
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final int GEAR_ID=1033, GEAR_P=1, MIRROR_ID=2069;
    private static final int SPEED_FLOAT_ID=2148, SPEED_INT_ID=1089;
    private static final long PULSE_MS=120, COOLDOWN_MS=1800;
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private volatile boolean foreground=false;
    private volatile long lastAttemptAt=0;

    public MirrorPulsePilot(Context context){app=context.getApplicationContext();}
    public void setForeground(boolean enabled){
        foreground=enabled;
        if(!enabled)stop.set(true);
    }
    public boolean busy(){return busy.get();}
    public void cancel(){stop.set(true);}

    /** Single press triggers ONE 120ms pulse. There is deliberately no HOLD yet. */
    public boolean pulse(int direction,Callback callback){
        if(!foreground||(direction!=1&&direction!=2))return false;
        if(!busy.compareAndSet(false,true))return false;
        long now=SystemClock.elapsedRealtime();
        if(now-lastAttemptAt<COOLDOWN_MS){
            busy.set(false);
            return false;
        }
        lastAttemptAt=now;
        stop.set(false);
        new Thread(()->executePulse(direction,callback),"T1-Mirror-OnePulse").start();
        return true;
    }

    private void emit(Callback callback,String status){
        main.post(()->callback.done(status));
    }

    private void executePulse(int direction,Callback callback){
        Connection c=null;
        boolean motionAttempted=false,releaseOk=false;
        String status="Sin operación";
        try{
            requireReady();
            c=connect();
            int gear=readInt(c.binder,GEAR_ID);
            if(gear!=GEAR_P)
                throw new IllegalStateException("Marcha 1033="+gear+
                    "; se exige P (=1 en APK) para esta prueba");
            double speed=readSpeed(c.binder);
            if(!Double.isFinite(speed)||speed<0||speed>0.5)
                throw new IllegalStateException("Velocidad no confirmada en 0 km/h: "+speed);
            requireReady();
            // A release command BEFORE motion is a conservative neutralization.
            write(c.binder,0);
            requireReady();
            // Never repeat this command; STOP in finally even on exceptions.
            motionAttempted=true;
            write(c.binder,direction);
            long until=SystemClock.elapsedRealtime()+PULSE_MS;
            while(SystemClock.elapsedRealtime()<until && !stop.get() && foreground){
                SystemClock.sleep(10);
            }
            status="Se envió un pulso breve (2069="+direction+"). "+
                "Confirmar visualmente dirección/espejo y que se detenga.";
        }catch(Exception ex){
            String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            status="NO COMPLETADO · "+detail;
        }finally{
            if(c!=null && c.binder!=null){
                try{
                    // Safe-release candidate from T1N134; still needs physical proof.
                    write(c.binder,0);
                    releaseOk=true;
                }catch(Exception releaseError){
                    status+=" · ALERTA: no se confirmó liberación 2069=0";
                }
            }
            if(c!=null)c.close();
            busy.set(false);
            if(motionAttempted && !releaseOk)
                status+=" · Usá el mando físico OEM y comprobá el espejo antes de circular";
            emit(callback,status);
        }
    }

    /**
     * Explicit foreground emergency release. Independent Binder binding allows
     * trying 2069=0 even if a pulse thread has not completed.
     */
    public boolean release(Callback callback){
        if(!foreground)return false;
        stop.set(true);
        new Thread(()->{
            String status;
            Connection c=null;
            try{
                c=connect();
                write(c.binder,0);
                status="Liberación 2069=0 enviada. Comprobar físicamente: "+
                    "la respuesta Binder NO prueba la parada del motor.";
            }catch(Exception ex){
                status="ALERTA: no se pudo enviar liberación 2069=0: "+
                    ex.getClass().getSimpleName();
            }finally{
                if(c!=null)c.close();
            }
            emit(callback,status);
        },"T1-Mirror-Release").start();
        return true;
    }

    private void requireReady(){
        if(!foreground||stop.get())
            throw new IllegalStateException("Panel no visible o prueba cancelada");
    }

    private static double readSpeed(IBinder binder)throws Exception{
        try{
            float raw=readFloat(binder,SPEED_FLOAT_ID);
            if(Float.isFinite(raw) && raw>=0 && raw<300)return raw;
        }catch(Exception ignored){}
        // Alternative documented in T1N134: integer speed in same OEM interface.
        int raw=readInt(binder,SPEED_INT_ID);
        if(raw<0 || raw>300)
            throw new IllegalStateException("Velocidad OEM fuera de rango");
        return raw;
    }

    private static int readInt(IBinder binder,int id)throws Exception{
        if(id!=GEAR_ID&&id!=SPEED_INT_ID)throw new SecurityException("ID lectura inválido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            if(!binder.transact(5,data,reply,0))
                throw new IllegalStateException("Lectura int rechazada: "+id);
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }

    private static float readFloat(IBinder binder,int id)throws Exception{
        if(id!=SPEED_FLOAT_ID)throw new SecurityException("ID lectura inválido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            if(!binder.transact(6,data,reply,0))
                throw new IllegalStateException("Lectura float rechazada: "+id);
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }

    private void write(IBinder binder,int value)throws Exception{
        if(value!=0&&value!=1&&value!=2)
            throw new SecurityException("Valor de espejo no permitido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(MIRROR_ID);
            data.writeInt(value);
            data.writeString(app.getPackageName());
            if(!binder.transact(15,data,reply,0))
                throw new IllegalStateException("OEM rechazó escritura 2069="+value);
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }

    private Connection connect()throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);latch.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){stop.set(true);}
            @Override public void onNullBinding(ComponentName n){latch.countDown();}
            @Override public void onBindingDied(ComponentName n){stop.set(true);latch.countDown();}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),conn,0);
        if(!bound)throw new IllegalStateException("VehicleService no disponible");
        try{
            if(!latch.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("VehicleService sin respuesta");
            IBinder b=remote.get();
            if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Descriptor OEM inválido");
            return new Connection(conn,b);
        }catch(Exception e){
            try{app.unbindService(conn);}catch(Exception ignored){}
            throw e;
        }
    }
    private final class Connection{
        final ServiceConnection connection;
        final IBinder binder;
        Connection(ServiceConnection c,IBinder b){connection=c;binder=b;}
        void close(){try{app.unbindService(connection);}catch(Exception ignored){}}
    }
}
