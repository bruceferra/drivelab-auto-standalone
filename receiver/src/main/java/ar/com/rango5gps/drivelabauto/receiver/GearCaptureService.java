package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User-triggered, 35-second read-only gear sampler.
 * Foreground service keeps recording when the OEM reverse camera takes focus.
 * This code contains NO car-setting write transaction, no mirror movement,
 * no boot scheduling and no connection to the navigation receiver service.
 */
public final class GearCaptureService extends Service {
    public static final String ACTION_CAPTURE=
        "ar.com.rango5gps.drivelabauto.receiver.CAPTURE_GEARS";
    public static final String ACTION_CANCEL=
        "ar.com.rango5gps.drivelabauto.receiver.CANCEL_GEAR_CAPTURE";
    public static final String PREFS="t1_gear_capture";
    public static final String KEY_STATUS="status";
    public static final String KEY_LOG="log";
    public static final String KEY_RUNNING="running";
    private static final String CHANNEL="t1_gear_readonly";
    private static final int NOTIFICATION_ID=7240;
    private static final long CAPTURE_MS=35000L, POLL_MS=250L;
    private static final String SERVICE_PKG="com.autolink.app.vehicleservice";
    private static final String SERVICE_CLS="com.autolink.app.vehicleservice.VehicleService";
    private static final String TOKEN="com.autolink.app.vehicleservice.IVehicleControl";
    private volatile boolean cancel=false,running=false;

    @Override public IBinder onBind(Intent intent){return null;}
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_CANCEL.equals(action)){
            cancel=true;
            prefs().edit().putString(KEY_STATUS,"Registro cancelado").apply();
            if(!running)stopSelf();
            return START_NOT_STICKY;
        }
        if(!ACTION_CAPTURE.equals(action))return START_NOT_STICKY;
        if(running)return START_NOT_STICKY;
        cancel=false;
        running=true;
        ensureNotification();
        prefs().edit().putBoolean(KEY_RUNNING,true)
            .putString(KEY_STATUS,"Grabando cambios de marcha durante 35 segundos…")
            .putString(KEY_LOG,"").apply();
        new Thread(this::capture,"T1-Gear-Camera-ReadOnly").start();
        return START_NOT_STICKY;
    }

    private void ensureNotification(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Diagnóstico de marcha T1",NotificationManager.IMPORTANCE_LOW));
        }
        Intent open=new Intent(this,ReceiverVehicleActivity.class);
        PendingIntent go=PendingIntent.getActivity(this,7240,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?
            new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        startForeground(NOTIFICATION_ID,b
            .setContentTitle("DriveLab · Lectura de marchas")
            .setContentText("Sólo lectura · finaliza automáticamente en 35 s")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(go).setOngoing(true).build());
    }
    private static String stamp(){
        return new SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT).format(new Date());
    }
    private static String explain(int gear){
        switch(gear){
            case 1:return "P (verificado)";
            case 3:return "N (observado)";
            case 4:return "D (observado)";
            case 2:return "R (CANDIDATO por APK)";
            default:return "NO MAPEADO";
        }
    }
    private void capture(){
        ServiceConnection conn=null;
        boolean bound=false;
        StringBuilder events=new StringBuilder();
        int last=Integer.MIN_VALUE;
        int count=0,readErrors=0;
        long deadline=SystemClock.elapsedRealtime()+CAPTURE_MS;
        String finalStatus="Registro finalizado";
        try{
            final CountDownLatch latch=new CountDownLatch(1);
            final AtomicReference<IBinder> remote=new AtomicReference<>();
            conn=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName n,IBinder binder){
                    remote.set(binder);latch.countDown();
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
                new ComponentName(SERVICE_PKG,SERVICE_CLS)),conn,0);
            if(!bound||!latch.await(4,TimeUnit.SECONDS))
                throw new IllegalStateException("VehicleService no disponible");
            IBinder binder=remote.get();
            if(binder==null || !TOKEN.equals(binder.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no disponible");
            while(!cancel && SystemClock.elapsedRealtime()<deadline){
                try{
                    if(!binder.isBinderAlive())
                        throw new IllegalStateException("Binder desconectado");
                    int gear=readGear(binder);
                    if(gear!=last){
                        if(count<32){
                            events.append(stamp()).append(" · 1033=")
                                .append(gear).append(" · ")
                                .append(explain(gear)).append("\n");
                            count++;
                        }
                        last=gear;
                        prefs().edit().putString(KEY_LOG,events.toString()).apply();
                    }
                    readErrors=0;
                }catch(Exception err){
                    readErrors++;
                    if(readErrors>=4)throw err;
                }
                SystemClock.sleep(POLL_MS);
            }
            if(cancel)finalStatus="Registro detenido por el usuario";
            else if(count==0)finalStatus="Sin cambios detectados (no hay lecturas)";
            else finalStatus="Lectura finalizada: "+count+" cambios de marcha";
        }catch(Exception ex){
            finalStatus="No se pudo completar: "+ex.getClass().getSimpleName()+
                (ex.getMessage()==null?"":" · "+ex.getMessage());
        }finally{
            if(bound&&conn!=null)try{unbindService(conn);}catch(Exception ignored){}
            prefs().edit().putString(KEY_STATUS,finalStatus)
                .putBoolean(KEY_RUNNING,false)
                .putString(KEY_LOG,events.toString()).apply();
            running=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    private static int readGear(IBinder binder)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(TOKEN);
            data.writeInt(1033);
            if(!binder.transact(5,data,reply,0))
                throw new IllegalStateException("Lectura 1033 rechazada");
            reply.readException();
            return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    @Override public void onDestroy(){
        cancel=true;
        super.onDestroy();
    }
}
