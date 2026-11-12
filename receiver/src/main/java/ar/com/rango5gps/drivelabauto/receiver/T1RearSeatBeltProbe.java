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
 * Seat belt / seat occupancy / warning probe. Read-only.
 * 9-digit IDs were extracted from T1Launcher-T1N134.apk and have not
 * been validated against the Argentine car. Seat-area standard properties
 * may need boolean getters rather than getInt; report exceptions, never
 * assume 0 is false or synthesize alarms.
 */
final class T1RearSeatBeltProbe {
    static final class Property {
        final int id,area;final String label;
        Property(int id,int area,String label){
            this.id=id;this.area=area;this.label=label;
        }
    }
    private static final Property[] PROPERTIES={
        new Property(557856516,0,"CLUSTER cinturón segunda fila izquierda"),
        new Property(557856533,0,"CLUSTER cinturón segunda fila centro"),
        new Property(557856534,0,"CLUSTER cinturón segunda fila derecha"),
        new Property(557856513,0,"CLUSTER cinturón acompañante delantero (control)"),
        new Property(557856554,0,"CLUSTER interruptor cinturón conductor (control)"),
        new Property(557856579,0,"CLUSTER aviso cinturones delanteros (control)"),
        new Property(354421634,16,"SEAT_BELT_BUCKLED · atrás izquierda / area 16"),
        new Property(354421634,32,"SEAT_BELT_BUCKLED · atrás centro / area 32"),
        new Property(354421634,64,"SEAT_BELT_BUCKLED · atrás derecha / area 64"),
        new Property(356518832,16,"SEAT_OCCUPANCY · atrás izquierda / area 16"),
        new Property(356518832,32,"SEAT_OCCUPANCY · atrás centro / area 32"),
        new Property(356518832,64,"SEAT_OCCUPANCY · atrás derecha / area 64")
    };
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";

    private final Context app;
    T1RearSeatBeltProbe(Context c){app=c.getApplicationContext();}
    static final class Snapshot {
        String time="";
        final String[] values=new String[PROPERTIES.length];
        String error="";
        String format(){
            StringBuilder s=new StringBuilder(
                "CINTURONES TRASEROS · SOLO LECTURA\n"+
                "Hora: "+time+"\n");
            if(!error.isEmpty())s.append("ERROR OEM: ").append(error).append("\n");
            for(int i=0;i<PROPERTIES.length;i++){
                Property p=PROPERTIES[i];
                s.append("\n").append(p.label).append(" [").append(p.id)
                    .append(" / area ").append(p.area).append("] = ")
                    .append(values[i]==null?"SIN LECTURA":values[i]);
            }
            return s.append("\n\nValores crudos: 0, 1 u otros aún "+
                "NO equivalen a abrochado ni a alarma validada. "+
                "No hay comandos de escritura.").toString();
        }
        static String compare(Snapshot before,Snapshot after) {
            StringBuilder s=new StringBuilder("CINTURONES · DIFERENCIAS A/B\n");
            s.append("A: ").append(before.time).append(" · B: ")
                .append(after.time);
            int comparable=0,changed=0;
            for(int i=0;i<PROPERTIES.length;i++){
                String a=before.values[i],b=after.values[i];
                if(a==null||b==null||
                    !a.startsWith("OK ")||!b.startsWith("OK "))continue;
                comparable++;
                if(!a.equals(b)){
                    changed++;
                    Property p=PROPERTIES[i];
                    s.append("\nCAMBIÓ ").append(p.label)
                        .append(" [").append(p.id).append("/")
                        .append(p.area).append("]: ")
                        .append(a).append(" → ").append(b);
                }
            }
            s.append("\n\n").append(changed).append(" cambios en ")
                .append(comparable).append(" lecturas comparables.");
            if(changed==0)s.append("\nSin cambio visible: el vehículo "+
                "podría usar otra propiedad, el acceso/área ser incorrectos "+
                "o la HAL no publicar el dato del BCM/SRS.");
            return s.append("\n\nCAPTURA A:\n").append(before.format())
                .append("\n\nCAPTURA B:\n").append(after.format()).toString();
        }
    }
    Snapshot read(){
        Snapshot s=new Snapshot();
        s.time=new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date());
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder b){
                remote.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name){remote.set(null);}
            @Override public void onNullBinding(ComponentName name){ready.countDown();}
            @Override public void onBindingDied(ComponentName name){remote.set(null);}
        };
        boolean bound=false;
        try{
            bound=app.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),conn,0);
            if(!bound||!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService sin conexión");
            IBinder binder=remote.get();
            if(binder==null||!binder.isBinderAlive()||
                !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM incompatible");
            for(int i=0;i<PROPERTIES.length;i++){
                Property p=PROPERTIES[i];
                try{s.values[i]="OK "+getInt(binder,p.id,p.area);}
                catch(Exception ex){
                    String detail=ex.getClass().getSimpleName()+
                        " "+(ex.getMessage()==null?"":ex.getMessage());
                    if(detail.length()>60)detail=detail.substring(0,60);
                    s.values[i]="NO DISPONIBLE · "+detail;
                }
            }
        }catch(Exception ex){
            s.error=ex.getClass().getSimpleName()+
                ": "+(ex.getMessage()==null?"":ex.getMessage());
            for(int i=0;i<PROPERTIES.length;i++)s.values[i]="SIN ACCESO";
        }finally{
            if(bound)try{app.unbindService(conn);}catch(Exception ignored){}
        }
        return s;
    }
    private static int getInt(IBinder b,int id,int area)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);data.writeInt(area);
            if(!b.transact(2,data,reply,0))
                throw new IllegalStateException("IALCar.getInt rechazado");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
}
