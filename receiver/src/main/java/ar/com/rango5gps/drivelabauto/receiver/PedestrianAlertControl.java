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
 * One-tap MANUAL control for OEM 2074 pedestrian acoustic alert.
 * This class never runs on startup/background or issues automatic writes.
 * The user-requested toggle reads current OEM state, requests the opposite
 * state once, then verifies the readback. Any OEM-side refusal is respected.
 * No fabricated application-side P/0 km/h interlock or 12s cooldown.
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
    private static final int ID=2074,ON=1,OFF=0,COOLDOWN_MS=350;
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
    /** Read-only OEM 2074 inspection. Never toggles the warning sound,
     * and shares BUSY with toggle so a poll never overlaps a write.
     */
    public boolean readState(Callback callback){
        if(!foreground||!busy.compareAndSet(false,true))return false;
        new Thread(()->{
            boolean bound=false;
            ServiceConnection connection=null;
            Integer state=null;
            String status;
            try{
                CountDownLatch latch=new CountDownLatch(1);
                AtomicReference<IBinder> remote=new AtomicReference<>();
                connection=new ServiceConnection(){
                    @Override public void onServiceConnected(ComponentName c,IBinder b){
                        remote.set(b);latch.countDown();
                    }
                    @Override public void onServiceDisconnected(ComponentName c){}
                    @Override public void onNullBinding(ComponentName c){latch.countDown();}
                    @Override public void onBindingDied(ComponentName c){}
                };
                bound=app.bindService(new Intent().setComponent(
                    new ComponentName(PKG,CLS)),connection,0);
                if(!bound||!latch.await(4,TimeUnit.SECONDS))
                    throw new IllegalStateException("Sin enlace OEM 2074");
                IBinder binder=remote.get();
                if(binder==null||!TOKEN.equals(binder.getInterfaceDescriptor()))
                    throw new SecurityException("Interfaz OEM inesperada");
                state=read(binder);
                status=state==null?"2074: SIN LECTURA":
                    "2074: "+(state==ON?"PEATÓN ON":"PEATÓN OFF")+
                    " · lectura OEM";
            }catch(Exception e){
                status="2074 SIN LECTURA: "+e.getClass().getSimpleName();
            }finally{
                if(bound&&connection!=null)
                    try{app.unbindService(connection);}catch(Exception ignored){}
                busy.set(false);
            }
            final Result result=new Result(state,state,false,status);
            main.post(()->callback.done(result));
        },"T1-Pedestrian-ReadOnly-2074").start();
        return true;
    }
    /** Explicit on-screen manual tap; never automatic or a persistent guard.
     * No gear/speed reads are required for the manual OEM setting.
     * The vehicle's original control service remains final authority.
     */
    public boolean toggle(Callback callback){
        if(!foreground||coolingDown()||!busy.compareAndSet(false,true))
            return false;
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
                    @Override public void onNullBinding(ComponentName c){
                        latch.countDown();
                    }
                    @Override public void onBindingDied(ComponentName c){}
                };
                bound=app.bindService(new Intent().setComponent(
                    new ComponentName(PKG,CLS)),connection,0);
                if(!bound||!latch.await(4,TimeUnit.SECONDS))
                    throw new IllegalStateException(
                        "Servicio OEM no disponible");
                IBinder b=ref.get();
                if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))
                    throw new SecurityException("Interfaz OEM inesperada");
                before=read(b);
                if(before==null)
                    throw new IllegalStateException(
                        "No se pudo leer 2074 (estado ON/OFF)");
                if(!foreground)
                    throw new IllegalStateException(
                        "Pantalla de control no activa");
                int requested=before==ON?OFF:ON;
                lastAttemptAt=SystemClock.elapsedRealtime();
                Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                try{
                    data.writeInterfaceToken(TOKEN);
                    data.writeInt(ID);data.writeInt(requested);
                    data.writeString(app.getPackageName());
                    if(!b.transact(15,data,reply,0))
                        throw new IllegalStateException(
                            "Servicio OEM rechazó la orden");
                    reply.readException();
                    written=true;
                }finally{
                    data.recycle();reply.recycle();
                }
                // Some T1 units delay 2074 getter updates after setter.
                // Poll READ-ONLY for up to 1.5 seconds; never send a second
                // toggle based on a stale value or a "setter returned OK".
                long deadline=SystemClock.elapsedRealtime()+1500L;
                do{
                    SystemClock.sleep(180);
                    after=read(b);
                    if(after!=null&&after==requested)break;
                }while(foreground&&SystemClock.elapsedRealtime()<deadline);
                status=after!=null&&after==requested?
                    "Alerta peatonal "+(after==ON?"ACTIVADA":"DESACTIVADA")+
                        " · OEM confirmado":
                    "Orden enviada, cambio 2074 NO CONFIRMADO; "+
                    "verificar control original Jetour";
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
