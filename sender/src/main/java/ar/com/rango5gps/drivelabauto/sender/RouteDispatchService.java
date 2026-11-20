package ar.com.rango5gps.drivelabauto.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.util.Log;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.Context;
import android.os.IBinder;
import android.net.Uri;
import android.widget.Toast;
import ar.com.rango5gps.drivelabauto.common.MapLinks;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;
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
    static void mark(Context context,String state){
        context.getSharedPreferences(STATE_PREFS,Context.MODE_PRIVATE).edit()
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
            mark(this,"SERVICIO BLOQUEADO · "+error.getClass().getSimpleName()+
                " · Android impidió el envío en segundo plano");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        mark(this,"Procesando destino en segundo plano…");
        final String raw=intent==null?null:intent.getStringExtra(EXTRA_RAW);
        try{
            queue.execute(()->{
                try{process(this,raw);}
                catch(Exception error){
                    Log.e("DriveLabRoute","Error procesando navegación",error);
                    failure(this,"No se pudo procesar enlace de navegación",null);
                }finally{stopSelf(startId);}
            });
        }catch(RuntimeException error){
            mark(this,"SERVICIO BLOQUEADO · "+error.getClass().getSimpleName());
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }
    /**
     * Single delivery path for real Uber and Android Shares. A visible,
     * transparent RouteActivity now calls this directly on a worker thread,
     * so Xiaomi/Android 16 cannot discard a foreground-service launch
     * immediately after the trampoline finishes. The legacy service still
     * delegates here for backward compatibility.
     */
    static void process(Context context,String raw){
        if(raw==null||raw.isEmpty()||raw.length()>8192){
            failure(context,"No llegó un enlace de Uber/Maps; "+
                "el intent externo no contenía texto ni URL",null);
            return;
        }
        mark(context,"ENLACE EXTERNO RECIBIDO · resolviendo destino…");
        MapLinks.Destination d=MapLinks.resolve(raw);
        if(d==null){
            failure(context,"Uber/Maps entregó un enlace que no contiene "+
                "un destino verificable o su redirección no resolvió",null);
            return;
        }
        if(!SenderClient.configured(context)){
            failure(context,"No hay clave QR guardada en Sender; "+
                "no se modificó la vinculación",d);
            return;
        }
        String mode=SenderRouteTarget.selected(context);
        String wireKind=BridgeRouteTarget.sendKind(d.kind,mode);
        mark(context,"DESTINO IDENTIFICADO · conectando con Bridge…");
        SenderClient.Delivery sent=SenderClient.send(context,wireKind,d.value);
        // Transient Wi-Fi or socket startup races are common when the user
        // jumps from Uber to a transparent activity. Retry transport once.
        // Never re-send a 'not_ready' response or an accepted route.
        if(!sent.accepted&&sent.status.startsWith("SIN CONEXIÓN BRIDGE")){
            mark(context,"CONEXIÓN INTERRUMPIDA · reintentando una vez…");
            try{Thread.sleep(650L);}
            catch(InterruptedException stopped){
                Thread.currentThread().interrupt();
                failure(context,"Envío interrumpido por Android",d);
                return;
            }
            sent=SenderClient.send(context,wireKind,d.value);
        }
        if(!sent.accepted){
            failure(context,sent.status,d);
            return;
        }
        mark(context,"BRIDGE CONFIRMÓ DESTINO · "+
            SenderRouteTarget.label(mode)+" · recibido por la Jetour");
        boolean only=SenderClient.prefs(context).getBoolean("only_cluster",true);
        if(!only){
            boolean google=BridgeRouteTarget.MAPS.equals(mode);
            Uri phoneUri=google?
                Uri.parse("google.navigation:q="+
                    Uri.encode(d.value,"coord".equals(d.kind)?",":"")+
                    "&mode=d"):d.wazeUri();
            Intent phone=new Intent(Intent.ACTION_VIEW,phoneUri)
                .setPackage(google?"com.google.android.apps.maps":"com.waze");
            phone.addCategory(Intent.CATEGORY_BROWSABLE);
            phone.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try{context.startActivity(phone);}
            catch(Exception ex){
                mark(context,"BRIDGE RECIBIÓ EL DESTINO; Android bloqueó "+
                    "abrir el mapa adicional en Xiaomi ("+
                    ex.getClass().getSimpleName()+")");
            }
        }
    }
    private static void failure(Context context,String reason,
                                MapLinks.Destination destination){
        mark(context,"FALLÓ ENVÍO EXTERNO · "+reason);
        NotificationManager nm=context.getSystemService(NotificationManager.class);
        if(nm==null)return;
        try{
            nm.createNotificationChannel(new NotificationChannel(
                CHANNEL,"DriveLab destinos",NotificationManager.IMPORTANCE_LOW));
            PendingIntent tap;
            if(destination!=null){
                String target=SenderRouteTarget.selected(context);
                boolean google=BridgeRouteTarget.MAPS.equals(target);
                Uri uri=google?
                    Uri.parse("google.navigation:q="+Uri.encode(destination.value)+
                        "&mode=d"):destination.wazeUri();
                Intent fallback=new Intent(Intent.ACTION_VIEW,uri)
                    .setPackage(google?"com.google.android.apps.maps":"com.waze");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                tap=PendingIntent.getActivity(context,FAILURE_ID,fallback,
                    PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            }else{
                tap=PendingIntent.getActivity(context,FAILURE_ID,
                    new Intent(context,SenderActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            }
            nm.notify(FAILURE_ID,new Notification.Builder(context,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("DriveLab · destino externo no enviado")
                .setContentText(reason)
                .setStyle(new Notification.BigTextStyle().bigText(reason+
                    "\nTocá para abrir el destino o revisar Sender."))
                .setAutoCancel(true).setContentIntent(tap).build());
        }catch(RuntimeException ex){
            Log.w("DriveLabRoute","No se pudo notificar fallo",ex);
        }
    }
    @Override public void onDestroy(){
        if(queue!=null)queue.shutdownNow();
        super.onDestroy();
    }
}
