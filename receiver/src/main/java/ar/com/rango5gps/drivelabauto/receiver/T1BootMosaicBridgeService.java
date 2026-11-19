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
            final int finalMode=mode,finalPercent=percent;
            T1CenterSavedFreeformMosaic.apply(this,packages,tiles,count,result->{
                try{
                    boolean prepared=result.contains(
                        "ESTADO: VENTANAS PREPARADAS.");
                    if(prepared){
                        SharedPreferences.Editor edit=getSharedPreferences(
                            PREFS,MODE_PRIVATE).edit()
                            .putInt("mode",finalMode)
                            .putInt("percent",finalPercent);
                        for(int i=0;i<packages.length;i++)
                            edit.putString("pkg"+i,packages[i]);
                        edit.apply();
                    }
                    report(this,(prepared?
                        "APLICACIÓN SOLICITADA: ":"NO CONFIRMADO: ")+result);
                }finally{
                    BUSY.set(false);
                    stopSelf();
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
