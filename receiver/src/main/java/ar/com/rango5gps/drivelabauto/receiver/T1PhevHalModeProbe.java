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
 * The user's 2117-ID OEM catalog identifies HAL 557867211 as the PHEV
 * power-mode READ and 557843642 as the matching candidate WRITE.
 * This probe uses ONLY the read property via IALCar.getInt transaction 2.
 * It NEVER sends 557843642 or changes VehicleService 1043.
 */
final class T1PhevHalModeProbe {
    static final int HAL_PHEV_POWER_MODE_READ=557867211;
    static final int HAL_PHEV_POWER_MODE_WRITE_UNTESTED=557843642;
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";
    static String inspect(Context app,int vehicleMode){
        final CountDownLatch ready=new CountDownLatch(1);
        final AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder binder){
                remote.set(binder);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){remote.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){remote.set(null);}
        };
        boolean bound=false;
        try{
            Context c=app.getApplicationContext();
            bound=c.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),connection,0);
            if(!bound||!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService no responde");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
               !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz HAL no coincide");
            Parcel d=Parcel.obtain(),r=Parcel.obtain();
            try{
                d.writeInterfaceToken(TOKEN);
                d.writeInt(HAL_PHEV_POWER_MODE_READ);
                d.writeInt(0); // área global; por confirmar
                if(!b.transact(2,d,r,0))
                    throw new IllegalStateException("getter 557867211 rechazado");
                r.readException();
                int raw=r.readInt();
                return "HAL 557867211 = "+raw+
                    " · VEHICLE 1043 = "+vehicleMode+
                    " · "+(raw==vehicleMode?
                    "COINCIDEN (un snapshot; no prueba setter)":
                    "NO COINCIDEN; revisar escala, área y permisos")+
                    ". El candidato HAL de ESCRITURA 557843642 "+
                    "NO se ejecutó.";
            }finally{d.recycle();r.recycle();}
        }catch(Exception ex){
            return "HAL 557867211 SIN LECTURA VALIDADA: "+
                ex.getClass().getSimpleName()+
                (ex.getMessage()==null?"":" · "+ex.getMessage())+
                ". 557843642 NO se ejecutó.";
        }finally{
            if(bound)try{app.getApplicationContext().unbindService(connection);}
                catch(Exception ignored){}
        }
    }
    private T1PhevHalModeProbe(){}
}
