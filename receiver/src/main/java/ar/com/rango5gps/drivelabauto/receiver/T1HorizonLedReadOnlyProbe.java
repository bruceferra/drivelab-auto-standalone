package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HORIZON LED lightbar on Shanhai/Jetour T1: rhythmic display in P,
 * steady in N according to user's direct observation.
 *
 * ALL 9-digit vendor names are candidates from the T1N134 APK DEX catalog,
 * not confirmed on the Argentine firmware. The "SHOW MODE" and
 * "WELCOME LIGHT SHOW MODE" property IDs are marked WRITE ONLY in catalog,
 * so NEVER call or guess their setter from this diagnostic.
 *
 * Only IALCar.getInt(id,area=0) binder transaction 2. This class never writes
 * vehicle signals, nor changes headlights, DRLs or hazard lights.
 */
final class T1HorizonLedReadOnlyProbe {
    static final class Property {
        final int id; final String title;
        Property(int id,String title){this.id=id;this.title=title;}
    }
    private static final Property[] PROPERTIES={
        new Property(557843630,"SCENE VENDOR LIGHT SHOW · status"),
        new Property(557843784,"EXT.LAMP LED · status"),
        new Property(557844233,"VC.DRL STS · luces diurnas"),
        new Property(557856517,"CLUSTER DRL STS · luces diurnas"),
        new Property(557844481,"CLUSTER CEM 2 DRL STS"),
        new Property(557843555,"VC.WELCOME · bienvenida"),
        new Property(557843637,"VC.WELCOME PASSENGER"),
        new Property(557867035,"SETTINGS WELCOME LIGHT POLLING WORKING"),
        new Property(557879316,"SETTINGS WELCOME LIGHT POLLING WORKING BY QNX"),
        new Property(557867036,"SETTINGS HEADLAMP DELAY LIGHT WORKING"),
        new Property(557867047,"SETTINGS SPECIAL PARKING WORKING"),
        new Property(557879382,"SETTINGS SPECIAL PARKING WORKING BY QNX"),
        new Property(557843618,"SCENE VENDOR KEY STS"),
        new Property(557843619,"SCENE VENDOR ENGINE STS"),
        new Property(557843647,"SCENE VENDOR LOW BEAM"),
        new Property(557843648,"SCENE VENDOR HIGH BEAM")
    };
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";
    private final Context app;
    T1HorizonLedReadOnlyProbe(Context c){app=c.getApplicationContext();}

    static final class Snapshot {
        final String[] values=new String[PROPERTIES.length];
        String error="";long at=System.currentTimeMillis();
        String detail(){
            StringBuilder b=new StringBuilder(
                "LED horizonte · lectura HAL SIN ESCRITURA\n");
            if(!error.isEmpty())b.append("Error: ").append(error).append("\n");
            for(int i=0;i<PROPERTIES.length;i++)
                b.append(PROPERTIES[i].id).append(" · ")
                    .append(PROPERTIES[i].title).append(": ")
                    .append(values[i]==null?"SIN DATO":values[i]).append("\n");
            return b.toString();
        }
        static String transitions(ArrayList<Snapshot> frames,
                                  ArrayList<Integer> gears){
            StringBuilder s=new StringBuilder(
                "JETOUR T1 · P/N · LED HORIZONTE · SOLO LECTURA\n"+
                "Estado P animado vs N fijo: observación del propietario, "+
                "NO un comando probado.\n");
            s.append("Muestras obtenidas: ").append(frames.size())
                .append("\nMarchas crudas (1033): ");
            for(int i=0;i<gears.size();i++){
                if(i>0)s.append(", ");
                s.append(gears.get(i));
            }
            s.append("\n\n");
            Snapshot previous=null;int changed=0,comparable=0;
            for(int i=0;i<frames.size();i++){
                Snapshot cur=frames.get(i);
                if(cur.error.length()>0)s.append("Muestra ").append(i+1)
                    .append(" error: ").append(cur.error).append("\n");
                if(previous!=null){
                    for(int j=0;j<PROPERTIES.length;j++){
                        String a=previous.values[j],b=cur.values[j];
                        if(a!=null&&b!=null&&a.startsWith("OK ")&&b.startsWith("OK ")){
                            comparable++;
                            if(!a.equals(b)){
                                changed++;
                                s.append("CAMBIÓ ").append(PROPERTIES[j].id)
                                    .append(" · ").append(PROPERTIES[j].title)
                                    .append(" · marcha ").append(gears.get(i-1))
                                    .append(" → ").append(gears.get(i))
                                    .append(" · ").append(a).append(" → ")
                                    .append(b).append("\n");
                            }
                        }
                    }
                }
                previous=cur;
            }
            s.append("\nCambios: ").append(changed)
                .append(" en ").append(comparable)
                .append(" pares de valores legibles.");
            if(changed==0)s.append("\nSin cambios detectados: estas propiedades "+
                "podrían ser constantes no implementadas; el efecto puede "+
                "estar controlado por BCM o un modo distinto.");
            if(!frames.isEmpty()){
                s.append("\n\nPRIMERA MUESTRA:\n").append(frames.get(0).detail());
                s.append("\nÚLTIMA MUESTRA:\n")
                    .append(frames.get(frames.size()-1).detail());
            }
            s.append("\nSe excluyen setters 557843627, 557843797, 557867037, "+
                "557843785, 557843845 y otros: no escribir sin validar.");
            return s.toString();
        }
    }
    Snapshot read(){
        Snapshot s=new Snapshot();
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection sc=new ServiceConnection(){
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
                new ComponentName(PKG,CLS)),sc,0);
            if(!bound||!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService sin respuesta");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
                    !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Contrato HAL no disponible");
            for(int i=0;i<PROPERTIES.length;i++){
                int id=PROPERTIES[i].id;
                try{s.values[i]="OK "+getInt(b,id);}
                catch(Exception e){
                    String msg=e.getMessage()==null?"":e.getMessage();
                    if(msg.length()>60)msg=msg.substring(0,60);
                    s.values[i]="NO DISPONIBLE · "+
                        e.getClass().getSimpleName()+" "+msg;
                }
            }
        }catch(Exception e){
            s.error=e.getClass().getSimpleName()+" · "+
                (e.getMessage()==null?"":e.getMessage());
            for(int i=0;i<PROPERTIES.length;i++)s.values[i]="SIN ACCESO";
        }finally{if(bound)try{app.unbindService(sc);}catch(Exception ignored){}}
        return s;
    }
    private static int getInt(IBinder b,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);
            d.writeInt(id);d.writeInt(0);
            if(!b.transact(2,d,r,0))
                throw new IllegalStateException("getInt rechazado");
            r.readException();return r.readInt();
        }finally{d.recycle();r.recycle();}
    }
}
