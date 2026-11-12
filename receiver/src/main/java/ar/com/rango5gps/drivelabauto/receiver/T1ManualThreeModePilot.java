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
 * Explicit one-shot manual mode selector. 1043=2 HEV was physically
 * confirmed ON THIS VEHICLE while parked by the owner. Reverse transitions
 * 1043=1 EV and 1043=3 EV MAX are still UNVALIDATED.
 *
 * NEVER callable from GPS, route detection, boot, or background services.
 * All writes require stable P, OEM speed exactly zero, 2 fresh reads, an
 * active Activity and an independent user confirmation in the caller.
 * Changing modes through Jetour's certified OEM UI in motion is NOT proof
 * this third-party Binder transaction is safe to send while moving.
 */
final class T1ManualThreeModePilot {
    interface Callback { void complete(String result); }
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context app;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    T1ManualThreeModePilot(Context c){app=c.getApplicationContext();}
    boolean busy(){return busy.get();}
    boolean request(int target,BooleanSupplier screenVisible,Callback cb){
        if(target!=1&&target!=2&&target!=3)return false;
        if(!busy.compareAndSet(false,true))return false;
        T1OemDiagnosticLog.begin(app,
            "Control manual triple · objetivo 1043="+target+
            ". Sin GPS, únicamente vehículo detenido en P.");
        new Thread(()->{
            String result;
            try{result=attempt(target,screenVisible);}
            catch(Exception e){
                String detail=e.getClass().getSimpleName()+
                    (e.getMessage()==null?"":" · "+e.getMessage());
                T1OemDiagnosticLog.add(app,"ERROR_TRIPLE",detail);
                result="NO CONFIRMADO · "+detail+
                    ". No reiterar a ciegas. Consultar modo OEM.";
            }
            T1OemDiagnosticLog.add(app,"FINAL_TRIPLE",result);
            busy.set(false);
            cb.complete(result);
        },"T1-Manual-Three-Modes").start();
        return true;
    }
    private String attempt(int target,BooleanSupplier visible)throws Exception{
        if(!visible.getAsBoolean())
            throw new IllegalStateException("Cockpit fuera de primer plano");
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> link=new AtomicReference<>();
        ServiceConnection c=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                link.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){link.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){link.set(null);}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),c,0);
        T1OemDiagnosticLog.add(app,"BIND_TRIPLE","bound="+bound);
        if(!bound)throw new IllegalStateException("VehicleService no responde");
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Sin Binder OEM");
            IBinder b=link.get();
            if(b==null||!b.isBinderAlive()||
                    !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no reconocida");
            int start=readInt(b,1043);
            checkParked(b,start,target,"PRIMERA_LECTURA");
            SystemClock.sleep(420);
            if(!visible.getAsBoolean()||!b.isBinderAlive())
                throw new IllegalStateException("Cockpit perdió el foco");
            int still=readInt(b,1043);
            if(still!=start)
                throw new IllegalStateException(
                    "Modo cambió durante comprobación: "+start+" a "+still);
            checkParked(b,still,target,"SEGUNDA_LECTURA");
            if(!visible.getAsBoolean())throw new SecurityException("Sin foco");
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(TOKEN);
                data.writeInt(1043);data.writeInt(target);
                data.writeString(app.getPackageName());
                T1OemDiagnosticLog.add(app,"ESCRITURA_TRIPLE",
                    "Binder transact(15): 1043="+target+", una sola vez");
                if(!b.transact(15,data,reply,0))
                    throw new IllegalStateException("Setter 1043 no implementado");
                reply.readException();
            }finally{data.recycle();reply.recycle();}
            long limit=SystemClock.elapsedRealtime()+3500L;
            while(SystemClock.elapsedRealtime()<limit&&
                    visible.getAsBoolean()&&b.isBinderAlive()){
                SystemClock.sleep(320);
                int actual=readInt(b,1043);
                T1OemDiagnosticLog.add(app,"READBACK_1043",
                    "esperado="+target+", actual="+actual);
                if(actual==target)
                    return "LECTURA CONFIRMADA: 1043="+target+
                        " ("+T1ManualHevPolicy.currentMode(target)+
                        "). Confirmá visualmente en la pantalla original.";
            }
            return "LA ORDEN SE ENVIÓ PERO 1043 NO CONFIRMÓ "+
                T1ManualHevPolicy.currentMode(target)+
                ". No reenviar sin revisar el log.";
        }finally{try{app.unbindService(c);}catch(Exception ignored){}}
    }
    private void checkParked(IBinder b,int mode,int target,String stage)
            throws Exception{
        int gear=readInt(b,1033);
        double speed;
        try{speed=readFloat(b,2148);}
        catch(Exception e){speed=readInt(b,1089);}
        T1OemDiagnosticLog.add(app,stage,
            "1033="+gear+"; velocidad="+speed+"; 1043="+mode+
            "; objetivo="+target);
        if(gear!=1||!Double.isFinite(speed)||speed<0||speed>0.001)
            throw new IllegalStateException(
                "Bloqueado: solo P y 0,00 km/h. Lecturas "+
                "1033="+gear+", velocidad="+speed);
        if(mode!=1&&mode!=2&&mode!=3)
            throw new IllegalStateException(
                "Modo actual 1043="+mode+" no validado");
        if(mode==target)throw new IllegalStateException(
            "Ya está en "+T1ManualHevPolicy.currentMode(target)+
            "; ninguna escritura necesaria");
    }
    private static int readInt(IBinder b,int id)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!b.transact(5,data,reply,0))
                throw new IllegalStateException("Getter "+id+" no disponible");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    private static float readFloat(IBinder b,int id)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);
            if(!b.transact(6,data,reply,0))
                throw new IllegalStateException("Getter float "+id+" no disponible");
            reply.readException();return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
}
