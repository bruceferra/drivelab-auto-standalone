package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Android's explicit confirmation callback for the separate Boot package. */
public final class T1BootInstallResultReceiver extends BroadcastReceiver {
    public static final String ACTION="drivelab.auto.BOOT_INSTALL_RESULT";
    private static final String CHANNEL="drivelab_boot_native_installer";
    @Override public void onReceive(Context c,Intent response){
        if(response==null||!ACTION.equals(response.getAction()))return;
        int status=response.getIntExtra(PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_SUCCESS){
            T1BootInstaller.state(c,"DriveLab Boot instalada correctamente. "+
                "Abrila desde Inicio para configurar el mosaico.");
            return;
        }
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm=response.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm==null){
                T1BootInstaller.state(c,"Android no entregó la ventana de confirmación.");
                return;
            }
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            T1BootInstaller.state(c,"Android requiere confirmar la instalación de Boot");
            try{
                c.startActivity(confirm);
            }catch(RuntimeException blocked){
                try{
                    NotificationManager nm=c.getSystemService(NotificationManager.class);
                    if(nm==null)throw new IllegalStateException("Sin notificaciones");
                    nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL,"DriveLab Boot · instalación",NotificationManager.IMPORTANCE_HIGH));
                    int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
                    PendingIntent click=PendingIntent.getActivity(c,501,confirm,flags);
                    Notification n=new Notification.Builder(c,CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Instalar DriveLab Boot")
                        .setContentText("Tocá para confirmar con el instalador Android")
                        .setContentIntent(click).setAutoCancel(true).build();
                    nm.notify(4501,n);
                    T1BootInstaller.state(c,
                        "Confirmación pendiente: tocá la notificación de Boot");
                }catch(RuntimeException failure){
                    T1BootInstaller.state(c,
                        "El Jetour bloqueó la ventana de instalación: "+
                        blocked.getClass().getSimpleName());
                }
            }
            return;
        }
        String detail=response.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if(detail==null)detail="";
        detail=detail.replace('\n',' ').replace('\r',' ');
        if(detail.length()>140)detail=detail.substring(0,140);
        T1BootInstaller.state(c,"Instalación Boot rechazada por Android (estado "+
            status+"). "+detail+
            " · Podés reintentar o usar el instalador alternativo.");
    }
}
