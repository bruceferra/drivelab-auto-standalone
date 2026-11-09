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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded manual-triggered change detector. Reads six pre-identified
 * VehicleService values via known read-only getters; never issues writes.
 */
public final class VehicleChangeScanner {
    public interface Callback { void update(Snapshot value); }
    public static final class Snapshot {
        public final boolean running;
        public final String status;
        public final int sampleCount;
        public final int changedCount;
        public final Map<String,String> latest;
        public final ArrayList<String> events;
        public Snapshot(boolean running,String status,int sampleCount,int changedCount,
                Map<String,String> latest,ArrayList<String> events){
            this.running=running;this.status=status;this.sampleCount=sampleCount;
            this.changedCount=changedCount;this.latest=latest;this.events=events;
        }
    }

    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopRequested=new AtomicBoolean(false);
    private final AtomicBoolean active=new AtomicBoolean(false);

    public VehicleChangeScanner(Context c){context=c.getApplicationContext();}
    public boolean isRunning(){return active.get();}
    public void stop(){stopRequested.set(true);}

    public boolean start(Callback callback){
        if(!active.compareAndSet(false,true))return false;
        stopRequested.set(false);
        new Thread(()->scan(callback),"T1-Change-Scanner").start();
        return true;
    }
    private void emit(Callback cb,boolean running,String state,int samples,int changes,
            Map<String,String> latest,ArrayList<String> events){
        Snapshot copy=new Snapshot(running,state,samples,changes,
            new LinkedHashMap<>(latest),new ArrayList<>(events));
        main.post(()->cb.update(copy));
    }
    private static final class ValueResult {
        final Double value;
        final String error;
        ValueResult(Double value,String error){this.value=value;this.error=error;}
    }

    private void scan(Callback callback){
        final ChangeDetectorPolicy.Signal[] specs=ChangeDetectorPolicy.signals();
        final Map<String,String> latest=new LinkedHashMap<>();
        final ArrayList<String> events=new ArrayList<>();
        final Double[] previous=new Double[specs.length];
        final boolean[] everValid=new boolean[specs.length];
        int samples=0,changes=0;
        boolean bound=false;
        ServiceConnection connection=null;
        String result="45 segundos terminados";
        try{
            CountDownLatch latch=new CountDownLatch(1);
            AtomicReference<IBinder> remote=new AtomicReference<>();
            connection=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName c,IBinder b){
                    remote.set(b);latch.countDown();
                }
                @Override public void onServiceDisconnected(ComponentName c){stopRequested.set(true);}
                @Override public void onBindingDied(ComponentName c){stopRequested.set(true);}
                @Override public void onNullBinding(ComponentName c){latch.countDown();}
            };
            bound=context.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),connection,0);
            if(!bound)throw new IllegalStateException("VehicleService no vinculable");
            if(!latch.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("VehicleService no respondió en 4 s");
            IBinder binder=remote.get();
            if(binder==null)throw new IllegalStateException("Binder no disponible");
            if(!TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("Descriptor OEM inesperado");
            long start=SystemClock.elapsedRealtime();
            while(!stopRequested.get()&&SystemClock.elapsedRealtime()-start<
                    ChangeDetectorPolicy.DURATION_MS){
                for(int i=0;i<specs.length;i++){
                    if(stopRequested.get())break;
                    ChangeDetectorPolicy.Signal s=specs[i];
                    ValueResult value=read(binder,s);
                    if(value.value==null||!ChangeDetectorPolicy.usable(s,value.value)){
                        latest.put(s.label(),value.error==null?"Sin lectura":value.error);
                        continue;
                    }
                    double current=value.value;
                    latest.put(s.label(),ChangeDetectorPolicy.fmt(s,current));
                    if(everValid[i]&&ChangeDetectorPolicy.changed(s,previous[i],current)){
                        String at=new SimpleDateFormat("HH:mm:ss.SSS",Locale.getDefault())
                            .format(new Date());
                        String event=at+" · "+s.label()+" · "+
                            ChangeDetectorPolicy.fmt(s,previous[i])+" → "+
                            ChangeDetectorPolicy.fmt(s,current);
                        events.add(0,event);
                        if(events.size()>ChangeDetectorPolicy.MAX_EVENTS)
                            events.remove(events.size()-1);
                        changes++;
                    }
                    previous[i]=current;
                    everValid[i]=true;
                }
                samples++;
                emit(callback,true,"Detectando cambios · detenelo cuando quieras",
                    samples,changes,latest,events);
                if(stopRequested.get())break;
                SystemClock.sleep(ChangeDetectorPolicy.INTERVAL_MS);
            }
            if(stopRequested.get())result="Detenido por usuario o al salir de la app";
        }catch(Exception ex){
            String message=ex.getMessage();
            result="No se pudo continuar: "+ex.getClass().getSimpleName()+
                (message==null?"":" · "+message);
        }finally{
            if(bound&&connection!=null){
                try{context.unbindService(connection);}
                catch(IllegalArgumentException ignored){}
            }
            active.set(false);
            emit(callback,false,result,samples,changes,latest,events);
        }
    }

    private static ValueResult read(IBinder binder,ChangeDetectorPolicy.Signal s){
        if(!ChangeDetectorPolicy.allowed(s.id,s.isFloat))
            return new ValueResult(null,"ID fuera de lista");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(s.id);
            // VehicleLink.getInt = 5, VehicleLink.getFloat = 6 from T1N134.
            if(s.isFloat){
                if(!binder.transact(6,data,reply,0))
                    return new ValueResult(null,"Getter float rechazado");
                reply.readException();
                float value=reply.readFloat();
                if(!ChangeDetectorPolicy.usable(s,value))
                    return new ValueResult(null,"Float sin valor ("+value+")");
                return new ValueResult((double)value,null);
            }else{
                if(!binder.transact(5,data,reply,0))
                    return new ValueResult(null,"Getter int rechazado");
                reply.readException();
                int value=reply.readInt();
                if(!ChangeDetectorPolicy.usable(s,value))
                    return new ValueResult(null,"Int sin valor ("+value+")");
                return new ValueResult((double)value,null);
            }
        }catch(Exception error){
            return new ValueResult(null,"Sin lectura · "+error.getClass().getSimpleName());
        }finally{data.recycle();reply.recycle();}
    }
}
