package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Bridge's ONLY opt-in ignition service. Does NOT send any mosaic/cluster
 * command to the legacy DriveLab Auto Receiver, never launches that UI,
 * never changes T1 OEM services, and never touches a third-party app.
 * Independent QR/LAN listener is started after one bounded post-boot delay.
 * Window boot is deliberately disabled until Bridge owns a verified
 * standalone Freeform/Display 1 implementation and physical Jetour tests.
 */
public final class BootAutoService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!BootConfig.enabled(this)){stopSelf(startId);return START_NOT_STICKY;}
        NotificationManager nm=(NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            "drivelab_boot_auto","DriveLab Bridge",
            NotificationManager.IMPORTANCE_LOW));
        startForeground(7634,new Notification.Builder(this,"drivelab_boot_auto")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("DriveLab Bridge")
            .setContentText("Iniciando conexión independiente Xiaomi")
            .setOngoing(true).build());
        new Thread(()->{
            try{
                Thread.sleep(15000L);
                if(BootConfig.enabled(this)){
                    startForegroundService(new Intent(this,BootLinkService.class)
                        .setAction(BootLinkService.START));
                    BootConfig.prefs(this).edit().putString("last_status",
                        "BRIDGE: receptor Xiaomi solicitado al arrancar; "+
                        "Display 0/1 NO se abrió automáticamente. "+
                        "Sin DriveLab Auto.").apply();
                }
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
            }catch(Exception ex){
                BootConfig.prefs(this).edit().putString("last_status",
                    "Bridge no pudo iniciar enlace al encender: "+
                    ex.getClass().getSimpleName()).apply();
            }finally{
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
        },"DriveLab-Bridge-OneShot").start();
        return START_NOT_STICKY;
    }
}
