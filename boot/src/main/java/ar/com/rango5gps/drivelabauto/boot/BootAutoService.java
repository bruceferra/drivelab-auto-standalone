package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** One controlled 15s post-boot handoff after OEM and Receiver initialization.
 * Does not open its own fullscreen activity at ignition, and never loops.
 */
public final class BootAutoService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        NotificationManager nm=(NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            "drivelab_boot_auto","DriveLab Boot",
            NotificationManager.IMPORTANCE_LOW));
        startForeground(7634,new Notification.Builder(this,"drivelab_boot_auto")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("DriveLab Boot · mosaico")
            .setContentText("Preparando las pantallas configuradas")
            .setOngoing(true).build());
        new Thread(()->{
            try{
                Thread.sleep(15000L);
                if(BootConfig.enabled(this))BootConfig.send(this,true);
            }catch(Exception ex){
                BootConfig.prefs(this).edit().putString("last_status",
                    "No se pudo iniciar mosaico al encender: "+
                    ex.getClass().getSimpleName()+": "+ex.getMessage()).apply();
            }finally{
                stopSelf(startId);
            }
        },"DriveLab-Boot-Once").start();
        return START_NOT_STICKY;
    }
}
