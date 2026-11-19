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
        final int volts1063Int,volts1090Int;
        final double volts1090Float,volts1063AlcarFloat;
        Reading(long t,int c,double s,double v,double a,int vi,int ai,
                double vf,double av){
            timeMs=t;cable=c;soc=s;volts=v;amps=a;
            volts1063Int=vi;volts1090Int=ai;
            volts1090Float=vf;volts1063AlcarFloat=av;
        }
    }
    private static final String PKG="com.autolink.app.vehicleservice",
        CLS="com.autolink.app.vehicleservice.VehicleService",
        TOKEN="com.autolink.app.vehicleservice.IVehicleControl",
        ALCAR_PKG="com.autolink.carservice",
        ALCAR_CLS="com.autolink.carservice.ALCarService",
        ALCAR_TOKEN="com.autolink.adapterinterface.car.IALCar";
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
            // Read-only ALTERNATE getters, for diagnosis only. Never use
            // integer/1090/ALCar candidates as HV energy voltage until
            // their physical scale and meaning are verified on this car.
            boolean uncalibrated=!Double.isFinite(volts)||
                volts<100||volts>1000;
            int raw1063=uncalibrated?getIntOrUnknown(b,1063):Integer.MIN_VALUE;
            double raw1090=uncalibrated?getFloatOrUnknown(b,1090):Double.NaN;
            int raw1090Int=uncalibrated?getIntOrUnknown(b,1090):
                Integer.MIN_VALUE;
            double alcar=uncalibrated?readAlcar1063():Double.NaN;
            return new Reading(System.currentTimeMillis(),cable,soc,volts,amps,
                raw1063,raw1090Int,raw1090,alcar);
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

    private static int getIntOrUnknown(IBinder b,int id){
        try{return getInt(b,id);}
        catch(Exception ignored){return Integer.MIN_VALUE;}
    }
    /** ALCar getFloat(1063,area=0): known read-only transaction 9.
     * A diagnostic candidate only. Never substitute for OEM 1063 voltage.
     */
    private double readAlcar1063(){
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> binder=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                binder.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                ready.countDown();
            }
            @Override public void onNullBinding(ComponentName n){
                ready.countDown();
            }
        };
        boolean bound=false;
        try{
            bound=app.bindService(new Intent().setComponent(new ComponentName(
                ALCAR_PKG,ALCAR_CLS)),conn,0);
            if(!bound||!ready.await(1500,TimeUnit.MILLISECONDS))
                return Double.NaN;
            IBinder b=binder.get();
            if(b==null||!b.isBinderAlive()||
                !ALCAR_TOKEN.equals(b.getInterfaceDescriptor()))
                return Double.NaN;
            Parcel args=Parcel.obtain(),reply=Parcel.obtain();
            try{
                args.writeInterfaceToken(ALCAR_TOKEN);
                args.writeInt(1063);
                args.writeInt(0);
                if(!b.transact(9,args,reply,0))return Double.NaN;
                reply.readException();
                float value=reply.readFloat();
                return Float.isFinite(value)?value:Double.NaN;
            }finally{
                args.recycle();reply.recycle();
            }
        }catch(Exception ignored){
            return Double.NaN;
        }finally{
            if(bound)try{app.unbindService(conn);}catch(Exception ignored){}
        }
    }

}
