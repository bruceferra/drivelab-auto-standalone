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

/** Strict allowlist: read 1004/2101, write ONLY 1004=1 to enable cabin recirculation. */
final class RecirculationOem implements AutoCloseable {
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    private final ServiceConnection connection;
    private final IBinder binder;
    private boolean bound;

    RecirculationOem(Context context)throws Exception{
        app=context.getApplicationContext();
        CountDownLatch connected=new CountDownLatch(1);
        AtomicReference<IBinder> ref=new AtomicReference<>();
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                ref.set(b);connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                ref.set(null);
            }
            @Override public void onNullBinding(ComponentName n){connected.countDown();}
            @Override public void onBindingDied(ComponentName n){
                ref.set(null);connected.countDown();
            }
        };
        boolean ok=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),connection,0);
        bound=ok;
        if(!ok)throw new IllegalStateException("VehicleService no disponible");
        IBinder result;
        try{
            if(!connected.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("VehicleService sin respuesta");
            result=ref.get();
            if(result==null||!result.isBinderAlive()||
                    !TOKEN.equals(result.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no disponible");
        }catch(Exception e){
            close();throw e;
        }
        binder=result;
    }

    int read(int id)throws Exception{
        if(id!=1004&&id!=2101)throw new SecurityException("ID de lectura no permitido");
        if(!binder.isBinderAlive())throw new IllegalStateException("OEM desconectado");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            if(!binder.transact(5,data,reply,0))
                throw new IllegalStateException("Lectura OEM rechazada "+id);
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }

    void enableCabinRecirculation()throws Exception{
        if(!binder.isBinderAlive())throw new IllegalStateException("OEM desconectado");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(1004);
            data.writeInt(RecirculationPolicy.RECIRC_ON);
            data.writeString(app.getPackageName());
            if(!binder.transact(15,data,reply,0))
                throw new IllegalStateException("OEM rechazó comando 1004=1");
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }

    @Override public void close(){
        if(bound){
            bound=false;
            try{app.unbindService(connection);}catch(Exception ignored){}
        }
    }
}
