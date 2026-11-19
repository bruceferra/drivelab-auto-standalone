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
    static final String PERMISSION=
        "ar.com.rango5gps.drivelabauto.receiver.permission.BOOT_MOSAIC";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION_APPLY.equals(intent.getAction()))return;
        if(context.getPackageManager().checkSignatures(
            context.getPackageName(),BOOT_PACKAGE)!=
            PackageManager.SIGNATURE_MATCH)return;
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
