package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.SystemClock;

/** Independent headunit charge recorder. No need for GPS trip to be running.
 * Read only OEM 1060 cable, 1062 SOC, 1063 V, 1064 A.
 * Polling is slower when unplugged to avoid launcher-style constant load.
 */
public final class T1ChargeRecorderService extends Service {
    static final String ACTION_START="t1.charge.START",
        ACTION_STOP="t1.charge.STOP";
    private static final String CHANNEL="t1_charge_recorder";
    private static final int NOTIFICATION_ID=7531;
    private static final long PLUG_POLL_MS=5000L,IDLE_POLL_MS=15000L;
    private volatile boolean stopped;
    private Thread worker;
    private boolean running;
    private SharedPreferences prefs(){
        return getSharedPreferences(T1ChargeStore.PREFS,Context.MODE_PRIVATE);
    }
    static void startIfEnabled(Context context){
        if(!context.getSharedPreferences(T1ChargeStore.PREFS,MODE_PRIVATE)
                .getBoolean(T1ChargeStore.ENABLED,false))return;
        try{
            context.startForegroundService(new Intent(context,
                T1ChargeRecorderService.class).setAction(ACTION_START));
        }catch(RuntimeException ex){
            context.getSharedPreferences(T1ChargeStore.PREFS,MODE_PRIVATE)
                .edit().putString(T1ChargeStore.STATUS,
                    "Android bloqueó registro de cargas: "+
                    ex.getClass().getSimpleName()).commit();
        }
    }
    @Override public IBinder onBind(Intent ignored){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopped=true;
            if(worker!=null)worker.interrupt();
            stopSelf();return START_NOT_STICKY;
        }
        if(!prefs().getBoolean(T1ChargeStore.ENABLED,false)){
            stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_STICKY;
        foreground();
        running=true;stopped=false;
        worker=new Thread(this::observe,"DriveLab-Charge-Cable-ReadOnly");
        worker.start();
        return START_STICKY;
    }
    private void foreground(){
        NotificationManager mgr=getSystemService(NotificationManager.class);
        mgr.createNotificationChannel(new NotificationChannel(CHANNEL,
            "Registro automático de cargas T1",
            NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,NOTIFICATION_ID,
            new Intent(this,T1ChargeHistoryActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("DriveLab · cargas automáticas")
            .setContentText("Cable OEM 1060 · solo lectura")
            .setContentIntent(open).setOngoing(true).build();
        startForeground(NOTIFICATION_ID,n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }
    private void status(String value,int cable,long at){
        prefs().edit().putInt(T1ChargeStore.LAST_CABLE,cable)
            .putLong(T1ChargeStore.LAST_READ,at)
            .putString(T1ChargeStore.STATUS,value).apply();
    }
    private void observe(){
        T1ChargeOemReader oem=new T1ChargeOemReader(this);
        int cableConnected=0,cableRemoved=0,errors=0;
        long openId=-1;
        boolean open=false;
        try(T1ChargeStore db=new T1ChargeStore(this)){
            T1ChargeStore.Record prior=db.openSession();
            if(prior!=null){open=true;openId=prior.id;}
            status(open?"Retomando carga abierta desde SQLite; "+
                "esperando señal OEM 1060":"Monitoreando cable OEM 1060",
                -1,System.currentTimeMillis());
            while(!stopped&&prefs().getBoolean(T1ChargeStore.ENABLED,false)){
                int raw=-1;
                try{
                    T1ChargeOemReader.Reading data=oem.poll();
                    raw=data.cable;
                    errors=0;
                    String hvDetail="FUENTE: "+data.voltageSource+
                        " · Vehicle 1063="+
                            T1ChargeStore.fmt(data.vehicleVolts,1)+" V"+
                        " · HAL 559940910="+
                            T1ChargeStore.fmt(data.halVolts,1)+" V"+
                        " · HAL 559940911="+
                            T1ChargeStore.fmt(data.halAmps,2)+" A"+
                        " · HAL 557843808 potencia="+
                            (data.halPower==Integer.MIN_VALUE?"—":
                                Integer.toString(data.halPower))+
                        " · HAL 559940899 carga individual="+
                            T1ChargeStore.fmt(
                                data.halSingleChargeEnergy,2)+
                        " · Diagnóstico alternativo 1063/int="+
                            (data.volts1063Int==Integer.MIN_VALUE?"—":
                                Integer.toString(data.volts1063Int))+
                        " · 1090/float="+T1ChargeStore.fmt(
                            data.volts1090Float,2)+
                        " · ALCAR 1063="+T1ChargeStore.fmt(
                            data.volts1063AlcarFloat,2);
                    // Persist raw OEM readings even before a charge session
                    // exists. This lets the owner diagnose current polarity.
                    prefs().edit()
                        .putFloat(T1ChargeStore.LAST_SOC,(float)data.soc)
                        .putFloat(T1ChargeStore.LAST_VOLTS,(float)data.volts)
                        .putFloat(T1ChargeStore.LAST_AMPS,(float)data.amps)
                        .putLong(T1ChargeStore.LAST_SAMPLE_AT,data.timeMs)
                        .putBoolean(T1ChargeStore.LAST_SAMPLE_OK,true)
                        .putString(T1ChargeStore.LAST_VOLTAGE_PROBE,hvDetail)
                        .apply();
                    if(T1ChargeMath.plugged(raw)){
                        cableConnected++;
                        cableRemoved=0;
                        // Stable OEM plug state opens a connection session.
                        // HV current sign is uncalibrated; do not lose the
                        // event while waiting for a valid energy sample.
                        if(!open && cableConnected>=2){
                            double price=getSharedPreferences(
                                T1TripRecorderService.PREFS,MODE_PRIVATE)
                                .getFloat(T1TripRecorderService.KWH_PRICE,0f);
                            openId=db.begin(data.timeMs,data.soc,
                                Math.max(0,price),raw);
                            open=true;
                        }
                        if(open){
                            db.sample(openId,data);
                            T1ChargeStore.Record latest=db.openSession();
                            boolean flow=T1ChargeMath.charging(
                                raw,data.volts,data.amps);
                            boolean currentFlow=T1ChargeMath.currentCharging(
                                raw,data.amps);
                            status("CABLE 1060=1 · SESIÓN "+openId+
                                " · "+(flow?"energía HV medible":
                                    currentFlow?"corriente de carga detectada; "+
                                        "falta voltaje para kWh":
                                    "cable conectado; flujo HV no validado")+
                                " · HV "+data.voltageSource+" ="+
                                T1ChargeStore.fmt(data.volts,1)+" V"+
                                " · 1064="+T1ChargeStore.fmt(data.amps,2)+" A"+
                                " · batería +"+(latest==null?"—":
                                    T1ChargeStore.fmt(latest.kwh,3))+
                                " kWh est. · "+
                                (T1ChargeMath.validSoc(data.soc)?
                                    T1ChargeStore.fmt(data.soc,1)+" %":"SOC no leído"),
                                raw,data.timeMs);
                        }else{
                            status("Cable 1060=1 · confirmando conexión "+
                                cableConnected+"/2; tensión 1063="+
                                T1ChargeStore.fmt(data.volts,1)+" V, "+
                                "corriente 1064="+
                                T1ChargeStore.fmt(data.amps,2)+" A",
                                raw,data.timeMs);
                        }
                    }else if(T1ChargeMath.unplugged(raw)){
                        cableConnected=0;
                        cableRemoved++;
                        if(open&&cableRemoved>=2){
                            if(db.finish(openId,data.timeMs)){
                                db.exportForCosts(this);
                                status("CARGA FINALIZADA · SQLite + CSV "+
                                    "DriveLab propio",raw,data.timeMs);
                            }
                            open=false;openId=-1;
                        }else{
                            status(open?"Cable desconectado · confirmando cierre":
                                "Cable desconectado (1060=0) · modo liviano",
                                raw,data.timeMs);
                        }
                    }else{
                        cableConnected=0;cableRemoved=0;
                        status("Estado cable 1060="+raw+
                            " NO VALIDADO · sin abrir ni cerrar sesión",
                            raw,data.timeMs);
                    }
                }catch(Exception ex){
                    errors++;
                    prefs().edit().putBoolean(
                        T1ChargeStore.LAST_SAMPLE_OK,false).apply();
                    status("Lectura OEM no disponible ("+errors+"): "+
                        ex.getClass().getSimpleName()+
                        " · sesión conservada, sin inventar kWh",
                        raw,System.currentTimeMillis());
                }
                long sleep=T1ChargeMath.plugged(raw)||open?
                    PLUG_POLL_MS:IDLE_POLL_MS;
                SystemClock.sleep(sleep);
            }
        }catch(Exception error){
            status("CARGAS AUTO · error de almacenamiento: "+
                error.getClass().getSimpleName(),
                -1,System.currentTimeMillis());
        }finally{
            running=false;
            // Open session remains DURABLY OPEN if ignition is cut mid-charge.
            // Restart resumes from last sample and skips all unobserved time.
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    @Override public void onDestroy(){
        stopped=true;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }
}
