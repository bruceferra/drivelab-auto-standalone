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
 * Explicit manual toggle of the existing OEM pedestrian-warning setting 2074.
 * No boot receiver, no background automation and no writes on startup.
 * The alert is safety-relevant: operator must confirm the vehicle is parked;
 * the app cannot verify gear or speed through this interface.
 */
public final class PedestrianAlertControl {
    public interface Callback{void done(Result result);}
    public static final class Result {
        public final Integer before,after;
        public final boolean written;
        public final String status;
        Result(Integer before,Integer after,boolean written,String status){
            this.before=before;this.after=after;this.written=written;this.status=status;
        }
        public String summary(){
            return status+" · 2074 antes="+(before==null?"?":before)+
                " después="+(after==null?"?":after);
        }
    }
    private static final int ID=2074,ON=1,OFF=0,COOLDOWN_MS=12000;
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile boolean foreground=false;
    private volatile long lastAttemptAt=0;
    public PedestrianAlertControl(Context context){app=context.getApplicationContext();}
    public void setForeground(boolean value){foreground=value;}
    public boolean busy(){return busy.get();}
    public boolean coolingDown(){
        long t=lastAttemptAt;
        return t!=0&&SystemClock.elapsedRealtime()-t<COOLDOWN_MS;
    }
    public boolean toggle(Callback callback){
        if(!foreground||coolingDown()||!busy.compareAndSet(false,true))return false;
        new Thread(()->{
            Integer before=null,after=null;
            boolean written=false,bound=false;
            ServiceConnection connection=null;
            String status="No ejecutado";
            try{
                CountDownLatch latch=new CountDownLatch(1);
                AtomicReference<IBinder> ref=new AtomicReference<>();
                connection=new ServiceConnection(){
                    @Override public void onServiceConnected(ComponentName c,IBinder b){
                        ref.set(b);latch.countDown();
                    }
                    @Override public void onServiceDisconnected(ComponentName c){}
                    @Override public void onNullBinding(ComponentName c){latch.countDown();}
                    @Override public void onBindingDied(ComponentName c){}
                };
                bound=app.bindService(new Intent().setComponent(
                    new ComponentName(PKG,CLS)),connection,0);
                if(!bound||!latch.await(4,TimeUnit.SECONDS))
                    throw new IllegalStateException("Servicio OEM no disponible");
                IBinder b=ref.get();
                if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))
                    throw new SecurityException("Interfaz OEM inesperada");
                before=read(b);
                if(before==null)
                    throw new IllegalStateException("Lectura 2074 no es 0 ni 1");
                if(!foreground||coolingDown())
                    throw new IllegalStateException("Aplicación no activa o intervalo de seguridad");
                // Toggle only: current ON -> OFF, current OFF -> ON.
                final int command=before==ON?OFF:ON;
                lastAttemptAt=SystemClock.elapsedRealtime();
                Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                try{
                    data.writeInterfaceToken(TOKEN);
                    data.writeInt(ID);data.writeInt(command);
                    data.writeString(app.getPackageName());
                    if(!b.transact(15,data,reply,0))
                        throw new IllegalStateException("Comando rechazado por OEM");
                    reply.readException();
                    written=true;
                }finally{data.recycle();reply.recycle();}
                SystemClock.sleep(600);
                after=read(b);
                status=(after!=null&&after==command?
                    "Alerta "+(after==ON?"ACTIVADA":"DESACTIVADA")+
                    " · lectura OEM confirmada":
                    "Orden enviada; estado final NO confirmado");
            }catch(Exception e){
                status=(written?"Orden enviada; ":"SIN ORDEN · ")+
                    e.getClass().getSimpleName()+": "+e.getMessage();
            }finally{
                if(bound&&connection!=null)
                    try{app.unbindService(connection);}catch(Exception ignored){}
                busy.set(false);
                Result result=new Result(before,after,written,status);
                main.post(()->callback.done(result));
            }
        },"T1-Pedestrian-ManualToggle").start();
        return true;
    }
    private static Integer read(IBinder b){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(ID);
            if(!b.transact(5,data,reply,0))return null;
            reply.readException();
            int value=reply.readInt();
            return value==ON||value==OFF?value:null;
        }catch(Exception e){return null;}
        finally{data.recycle();reply.recycle();}
    }
}
