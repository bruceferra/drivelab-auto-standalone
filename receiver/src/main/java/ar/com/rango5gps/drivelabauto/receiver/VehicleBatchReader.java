package ar.com.rango5gps.drivelabauto.receiver;
import android.content.ComponentName;import android.content.Context;import android.content.Intent;import android.content.ServiceConnection;
import android.os.Handler;import android.os.IBinder;import android.os.Looper;import android.os.Parcel;import android.os.SystemClock;
import java.util.LinkedHashMap;import java.util.Map;
import java.util.concurrent.CountDownLatch;import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;import java.util.concurrent.atomic.AtomicReference;
/** Only 25 IDs from VEHICLE read via known getter transact codes 5/6, never writes. */
public final class VehicleBatchReader {
 public interface Callback{void done(Result result);}
 public static final class Result {
  public final int start,end;public final String status;public final Map<String,String> results;
  Result(int start,int end,String status,Map<String,String> results){
   this.start=start;this.end=end;this.status=status;this.results=results;
  }
 }
 private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
 private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
 private final AtomicBoolean busy=new AtomicBoolean(false);
 public VehicleBatchReader(Context context){this.context=context.getApplicationContext();}
 public boolean busy(){return busy.get();}
 public boolean read(int first,Callback callback){
  BatchReadPolicy.validate(first);
  if(!busy.compareAndSet(false,true))return false;
  new Thread(()->{
   final int last=BatchReadPolicy.end(first);
   final LinkedHashMap<String,String> out=new LinkedHashMap<>();
   ServiceConnection connection=null;boolean bound=false;String status="OK";
   try{
    CountDownLatch latch=new CountDownLatch(1);AtomicReference<IBinder> remote=new AtomicReference<>();
    connection=new ServiceConnection(){
     public void onServiceConnected(ComponentName n,IBinder b){remote.set(b);latch.countDown();}
     public void onServiceDisconnected(ComponentName n){}
     public void onNullBinding(ComponentName n){latch.countDown();}
     public void onBindingDied(ComponentName n){}
    };
    bound=context.bindService(new Intent().setComponent(new ComponentName(
     "com.autolink.app.vehicleservice","com.autolink.app.vehicleservice.VehicleService")),connection,0);
    if(!bound||!latch.await(4,TimeUnit.SECONDS))throw new IllegalStateException("Servicio no disponible");
    IBinder b=remote.get();
    if(b==null||!TOKEN.equals(b.getInterfaceDescriptor()))throw new SecurityException("Binder inesperado");
    int hits=0;
    for(int id=first;id<=last;id++){
     String i=get(b,id,false),f=get(b,id,true);
     if(!i.startsWith("—")||!f.startsWith("—"))hits++;
     out.put("VEHICLE:"+id,"int="+i+" / float="+f);
     SystemClock.sleep(BatchReadPolicy.DELAY_MS);
    }
    status=out.size()+" IDs leídos; "+hits+" con respuesta";
   }catch(Exception e){status="Incompleto: "+e.getClass().getSimpleName();}
   finally{
    if(bound&&connection!=null)try{context.unbindService(connection);}catch(Exception ignored){}
    busy.set(false);String text=status;
    main.post(()->callback.done(new Result(first,last,text,out)));
   }
  },"T1-Bounded-Batch").start();
  return true;
 }
 private static String get(IBinder b,int id,boolean isFloat){
  Parcel data=Parcel.obtain(),reply=Parcel.obtain();
  try{
   data.writeInterfaceToken(TOKEN);data.writeInt(id);
   if(!b.transact(isFloat?6:5,data,reply,0))return "—rechazado";
   reply.readException();
   if(isFloat){float v=reply.readFloat();return BatchReadPolicy.valid(v)?String.valueOf(v):"—sin valor";}
   int v=reply.readInt();return BatchReadPolicy.valid(v)?String.valueOf(v):"—sin valor";
  }catch(Exception e){return "—"+e.getClass().getSimpleName();}
  finally{data.recycle();reply.recycle();}
 }
}
