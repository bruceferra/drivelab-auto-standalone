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
 * Minimal vehicle Binder gateway. All IDs and values allowlisted.
 * No 2003 write, no windows, no AVAS, no access to ReceiverService.
 */
final class MirrorAutomationOem implements AutoCloseable{
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    private final ServiceConnection conn;
    private IBinder binder;
    private boolean bound;
    MirrorAutomationOem(Context context)throws Exception{
        app=context.getApplicationContext();
        CountDownLatch connected=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                remote.set(null);
            }
            @Override public void onNullBinding(ComponentName n){connected.countDown();}
            @Override public void onBindingDied(ComponentName n){
                remote.set(null);connected.countDown();
            }
        };
        bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),conn,0);
        if(!bound)throw new IllegalStateException("VehicleService sin enlace");
        try{
            if(!connected.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("VehicleService no responde");
            binder=remote.get();
            if(binder==null||!binder.isBinderAlive()||
                    !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz vehículo incorrecta");
        }catch(Exception ex){close();throw ex;}
    }

    int gear()throws Exception{return readInt(1033);}
    int readInt(int id)throws Exception{
        if(id!=1033 && id!=1089 && id!=2068 && id!=2069)
            throw new SecurityException("Getter ID desconocido");
        if(binder==null||!binder.isBinderAlive())throw new IllegalStateException("OEM desconectado");
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(id);
            if(!binder.transact(5,d,r,0))throw new IllegalStateException("readInt rechazado");
            r.readException();return r.readInt();
        }finally{d.recycle();r.recycle();}
    }
    double speedKmh()throws Exception{
        try{
            float f=readFloat2148();
            if(Float.isFinite(f)&&f>=0&&f<250)return f;
        }catch(Exception ignored){}
        int s=readInt(1089);
        if(s<0||s>250)throw new IllegalStateException("Velocidad fuera de rango");
        return s;
    }
    private float readFloat2148()throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(2148);
            if(!binder.transact(6,d,r,0))
                throw new IllegalStateException("float OEM rechazado");
            r.readException();return r.readFloat();
        }finally{d.recycle();r.recycle();}
    }
    void write(int id,int value)throws Exception{
        if((id!=2068&&id!=2069)||(value<0||value>2))
            throw new SecurityException("Setter de espejo no permitido");
        if(binder==null||!binder.isBinderAlive())
            throw new IllegalStateException("OEM desconectado");
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(id);
            d.writeInt(value);d.writeString(app.getPackageName());
            if(!binder.transact(15,d,r,0))
                throw new IllegalStateException("OEM rechazó "+id+"="+value);
            r.readException();
        }finally{d.recycle();r.recycle();}
    }
    @Override public void close(){
        if(bound){bound=false;try{app.unbindService(conn);}catch(Exception ignored){}}
    }
}
