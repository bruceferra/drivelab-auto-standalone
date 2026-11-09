package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;

/**
 * Callback for the standard Android PackageInstaller session initiated by a
 * stationary T1 user. Launches the OS confirmation dialog, never installs
 * silently and never accesses OEM/system permissions.
 */
public final class ReceiverInstallResultReceiver extends BroadcastReceiver {
    public static final String ACTION_RESULT="drivelab.auto.RECEIVER_INSTALL_RESULT";
    private static final String PREFS="drivelab_native_update";
    private static final String STATUS_KEY="last_install_status";
    private static final String CHANNEL="drivelab_receiver_install";

    private static void state(Context c,String message) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(STATUS_KEY,message).apply();
    }
    @Override public void onReceive(Context context,Intent received) {
        if(received==null||!ACTION_RESULT.equals(received.getAction()))return;
        int status=received.getIntExtra(PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent approve=received.getParcelableExtra(Intent.EXTRA_INTENT);
            if(approve==null){
                state(context,"Android no proporcionó la ventana de confirmación de instalación");
                return;
            }
            approve.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            state(context,"Android solicita confirmar la instalación de Receiver");
            try{
                // On Android 11, a foreground user-initiated install should
                // open the OEM package installer without entering Settings.
                context.startActivity(approve);
            }catch(RuntimeException blocked){
                // OEM may block background activity launches from callbacks.
                // Provide a user-tappable OS notification as safe fallback.
                try{
                    NotificationManager manager=context.getSystemService(NotificationManager.class);
                    manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL,"DriveLab Auto · Instalar actualización",
                        NotificationManager.IMPORTANCE_HIGH));
                    int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
                    PendingIntent open=PendingIntent.getActivity(context,401,approve,flags);
                    Notification n=new Notification.Builder(context,CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Confirmar actualización de Receiver")
                        .setContentText("Tocá para abrir el instalador Android")
                        .setContentIntent(open).setAutoCancel(true).build();
                    manager.notify(4302,n);
                    state(context,"Android requiere confirmación: tocá la notificación de instalación");
                }catch(RuntimeException fail){
                    state(context,"La ROM no permitió mostrar el instalador: "+
                        blocked.getClass().getSimpleName());
                }
            }
            return;
        }
        if(status==PackageInstaller.STATUS_SUCCESS){
            state(context,"Actualización instalada por Android correctamente");
            return;
        }
        String detail=received.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if(detail==null)detail="";
        detail=detail.replace('\n',' ').replace('\r',' ');
        if(detail.length()>140)detail=detail.substring(0,140);
        state(context,"Instalador Android rechazó la actualización (estado "+
            status+"). "+detail+" · La versión anterior sigue instalada.");
    }
}
