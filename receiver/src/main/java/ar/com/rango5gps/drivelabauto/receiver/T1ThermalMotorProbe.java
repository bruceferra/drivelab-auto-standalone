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
 * Read-only thermal/powertrain diagnosis. 9-digit values are from the
 * T1Launcher T1N134 DEX catalog, NOT verified 4-digit VehicleService codes.
 * No confirmed electric motor RPM property in that catalog; do not infer
 * ON/OFF merely from speed, EV mode or HV battery current.
 */
final class T1ThermalMotorProbe {
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";
    private final Context app;
    T1ThermalMotorProbe(Context c){app=c.getApplicationContext();}

    static final class Candidate {
        final int id;final String name;final boolean floating;
        Candidate(int id,String name,boolean floating){
            this.id=id;this.name=name;this.floating=floating;
        }
    }
    private static final Candidate[] CANDIDATES={
        new Candidate(291504897,"ENGINE_COOLANT_TEMP (AOSP, °C)",true),
        new Candidate(291504900,"ENGINE_OIL_TEMP (AOSP, °C)",true),
        new Candidate(559940105,"CLUSTER ENGINE TEMP (alternativo)",true),
        new Candidate(559957277,"CLUSTER ENGINE TEMP VAL (alternativo)",true),
        new Candidate(557860126,"CLUSTER ENGINE TEMP STS (estado)",false),
        new Candidate(557860127,"CLUSTER BATT TEMP (unidad por verificar)",false),
        new Candidate(557860159,"CLUSTER BATT TEMP STS (estado)",false),
        new Candidate(557860106,"CLUSTER BAT TEMP HIGH (alerta)",false),
        new Candidate(291504901,"ENGINE RPM (motor térmico AOSP)",true),
        new Candidate(557860187,"CLUSTER ENGINE SPEED (motor térmico)",false),
        new Candidate(557867145,"PHEV ENERGY FLOW (no es RPM eléctrica)",false)
    };
    static final class Snapshot {
        long ts;
        final String[] readouts=new String[CANDIDATES.length];
        double engineCoolantC=Double.NaN,engineOilC=Double.NaN,
            engineClusterTempRaw=Double.NaN;
        String engineTempSource="SIN LECTURA";
        String batteryTempRaw="SIN LECTURA",flowRaw="SIN LECTURA";
        String engineTemperatureDisplay(){
            if(Double.isFinite(engineClusterTempRaw)&&engineClusterTempRaw>0)
                return String.format(Locale.forLanguageTag("es-AR"),
                    "%.1f · RAW (exp.)",engineClusterTempRaw);
            if(Double.isFinite(engineCoolantC)&&engineCoolantC>0)
                return temp(engineCoolantC);
            return "SIN LECTURA";
        }
        int ok=0;
        String issue="";
        String summary(){
            return "MOTOR TÉRMICO: "+engineTemperatureDisplay()+
                " · fuente "+engineTempSource+
                " · aceite "+temp(engineOilC)+
                "\nBATERÍA TRACCIÓN: "+batteryTempRaw+
                " (valor crudo, no °C validados)"+
                "\nMOTOR ELÉCTRICO: RPM/ON-OFF SIN SEÑAL VERIFICADA"+
                "\nFlujo PHEV (candidato): "+flowRaw+
                "\nHAL: "+ok+"/"+CANDIDATES.length+" señales legibles";
        }
        String report(){
            StringBuilder b=new StringBuilder("TEMPERATURAS Y MOTOR · SOLO LECTURA\n");
            b.append("Hora: ").append(
                new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date(ts)));
            if(!issue.isEmpty())b.append("\nERROR GENERAL: ").append(issue);
            b.append("\n\n").append(summary());
            for(int i=0;i<CANDIDATES.length;i++){
                Candidate c=CANDIDATES[i];
                b.append("\n").append(c.name).append(" [").append(c.id)
                    .append("] ").append(readouts[i]);
            }
            b.append("\n\nNota: 559940105 cambia al calentarse, "+
                "confirmado en prueba del propietario. La escala RAW NO "+
                "está calibrada a °C. AOSP 291504897, aceite, RPM AOSP y "+
                "temperatura batería han devuelto cero: cero NO equivale "+
                "a lectura verificada. RPM térmicas de cockpit=1035. "+
                "RPM eléctricas sin código validado.");
            return b.toString();
        }
        private static String temp(double c){
            return Double.isFinite(c)?
                String.format(Locale.forLanguageTag("es-AR"),"%.1f °C",c):
                "SIN LECTURA";
        }
    }
    Snapshot read(){
        Snapshot snap=new Snapshot();snap.ts=System.currentTimeMillis();
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> link=new AtomicReference<>();
        ServiceConnection sc=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                link.set(binder);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name){link.set(null);}
            @Override public void onNullBinding(ComponentName name){ready.countDown();}
            @Override public void onBindingDied(ComponentName name){link.set(null);}
        };
        boolean bound=false;
        try{
            bound=app.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),sc,0);
            if(!bound||!ready.await(3000,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService no responde");
            IBinder binder=link.get();
            if(binder==null||!binder.isBinderAlive()||
                !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz HAL incompatible");
            for(int i=0;i<CANDIDATES.length;i++){
                Candidate c=CANDIDATES[i];
                try{
                    double raw=c.floating?getFloat(binder,c.id):getInt(binder,c.id);
                    if(!Double.isFinite(raw))
                        throw new IllegalStateException("getter sin valor finito");
                    snap.readouts[i]=c.floating?
                        "FLOAT "+Float.toString((float)raw):
                        "INT "+Long.toString((long)raw);
                    snap.ok++;
                    if(i==0&&raw>0&&raw<=180)snap.engineCoolantC=raw;
                    if(i==1&&raw>0&&raw<=180)snap.engineOilC=raw;
                    if(i==2&&raw>0&&raw<5000)
                        snap.engineClusterTempRaw=raw;
                    if(i==5&&raw!=0)snap.batteryTempRaw=snap.readouts[i];
                    if(i==10)snap.flowRaw=snap.readouts[i];
                }catch(Exception ex){
                    String msg=ex.getMessage()==null?"":ex.getMessage();
                    if(msg.length()>60)msg=msg.substring(0,60);
                    snap.readouts[i]="NO DISPONIBLE · "+
                        ex.getClass().getSimpleName()+" "+msg;
                }
            }
        }catch(Exception ex){
            snap.issue=ex.getClass().getSimpleName()+" "+ex.getMessage();
            for(int i=0;i<CANDIDATES.length;i++)
                snap.readouts[i]="SIN ACCESO";
        }finally{if(bound)try{app.unbindService(sc);}catch(Exception ignored){}}
        if(Double.isFinite(snap.engineClusterTempRaw))
            snap.engineTempSource="CLUSTER ENGINE TEMP · 559940105 · RAW";
        else if(Double.isFinite(snap.engineCoolantC))
            snap.engineTempSource="AOSP ENGINE COOLANT · 291504897 · °C";
        return snap;
    }
    private static int getInt(IBinder binder,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(id);d.writeInt(0);
            if(!binder.transact(2,d,r,0))
                throw new IllegalStateException("getInt rechazado");
            r.readException();return r.readInt();
        }finally{d.recycle();r.recycle();}
    }
    private static float getFloat(IBinder binder,int id)throws Exception{
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try{
            d.writeInterfaceToken(TOKEN);d.writeInt(id);d.writeInt(0);
            if(!binder.transact(9,d,r,0))
                throw new IllegalStateException("getFloat rechazado");
            r.readException();return r.readFloat();
        }finally{d.recycle();r.recycle();}
    }
}
