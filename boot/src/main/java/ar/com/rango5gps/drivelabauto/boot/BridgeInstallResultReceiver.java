package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Android PackageInstaller's user-consent callback. This is NOT a silent
 * install. Jetour ROMs can block opening installer UI from a broadcast:
 * notify owner with a tap-to-confirm activity PendingIntent instead.
 */
public final class BridgeInstallResultReceiver extends BroadcastReceiver {
    static final String ACTION=
        "ar.com.rango5gps.drivelabauto.boot.action.LAB_INSTALL_RESULT";
    private static final String CHANNEL="bridge_lab_install_prompt_v1";
    @Override public void onReceive(Context context,Intent reply){
        if(reply==null||!ACTION.equals(reply.getAction()))return;
        int status=reply.getIntExtra(PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_SUCCESS){
            BootUpdater.record(context,"BRIDGE INSTALADO · "+
                "Android confirmó actualización. Abrí Bridge nuevamente.");
            return;
        }
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm=reply.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm==null){
                BootUpdater.record(context,"Android no entregó la ventana "+
                    "para confirmar. Tocá REINTENTAR INSTALACIÓN.");
                return;
            }
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try{
                context.startActivity(confirm);
                BootUpdater.record(context,"ANDROID: confirmá INSTALAR "+
                    "en la ventana que aparece.");
            }catch(RuntimeException blocked){
                try{
                    NotificationManager manager=
                        context.getSystemService(NotificationManager.class);
                    if(manager==null)
                        throw new IllegalStateException("Sin NotificationManager");
                    manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL,"Bridge LAB · Confirmar instalación",
                        NotificationManager.IMPORTANCE_HIGH));
                    PendingIntent click=PendingIntent.getActivity(context,712,
                        confirm,PendingIntent.FLAG_UPDATE_CURRENT|
                        PendingIntent.FLAG_IMMUTABLE);
                    Notification n=new Notification.Builder(context,CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("INSTALAR actualización de Bridge")
                        .setContentText("Tocá acá para confirmar en Android")
                        .setContentIntent(click).setAutoCancel(true).build();
                    manager.notify(7712,n);
                    BootUpdater.record(context,"Android requiere confirmación. "+
                        "TOCÁ la notificación «INSTALAR actualización de Bridge».");
                }catch(RuntimeException unsupported){
                    BootUpdater.record(context,"Jetour bloqueó la ventana "+
                        "de instalación: "+blocked.getClass().getSimpleName()+
                        ". Tocá REINTENTAR INSTALACIÓN en Bridge.");
                }
            }
            return;
        }
        String reason=reply.getStringExtra(
            PackageInstaller.EXTRA_STATUS_MESSAGE);
        if(reason==null)reason="";
        reason=reason.replace('\n',' ').replace('\r',' ');
        if(reason.length()>170)reason=reason.substring(0,170);
        BootUpdater.record(context,"Android rechazó instalar Bridge (estado "+
            status+"): "+reason+". Tocá REINTENTAR INSTALACIÓN.");
    }
}
