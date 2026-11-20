package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Activity;
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
    // Android 11 headunits can reject startActivity from a broadcast,
    // even if PackageInstaller has already requested owner confirmation.
    // Retain that exact system-provided intent for a direct foreground tap.
    private static volatile Intent pendingAndroidConsent;
    private static volatile long pendingSince;
    static void confirmFromForeground(Activity activity,BootUpdater.Report status){
        Intent confirm=pendingAndroidConsent;
        if(confirm==null||
           android.os.SystemClock.elapsedRealtime()-pendingSince>600_000L){
            pendingAndroidConsent=null;
            status.result("Android todavía no entregó confirmación "+
                "para instalar. Tocá REINTENTAR APK VERIFICADA "+
                "para enviar otra sesión a Android.");
            return;
        }
        try{
            // Direct owner tap on Bridge Activity, not background broadcast.
            activity.startActivity(confirm);
            status.result("Se solicitó la pantalla de instalación desde "+
                "Bridge visible. Confirmá INSTALAR en Android.");
        }catch(Exception blocked){
            status.result("Android bloqueó la pantalla de instalación: "+
                blocked.getClass().getSimpleName()+": "+
                String.valueOf(blocked.getMessage())+
                ". APK verificada guardada; no desinstales Bridge.");
        }
    }
    @Override public void onReceive(Context context,Intent reply){
        if(reply==null||!ACTION.equals(reply.getAction()))return;
        int status=reply.getIntExtra(PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_SUCCESS){
            pendingAndroidConsent=null;
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
            pendingAndroidConsent=confirm;
            pendingSince=android.os.SystemClock.elapsedRealtime();
            BootUpdater.record(context,
                "Android solicitó confirmar instalación. Si no ves la ventana, "+
                "tocá CONFIRMAR INSTALACIÓN ANDROID dentro de Bridge.");
            try{
                context.startActivity(confirm);
                BootUpdater.record(context,"Android solicitó abrir INSTALAR. "+
                    "Si no ves la ventana, tocá CONFIRMAR INSTALACIÓN "+
                    "ANDROID en Bridge para abrirla manualmente.");
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
                        "Tocá CONFIRMAR INSTALACIÓN ANDROID en Bridge "+
                        "o la notificación de instalación, si aparece.");
                }catch(RuntimeException unsupported){
                    BootUpdater.record(context,"Jetour bloqueó la ventana "+
                        "de instalación: "+blocked.getClass().getSimpleName()+
                        ". Tocá REINTENTAR INSTALACIÓN en Bridge.");
                }
            }
            return;
        }
        pendingAndroidConsent=null;
        String reason=reply.getStringExtra(
            PackageInstaller.EXTRA_STATUS_MESSAGE);
        if(reason==null)reason="";
        reason=reason.replace('\n',' ').replace('\r',' ');
        if(reason.length()>170)reason=reason.substring(0,170);
        BootUpdater.record(context,"Android rechazó instalar Bridge (estado "+
            status+"): "+reason+". Tocá REINTENTAR INSTALACIÓN.");
    }
}
