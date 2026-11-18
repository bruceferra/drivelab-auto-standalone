package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Only four OEM read calls per poll. No writes, GPS, T1Launcher APIs,
 * root or reflection. An unavailable getter remains UNKNOWN, never 0. */
final class T1ChargeOemReader {
    static final class Reading {
        final long timeMs; final int cable;
        final double soc,volts,amps;
        Reading(long t,int c,double s,double v,double a){
            timeMs=t;cable=c;soc=s;volts=v;amps=a;
        }
    }
    private static final String PKG="com.autolink.app.vehicleservice",
        CLS="com.autolink.app.vehicleservice.VehicleService",
        TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    T1ChargeOemReader(Context c){app=c.getApplicationContext();}
    private interface Task{void read(IBinder binder)throws Exception;}
    Reading poll()throws Exception{
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                remote.set(null);
            }
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),conn,0);
        if(!bound)throw new IllegalStateException("OEM sin enlace");
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("OEM sin respuesta");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
               !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no válida");
            int cable=getInt(b,1060);
            double soc=getFloatOrUnknown(b,1062);
            double volts=getFloatOrUnknown(b,1063);
            double amps=getFloatOrUnknown(b,1064);
            return new Reading(System.currentTimeMillis(),cable,soc,volts,amps);
        }finally{
            try{app.unbindService(conn);}catch(Exception ignored){}
        }
    }
    private static int getInt(IBinder b,int id)throws Exception{
        Parcel a=Parcel.obtain(),z=Parcel.obtain();
        try{
            a.writeInterfaceToken(TOKEN);a.writeInt(id);
            if(!b.transact(5,a,z,0))throw new IllegalStateException("readInt "+id);
            z.readException();return z.readInt();
        }finally{a.recycle();z.recycle();}
    }
    private static double getFloatOrUnknown(IBinder b,int id){
        Parcel a=Parcel.obtain(),z=Parcel.obtain();
        try{
            a.writeInterfaceToken(TOKEN);a.writeInt(id);
            if(!b.transact(6,a,z,0))return Double.NaN;
            z.readException();double x=z.readFloat();
            return Double.isFinite(x)?x:Double.NaN;
        }catch(Exception ignored){return Double.NaN;}
        finally{a.recycle();z.recycle();}
    }
}
