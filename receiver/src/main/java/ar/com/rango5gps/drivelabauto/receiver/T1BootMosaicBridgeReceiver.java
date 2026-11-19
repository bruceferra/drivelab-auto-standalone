package ar.com.rango5gps.drivelabauto.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Only the separate, identically signed DriveLab Boot package may request
 * the existing proven local-ADB mosaic. Receiver does not export raw shell,
 * HOME, OEM commands or force-stop operations. T1Launcher untouched.
 */
public final class T1BootMosaicBridgeReceiver extends BroadcastReceiver {
    static final String BOOT_PACKAGE="ar.com.rango5gps.drivelabauto.boot";
    static final String ACTION_APPLY=
        "ar.com.rango5gps.drivelabauto.receiver.action.APPLY_BOOT_MOSAIC";
    static final String ACTION_TAKEOVER=
        "ar.com.rango5gps.drivelabauto.receiver.action.BOOT_MOSAIC_TAKEOVER";
    static final String PERMISSION=
        "ar.com.rango5gps.drivelabauto.receiver.permission.BOOT_MOSAIC";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!(
            ACTION_APPLY.equals(intent.getAction())||
            ACTION_TAKEOVER.equals(intent.getAction())))return;
        if(context.getPackageManager().checkSignatures(
            context.getPackageName(),BOOT_PACKAGE)!=
            PackageManager.SIGNATURE_MATCH)return;
        if(ACTION_TAKEOVER.equals(intent.getAction())){
            // Only the former generic APP-UI auto-open is turned off.
            // ReceiverService boot, Waze cluster recovery, trips, charging,
            // mirrors and climate opt-ins remain untouched.
            boolean saved=context.getSharedPreferences(
                ReceiverService.PREFS,Context.MODE_PRIVATE)
                .edit().putBoolean("launch_app_on_boot",false)
                .putString("boot_status",
                    "DriveLab Boot administra la apertura; "+
                    "servicios y clúster siguen normales").commit();
            T1BootMosaicBridgeService.report(context,saved?
                "DriveLab original ya no abrirá su interfaz al encender. "+
                "Sus servicios, T1 Launcher y clúster siguen sin cambios.":
                "No se guardó la desactivación del inicio de interfaz Receiver");
            return;
        }
        try{
            Intent service=new Intent(context,T1BootMosaicBridgeService.class);
            service.putExtras(intent);
            context.startForegroundService(service);
        }catch(Exception ex){
            T1BootMosaicBridgeService.report(context,
                "NO SE ABRIÓ MOSAICO: Android bloqueó el servicio: "+
                ex.getClass().getSimpleName());
        }
    }
}
