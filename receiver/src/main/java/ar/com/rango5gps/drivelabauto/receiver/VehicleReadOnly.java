package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Handler;
import android.os.Looper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Strictly three manual, allowlisted OEM read operations, no generic transaction UI. */
public final class VehicleReadOnly {
    public interface Callback { void done(Map<String,String> values); }
    public interface FuelCallback { void finished(FuelReading reading); }
    public static final class FuelReading {
        public final Float liters;
        public final String detail;
        public final long timestampMillis;
        public FuelReading(Float liters,String detail,long timestampMillis){
            this.liters=liters;this.detail=detail;this.timestampMillis=timestampMillis;
        }
        public boolean valid(){return liters!=null;}
    }

    private static final String VEHICLE_PKG="com.autolink.app.vehicleservice";
    private static final String VEHICLE_CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String VEHICLE_TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final String ALCAR_PKG="com.autolink.carservice";
    private static final String ALCAR_CLS="com.autolink.carservice.ALCarService";
    private static final String ALCAR_TOKEN="com.autolink.adapterinterface.car.IALCar";
    private static final String CLUSTER_PKG="com.autolink.clusterinteraction";
    private static final String CLUSTER_CLS="com.autolink.clusterinteraction.CiService";
    private static final String CLUSTER_TOKEN="com.autolink.adapterinterface.clusterinteraction.IClusterInteraction";

    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean running;

    public VehicleReadOnly(Context c){context=c.getApplicationContext();}

    public void readOnce(Callback callback){
        if(running){
            Map<String,String> message=new LinkedHashMap<>();
            message.put("estado","Otra lectura está en curso");
            main.post(()->callback.done(message));
            return;
        }
        running=true;
        new Thread(()->{
            Map<String,String> output=new LinkedHashMap<>();
            try{
                try{
                    withBinder(VEHICLE_PKG,VEHICLE_CLS,binder->{
                        output.put("SOC batería · VEHICLE 1042 / int",
                            vehicleInt(binder,1042));
                        output.put("Voltaje HV · VEHICLE 1063 / float",
                            vehicleFloat(binder,1063));
                    });
                }catch(Exception e){
                    String err=problem(e);
                    output.putIfAbsent("SOC batería · VEHICLE 1042 / int",err);
                    output.putIfAbsent("Voltaje HV · VEHICLE 1063 / float",err);
                }
                try{
                    withBinder(CLUSTER_PKG,CLUSTER_CLS,binder->
                        output.put("Tanque · CI 186 / float",clusterFuel(binder)));
                }catch(Exception e){
                    output.put("Tanque · CI 186 / float",problem(e));
                }
                output.put("validación","Lecturas crudas: comparar escala con menú OEM");
            }finally{
                running=false;
                main.post(()->callback.done(output));
            }
        },"T1-OEM-Pilot-ReadOnly").start();
    }

    /** A separate one-shot query: only CI 186, never requests the failed SOC/HV getters. */
    public void readFuelOnce(FuelCallback callback){
        if(running){
            main.post(()->callback.finished(new FuelReading(null,
                "Otra lectura está en curso",0)));
            return;
        }
        running=true;
        new Thread(()->{
            final java.util.concurrent.atomic.AtomicReference<FuelReading> result=
                new java.util.concurrent.atomic.AtomicReference<>();
            try{
                withBinder(CLUSTER_PKG,CLUSTER_CLS,binder->{
                    float raw=clusterFuelValue(binder);
                    if(!FuelReadingPolicy.usable(raw)){
                        result.set(new FuelReading(null,
                            "Respuesta OEM fuera de rango/ inválida: "+raw,0));
                    }else{
                        result.set(new FuelReading(raw,"Lectura OEM CI 186",System.currentTimeMillis()));
                    }
                });
            }catch(Exception error){
                result.set(new FuelReading(null,problem(error),0));
            }finally{
                running=false;
                FuelReading reading=result.get();
                if(reading==null)reading=new FuelReading(null,"Sin respuesta OEM",0);
                final FuelReading finalResult=reading;
                main.post(()->callback.finished(finalResult));
            }
        },"T1-Fuel-ReadOnce").start();
    }

    /** Three allowlisted raw float queries, once, no random property discovery. */
    public void readEnergyOnce(Callback callback){
        if(running){
            Map<String,String> values=new LinkedHashMap<>();
            values.put("estado","Otra consulta está en curso");
            main.post(()->callback.done(values));
            return;
        }
        running=true;
        new Thread(()->{
            final int[] ids=EnergySignalPolicy.ids();
            final String[] titles={"1042 · SOC interno","1062 · SOC tablero",
                "1064 · corriente batería HV"};
            final boolean[] available=new boolean[ids.length];
            final String[] diagnostics=new String[ids.length];
            for(int i=0;i<ids.length;i++)
                diagnostics[i]="VEHICLE: no se consultó";
            try{
                try{
                    withBinder(VEHICLE_PKG,VEHICLE_CLS,binder->{
                        ensureDescriptor(binder,VEHICLE_TOKEN);
                        for(int i=0;i<ids.length;i++){
                            try{
                                float raw=vehicleFloatValue(binder,ids[i]);
                                if(EnergySignalPolicy.usable(ids[i],raw)){
                                    diagnostics[i]="VEHICLE float = "+
                                        EnergySignalPolicy.display(raw);
                                    available[i]=true;
                                }else{
                                    diagnostics[i]="VEHICLE: valor no disponible ("+raw+")";
                                }
                            }catch(Exception ex){
                                diagnostics[i]="VEHICLE: "+problem(ex);
                            }
                        }
                    });
                }catch(Exception ex){
                    for(int i=0;i<ids.length;i++)
                        diagnostics[i]="VEHICLE: "+problem(ex);
                }
                // Fallback is the known ALCarLink.getFloat getter: transaction 9
                // with two integers (id, area=0). Never call unknown methods.
                try{
                    withBinder(ALCAR_PKG,ALCAR_CLS,binder->{
                        ensureDescriptor(binder,ALCAR_TOKEN);
                        for(int i=0;i<ids.length;i++){
                            if(available[i])continue;
                            try{
                                float raw=alCarFloatValue(binder,ids[i]);
                                if(EnergySignalPolicy.usable(ids[i],raw)){
                                    diagnostics[i]="ALCAR float = "+
                                        EnergySignalPolicy.display(raw)+
                                        " (VehicleService no disponible)";
                                    available[i]=true;
                                }else{
                                    diagnostics[i]+="; ALCAR: sin valor ("+raw+")";
                                }
                            }catch(Exception ex){
                                diagnostics[i]+="; ALCAR: "+problem(ex);
                            }
                        }
                    });
                }catch(Exception ex){
                    for(int i=0;i<ids.length;i++)
                        if(!available[i]) diagnostics[i]+="; ALCAR: "+problem(ex);
                }
            }finally{
                Map<String,String> result=new LinkedHashMap<>();
                for(int i=0;i<ids.length;i++)
                    result.put(titles[i],diagnostics[i]);
                result.put("Calidad","Datos crudos. Comparar con T1Launcher/tablero; " +
                    "no interpretar 1064 en amperios sin validar");
                result.put("Hora",new java.text.SimpleDateFormat(
                    "HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date()));
                running=false;
                main.post(()->callback.done(result));
            }
        },"T1-Energy-ReadOnce").start();
    }

    private static void ensureDescriptor(IBinder binder,String expected) throws Exception {
        String actual=binder.getInterfaceDescriptor();
        if(!expected.equals(actual))
            throw new SecurityException("Interfaz inesperada: "+actual);
    }

    private static float vehicleFloatValue(IBinder binder,int id) throws Exception {
        if(!EnergySignalPolicy.allowed(id))
            throw new IllegalArgumentException("Señal no autorizada");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(VEHICLE_TOKEN);
            data.writeInt(id);
            if(!binder.transact(6,data,reply,0))
                throw new java.io.IOException("getter float (6) rechazado");
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
    private static float alCarFloatValue(IBinder binder,int id) throws Exception {
        if(!EnergySignalPolicy.allowed(id))
            throw new IllegalArgumentException("Señal no autorizada");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(ALCAR_TOKEN);
            data.writeInt(id);
            data.writeInt(0); // ALCarLink.getFloat(id, area=0) from T1N134 DEX
            if(!binder.transact(9,data,reply,0))
                throw new java.io.IOException("getter float (9) rechazado");
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }

    private interface BoundAction { void run(IBinder binder) throws Exception; }
    private void withBinder(String pkg,String cls,BoundAction action) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<IBinder> connected=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                connected.set(b);latch.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){latch.countDown();}
            @Override public void onNullBinding(ComponentName n){latch.countDown();}
            @Override public void onBindingDied(ComponentName n){latch.countDown();}
        };
        // 0 means DO NOT start an inactive OEM process.
        boolean bound=context.bindService(
            new Intent().setComponent(new ComponentName(pkg,cls)),connection,0);
        if(!bound)throw new IllegalStateException("Servicio OEM no activo/no vinculable");
        try{
            if(!latch.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("Servicio sin respuesta en 4 s");
            IBinder binder=connected.get();
            if(binder==null)throw new IllegalStateException("Sin interfaz Binder");
            action.run(binder);
        }finally{
            try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}
        }
    }

    // Verified against static getter implementation in T1N134. No setters.
    private static String vehicleInt(IBinder binder,int id){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(VEHICLE_TOKEN);
            data.writeInt(id);
            if(!binder.transact(5,data,reply,0))return "SIN LECTURA: readInt rechazado";
            reply.readException();
            return "CRUDO int = "+reply.readInt();
        }catch(Exception e){return problem(e);}
        finally{data.recycle();reply.recycle();}
    }
    private static String vehicleFloat(IBinder binder,int id){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(VEHICLE_TOKEN);
            data.writeInt(id);
            if(!binder.transact(6,data,reply,0))return "SIN LECTURA: readFloat rechazado";
            reply.readException();
            return "CRUDO float = "+reply.readFloat();
        }catch(Exception e){return problem(e);}
        finally{data.recycle();reply.recycle();}
    }
    private static float clusterFuelValue(IBinder binder) throws Exception {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(CLUSTER_TOKEN);
            // Validated CiLink read-only getter; never send arbitrary IDs.
            if(!binder.transact(186,data,reply,0))
                throw new java.io.IOException("CI 186 rechazado");
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
    private static String clusterFuel(IBinder binder){
        try{return "CRUDO float = "+clusterFuelValue(binder);}
        catch(Exception error){return problem(error);}
    }
    private static String problem(Exception e){
        String msg=e.getMessage();
        if(msg!=null&&msg.length()>150)msg=msg.substring(0,150);
        return "SIN LECTURA: "+e.getClass().getSimpleName()+
            (msg==null?"":(" · "+msg));
    }
}
