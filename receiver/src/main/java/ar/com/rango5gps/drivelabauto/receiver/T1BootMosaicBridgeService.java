package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.view.Display;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated, bounded companion launcher executor. Reuses Receiver's proven
 * ADB pairing without exposing shell to Boot or changing T1 Launcher.
 * Never force-stops apps or changes vehicle/cluster settings.
 */
public final class T1BootMosaicBridgeService extends Service {
    private static final String CHANNEL="drivelab_boot_mosaic_bridge";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    static final String PREFS="drivelab_center_mosaic_v1";
    static final String RESULT_ACTION=
        "ar.com.rango5gps.drivelabauto.boot.action.MOSAIC_RESULT";
    private int requestId;
    static void report(Context context,String result){
        context.getSharedPreferences("drivelab_boot_bridge_result",MODE_PRIVATE)
            .edit().putString("last_result",result)
            .putLong("at",System.currentTimeMillis()).apply();
        try{
            Intent reply=new Intent(RESULT_ACTION);
            reply.setClassName(T1BootMosaicBridgeReceiver.BOOT_PACKAGE,
                "ar.com.rango5gps.drivelabauto.boot.BootResultReceiver");
            reply.putExtra("result",result);
            context.sendBroadcast(reply,
                T1BootMosaicBridgeReceiver.PERMISSION);
        }catch(Exception ignored){}
    }
    /**
     * Receiver is the ONLY authoritative persisted Display 1 layout.
     * Send an exact signed snapshot back to Boot for display, never
     * synchronize by guessing from Boot's duplicated 50/50 defaults.
     */
    static void reportClusterState(Context context,String note){
        android.content.SharedPreferences prefs=T1ClusterWazeRouter.prefs(context);
        int ratio=T1ClusterLayoutPolicy.number(context,
            T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
        boolean dual=T1ClusterLayoutPolicy.savedDualSplit(context);
        String a=T1ClusterLayoutPolicy.firstPackage(context);
        String b=T1ClusterLayoutPolicy.secondPackage(context);
        String source="RECEIVER · DISPLAY 1 · "+(dual?"DOS PANELES":"UN PANEL")+
            " · "+a+(dual?" + "+b:"")+
            (dual?" · "+ratio+"/"+(100-ratio):" · 100%")+
            "\n"+note;
        try{
            Intent reply=new Intent(RESULT_ACTION);
            reply.setClassName(T1BootMosaicBridgeReceiver.BOOT_PACKAGE,
                "ar.com.rango5gps.drivelabauto.boot.BootResultReceiver");
            reply.putExtra("result",source);
            reply.putExtra("cluster_snapshot",true);
            reply.putExtra("cluster_count",dual?2:1);
            reply.putExtra("cluster_percent",ratio);
            reply.putExtra("cluster_pkg0",a);
            reply.putExtra("cluster_pkg1",b);
            reply.putExtra("cluster_enabled",
                T1ClusterWazeRouter.enabled(context));
            reply.putExtra("cluster_display_id",
                prefs.getInt(T1ClusterWazeRouter.DISPLAY_ID,-1));
            context.sendBroadcast(reply,T1BootMosaicBridgeReceiver.PERMISSION);
        }catch(Exception error){
            report(context,"NO SE PUDO LEER ESTADO DE DISPLAY 1: "+
                error.getClass().getSimpleName());
        }
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=(NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            CHANNEL,"DriveLab Boot · mosaico",
            NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,CHANNEL)
            .setContentTitle("DriveLab Boot · iniciando mosaico")
            .setContentText("Display 0 · T1 Launcher no se modifica")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build();
        startForeground(7652,n);
    }
    @Override public int onStartCommand(Intent incoming,int flags,int startId){
        if(!BUSY.compareAndSet(false,true)){
            report(this,"MOSAICO OCUPADO: ya hay una operación en curso");
            // Do not stop the active foreground worker that owns the
            // earlier (and still running) ADB resize operation.
            return START_NOT_STICKY;
        }
        try{
            if(incoming==null)throw new IllegalArgumentException("Sin pedido");
            if(incoming.getBooleanExtra("cluster_state_only",false)){
                // Read-only, no window changes, and never launch a map.
                reportClusterState(this,"Estado real guardado; "+
                    "no confirma visibilidad de dos ventanas.");
                BUSY.set(false);
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            if(incoming.getBooleanExtra("reveal_only",false)){
                // Only identify and move the tasks that DriveLab already
                // created on Display 0. Never force-stop a running app or
                // start duplicates to make the layout visible.
                int count=incoming.getIntExtra("reveal_count",-1);
                if(count<1||count>3)
                    throw new IllegalArgumentException(
                        "Cantidad de ventanas para recuperar inválida");
                String[] packages=new String[count];
                HashSet<String> different=new HashSet<>();
                for(int i=0;i<count;i++){
                    String pkg=incoming.getStringExtra("pkg"+i);
                    if(pkg==null||!different.add(pkg)||
                       !(pkg.equals(getPackageName())||
                         T1CenterUserPackagePolicy.allowed(pkg)))
                        throw new SecurityException(
                            "Paquete no autorizado en panel "+(i+1));
                    packages[i]=pkg;
                }
                T1CenterBootReveal.request(this,packages,result->{
                    try{report(this,result);}
                    finally{
                        BUSY.set(false);
                        stopSelf(startId);
                    }
                });
                return START_NOT_STICKY;
            }
            if(incoming.getBooleanExtra("cluster_only",false)){
                // The standalone Boot "PROBAR DISPLAY 1" button intentionally
                // does NOT launch another Receiver Activity, Freeform task or
                // the Display 0 Cockpit. Keep configuration in Boot foreground.
                final int count=incoming.getIntExtra("cluster_count",-1);
                final int ratio=incoming.getIntExtra("cluster_percent",-1);
                final String[] packages=new String[count==2?2:1];
                packages[0]=incoming.getStringExtra("cluster_pkg0");
                if(count==2)packages[1]=incoming.getStringExtra("cluster_pkg1");
                new Thread(()->{
                    try{
                        String result=T1BootClusterLayout.apply(this,count,ratio,
                            packages,false,false);
                        reportClusterState(this,"PRUEBA DISPLAY 1: "+result+
                            "\nBoot administra el diseño; Receiver ejecuta "+
                            "sin abrir otra pantalla de ajustes.");
                    }catch(Exception error){
                        report(this,"DISPLAY 1 NO ABIERTO: "+
                            error.getClass().getSimpleName()+": "+error.getMessage());
                    }finally{
                        BUSY.set(false);
                        stopSelf(startId);
                    }
                },"DriveLab-Boot-Manual-Display1-Only").start();
                return START_NOT_STICKY;
            }
            int mode=incoming.getIntExtra("mode",-1);
            int percent=incoming.getIntExtra("percent",-1);
            if(mode<0||mode>4||percent<1||percent>99)
                throw new IllegalArgumentException("Distribución inválida");
            int count=mode==4?1:mode==3?2:3;
            String[] packages=new String[count];
            HashSet<String> distinct=new HashSet<>();
            for(int i=0;i<count;i++){
                String pkg=incoming.getStringExtra("pkg"+i);
                if(pkg==null||!pkg.matches(
                    "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")||
                    !(pkg.equals(getPackageName())||
                        T1CenterUserPackagePolicy.allowed(pkg))||
                    !distinct.add(pkg))
                    throw new SecurityException("Aplicación del panel "+
                        (i+1)+" no autorizada");
                packages[i]=pkg;
            }
            DisplayManager dm=(DisplayManager)getSystemService(DISPLAY_SERVICE);
            Display display=dm==null?null:dm.getDisplay(Display.DEFAULT_DISPLAY);
            if(display==null)throw new IllegalStateException(
                "Display 0 no disponible");
            Point size=new Point();
            display.getRealSize(size);
            if(size.x<800||size.y<480||size.x>4096||size.y>2160)
                throw new IllegalStateException("Pantalla central incorrecta");
            T1CenterMosaicGeometry.Tile[] tiles=mode==4?
                new T1CenterMosaicGeometry.Tile[]{
                    new T1CenterMosaicGeometry.Tile(0,0,size.x,size.y)}:
                T1CenterMosaicGeometry.layout(size.x,size.y,mode,percent);
            if(tiles.length!=count)
                throw new IllegalStateException("Cantidad de paneles inválida");
            // Boot can reserve a small band INSIDE its own Receiver
            // freeform cockpit window for Jetour's grey maximize caption.
            // Does not touch OEM window manager, bottom nav dock or HOME.
            int caption=incoming.getIntExtra("caption_safe_dp",44);
            if(caption<0||caption>88)
                throw new IllegalArgumentException("Separación Freeform inválida");
            if(!getSharedPreferences("drivelab_freeform_ui_v1",MODE_PRIVATE)
                .edit().putInt("caption_safe_dp",caption).commit())
                throw new IllegalStateException("No se guardó el margen de controles");
            boolean cluster=incoming.getBooleanExtra("cluster_enabled",false);
            boolean fromBoot=incoming.getBooleanExtra("from_boot",false);
            boolean confirmed=incoming.getBooleanExtra("cluster_confirmed",false);
            int clusterCount=incoming.getIntExtra("cluster_count",-1);
            int clusterPercent=incoming.getIntExtra("cluster_percent",-1);
            String[] clusterPackages=cluster?new String[]{
                incoming.getStringExtra("cluster_pkg0"),
                incoming.getStringExtra("cluster_pkg1")}:
                new String[0];
            final int finalMode=mode,finalPercent=percent;
            T1CenterSavedFreeformMosaic.apply(this,packages,tiles,count,result->{
                boolean prepared=result.contains(
                    "ESTADO: VENTANAS PREPARADAS.");
                try{
                    if(prepared){
                        SharedPreferences.Editor edit=getSharedPreferences(
                            PREFS,MODE_PRIVATE).edit()
                            .putInt("mode",finalMode)
                            .putInt("percent",finalPercent);
                        for(int i=0;i<packages.length;i++)
                            edit.putString("pkg"+i,packages[i]);
                        edit.apply();
                    }
                    String center=(prepared?
                        "DISPLAY 0 · APLICACIÓN SOLICITADA: ":
                        "DISPLAY 0 · NO CONFIRMADO: ")+result+
                        "\nMargen interior de protección de controles: "+
                        caption+" dp. Cabecera Freeform y barra inferior "+
                        "del Jetour siguen bajo control del sistema.";
                    if(!cluster){
                        report(this,center);
                        BUSY.set(false);
                        stopSelf(startId);
                        return;
                    }
                    // Cluster work must not block Receiver UI. A separate
                    // stage keeps one BUSY owner until both displays finish.
                    new Thread(()->{
                        try{
                            String secondary=T1BootClusterLayout.apply(
                                this,clusterCount,clusterPercent,
                                clusterPackages,fromBoot,confirmed);
                            reportClusterState(this,center+"\n"+secondary);
                        }catch(Exception e){
                            report(this,center+"\nDISPLAY 1 NO CONFIRMADO: "+
                                e.getClass().getSimpleName()+": "+e.getMessage());
                        }finally{
                            BUSY.set(false);
                            stopSelf(startId);
                        }
                    },"DriveLab-Boot-Display1-After-Center").start();
                }catch(Exception e){
                    report(this,"ARRANQUE INCOMPLETO: "+
                        e.getClass().getSimpleName()+": "+e.getMessage());
                    BUSY.set(false);
                    stopSelf(startId);
                }
            });
        }catch(Exception e){
            report(this,"NO SE ABRIÓ MOSAICO: "+
                e.getClass().getSimpleName()+": "+e.getMessage());
            BUSY.set(false);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }
}
