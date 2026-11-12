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
 * Noninvasive 12-V battery / IBS / DC-DC OEM HAL exploration.
 *
 * Every property comes from the T1N134 DEX *catalog*. Availability,
 * units and electrical direction are UNVERIFIED on Argentine firmware.
 * INT32 -> IALCar.getInt(id,area 0) / transaction 2.
 * FLOAT -> IALCar.getFloat(id,area 0) / transaction 9.
 * No setter, no "write" Binder, no generic user-provided property IDs.
 */
final class T1Aux12vReadOnlyProbe {
    static final class Item {
        final int id;final String label;final boolean floatType;
        Item(int i,String n,boolean floating){id=i;label=n;floatType=floating;}
    }
    private static final Item[] ITEMS={
        new Item(559964342,"IBS voltaje de batería auxiliar",true),
        new Item(559940910,"EXT voltaje batería · dominio por determinar",true),
        new Item(559940911,"EXT corriente batería · NO asumir 12 V",true),
        new Item(557867192,"IBS corriente",false),
        new Item(559964408,"IBS corriente en reposo",true),
        new Item(557867191,"IBS SOC",false),
        new Item(557867252,"IBS SOC estático",false),
        new Item(557867255,"IBS SOH",false),
        new Item(557867257,"IBS rango de corriente",false),
        new Item(557867264,"IBS temperatura de batería",false),
        new Item(557856583,"DC-DC habilitado",false),
        new Item(557867051,"DC-DC trabajando",false),
        new Item(557843182,"DC-DC avería indicada",false),
        new Item(557843808,"EXT potencia de batería",false)
    };
    private static final String PKG="com.autolink.carservice";
    private static final String CLS="com.autolink.carservice.ALCarService";
    private static final String TOKEN="com.autolink.adapterinterface.car.IALCar";
    private final Context app;
    T1Aux12vReadOnlyProbe(Context c){app=c.getApplicationContext();}
    static final class Result {
        final String[] readings=new String[ITEMS.length];
        String header="";int ok;
        String show(){
            StringBuilder b=new StringBuilder(header);
            for(int i=0;i<ITEMS.length;i++){
                b.append("\n").append(ITEMS[i].label).append(" · ")
                    .append(ITEMS[i].id).append(": ")
                    .append(readings[i]==null?"SIN DATO":readings[i]);
            }
            b.append("\n\nANÁLISIS CRÍTICO: EXT 559940910: ")
                .append(T1ElectricalEvidence.report(readings[1]));
            b.append("\nIBS 559964342 a 0 NO es 0 voltios físicos: "+
                "significa SIN LECTURA VALIDADA hasta corroborar. "+
                "Los códigos EXT podrían corresponder al circuito HV. "+
                "No mezclar con 1064 de tracción ni etiquetar "+
                "EXT 315 como voltaje de 12 V.");
            return b.toString();
        }
        String headline(){
            String ibs=readings[0];
            boolean ibsMissing=ibs==null||ibs.equals("RAW float=0.0")||
                !ibs.startsWith("RAW float=");
            return "BATERÍA AUXILIAR 12 V · "+
                (ibsMissing?"SIN VOLTAJE IBS VÁLIDO":
                    "IBS candidato "+ibs)+
                "\nEXT 559940910: "+
                T1ElectricalEvidence.report(readings[1])+
                "\n"+ok+"/"+ITEMS.length+" getters responden, "+
                "pero NO son mediciones 12 V confirmadas. "+
                "No inferir carga, SOC ni amperes auxiliares.";
        }
    }
    Result read(){
        Result out=new Result();
        out.header="12 V / IBS / DC-DC · lectura HAL "+
            new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+
            "\nFuente: catálogo T1Launcher · solo lectura, area=0";
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> link=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
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
                new ComponentName(PKG,CLS)),connection,0);
            if(!bound||!ready.await(3500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("ALCarService no responde");
            IBinder binder=link.get();
            if(binder==null||!binder.isBinderAlive()||
                !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz HAL no disponible");
            for(int n=0;n<ITEMS.length;n++){
                Item item=ITEMS[n];
                try{
                    if(item.floatType){
                        float v=getFloat(binder,item.id);
                        if(!Float.isFinite(v))
                            throw new IllegalStateException("valor no finito");
                        out.readings[n]="RAW float="+v;
                    }else out.readings[n]="RAW int="+getInt(binder,item.id);
                    out.ok++;
                }catch(Exception e){
                    String msg=e.getMessage();
                    if(msg==null)msg="";
                    if(msg.length()>70)msg=msg.substring(0,70);
                    out.readings[n]="NO DISPONIBLE · "+
                        e.getClass().getSimpleName()+" "+msg;
                }
            }
        }catch(Exception e){
            out.header+="\nAcceso OEM rechazado: "+e.getClass().getSimpleName()+
                " · "+e.getMessage();
            for(int n=0;n<ITEMS.length;n++)out.readings[n]="SIN ACCESO";
        }finally{
            if(bound)try{app.unbindService(connection);}catch(Exception ignored){}
        }
        return out;
    }
    private static int getInt(IBinder b,int id)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);data.writeInt(0);
            if(!b.transact(2,data,reply,0))
                throw new IllegalStateException("getter entero rechazado");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    private static float getFloat(IBinder b,int id)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);data.writeInt(id);data.writeInt(0);
            if(!b.transact(9,data,reply,0))
                throw new IllegalStateException("getter flotante rechazado");
            reply.readException();return reply.readFloat();
        }finally{data.recycle();reply.recycle();}
    }
}
