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
 * VehicleService setter transaction 15 for 1043=2 has been physically
 * confirmed by this Jetour owner while parked. In-motion behaviour and
 * the reverse EV/EV MAX transitions are NOT validated. A binder success
 * alone is not proof of transition; readback is required.
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
        return run(screenVisible,cb,false);
    }
    /** Read-only diagnostic of the actual 1033/2148/1043 values and Binder
     * interface. It NEVER issues transaction 15. */
    boolean inspect(BooleanSupplier screenVisible,Callback cb){
        return run(screenVisible,cb,true);
    }
    private boolean run(BooleanSupplier screenVisible,Callback cb,boolean inspect){
        if(!busy.compareAndSet(false,true))return false;
        T1OemDiagnosticLog.begin(app,inspect?
            "Diagnóstico EV/HEV SOLO LECTURA; no se transmiten comandos":
            "El usuario confirmó un único intento manual EV/EV MAX a HEV");
        new Thread(()->{
            String result;
            try{result=attempt(screenVisible,inspect);}
            catch(Exception error){
                String detail=error.getClass().getSimpleName()+
                    " · "+(error.getMessage()==null?"sin detalle":error.getMessage());
                T1OemDiagnosticLog.add(app,"ERROR",detail);
                result="NO SE CAMBIÓ: "+detail+
                    (inspect?" · diagnóstico solo lectura":" · revisar informe por etapas");
            }
            T1OemDiagnosticLog.add(app,"RESULTADO",result);
            busy.set(false);
            cb.complete(result);
        },inspect?"T1-HEV-Diagnose-ReadOnly":"T1-Manual-EV-to-HEV").start();
        return true;
    }
    private String attempt(BooleanSupplier visible,boolean inspect)throws Exception{
        if(!visible.getAsBoolean())throw new SecurityException("Pantalla ya no visible");
        T1OemDiagnosticLog.add(app,"PREPARACIÓN",
            "Activity visible. Fuente=VehicleService Binder con token IVehicleControl");
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
        T1OemDiagnosticLog.add(app,"BIND","Solicitando "+PKG+"/"+CLS);
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),connection,0);
        T1OemDiagnosticLog.add(app,"BIND_RESULT","bindService="+bound);
        if(!bound)throw new IllegalStateException("VehicleService no disponible");
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("VehicleService sin respuesta");
            IBinder b=binder.get();
            if(b==null||!b.isBinderAlive()||
               !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no coincide");
            T1OemDiagnosticLog.add(app,"INTERFAZ_OK",b.getInterfaceDescriptor());
            int mode=readInt(b,T1ManualHevPolicy.POWER_MODE_ID);
            T1OemDiagnosticLog.add(app,"1043_PRE_1","modo="+mode);
            if(inspect){
                VehiclePreflight readback=readPreflight(b,mode);
                T1OemDiagnosticLog.add(app,"SOLO_LECTURA",
                    "1033="+readback.gear+"; 2148/1089="+readback.speed+
                    " km/h; 1043="+mode+"; apto="+readback.canRequest);
                // Compare the actual PHEV-mode HAL read from the T1N134
                // catalog with the proven 4-digit VehicleService getter.
                // This is a separate getter, NOT a propulsion-mode write.
                String hal=T1PhevHalModeProbe.inspect(app,mode);
                T1OemDiagnosticLog.add(app,"HAL_557867211",hal);
                return "LECTURA OEM COMPLETADA: P/1033="+readback.gear+
                    ", velocidad="+readback.speed+" km/h, 1043="+mode+
                    " ("+T1ManualHevPolicy.currentMode(mode)+"). "+
                    "Comando NO enviado. "+
                    T1ManualHevPolicy.preflightReason(
                        readback.gear,readback.speed,mode,0)+
                    "\n\nCOMPARACIÓN HAL: "+hal;
            }
            checkStopped(b,mode,"CONTROL_1");
            SystemClock.sleep(350);
            if(!visible.getAsBoolean()||!b.isBinderAlive())
                throw new SecurityException("Pantalla cerrada o enlace perdido");
            // Second fresh read must still be in P, at 0 km/h, with EV/EV MAX.
            int confirmedMode=readInt(b,T1ManualHevPolicy.POWER_MODE_ID);
            T1OemDiagnosticLog.add(app,"1043_PRE_2","modo="+confirmedMode);
            checkStopped(b,confirmedMode,"CONTROL_2");
            T1OemDiagnosticLog.add(app,"ANTES_DE_ESCRIBIR",
                "P confirmada dos veces; preparando UNA única transacción 15");
            writeHev(b,visible);
            T1OemDiagnosticLog.add(app,"ESCRITURA_OK",
                "Transacción 15 respondió sin excepción; falta validar 1043 y tablero");
            long deadline=SystemClock.elapsedRealtime()+3500L;
            do{
                if(!visible.getAsBoolean())break;
                SystemClock.sleep(350);
                int value=readInt(b,T1ManualHevPolicy.POWER_MODE_ID);
                T1OemDiagnosticLog.add(app,"VERIFICACIÓN_1043","valor="+value);
                if(value==T1ManualHevPolicy.HEV)
                    return "CONFIRMADO · 1043=2 HEV. Verificá también la pantalla OEM.";
            }while(SystemClock.elapsedRealtime()<deadline);
            return "ORDEN ENVIADA SIN CONFIRMACIÓN de HEV. "+
                "No repetir a ciegas; verificá modo en la pantalla OEM.";
        }finally{
            try{app.unbindService(connection);}catch(Exception ignored){}
        }
    }
    private static final class VehiclePreflight {
        final int gear;final double speed;final boolean canRequest;
        VehiclePreflight(int gear,double speed,int mode){
            this.gear=gear;this.speed=speed;
            canRequest=T1ManualHevPolicy.canRequest(gear,speed,mode);
        }
    }
    private static VehiclePreflight readPreflight(IBinder b,int mode)throws Exception{
        int gear=readInt(b,1033);
        double speed;
        try{speed=readFloat(b,2148);}
        catch(Exception ex){speed=readInt(b,1089);}
        return new VehiclePreflight(gear,speed,mode);
    }
    private void checkStopped(IBinder b,int mode,String stage)throws Exception{
        VehiclePreflight check=readPreflight(b,mode);
        T1OemDiagnosticLog.add(app,stage,"1033="+check.gear+
            "; 2148/1089="+check.speed+" km/h; 1043="+mode+
            "; apto="+check.canRequest);
        if(!check.canRequest)
            throw new IllegalStateException(
                T1ManualHevPolicy.preflightReason(
                    check.gear,check.speed,mode,0)+
                " · Binder: 1033="+check.gear+
                ", velocidad="+check.speed+", 1043="+mode);
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
            T1OemDiagnosticLog.add(app,"TRANSACT_15",
                "INICIO: id 1043, valor 2 (HEV), paquete del Receiver");
            if(!b.transact(15,d,r,0))
                throw new IllegalStateException("Setter OEM 1043 no disponible");
            T1OemDiagnosticLog.add(app,"TRANSACT_15",
                "Binder aceptó transacción; leyendo excepción OEM");
            r.readException();
        }finally{d.recycle();r.recycle();}
    }
}
