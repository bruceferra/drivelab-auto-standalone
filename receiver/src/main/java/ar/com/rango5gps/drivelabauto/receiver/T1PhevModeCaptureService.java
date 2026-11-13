package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User-started, strictly READ-ONLY background probe (180 s, ~3 s/sample).
 * Samples PHEV drive/SOC modes, regeneration, wiper/radar state and AVAS.
 * It does not call VehicleService write transaction 15 or touch OEM settings.
 * Recording persists when the user switches to the stock Jetour UI/Maps.
 */
public final class T1PhevModeCaptureService extends Service {
    static final String PREFS="t1_phev_mode_probe";
    static final String ACTION_START="t1.phev.probe.START";
    static final String ACTION_STOP="t1.phev.probe.STOP";
    static final String RUNNING="running",STATUS="status",LAST="last",SAMPLES="samples",FILE="file";
    static final String EVENTS="events",EVENT_FILE="events_file";
    private static final String CHANNEL="t1_phev_mode_probe_channel";
    private static final int NOTIFY_ID=7291;
    private static final long DURATION_MS=180000L,POLL_MS=3000L;
    private static final String PKG="com.autolink.app.vehicleservice";
    private static final String CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private static final String HAL_PKG="com.autolink.carservice";
    private static final String HAL_CLS="com.autolink.carservice.ALCarService";
    private static final String HAL_TOKEN="com.autolink.adapterinterface.car.IALCar";
    // Catalogo T1: candidatos de SOLO LECTURA. Su semantica (pisado/suelto)
    // no esta confirmada: nunca usarlos para autorizar una escritura 1043.
    private static final int HAL_BRAKE_PEDAL_STATUS=557867210;
    private static final int HAL_BRAKE_PEDAL_VALIDITY=557867238;
    private static final int[] FRONT_ZONES={557843300,557843301,557843302,557843303};
    private static final int[] REAR_ZONES={557843296,557843297,557843298,557843299};
    private volatile boolean stop,active;
    private Thread worker;
    private T1OemConsumptionCallbacks events;

    @Override public IBinder onBind(Intent intent){return null;}
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){
            stop=true;
            if(events!=null)events.stop();
            if(worker!=null)worker.interrupt();
            if(!active){
                prefs().edit().putBoolean(RUNNING,false).putString(STATUS,"Detenido").apply();
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(action))return START_NOT_STICKY;
        if(active)return START_NOT_STICKY;
        active=true;stop=false;
        notifyForeground();
        events=new T1OemConsumptionCallbacks(this);
        final boolean eventListening=events.start();
        prefs().edit().putBoolean(RUNNING,true).putInt(SAMPLES,0)
            .putString(STATUS,"Leyendo modos PHEV en segundo plano durante 3 minutos…"+
                (eventListening?" Eventos OEM también activos.":" Eventos OEM no iniciados."))
            .putString(LAST,"Aún sin muestra")
            .putString(EVENTS,"Esperando eventos OEM").apply();
        worker=new Thread(this::capture,"T1-PHEV-Mode-ReadOnly");
        worker.start();
        return START_NOT_STICKY;
    }
    private void notifyForeground(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            "DriveLab diagnóstico PHEV",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,NOTIFY_ID,
            new Intent(this,ReceiverVehicleActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CHANNEL)
            .setContentTitle("DriveLab · Diagnóstico de modos PHEV")
            .setContentText("Solo lectura · continúa fuera de la pantalla · 3 min")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(open).setOngoing(true).build();
        startForeground(NOTIFY_ID,n);
    }
    private static int readInt(IBinder b,int id)throws Exception{
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(TOKEN);in.writeInt(id);
            if(!b.transact(5,in,out,0))
                throw new IllegalStateException("readInt "+id+" rechazado");
            out.readException();return out.readInt();
        }finally{in.recycle();out.recycle();}
    }
    private static float readFloat(IBinder b,int id)throws Exception{
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(TOKEN);in.writeInt(id);
            if(!b.transact(6,in,out,0))
                throw new IllegalStateException("readFloat "+id+" rechazado");
            out.readException();return out.readFloat();
        }finally{in.recycle();out.recycle();}
    }
    private static String halInt(IBinder b,int id){
        if(b==null)return "";
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(HAL_TOKEN);
            in.writeInt(id);in.writeInt(0);
            if(!b.transact(2,in,out,0))return "";
            out.readException();
            return Integer.toString(out.readInt());
        }catch(Exception e){return "";}
        finally{in.recycle();out.recycle();}
    }
    private static String i(IBinder b,int id){
        try{return Integer.toString(readInt(b,id));}
        catch(Exception ignored){return "";}
    }
    private static String f(IBinder b,int id){
        try{
            float n=readFloat(b,id);
            return Float.isFinite(n)?Float.toString(n):"";
        }catch(Exception ignored){return "";}
    }
    private static String namePower(String raw){
        if("1".equals(raw))return "EV";
        if("2".equals(raw))return "HEV";
        if("3".equals(raw))return "EV MAX";
        return "sin mapa confirmado";
    }
    private static String nameSoc(String raw){
        if("2".equals(raw))return "SMART (confirmado T1)";
        if("4".equals(raw))return "FORZADO / CHARGE (confirmado T1)";
        if("3".equals(raw))return "AUTO (enum OEM; no confirmado físicamente)";
        return "sin mapa confirmado";
    }
    private static String nameRegen(String raw){
        if("1".equals(raw))return "BAJO";
        if("2".equals(raw))return "MEDIO";
        if("3".equals(raw))return "ALTO";
        return "sin mapa confirmado";
    }
    private static final String HEADER=
        "ts_epoch_ms,gear_1033_int,speed_2148_float,"+
        "soc_mode_1040_int,soc_target_1041_int,soc_target_1041_float,"+
        "power_mode_1043_int,charge_switch_1045_int,regen_1046_int,"+
        "soc_internal_1042_float,wiper_auto_2126_int,rear_wiper_2125_int,"+
        "radar_2049_int,radar_band_2107_int,pedestrian_2074_int,"+
        "pdc_front_557862926,pdc_rear_557862927,"+
        "front_zone_1,front_zone_2,front_zone_3,front_zone_4,"+
        "rear_zone_1,rear_zone_2,rear_zone_3,rear_zone_4,"+
        "brake_pedal_557867210_raw,brake_validity_557867238_raw,foot_brake_3011_int\n";
    private void capture(){
        ServiceConnection connection=null,halConnection=null;
        boolean bound=false,halBound=false;IBinder halBinder=null;
        String result="Finalizado";int samples=0;File file=null;
        try{
            file=new File(T1TripRecorderService.tripDir(this),
                "phev_modes_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT)
                .format(new Date())+".csv");
            prefs().edit().putString(FILE,file.getAbsolutePath()).apply();
            final CountDownLatch latch=new CountDownLatch(1);
            final AtomicReference<IBinder> remote=new AtomicReference<>();
            connection=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName n,IBinder b){
                    remote.set(b);latch.countDown();
                }
                @Override public void onServiceDisconnected(ComponentName n){
                    remote.set(null);latch.countDown();
                }
                @Override public void onNullBinding(ComponentName n){latch.countDown();}
                @Override public void onBindingDied(ComponentName n){
                    remote.set(null);latch.countDown();
                }
            };
            bound=bindService(new Intent().setComponent(
                new ComponentName(PKG,CLS)),connection,0);
            if(!bound||!latch.await(4000,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("VehicleService no responde");
            IBinder binder=remote.get();
            if(binder==null||!binder.isBinderAlive()||
                !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new IllegalStateException("Interfaz OEM no disponible");
            // Radar sector HAL is optional: absence must not stop reading PHEV modes.
            try{
                final CountDownLatch halReady=new CountDownLatch(1);
                final AtomicReference<IBinder> halRemote=new AtomicReference<>();
                halConnection=new ServiceConnection(){
                    @Override public void onServiceConnected(ComponentName n,IBinder b){
                        halRemote.set(b);halReady.countDown();
                    }
                    @Override public void onServiceDisconnected(ComponentName n){
                        halRemote.set(null);halReady.countDown();
                    }
                    @Override public void onNullBinding(ComponentName n){halReady.countDown();}
                    @Override public void onBindingDied(ComponentName n){halReady.countDown();}
                };
                halBound=bindService(new Intent().setComponent(
                    new ComponentName(HAL_PKG,HAL_CLS)),halConnection,0);
                if(halBound&&halReady.await(2000,TimeUnit.MILLISECONDS)){
                    IBinder candidate=halRemote.get();
                    if(candidate!=null&&candidate.isBinderAlive()&&
                        HAL_TOKEN.equals(candidate.getInterfaceDescriptor()))
                        halBinder=candidate;
                }
            }catch(Exception ignored){}
            final IBinder parking=halBinder;
            try(FileOutputStream out=new FileOutputStream(file,false)){
                out.write(HEADER.getBytes(StandardCharsets.UTF_8));
                long until=SystemClock.elapsedRealtime()+DURATION_MS;
                while(!stop&&SystemClock.elapsedRealtime()<until){
                    if(!binder.isBinderAlive())
                        throw new IllegalStateException("Servicio OEM desconectado");
                    String gear=i(binder,1033),speed=f(binder,2148);
                    String socMode=i(binder,1040),
                        socTarget=i(binder,1041),socTargetFloat=f(binder,1041),
                        power=i(binder,1043),charge=i(binder,1045),
                        regen=i(binder,1046),soc=f(binder,1042),
                        wiper=i(binder,2126),rear=i(binder,2125),
                        radar=i(binder,2049),band=i(binder,2107),avas=i(binder,2074);
                    String[] fronts=new String[4],rears=new String[4];
                    for(int k=0;k<4;k++){
                        fronts[k]=halInt(parking,FRONT_ZONES[k]);
                        rears[k]=halInt(parking,REAR_ZONES[k]);
                    }
                    String brakePedal=halInt(parking,HAL_BRAKE_PEDAL_STATUS),
                        brakeValidity=halInt(parking,HAL_BRAKE_PEDAL_VALIDITY),
                        footBrake=i(binder,3011);
                    String frontPdc=halInt(parking,557862926),
                        rearPdc=halInt(parking,557862927);
                    String row=System.currentTimeMillis()+","+gear+","+speed+","+
                        socMode+","+socTarget+","+socTargetFloat+","+power+","+
                        charge+","+regen+","+soc+","+wiper+","+rear+","+
                        radar+","+band+","+avas+","+frontPdc+","+rearPdc+","+
                        String.join(",",fronts)+","+String.join(",",rears)+","+
                    brakePedal+","+brakeValidity+","+footBrake+"\n";
                    out.write(row.getBytes(StandardCharsets.UTF_8));out.flush();
                    samples++;
                    String last="Muestras: "+samples+" · 1043="+power+" ("+
                        namePower(power)+") · 1040="+socMode+" ("+nameSoc(socMode)+
                        ") · 1041 int/float="+socTarget+"/"+socTargetFloat+
                        " · 1045="+charge+" · 1046="+regen+" ("+
                        nameRegen(regen)+") · SOC="+soc+
                        " · wiper 2126="+wiper+" · radar 2049="+radar+
                        " / banda 2107="+band+" · AVAS 2074="+avas+
                        " · freno candidato 557867210="+
                        (brakePedal.isEmpty()?"SIN LECTURA":brakePedal)+
                        " / validez candidata 557867238="+
                        (brakeValidity.isEmpty()?"SIN LECTURA":brakeValidity)+
                        " (crudos NO calibrados)" +
                        " · freno pie 3011="+(footBrake.isEmpty()?"SIN LECTURA":footBrake)+
                        " (0 suelto / 1 pisado observado por usuario)" +
                        " · velocidad="+speed+
                        " · PDC adelante="+String.join("/",fronts)+
                        " atrás="+String.join("/",rears)+
                        " (lecturas crudas, NO metros)";
                    String eventStatus=events==null?
                        "Sin escucha de eventos":events.snapshot();
                    prefs().edit().putInt(SAMPLES,samples).putString(LAST,last)
                        .putString(EVENTS,eventStatus)
                        .putString(STATUS,"Grabando lecturas · segundo plano activo").apply();
                    try{Thread.sleep(POLL_MS);}catch(InterruptedException e){if(stop)break;}
                }
            }
            result=stop?"Registro detenido manualmente":
                "Registro completo (3 min). CSV interno conservado.";
        }catch(Exception e){
            result="No se pudo completar: "+e.getClass().getSimpleName()+
                (e.getMessage()==null?"":(" · "+e.getMessage()));
        }finally{
            if(events!=null)events.stop();
            if(bound&&connection!=null)try{unbindService(connection);}catch(Exception ignored){}
            if(halBound&&halConnection!=null)
                try{unbindService(halConnection);}catch(Exception ignored){}
            File callbackFile=events==null?null:events.lastLog();
            prefs().edit().putBoolean(RUNNING,false).putInt(SAMPLES,samples)
                .putString(STATUS,result)
                .putString(EVENTS,events==null?"Sin evento":events.snapshot())
                .putString(EVENT_FILE,callbackFile==null?"":callbackFile.getAbsolutePath())
                .apply();
            active=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    @Override public void onDestroy(){
        stop=true;
        if(events!=null)events.stop();
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }
}
