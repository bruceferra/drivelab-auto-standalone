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
import java.util.concurrent.atomic.AtomicReference;

/** Compact persistent OEM Binder read-only monitor; never writes to car.
 * A negative current also includes engine HV generation, NOT only braking.
 */
final class T1AutoDriveOem implements AutoCloseable {
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    static final class Point{
        long wallMs,elapsedMs;
        int ready,gear,cable;
        double speed,soc,volt,amps,rpm;
    }
    private final Context app;
    private IBinder binder;
    private ServiceConnection connection;
    private boolean bound;
    T1AutoDriveOem(Context context)throws Exception{
        app=context.getApplicationContext();
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){remote.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
        };
        bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),connection,0);
        if(!bound)throw new IllegalStateException("OEM no permite bind");
        try{
            if(!ready.await(2800,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("OEM sin respuesta");
            binder=remote.get();
            if(binder==null||!binder.isBinderAlive()||
               !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("OEM interfaz distinta");
        }catch(Exception e){close();throw e;}
    }
    private int intValue(int id)throws Exception{
        if(binder==null||!binder.isBinderAlive())
            throw new IllegalStateException("Binder OEM desconectado");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!binder.transact(5,data,reply,0))
                throw new IllegalStateException("int OEM "+id+" rechazado");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    private double floatValue(int id)throws Exception{
        if(binder==null||!binder.isBinderAlive())
            throw new IllegalStateException("Binder OEM desconectado");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!binder.transact(6,data,reply,0))
                throw new IllegalStateException("float OEM "+id+" rechazado");
            reply.readException();return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
    private int i(int id){
        try{return intValue(id);}catch(Exception e){return -1;}
    }
    private double f(int id){
        try{
            double v=floatValue(id);return Double.isFinite(v)?v:Double.NaN;
        }catch(Exception e){return Double.NaN;}
    }
    Point read()throws Exception{
        if(binder==null||!binder.isBinderAlive())
            throw new IllegalStateException("OEM no conectado");
        Point p=new Point();
        p.ready=i(1031);p.gear=i(1033);p.cable=i(1060);
        p.speed=f(2148);
        if(!Double.isFinite(p.speed)||p.speed<0||p.speed>220)
            p.speed=i(1089);
        p.soc=f(1062);p.volt=f(1063);p.amps=f(1064);
        p.rpm=f(1035);
        p.wallMs=System.currentTimeMillis();
        p.elapsedMs=SystemClock.elapsedRealtime();
        return p;
    }
    @Override public void close(){
        if(bound){
            bound=false;try{app.unbindService(connection);}catch(Exception ignored){}
        }
    }
}
