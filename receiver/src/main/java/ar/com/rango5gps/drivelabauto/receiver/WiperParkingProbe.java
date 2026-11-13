package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One-tap, strictly read-only OEM investigation. No wiper/radar setters.
 *
 * Vehicle IDs recovered from the original ALVehicleService APK:
 * 2126 = AUTO wiper sensitivity (not manual motor sweep speed)
 * 2125 = rear wiper coordination; 2107 = parking radar warning-distance
 * bands (not a rear-only distance in metres); 2049 = parking radar switch.
 *
 * HAL integer IDs recovered from original ALCarService:
 * 557862926/927 = FRONT/REAR PDC OBJECT DISTANCE;
 * 557843300..303 = front zones; 557843296..299 = rear zones.
 * Scale/units unverified; NEVER render these raw integers as metres.
 */
final class WiperParkingProbe {
    interface Callback { void result(Map<String,String> values); }
    private static final String VEHICLE_PKG="com.autolink.app.vehicleservice";
    private static final String VEHICLE_CLS=
        "com.autolink.app.vehicleservice.VehicleService";
    private static final String VEHICLE_TOKEN=
        "com.autolink.app.vehicleservice.IVehicleControl";
    private static final String CAR_PKG="com.autolink.carservice";
    private static final String CAR_CLS="com.autolink.carservice.ALCarService";
    private static final String CAR_TOKEN="com.autolink.adapterinterface.car.IALCar";
    private static final int FRONT_DISTANCE=557862926;
    private static final int REAR_DISTANCE=557862927;
    // HAL candidates confirmed in the 2,117-entry T1N134 catalog.
    // Read endpoints only: NEVER call 557843862/557843558 writers.
    private static final int WIPER_MODE_READ=557843863;
    private static final int REAR_WIPER_READ=557843559;
    private static final int[] LIGHT_STATUS_IDS={
        289410560,  // HEADLIGHTS STATE - standard candidate
        289410561,  // HIGH BEAM LIGHTS STATE
        289410562,  // FOG LIGHTS STATE
        289410563,  // HAZARD LIGHTS STATE
        289410817,  // CABIN LIGHTS STATE
        557844233,  // VC.DRL STS - vendor candidate
        557856517   // CLUSTER DRL STS - vendor candidate
    };
    private static final String[] LIGHT_STATUS_LABELS={
        "289410560 · faros exteriores (AOSP candidato)",
        "289410561 · luces altas (AOSP candidato)",
        "289410562 · antinieblas (AOSP candidato)",
        "289410563 · balizas (AOSP candidato)",
        "289410817 · luz interior global (AOSP candidato)",
        "557844233 · DRL estado VC (vendor candidato)",
        "557856517 · DRL estado CLUSTER (vendor candidato)"
    };
    // Zone numbers are HAL candidates: verify actual wiring/scale in T1.
    private static final int[] FRONT_ZONES={
        557843300,557843301,557843302,557843303
    };
    private static final String[] FRONT_NAMES={
        "DELANTERO ZONA 1","DELANTERO ZONA 2",
        "DELANTERO ZONA 3","DELANTERO ZONA 4"
    };
    private static final int[] REAR_ZONES={
        557843296,557843297,557843298,557843299
    };
    private static final String[] REAR_NAMES={
        "IZQ TRASERO","DER INTERMEDIO TRASERO",
        "IZQ INTERMEDIO TRASERO","DER TRASERO"
    };
    private final Context app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AtomicBoolean running=new AtomicBoolean(false);
    WiperParkingProbe(Context context){app=context.getApplicationContext();}
    boolean busy(){return running.get();}

    private interface BoundAction { void read(IBinder binder)throws Exception; }
    private static void query(Context context,String pkg,String cls,
                              String token,BoundAction action)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);latch.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                remote.set(null);latch.countDown();
            }
            @Override public void onNullBinding(ComponentName n){latch.countDown();}
            @Override public void onBindingDied(ComponentName n){latch.countDown();}
        };
        boolean bound=context.bindService(new Intent().setComponent(
            new ComponentName(pkg,cls)),conn,0);
        if(!bound)throw new IllegalStateException("Servicio sin enlace");
        try{
            if(!latch.await(3500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Tiempo de espera OEM agotado");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
                    !token.equals(b.getInterfaceDescriptor()))
                throw new IllegalStateException("Interfaz OEM no disponible");
            action.read(b);
        }finally{
            try{context.unbindService(conn);}catch(Exception ignored){}
        }
    }
    // VehicleService readInt(id): Binder transaction 5.
    private static int vehicleInt(IBinder b,int id)throws Exception{
        if(id!=2126&&id!=2125&&id!=2107&&id!=2049)
            throw new SecurityException("ID no autorizado");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(VEHICLE_TOKEN);data.writeInt(id);
            if(!b.transact(5,data,reply,0))
                throw new IllegalStateException("Getter OEM rechazado");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    // Original IALCar$Stub$Proxy.getIntProperty(id, area=0) uses transact(2).
    // Never sends setIntProperty (a different Binder method).
    private static int carInt(IBinder b,int id)throws Exception{
        boolean allowed=id==REAR_DISTANCE||id==FRONT_DISTANCE;
        for(int zone:REAR_ZONES)allowed|=id==zone;
        for(int zone:FRONT_ZONES)allowed|=id==zone;
        allowed|=id==WIPER_MODE_READ||id==REAR_WIPER_READ;
        for(int signal:LIGHT_STATUS_IDS)allowed|=id==signal;
        if(!allowed)throw new SecurityException("HAL ID no autorizado");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(CAR_TOKEN);
            data.writeInt(id);data.writeInt(0);
            if(!b.transact(2,data,reply,0))
                throw new IllegalStateException("Getter HAL rechazado");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    private static String value(int raw){
        return raw==Integer.MIN_VALUE?"no disponible":Integer.toString(raw);
    }
    private static String error(Exception ex){
        return "SIN LECTURA ("+ex.getClass().getSimpleName()+")";
    }
    boolean readOnce(Callback cb){
        if(!running.compareAndSet(false,true))return false;
        new Thread(()->{
            LinkedHashMap<String,String> data=new LinkedHashMap<>();
            try{
                try{
                    query(app,VEHICLE_PKG,VEHICLE_CLS,VEHICLE_TOKEN,b->{
                        int[] ids={2126,2125,2107,2049};
                        String[] names={
                            "2126 · sensibilidad barrido AUTO",
                            "2125 · limpiaparabrisas trasero",
                            "2107 · tramo de distancia de aviso radar",
                            "2049 · radar estacionamiento activado"
                        };
                        for(int i=0;i<ids.length;i++){
                            try{data.put(names[i],value(vehicleInt(b,ids[i])));}
                            catch(Exception e){data.put(names[i],error(e));}
                        }
                    });
                }catch(Exception e){data.put("VehicleService",error(e));}
                try{
                    query(app,CAR_PKG,CAR_CLS,CAR_TOKEN,b->{
                        // Two HAL read endpoints NOT used in prior 18-step wizard.
                        try{
                            data.put("557843863 · modo limpia delantero (HAL LEE)",
                                value(carInt(b,WIPER_MODE_READ)));
                        }catch(Exception e){
                            data.put("557843863 · modo limpia delantero (HAL LEE)",error(e));
                        }
                        try{
                            data.put("557843559 · limpia trasero (HAL LEE)",
                                value(carInt(b,REAR_WIPER_READ)));
                        }catch(Exception e){
                            data.put("557843559 · limpia trasero (HAL LEE)",error(e));
                        }
                        for(int k=0;k<LIGHT_STATUS_IDS.length;k++){
                            try{
                                data.put(LIGHT_STATUS_LABELS[k],
                                    value(carInt(b,LIGHT_STATUS_IDS[k])));
                            }catch(Exception e){
                                data.put(LIGHT_STATUS_LABELS[k],error(e));
                            }
                        }
                        try{
                            data.put("PDC DELANTERO 557862926 · valor crudo",
                                value(carInt(b,FRONT_DISTANCE)));
                        }catch(Exception e){
                            data.put("PDC DELANTERO 557862926",error(e));
                        }
                        for(int i=0;i<FRONT_ZONES.length;i++){
                            try{
                                data.put(FRONT_NAMES[i]+" · "+FRONT_ZONES[i],
                                    value(carInt(b,FRONT_ZONES[i])));
                            }catch(Exception e){
                                data.put(FRONT_NAMES[i]+" · "+FRONT_ZONES[i],error(e));
                            }
                        }
                        try{
                            data.put("PDC TRASERO 557862927 · valor crudo",
                                value(carInt(b,REAR_DISTANCE)));
                        }catch(Exception e){
                            data.put("PDC TRASERO 557862927",error(e));
                        }
                        for(int i=0;i<REAR_ZONES.length;i++){
                            try{
                                data.put(REAR_NAMES[i]+" · "+REAR_ZONES[i],
                                    value(carInt(b,REAR_ZONES[i])));
                            }catch(Exception e){
                                data.put(REAR_NAMES[i]+" · "+REAR_ZONES[i],error(e));
                            }
                        }
                    });
                }catch(Exception e){data.put("ALCarService",error(e));}
                data.put("DISTANCIA EN METROS",
                    "— No calibrada. Comparar valores crudos con distancia física medida.");
                data.put("AJUSTES",
                    "Solo lectura, sin setters ni cambios de marcha. HAL wiper / " +
                    "luces son candidatos: si devuelven -1, 0 constante o error " +
                    "puede ser permiso, ECU distinta o propiedad no implementada. " +
                    "Faros/DRL NO identifican necesariamente luces traseras. " +
                    "No se enviaron órdenes al limpia, luces ni radar.");
            }finally{
                running.set(false);
                handler.post(()->cb.result(data));
            }
        },"T1-Wiper-Front-Rear-PDC-ReadOnly").start();
        return true;
    }
}
