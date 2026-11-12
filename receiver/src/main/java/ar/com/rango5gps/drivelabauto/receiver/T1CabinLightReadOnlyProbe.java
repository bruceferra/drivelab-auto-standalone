package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Only ALCarService IALCar.getInt(id, area) transaction 2, already used in
 * PHEV diagnostic. ZERO setters. Generic Automotive light IDs may not be
 * implemented; seat-area lights may require a nonzero areaId (unknown).
 */
final class T1CabinLightReadOnlyProbe {
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";
    private static final int[] IDS={
        289410817,289410818,356519683,356519684,
        557860128,557860129,557860131,557860132,557860133,
        557842982,557843530,557843555,557843637,557843850
    };
    private static final String[] LABELS={
        "Cabina estado GLOBAL","Cabina selector GLOBAL",
        "Lectura individual estado (area 0)","Lectura individual selector (area 0)",
        "Puerta conductor","Puerta acompañante","Puerta trasera derecha",
        "Puerta trasera izquierda","Puerta baúl",
        "Baúl - estado cluster","Ambiental interior","Bienvenida exterior",
        "Bienvenida acompañante","Portón posterior T1N"
    };
    static final class Snapshot {
        final LinkedHashMap<Integer,String> values=new LinkedHashMap<>();
        String header="";
        String describe(){
            StringBuilder sb=new StringBuilder(header);
            for(int i=0;i<IDS.length;i++){
                int id=IDS[i];
                sb.append("\n").append(LABELS[i]).append(" [").append(id)
                    .append("]: ").append(values.get(id));
            }
            sb.append("\n\nSolo LECTURA, area=0. La falta de dato NO prueba "+
                "que el vehículo no tenga el componente.");
            return sb.toString();
        }
        static String compare(Snapshot before,Snapshot after){
            StringBuilder sb=new StringBuilder(
                "COMPARACIÓN · ANTES / DESPUÉS DEL BOTÓN OEM\n");
            int changed=0,known=0;
            for(int i=0;i<IDS.length;i++){
                int id=IDS[i];String old=before.values.get(id),
                    now=after.values.get(id);
                if(old==null||now==null)continue;
                boolean oldOk=old.startsWith("OK "),
                    newOk=now.startsWith("OK ");
                if(oldOk&&newOk){
                    known++;
                    if(!old.equals(now)){
                        changed++;
                        sb.append("\n").append(LABELS[i]).append(" [").append(id)
                            .append("]: ").append(old).append(" → ").append(now);
                    }
                }
            }
            sb.append("\n\n").append(changed).append(" señales cambiaron; ")
                .append(known).append(" legibles en ambas capturas.");
            if(changed==0)
                sb.append("\nNo se detectó un cambio en las señales accesibles: "+
                    "puede estar en otro ECU, área de asiento o propiedad OEM.");
            sb.append("\n\nCAPTURA 1:\n").append(before.describe())
                .append("\n\nCAPTURA 2:\n").append(after.describe());
            return sb.toString();
        }
    }
    private final Context app;
    T1CabinLightReadOnlyProbe(Context c){app=c.getApplicationContext();}
    Snapshot read(){
        Snapshot snap=new Snapshot();
        snap.header="Diagnóstico luces Jetour T1 · "+
            new java.text.SimpleDateFormat("HH:mm:ss",
                java.util.Locale.ROOT).format(new java.util.Date());
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){remote.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){remote.set(null);}
        };
        boolean bound=false;
        try{
            bound=app.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),connection,0);
            if(!bound||!ready.await(3500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService sin respuesta");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
                    !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Contrato HAL no disponible");
            for(int id:IDS){
                try{snap.values.put(id,"OK "+getInt(b,id));}
                catch(Exception ex){
                    String msg=ex.getMessage();
                    if(msg==null)msg="";
                    if(msg.length()>70)msg=msg.substring(0,70);
                    snap.values.put(id,"NO DISPONIBLE · "+
                        ex.getClass().getSimpleName()+" "+msg);
                }
            }
        }catch(Exception e){
            snap.header+="\nError de acceso OEM: "+e.getClass().getSimpleName()+
                ": "+e.getMessage();
            for(int id:IDS)snap.values.put(id,"SIN ACCESO");
        }finally{
            if(bound)try{app.unbindService(connection);}catch(Exception ignored){}
        }
        return snap;
    }
    private static int getInt(IBinder b,int id)throws Exception{
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(TOKEN);
            in.writeInt(id);in.writeInt(0); // area 0 only, no generic guessing
            if(!b.transact(2,in,out,0))
                throw new IllegalStateException("ALCar.getInt rechazado");
            out.readException();return out.readInt();
        }finally{in.recycle();out.recycle();}
    }
}
