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
 * Foreground-only, ONE SHORT PULSE mirror control via validated T1 OEM service.
 * NEVER used by BootReceiver/ReceiverService. No reverse auto-tilt.
 *
 * 1033: reverse-gear candidate (T1N134 uses 1 for P, 2 for R).
 * 2069: command from EspejoRService (1/2 = opposite motions, 0 = release).
 * 2148 float / 1089 int: candidate speed readbacks (units to validate).
 *
 * Both left and right motion physically confirmed by the user. Only use while the
 * vehicle is physically in P, stationary, on level ground and supervised.
 * Android cannot guarantee motor stop after a crash or a dead Binder link.
 */
public final class MirrorPulsePilot {
    public interface Callback { void done(String status); }
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final int GEAR_ID=1033, GEAR_P=1, RIGHT_ID=2069, LEFT_ID=2068;
    private static final int SPEED_FLOAT_ID=2148, SPEED_INT_ID=1089;
    // One manually triggered motion at a time. No auto reversing or sweep.
    public static final int SHORT_MS=120, SMOOTH_TEST_MS=500,
        CALIBRATE_MS=1000, LONG_TEST_MS=2000;
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private volatile boolean foreground=false;
    private volatile int activeMirrorId=RIGHT_ID;

    public MirrorPulsePilot(Context context){app=context.getApplicationContext();}
    public void setForeground(boolean enabled){
        foreground=enabled;
        if(!enabled)stop.set(true);
    }
    public boolean busy(){return busy.get();}
    public void cancel(){stop.set(true);}

    /** Legacy 120ms RIGHT command kept for existing callers. */
    public boolean pulse(int direction,Callback callback){
        return pulse(RIGHT_ID,direction,SHORT_MS,callback);
    }
    /** One user-confirmed, time-bounded command. Both mirror IDs physically validated. */
    public boolean pulse(int mirrorId,int direction,int durationMs,Callback callback){
        if(!foreground||(mirrorId!=RIGHT_ID&&mirrorId!=LEFT_ID)||
                (direction!=1&&direction!=2)||
                (durationMs!=SHORT_MS&&durationMs!=SMOOTH_TEST_MS&&
                 durationMs!=CALIBRATE_MS&&durationMs!=LONG_TEST_MS))return false;
        if(!busy.compareAndSet(false,true))return false;
        stop.set(false);
        activeMirrorId=mirrorId;
        new Thread(()->executePulse(mirrorId,direction,durationMs,callback),
            "T1-Mirror-OneTimedCommand").start();
        return true;
    }

    /** Read P/R/D raw gear and speed without any actuator write; foreground only. */
    public boolean readGearOnce(Callback callback){
        if(!foreground||!busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;
            String result;
            try{
                requireReady();
                c=connect();
                int gear=readInt(c.binder,GEAR_ID);
                double speed=readSpeed(c.binder);
                String interpretation=gear==1?"P candidata":gear==2?"R candidata":
                    "marcha no mapeada (posible D/N)";
                result="LECTURA OEM · 1033="+gear+" ("+interpretation+
                    ") · velocidad="+speed+
                    " (unidad candidata km/h). Sin orden de movimiento.";
            }catch(Exception e){
                result="SIN LECTURA OEM · "+e.getClass().getSimpleName()+
                    " · "+String.valueOf(e.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,result);
        },"T1-Mirror-Gear-ReadOnly").start();
        return true;
    }

    /**
     * Diagnostic only: distinguish actual readback of 2068/2069 from the
     * setter used to drive motors. Codes 0/1/2 are not treated as angle.
     */
    public boolean readMirrorStates(Callback callback){
        if(!foreground || !busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;
            String result;
            try{
                requireReady();
                c=connect();
                int gear=readInt(c.binder,GEAR_ID);
                double speed=readSpeed(c.binder);
                if(gear!=GEAR_P || !Double.isFinite(speed) || speed>0.5 || speed<0)
                    throw new IllegalStateException("Lectura habilitada solo en P y detenido");
                String left, right;
                try{left=String.valueOf(readInt(c.binder,LEFT_ID));}
                catch(Exception ex){left="NO DISPONIBLE ("+ex.getClass().getSimpleName()+")";}
                try{right=String.valueOf(readInt(c.binder,RIGHT_ID));}
                catch(Exception ex){right="NO DISPONIBLE ("+ex.getClass().getSimpleName()+")";}
                result="LECTURA OEM RAW con mando suelto: 2068 izquierdo="+left+
                    " · 2069 derecho="+right+
                    ". Comparar extremos 2/1 e intermedio 0; no implica ángulo real.";
            }catch(Exception ex){
                result="SIN LECTURA DE ESPEJOS · "+ex.getClass().getSimpleName()+
                    " · "+String.valueOf(ex.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,result);
        },"T1-Mirror-State-ReadOnly").start();
        return true;
    }

    /**
     * OEM-safe fold attempt via standard Android Automotive MIRROR_FOLD.
     * Only if the privileged permission and a supported Boolean property exist.
     * Does not use any guessed vendor setters.
     */
    public boolean requestFold(boolean folded,Callback callback){
        if(!foreground || !busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;
            String status;
            try{
                requireReady();
                c=connect();
                int gear=readInt(c.binder,GEAR_ID);
                double speed=readSpeed(c.binder);
                if(gear!=GEAR_P || !Double.isFinite(speed) || speed<0 || speed>0.5)
                    throw new IllegalStateException("Plegado permitido solo en P/velocidad 0");
                if(app.checkSelfPermission("android.car.permission.CONTROL_CAR_MIRRORS")!=
                        android.content.pm.PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("Permiso OEM privilegiado NO concedido; "+
                        "no se envió comando de plegado");
                requireReady();
                status=foldStandard(folded);
            }catch(Exception ex){
                status="PLEGADO NO EJECUTADO · "+ex.getClass().getSimpleName()+
                    " · "+String.valueOf(ex.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,status);
        },"T1-Mirror-Fold-Guarded").start();
        return true;
    }

    private String foldStandard(boolean fold)throws Exception{
        Class<?> carType=Class.forName("android.car.Car");
        Object car=carType.getMethod("createCar",Context.class).invoke(null,app);
        if(car==null)throw new IllegalStateException("CarService no accesible");
        try{
            boolean connected=false;
            try{connected=(Boolean)carType.getMethod("isConnected").invoke(car);}
            catch(NoSuchMethodException ignored){}
            if(!connected)carType.getMethod("connect").invoke(car);
            Object manager=carType.getMethod("getCarManager",String.class)
                .invoke(car,"property");
            if(manager==null)throw new IllegalStateException("CarPropertyManager ausente");
            final int id=287312709; // standard Android Automotive MIRROR_FOLD
            Object configs=manager.getClass().getMethod("getPropertyList").invoke(manager);
            boolean supported=false;
            if(configs instanceof java.util.List){
                for(Object item:(java.util.List<?>)configs){
                    int property=(Integer)item.getClass().getMethod("getPropertyId")
                        .invoke(item);
                    if(property!=id)continue;
                    Class<?> type=(Class<?>)item.getClass().getMethod("getPropertyType")
                        .invoke(item);
                    if(type!=Boolean.class)
                        throw new SecurityException("Tipo MIRROR_FOLD no Boolean");
                    supported=true;
                    break;
                }
            }
            if(!supported)
                throw new SecurityException("MIRROR_FOLD 287312709 no está "+
                    "en el catálogo HAL accesible; no se escribió nada");
            java.lang.reflect.Method getter=manager.getClass().getMethod(
                "getProperty",Class.class,int.class,int.class);
            Object before=getter.invoke(manager,Boolean.class,id,0);
            if(before==null)throw new SecurityException("Sin estado previo; cancelado");
            Object raw=before.getClass().getMethod("getValue").invoke(before);
            if(!(raw instanceof Boolean))
                throw new SecurityException("Estado anterior desconocido; cancelado");
            if(((Boolean)raw).booleanValue()==fold)
                return fold?"Ya figura plegado, no se escribió":"Ya figura desplegado, no se escribió";
            requireReady();
            manager.getClass().getMethod(
                "setProperty",Class.class,int.class,int.class,Object.class)
                .invoke(manager,Boolean.class,id,0,Boolean.valueOf(fold));
            Object after=getter.invoke(manager,Boolean.class,id,0);
            Object readback=after==null?null:after.getClass()
                .getMethod("getValue").invoke(after);
            return "MIRROR_FOLD solicitado: "+(fold?"PLEGAR":"DESPLEGAR")+
                ". Estado HAL="+String.valueOf(readback)+
                ". Verificar visualmente: confirmación HAL no prueba movimiento.";
        }finally{
            try{carType.getMethod("disconnect").invoke(car);}catch(Exception ignored){}
        }
    }

    /**
     * First-party OEM property 2003 observed by the driver: 0 when folded,
     * 1 when unfolded. Diagnostic readback does not establish write permission.
     * P+stationary and foreground are required even for a read here.
     */
    public boolean readFold2003(Callback callback){
        if(!foreground||!busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;String status;
            try{
                requireReady();
                c=connect();
                requireSlowManeuver(c.binder);
                int value=readInt(c.binder,2003);
                status="LECTURA 2003="+value+" · "+
                    (value==0?"candidato PLEGADO":value==1?
                    "candidato DESPLEGADO":"NO IDENTIFICADO")+
                    " (comprobar visualmente)";
            }catch(Exception e){
                status="LECTURA 2003 NO DISPONIBLE · "+e.getClass().getSimpleName()+
                    " · "+String.valueOf(e.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,status);
        },"T1-Fold2003-ReadOnly").start();
        return true;
    }

    /**
     * Experimental manually initiated write. 2003 is a *latching fold state*,
     * not a timed 2068/2069 direction: NEVER send a release=0 after unfolded=1,
     * because 0 is the driver's observed FOLD state.
     */
    public boolean testFold2003(boolean unfold,Callback callback){
        if(!foreground||!busy.compareAndSet(false,true))return false;
        stop.set(false);
        new Thread(()->{
            Connection c=null;String status;int target=unfold?1:0;
            try{
                requireReady();
                c=connect();
                requireSlowManeuver(c.binder);
                int beforeState=readInt(c.binder,2003);
                if(beforeState!=0&&beforeState!=1)
                    throw new IllegalStateException("Estado 2003 desconocido: "+
                        beforeState+"; no se escribió");
                if(beforeState==target){
                    status="2003 ya indica "+target+
                        " · no se repitió orden (verificar visualmente)";
                }else{
                    requireReady();
                    // Exactly one write; no repeated/timed pulse and no reset to 0.
                    writeFold2003(c.binder,target);
                    SystemClock.sleep(250);
                    int afterState=readInt(c.binder,2003);
                    status="ENSAYO OEM 2003 "+beforeState+"→"+target+
                        " · lectura posterior="+afterState+
                        (afterState==target?" · lectura coherente":
                        " · CAMBIO NO CONFIRMADO")+
                        ". Verificar movimiento físico antes de reutilizar.";
                }
            }catch(Exception ex){
                status="PLEGADO 2003 NO EJECUTADO/NO CONFIRMADO · "+
                    ex.getClass().getSimpleName()+" · "+String.valueOf(ex.getMessage());
            }finally{
                if(c!=null)c.close();
                busy.set(false);
            }
            emit(callback,status);
        },"T1-Fold2003-SingleTest").start();
        return true;
    }

    /**
     * Folding 2003 is used during narrow entrance manoeuvres, so P is not
     * required. Keep an explicit low-speed guard; folded mirrors must not
     * be treated as suitable for ordinary road driving.
     */
    private void requireSlowManeuver(IBinder binder)throws Exception{
        int gear=readInt(binder,GEAR_ID);
        if(gear<1||gear>4)
            throw new IllegalStateException("Marcha OEM no reconocida: "+gear);
        double speed=readSpeed(binder);
        if(!Double.isFinite(speed)||speed<0||speed>5)
            throw new IllegalStateException(
                "Plegado solo detenido o maniobrando a 5 km/h o menos; velocidad="+speed);
    }

    private void writeFold2003(IBinder binder,int target)throws Exception{
        if(target!=0&&target!=1)
            throw new SecurityException("Estado de plegado no permitido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(2003);
            data.writeInt(target);
            data.writeString(app.getPackageName());
            if(!binder.transact(15,data,reply,0))
                throw new IllegalStateException("OEM rechazó setInt(2003,"+target+")");
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }

    private void emit(Callback callback,String status){
        main.post(()->callback.done(status));
    }

    private void executePulse(int mirrorId,int direction,int durationMs,Callback callback){
        Connection c=null;
        boolean motionAttempted=false,releaseOk=false;
        String status="Sin operación";
        try{
            requireReady();
            c=connect();
            int gear=readInt(c.binder,GEAR_ID);
            if(gear!=GEAR_P)
                throw new IllegalStateException("Marcha 1033="+gear+
                    "; se exige P (=1 en APK) para esta prueba");
            double speed=readSpeed(c.binder);
            if(!Double.isFinite(speed)||speed<0||speed>0.5)
                throw new IllegalStateException("Velocidad no confirmada en 0 km/h: "+speed);
            requireReady();
            // A release command BEFORE motion is a conservative neutralization.
            write(c.binder,mirrorId,0);
            requireReady();
            // Never repeat this command; STOP in finally even on exceptions.
            motionAttempted=true;
            write(c.binder,mirrorId,direction);
            long until=SystemClock.elapsedRealtime()+durationMs;
            while(SystemClock.elapsedRealtime()<until && !stop.get() && foreground){
                SystemClock.sleep(10);
            }
            status="Se envió una orden experimental "+mirrorId+"="+direction+
                " durante hasta "+durationMs+" ms, seguida de liberación 0. "+
                "Comprobar físicamente lado, sentido y parada.";
        }catch(Exception ex){
            String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            status="NO COMPLETADO · "+detail;
        }finally{
            if(c!=null && c.binder!=null){
                try{
                    // Release candidate from T1N134. Binder success != physical proof.
                    write(c.binder,mirrorId,0);
                    releaseOk=true;
                }catch(Exception releaseError){
                    status+=" · ALERTA: no se confirmó liberación "+mirrorId+"=0";
                }
            }
            if(c!=null)c.close();
            busy.set(false);
            if(motionAttempted && !releaseOk)
                status+=" · Usá el mando físico OEM y comprobá el espejo antes de circular";
            emit(callback,status);
        }
    }

    /**
     * Explicit foreground emergency release. Independent Binder binding allows
     * trying 2069=0 even if a pulse thread has not completed.
     */
    public boolean release(Callback callback){
        if(!foreground)return false;
        stop.set(true);
        final int mirrorId=activeMirrorId;
        new Thread(()->{
            String status;
            Connection c=null;
            try{
                c=connect();
                write(c.binder,mirrorId,0);
                status="Liberación "+mirrorId+"=0 enviada. Comprobar físicamente: "+
                    "respuesta Binder NO prueba parada mecánica.";
            }catch(Exception ex){
                status="ALERTA: no se pudo enviar liberación "+mirrorId+"=0: "+
                    ex.getClass().getSimpleName();
            }finally{
                if(c!=null)c.close();
            }
            emit(callback,status);
        },"T1-Mirror-Release").start();
        return true;
    }

    private void requireReady(){
        if(!foreground||stop.get())
            throw new IllegalStateException("Panel no visible o prueba cancelada");
    }

    private static double readSpeed(IBinder binder)throws Exception{
        try{
            float raw=readFloat(binder,SPEED_FLOAT_ID);
            if(Float.isFinite(raw) && raw>=0 && raw<300)return raw;
        }catch(Exception ignored){}
        // Alternative documented in T1N134: integer speed in same OEM interface.
        int raw=readInt(binder,SPEED_INT_ID);
        if(raw<0 || raw>300)
            throw new IllegalStateException("Velocidad OEM fuera de rango");
        return raw;
    }

    private static int readInt(IBinder binder,int id)throws Exception{
        if(id!=GEAR_ID&&id!=SPEED_INT_ID&&id!=LEFT_ID&&id!=RIGHT_ID&&id!=2003)
            throw new SecurityException("ID lectura inválido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            if(!binder.transact(5,data,reply,0))
                throw new IllegalStateException("Lectura int rechazada: "+id);
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }

    private static float readFloat(IBinder binder,int id)throws Exception{
        if(id!=SPEED_FLOAT_ID)throw new SecurityException("ID lectura inválido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            if(!binder.transact(6,data,reply,0))
                throw new IllegalStateException("Lectura float rechazada: "+id);
            reply.readException();
            return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }

    private void write(IBinder binder,int mirrorId,int value)throws Exception{
        if((mirrorId!=RIGHT_ID&&mirrorId!=LEFT_ID)||
                (value!=0&&value!=1&&value!=2))
            throw new SecurityException("Valor de espejo no permitido");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(mirrorId);
            data.writeInt(value);
            data.writeString(app.getPackageName());
            if(!binder.transact(15,data,reply,0))
                throw new IllegalStateException("OEM rechazó escritura "+mirrorId+"="+value);
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }

    private Connection connect()throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);latch.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){stop.set(true);}
            @Override public void onNullBinding(ComponentName n){latch.countDown();}
            @Override public void onBindingDied(ComponentName n){stop.set(true);latch.countDown();}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),conn,0);
        if(!bound)throw new IllegalStateException("VehicleService no disponible");
        try{
            if(!latch.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("VehicleService sin respuesta");
            IBinder b=remote.get();
            if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Descriptor OEM inválido");
            return new Connection(conn,b);
        }catch(Exception e){
            try{app.unbindService(conn);}catch(Exception ignored){}
            throw e;
        }
    }
    private final class Connection{
        final ServiceConnection connection;
        final IBinder binder;
        Connection(ServiceConnection c,IBinder b){connection=c;binder=b;}
        void close(){try{app.unbindService(connection);}catch(Exception ignored){}}
    }
}
