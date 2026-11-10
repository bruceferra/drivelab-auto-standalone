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
    private static final int GEAR_ID=1033, GEAR_P=1, RIGHT_ID=2069, LEFT_ID=2068;
    private static final int SPEED_FLOAT_ID=2148, SPEED_INT_ID=1089;
    private static final long COOLDOWN_MS=1800;
    // Manual timed tests only. 2 s is a hard maximum; no auto reversing or sweep.
    public static final int SHORT_MS=120, SMOOTH_TEST_MS=500,
        CALIBRATE_MS=1000, LONG_TEST_MS=2000;
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private volatile boolean foreground=false;
    private volatile long lastAttemptAt=0;
    private volatile int activeMirrorId=RIGHT_ID;

    public MirrorPulsePilot(Context context){app=context.getApplicationContext();}
    public void setForeground(boolean enabled){
        foreground=enabled;
        if(!enabled)stop.set(true);
    }
    public boolean busy(){return busy.get();}
    public void cancel(){stop.set(true);}

    /** Legacy 120ms RIGHT command kept for existing callers. */
    public boolean pulse(int direction,Callback callback){
        return pulse(RIGHT_ID,direction,SHORT_MS,callback);
    }
    /** One user-confirmed, time-bounded command. LEFT mapping is only a candidate. */
    public boolean pulse(int mirrorId,int direction,int durationMs,Callback callback){
        if(!foreground||(mirrorId!=RIGHT_ID&&mirrorId!=LEFT_ID)||
                (direction!=1&&direction!=2)||
                (durationMs!=SHORT_MS&&durationMs!=SMOOTH_TEST_MS&&
                 durationMs!=CALIBRATE_MS&&durationMs!=LONG_TEST_MS))return false;
        if(!busy.compareAndSet(false,true))return false;
        long now=SystemClock.elapsedRealtime();
        if(now-lastAttemptAt<COOLDOWN_MS){
            busy.set(false);
            return false;
        }
        lastAttemptAt=now;
        stop.set(false);
        activeMirrorId=mirrorId;
        new Thread(()->executePulse(mirrorId,direction,durationMs,callback),
            "T1-Mirror-OneTimedCommand").start();
        return true;
    }

    /** Read P/R/D raw gear and speed without any actuator write; foreground only. */
    public boolean readGearOnce(Callback callback){
        if(!foreground||!busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;
            String result;
            try{
                requireReady();
                c=connect();
                int gear=readInt(c.binder,GEAR_ID);
                double speed=readSpeed(c.binder);
                String interpretation=gear==1?"P candidata":gear==2?"R candidata":
                    "marcha no mapeada (posible D/N)";
                result="LECTURA OEM · 1033="+gear+" ("+interpretation+
                    ") · velocidad="+speed+
                    " (unidad candidata km/h). Sin orden de movimiento.";
            }catch(Exception e){
                result="SIN LECTURA OEM · "+e.getClass().getSimpleName()+
                    " · "+String.valueOf(e.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,result);
        },"T1-Mirror-Gear-ReadOnly").start();
        return true;
    }

    /**
     * Diagnostic only: distinguish actual readback of 2068/2069 from the
     * setter used to drive motors. Codes 0/1/2 are not treated as angle.
     */
    public boolean readMirrorStates(Callback callback){
        if(!foreground || !busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;
            String result;
            try{
                requireReady();
                c=connect();
                int gear=readInt(c.binder,GEAR_ID);
                double speed=readSpeed(c.binder);
                if(gear!=GEAR_P || !Double.isFinite(speed) || speed>0.5 || speed<0)
                    throw new IllegalStateException("Lectura habilitada solo en P y detenido");
                String left, right;
                try{left=String.valueOf(readInt(c.binder,LEFT_ID));}
                catch(Exception ex){left="NO DISPONIBLE ("+ex.getClass().getSimpleName()+")";}
                try{right=String.valueOf(readInt(c.binder,RIGHT_ID));}
                catch(Exception ex){right="NO DISPONIBLE ("+ex.getClass().getSimpleName()+")";}
                result="LECTURA OEM RAW con mando suelto: 2068 izquierdo="+left+
                    " · 2069 derecho="+right+
                    ". Comparar extremos 2/1 e intermedio 0; no implica ángulo real.";
            }catch(Exception ex){
                result="SIN LECTURA DE ESPEJOS · "+ex.getClass().getSimpleName()+
                    " · "+String.valueOf(ex.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,result);
        },"T1-Mirror-State-ReadOnly").start();
        return true;
    }

    private void emit(Callback callback,String status){
        main.post(()->callback.done(status));
    }

    private void executePulse(int mirrorId,int direction,int durationMs,Callback callback){
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
            write(c.binder,mirrorId,0);
            requireReady();
            // Never repeat this command; STOP in finally even on exceptions.
            motionAttempted=true;
            write(c.binder,mirrorId,direction);
            long until=SystemClock.elapsedRealtime()+durationMs;
            while(SystemClock.elapsedRealtime()<until && !stop.get() && foreground){
                SystemClock.sleep(10);
            }
            status="Se envió una orden experimental "+mirrorId+"="+direction+
                " durante hasta "+durationMs+" ms, seguida de liberación 0. "+
                "Comprobar físicamente lado, sentido y parada.";
        }catch(Exception ex){
            String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            status="NO COMPLETADO · "+detail;
        }finally{
            if(c!=null && c.binder!=null){
                try{
                    // Release candidate from T1N134. Binder success != physical proof.
                    write(c.binder,mirrorId,0);
                    releaseOk=true;
                }catch(Exception releaseError){
                    status+=" · ALERTA: no se confirmó liberación "+mirrorId+"=0";
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
        final int mirrorId=activeMirrorId;
        new Thread(()->{
            String status;
            Connection c=null;
            try{
                c=connect();
                write(c.binder,mirrorId,0);
                status="Liberación "+mirrorId+"=0 enviada. Comprobar físicamente: "+
                    "respuesta Binder NO prueba parada mecánica.";
            }catch(Exception ex){
                status="ALERTA: no se pudo enviar liberación "+mirrorId+"=0: "+
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
        if(id!=GEAR_ID&&id!=SPEED_INT_ID&&id!=LEFT_ID&&id!=RIGHT_ID)
            throw new SecurityException("ID lectura inválido");
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

    private void write(IBinder binder,int mirrorId,int value)throws Exception{
        if((mirrorId!=RIGHT_ID&&mirrorId!=LEFT_ID)||
                (value!=0&&value!=1&&value!=2))
            throw new SecurityException("Valor de espejo no permitido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(mirrorId);
            data.writeInt(value);
            data.writeString(app.getPackageName());
            if(!binder.transact(15,data,reply,0))
                throw new IllegalStateException("OEM rechazó escritura "+mirrorId+"="+value);
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
