package ar.com.rango5gps.drivelabauto.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.util.Log;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.IBinder;
import android.net.Uri;
import android.widget.Toast;
import ar.com.rango5gps.drivelabauto.common.MapLinks;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-initiated navigation share from Uber/Maps, delegated from a transparent
 * Activity so Sender does not become/stay visible on the Xiaomi.
 * Foreground dataSync only during work; no background monitoring of Uber.
 */
public final class RouteDispatchService extends Service {
    static final String ACTION_SEND_ROUTE="drivelab.sender.ROUTE_HEADLESS";
    static final String EXTRA_RAW="navigation_share_text";
    private static final String CHANNEL="sender_route_dispatch";
    private static final int FOREGROUND_ID=9432, FAILURE_ID=9433;
    static final String STATE_PREFS="sender_route_dispatch_state";
    static final String LAST_STATE="last_state";
    private ExecutorService queue;
    @Override public void onCreate(){
        super.onCreate();
        queue=Executors.newSingleThreadExecutor();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            CHANNEL,"DriveLab destinos",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private void mark(String state){
        getSharedPreferences(STATE_PREFS,MODE_PRIVATE).edit()
            .putString(LAST_STATE,state).putLong("timestamp",System.currentTimeMillis())
            .apply();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // Android 14+ requires the exact FOREGROUND_SERVICE_DATA_SYNC permission
        // for a dataSync service. Never let a SecurityException kill Sender.
        try{
            Intent open=new Intent(this,SenderActivity.class);
            PendingIntent tap=PendingIntent.getActivity(this,FOREGROUND_ID,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            startForeground(FOREGROUND_ID,new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("DriveLab · enviando destino")
                .setContentText("Envío cifrado a T1 en segundo plano")
                .setContentIntent(tap)
                .setOngoing(true).build());
        }catch(RuntimeException error){
            Log.e("DriveLabRoute","No se pudo iniciar servicio de ruta",error);
            mark("SERVICIO BLOQUEADO · "+error.getClass().getSimpleName()+
                " · Android impidió el envío en segundo plano");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        mark("Procesando destino en segundo plano…");
        final String raw=intent==null?null:intent.getStringExtra(EXTRA_RAW);
        try{
            queue.execute(()->{
                try{process(raw);}
                catch(Exception error){
                    Log.e("DriveLabRoute","Error procesando navegación",error);
                    failure("No se pudo procesar enlace de navegación",null);
                }finally{stopSelf(startId);}
            });
        }catch(RuntimeException error){
            mark("SERVICIO BLOQUEADO · "+error.getClass().getSimpleName());
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }
    private void process(String raw){
        if(raw==null||raw.isEmpty()||raw.length()>8192){
            failure("No llegó un enlace de destino válido desde Uber o Maps",null);
            return;
        }
        MapLinks.Destination d=MapLinks.resolve(raw);
        if(d==null){
            failure("Uber/Maps no entregó destino verificable; no se inventan coordenadas",null);
            return;
        }
        boolean only=SenderClient.prefs(this).getBoolean("only_cluster",true);
        if(!SenderClient.configured(this)){
            failure("El Xiaomi no está vinculado con Receiver de T1",d);
            return;
        }
        // Do not open Waze or DriveLab on the phone in SOLO CLÚSTER mode.
        SenderClient.Delivery sent=SenderClient.send(this,d.kind,d.value);
        if(!sent.accepted)failure(sent.status,d);
        else mark("T1 CONFIRMÓ · destino enviado en segundo plano");
        if(!only){
            // The user deliberately requested navigation on both devices.
            Intent waze=new Intent(Intent.ACTION_VIEW,d.wazeUri());
            waze.setPackage("com.waze");
            waze.addCategory(Intent.CATEGORY_BROWSABLE);
            waze.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try{startActivity(waze);}
            catch(Exception ex){failure("Android bloqueó abrir Waze automáticamente en Xiaomi",d);}
        }
        // Successful SOLO CLÚSTER dispatch is intentionally silent.
    }
    private void failure(String reason,MapLinks.Destination destination){
        mark("FALLÓ EL ENVÍO · "+reason);
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm==null)return;
        try{
        PendingIntent tap;
        if(destination!=null){
            Intent waze=new Intent(Intent.ACTION_VIEW,destination.wazeUri())
                .setPackage("com.waze");
            waze.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tap=PendingIntent.getActivity(this,FAILURE_ID,waze,
                PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        }else{
            tap=PendingIntent.getActivity(this,FAILURE_ID,
                new Intent(this,SenderActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        }
        nm.notify(FAILURE_ID,new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("DriveLab · destino no confirmado")
            .setContentText(reason)
            .setStyle(new Notification.BigTextStyle().bigText(reason+
                "\nTocá para revisar o abrir Waze manualmente."))
            .setAutoCancel(true).setContentIntent(tap).build());
        }catch(RuntimeException ex){
            // Notifications may be restricted by Xiaomi; never crash Sender
            // a second time while trying to report the first error.
            Log.w("DriveLabRoute","No se pudo mostrar aviso de error",ex);
        }
    }
    @Override public void onDestroy(){
        if(queue!=null)queue.shutdownNow();
        super.onDestroy();
    }
}
