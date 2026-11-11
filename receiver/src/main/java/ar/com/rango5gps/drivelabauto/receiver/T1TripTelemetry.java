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
 * One trip snapshot, strictly read only. Each raw value is saved unmodified
 * (missing getters return NaN/-1) so newer calibration can reprocess a trip.
 * No old T1Launcher fuel-consumption algorithm is used.
 */
final class T1TripTelemetry {
    static final class Sample {
        long timestamp;
        int gear=-1,recirc=-1,recircChange=-1,fold=-1,motorOn=-1;
        double speed=Double.NaN,fuel=Double.NaN,rpm=Double.NaN,
            socInternal=Double.NaN,socDisplay=Double.NaN,
            voltage=Double.NaN,current=Double.NaN,
            counter1048=Double.NaN,counter1049=Double.NaN,
            oemElectricRolling50=Double.NaN,oemFuelRolling50=Double.NaN,
            oemElectricAverage500m=Double.NaN,oemFuelAverage500m=Double.NaN,
            voltageFloat1090=Double.NaN;
        // Integer.MIN_VALUE means getter failed. -1 is preserved: it can be
        // a *real OEM sentinel* rather than an Android exception.
        int rpmValid1034=-1,oemElectric500mInt=Integer.MIN_VALUE,
            oemFuel500mInt=Integer.MIN_VALUE,voltage1063Int=Integer.MIN_VALUE,
            current1064Int=Integer.MIN_VALUE,raw1090Int=Integer.MIN_VALUE;
    }
    private final Context app;
    T1TripTelemetry(Context context){app=context.getApplicationContext();}
    private static final String VEHICLE_PKG="com.autolink.app.vehicleservice";
    private static final String VEHICLE_CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String VEHICLE_TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final String CI_PKG="com.autolink.clusterinteraction";
    private static final String CI_CLS="com.autolink.clusterinteraction.CiService";
    private static final String CI_TOKEN="com.autolink.adapterinterface.clusterinteraction.IClusterInteraction";

    private interface Action{void run(IBinder b)throws Exception;}
    private void withService(String pkg,String cls,String token,Action action)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection c=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                remote.set(null);ready.countDown();
            }
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){
                remote.set(null);ready.countDown();
            }
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(pkg,cls)),c,0);
        if(!bound)throw new IllegalStateException("OEM sin enlace: "+pkg);
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("OEM sin respuesta: "+pkg);
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
                    !token.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no disponible");
            action.run(b);
        }finally{try{app.unbindService(c);}catch(Exception ignored){}}
    }
    private static int intValue(IBinder b,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(VEHICLE_TOKEN);d.writeInt(id);
            if(!b.transact(5,d,r,0))throw new IllegalStateException("readInt "+id);
            r.readException();return r.readInt();
        }finally{d.recycle();r.recycle();}
    }
    private static float floatValue(IBinder b,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(VEHICLE_TOKEN);d.writeInt(id);
            if(!b.transact(6,d,r,0))throw new IllegalStateException("readFloat "+id);
            r.readException();return r.readFloat();
        }finally{d.recycle();r.recycle();}
    }
    private static double ciFuel(IBinder b)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(CI_TOKEN);
            if(!b.transact(186,d,r,0))throw new IllegalStateException("CI 186");
            r.readException();return r.readFloat();
        }finally{d.recycle();r.recycle();}
    }
    private interface IntGetter{int get()throws Exception;}
    private interface FloatGetter{double get()throws Exception;}
    private static int safeInt(IntGetter f){
        try{return f.get();}catch(Exception e){return -1;}
    }
    private static int diagnosticInt(IntGetter f){
        try{return f.get();}catch(Exception e){return Integer.MIN_VALUE;}
    }
    private static double safeFloat(FloatGetter f){
        try{double v=f.get();return Double.isFinite(v)?v:Double.NaN;}
        catch(Exception e){return Double.NaN;}
    }

    Sample snapshot(){
        Sample s=new Sample();s.timestamp=System.currentTimeMillis();
        try{
            withService(VEHICLE_PKG,VEHICLE_CLS,VEHICLE_TOKEN,b->{
                s.gear=safeInt(()->intValue(b,1033));
                // RPM 1035 is an OEM candidate, not a proven motor state.
                s.rpmValid1034=safeInt(()->intValue(b,1034));
                s.rpm=safeFloat(()->floatValue(b,1035));
                if(!Double.isFinite(s.rpm)||s.rpm<0||s.rpm>12000)
                    s.rpm=safeFloat(()->intValue(b,1035));
                if(Double.isFinite(s.rpm)&&s.rpm>=0&&s.rpm<=12000)
                    s.motorOn=s.rpm>0?1:0;
                s.speed=safeFloat(()->floatValue(b,2148));
                if(!Double.isFinite(s.speed)||s.speed<0||s.speed>220)
                    s.speed=safeFloat(()->intValue(b,1089));
                s.socInternal=safeFloat(()->floatValue(b,1042));
                s.socDisplay=safeFloat(()->floatValue(b,1062));
                s.voltage=safeFloat(()->floatValue(b,1063));
                s.current=safeFloat(()->floatValue(b,1064));
                // Alternate typed OEM getters are diagnostics only. Do not
                // feed unvalidated integer values into HV energy calculations.
                s.voltage1063Int=diagnosticInt(()->intValue(b,1063));
                s.current1064Int=diagnosticInt(()->intValue(b,1064));
                s.voltageFloat1090=safeFloat(()->floatValue(b,1090));
                s.raw1090Int=diagnosticInt(()->intValue(b,1090));
                s.counter1048=safeFloat(()->floatValue(b,1048));
                s.counter1049=safeFloat(()->floatValue(b,1049));
                // OEM 50-km moving averages, independently confirmed against
                // the stock Jetour cluster. NOT instantaneous consumption nor
                // cumulative trip liters/kWh. Preserve as read-only observations.
                s.oemElectricRolling50=safeFloat(()->floatValue(b,1084));
                s.oemFuelRolling50=safeFloat(()->floatValue(b,1085));
                // Candidate OEM moving consumption over 500 metres;
                // never sum every snapshot as if each were a new segment.
                s.oemElectricAverage500m=safeFloat(()->floatValue(b,1086));
                s.oemFuelAverage500m=safeFloat(()->floatValue(b,1087));
                // 1086/1087 returned -1 for an entire 177-sample 1.65 km trip.
                // Compare int/float getters in parallel before considering
                // callbacks. Never accumulate these values as trip litres.
                s.oemElectric500mInt=diagnosticInt(()->intValue(b,1086));
                s.oemFuel500mInt=diagnosticInt(()->intValue(b,1087));
                s.recirc=safeInt(()->intValue(b,1004));
                s.recircChange=safeInt(()->intValue(b,2101));
                s.fold=safeInt(()->intValue(b,2003));
            });
        }catch(Exception ignored){}
        try{withService(CI_PKG,CI_CLS,CI_TOKEN,
            b->s.fuel=safeFloat(()->ciFuel(b)));}
        catch(Exception ignored){}
        return s;
    }
}
