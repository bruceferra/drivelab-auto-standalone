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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * MANUAL, narrow experimental command: EV or EV MAX -> HEV only.
 * Never called by GPS/geofencing/boot/service. One Binder write to property
 * 1043, target=2, only after two independent P/0 km/h readbacks and an
 * explicit confirmation dialog in a still-visible Activity.
 *
 * VehicleService setter transaction 15 is proven for window properties but
 * NOT independently validated for power mode 1043 on this firmware. A
 * transaction success is not proof of mode transition; readback is required.
 */
final class T1ManualHevExperiment {
    interface Callback {void complete(String report);}
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    T1ManualHevExperiment(Context context){app=context.getApplicationContext();}
    boolean busy(){return busy.get();}
    boolean request(BooleanSupplier screenVisible,Callback cb){
        if(!busy.compareAndSet(false,true))return false;
        new Thread(()->{
            String result;
            try{result=attempt(screenVisible);}
            catch(Exception error){
                result="NO SE CAMBIÓ: "+error.getClass().getSimpleName()+
                    " · "+(error.getMessage()==null?"sin detalle":error.getMessage());
            }
            busy.set(false);
            cb.complete(result);
        },"T1-Manual-EV-to-HEV").start();
        return true;
    }
    private String attempt(BooleanSupplier visible)throws Exception{
        if(!visible.getAsBoolean())throw new SecurityException("Pantalla ya no visible");
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> binder=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                binder.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){binder.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){binder.set(null);}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),connection,0);
        if(!bound)throw new IllegalStateException("VehicleService no disponible");
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("VehicleService sin respuesta");
            IBinder b=binder.get();
            if(b==null||!b.isBinderAlive()||
               !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no coincide");
            int mode=readInt(b,T1ManualHevPolicy.POWER_MODE_ID);
            checkStopped(b,mode);
            SystemClock.sleep(350);
            if(!visible.getAsBoolean()||!b.isBinderAlive())
                throw new SecurityException("Pantalla cerrada o enlace perdido");
            // Second fresh read must still be in P, at 0 km/h, with EV/EV MAX.
            int confirmedMode=readInt(b,T1ManualHevPolicy.POWER_MODE_ID);
            checkStopped(b,confirmedMode);
            writeHev(b,visible);
            long deadline=SystemClock.elapsedRealtime()+3500L;
            do{
                if(!visible.getAsBoolean())break;
                SystemClock.sleep(350);
                int value=readInt(b,T1ManualHevPolicy.POWER_MODE_ID);
                if(value==T1ManualHevPolicy.HEV)
                    return "CONFIRMADO · 1043=2 HEV. Verificá también la pantalla OEM.";
            }while(SystemClock.elapsedRealtime()<deadline);
            return "ORDEN ENVIADA SIN CONFIRMACIÓN de HEV. "+
                "No repetir a ciegas; verificá modo en la pantalla OEM.";
        }finally{
            try{app.unbindService(connection);}catch(Exception ignored){}
        }
    }
    private static void checkStopped(IBinder b,int mode)throws Exception{
        int gear=readInt(b,1033);
        double speed;
        try{speed=readFloat(b,2148);}
        catch(Exception ex){speed=readInt(b,1089);}
        if(!T1ManualHevPolicy.canRequest(gear,speed,mode))
            throw new IllegalStateException(
                "Se exige P (1033=1), velocidad 0 y modo EV / EV MAX legibles. "+
                "Leídos: marcha "+gear+", velocidad "+speed+", modo "+mode);
    }
    private static int readInt(IBinder b,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(id);
            if(!b.transact(5,d,r,0))throw new IllegalStateException("readInt "+id);
            r.readException();return r.readInt();
        }finally{d.recycle();r.recycle();}
    }
    private static float readFloat(IBinder b,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(id);
            if(!b.transact(6,d,r,0))throw new IllegalStateException("readFloat "+id);
            r.readException();return r.readFloat();
        }finally{d.recycle();r.recycle();}
    }
    private void writeHev(IBinder b,BooleanSupplier visible)throws Exception{
        if(!visible.getAsBoolean()||!b.isBinderAlive())
            throw new SecurityException("No se escribe fuera del primer plano");
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);
            d.writeInt(T1ManualHevPolicy.POWER_MODE_ID);
            d.writeInt(T1ManualHevPolicy.HEV);
            d.writeString(app.getPackageName());
            if(!b.transact(15,d,r,0))
                throw new IllegalStateException("Setter OEM 1043 no disponible");
            r.readException();
        }finally{d.recycle();r.recycle();}
    }
}
