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

/**
 * Additional RAW OEM measurements for the automatic ignition journal.
 * Independent, optional and strictly read-only. Never controls the car.
 * ALCAR: VHAL 559940910 voltage and 559940911 current (transaction 9).
 * CI: fuel remaining 186 (transaction 186). CI level is not fuel consumed.
 * One binder connection per service, held across the 4-second polls.
 */
final class T1AutoReadOnlySignals implements AutoCloseable {
    private static final String AL_PKG="com.autolink.carservice",
        AL_CLS="com.autolink.carservice.ALCarService",
        AL_TOKEN="com.autolink.adapterinterface.car.IALCar";
    private static final String CI_PKG="com.autolink.clusterinteraction",
        CI_CLS="com.autolink.clusterinteraction.CiService",
        CI_TOKEN="com.autolink.adapterinterface.clusterinteraction.IClusterInteraction";
    private final Context app;
    private Link hal,ci;
    static final class Extra {
        double halVolts=Double.NaN,halAmps=Double.NaN,
            tankLiters=Double.NaN;
    }
    private static final class Link{
        IBinder binder;
        ServiceConnection connection;
        boolean bound;
    }
    T1AutoReadOnlySignals(Context context){
        app=context.getApplicationContext();
        hal=connect(AL_PKG,AL_CLS,AL_TOKEN);
        ci=connect(CI_PKG,CI_CLS,CI_TOKEN);
    }
    private Link connect(String pkg,String cls,String descriptor){
        Link link=new Link();
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> found=new AtomicReference<>();
        link.connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder b){
                found.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name){
                found.set(null);link.binder=null;
            }
            @Override public void onBindingDied(ComponentName name){
                found.set(null);link.binder=null;
            }
            @Override public void onNullBinding(ComponentName name){ready.countDown();}
        };
        try{
            link.bound=app.bindService(new Intent().setComponent(
                new ComponentName(pkg,cls)),link.connection,0);
            if(!link.bound||!ready.await(1800,TimeUnit.MILLISECONDS))
                return link;
            IBinder b=found.get();
            if(b!=null&&b.isBinderAlive()&&
               descriptor.equals(b.getInterfaceDescriptor()))
                link.binder=b;
        }catch(Exception ignored){}
        return link;
    }
    Extra read(){
        Extra e=new Extra();
        if(hal!=null&&hal.binder!=null&&hal.binder.isBinderAlive()){
            e.halVolts=alcarFloat(hal.binder,559940910);
            e.halAmps=alcarFloat(hal.binder,559940911);
        }
        if(ci!=null&&ci.binder!=null&&ci.binder.isBinderAlive()){
            e.tankLiters=ciFloat(ci.binder);
            if(!FuelReadingPolicy.usable((float)e.tankLiters))
                e.tankLiters=Double.NaN;
        }
        return e;
    }
    static boolean currentAgrees(double vehicle,double halCurrent){
        return Double.isFinite(vehicle)&&Double.isFinite(halCurrent)&&
            Math.abs(vehicle)<=1000&&
            Math.abs(vehicle-halCurrent)<=Math.max(2.5,Math.abs(vehicle)*0.30);
    }
    private static double alcarFloat(IBinder b,int prop){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(AL_TOKEN);
            data.writeInt(prop);data.writeInt(0);
            if(!b.transact(9,data,reply,0))return Double.NaN;
            reply.readException();
            float value=reply.readFloat();
            return Float.isFinite(value)?value:Double.NaN;
        }catch(Exception ignored){return Double.NaN;}
        finally{data.recycle();reply.recycle();}
    }
    private static double ciFloat(IBinder b){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(CI_TOKEN);
            if(!b.transact(186,data,reply,0))return Double.NaN;
            reply.readException();
            float value=reply.readFloat();
            return Float.isFinite(value)?value:Double.NaN;
        }catch(Exception ignored){return Double.NaN;}
        finally{data.recycle();reply.recycle();}
    }
    private void close(Link link){
        if(link==null||!link.bound)return;
        try{app.unbindService(link.connection);}catch(Exception ignored){}
        link.binder=null;link.bound=false;
    }
    @Override public void close(){close(hal);close(ci);}
}
