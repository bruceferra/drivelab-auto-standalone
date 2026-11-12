package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * READING_LIGHTS_STATE and READING_LIGHTS_SWITCH are defined as SEAT-area
 * properties, unlike the global cabin lights. OEM availability is untested.
 * Read-only comparison; no setters or switch write.
 */
final class T1RearSeatLightsProbe {
    private static final int[] AREAS={0,16,32,64};
    private static final String[] AREA_LABELS={
        "GLOBAL (control)","2.ª fila izquierda","2.ª fila centro","2.ª fila derecha"};
    private static final int[] IDS={356519683,356519684};
    private static final String[] SIGNALS={"Luz de lectura · estado","Luz de lectura · interruptor"};
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";
    private final Context app;
    T1RearSeatLightsProbe(Context c){app=c.getApplicationContext();}
    static final class Snapshot {
        final String[][] readings=new String[IDS.length][AREAS.length];
        String header="";
        String summary(){
            StringBuilder s=new StringBuilder(header);
            for(int area=0;area<AREAS.length;area++){
                s.append("\n\n").append(AREA_LABELS[area])
                    .append(" · areaId=").append(AREAS[area]);
                for(int k=0;k<IDS.length;k++){
                    s.append("\n  ").append(SIGNALS[k]).append(" · ")
                        .append(IDS[k]).append(": ")
                        .append(readings[k][area]==null?"SIN DATO":readings[k][area]);
                }
            }
            s.append("\n\nSIN ESCRITURAS. No afirmar que una zona exista "+
                "por el hecho de devolver 0. Comparar con el botón OEM.");
            return s.toString();
        }
        static String compare(Snapshot a,Snapshot b){
            StringBuilder s=new StringBuilder(
                "LUCES TRASERAS · COMPARACIÓN POR ASIENTO\n"+
                "ANTES: "+a.header+"\nDESPUÉS: "+b.header);
            int compared=0,changed=0;
            for(int area=0;area<AREAS.length;area++){
                for(int k=0;k<IDS.length;k++){
                    String x=a.readings[k][area],y=b.readings[k][area];
                    if(x!=null&&y!=null&&x.startsWith("OK ")&&y.startsWith("OK ")){
                        compared++;
                        if(!x.equals(y)){
                            changed++;
                            s.append("\nCAMBIÓ · ").append(AREA_LABELS[area])
                                .append(" [").append(AREAS[area]).append("] · ")
                                .append(IDS[k]).append(": ")
                                .append(x).append(" → ").append(y);
                        }
                    }
                }
            }
            s.append("\n\nComparadas: ").append(compared)
                .append(" · señales diferentes: ").append(changed);
            if(changed==0)s.append("\nNo se observó cambio. El área "+
                "podría ser distinta, la propiedad no estar implementada "+
                "o ser controlada directamente por el BCM.");
            return s.append("\n\nLECTURA ANTES:\n").append(a.summary())
                .append("\n\nLECTURA DESPUÉS:\n").append(b.summary()).toString();
        }
    }
    Snapshot read(){
        Snapshot s=new Snapshot();
        s.header=new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date());
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> link=new AtomicReference<>();
        ServiceConnection sc=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                link.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){link.set(null);}
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
            @Override public void onBindingDied(ComponentName n){link.set(null);}
        };
        boolean bound=false;
        try{
            bound=app.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),sc,0);
            if(!bound||!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService sin enlace");
            IBinder b=link.get();
            if(b==null||!b.isBinderAlive()||
                !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM incompatible");
            for(int k=0;k<IDS.length;k++){
                for(int i=0;i<AREAS.length;i++){
                    try{s.readings[k][i]="OK "+getInt(b,IDS[k],AREAS[i]);}
                    catch(Exception e){
                        String msg=e.getClass().getSimpleName()+
                            " "+(e.getMessage()==null?"":e.getMessage());
                        if(msg.length()>65)msg=msg.substring(0,65);
                        s.readings[k][i]="NO DISPONIBLE · "+msg;
                    }
                }
            }
        }catch(Exception e){
            s.header+=" · ERROR "+e.getClass().getSimpleName()+
                ": "+e.getMessage();
            for(int k=0;k<IDS.length;k++)
                for(int i=0;i<AREAS.length;i++)s.readings[k][i]="SIN ACCESO";
        }finally{if(bound)try{app.unbindService(sc);}catch(Exception ignored){}}
        return s;
    }
    private static int getInt(IBinder binder,int id,int area)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            data.writeInt(area);
            if(!binder.transact(2,data,reply,0))
                throw new IllegalStateException("getter 2 rechazado");
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
}
