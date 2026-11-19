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
            // When Boot becomes sole owner, turn OFF only redundant app-UI
            // autostart and (if explicitly selected) independent cluster
            // auto-launch. Keep all Receiver foreground trip/charge/OEM
            // services, the HOME role and T1 Launcher themselves intact.
            boolean ownership=intent.getBooleanExtra("boot_enabled",true);
            if(!ownership){
                T1BootMosaicBridgeService.report(context,
                    "Autoarranque de DriveLab Boot desactivado por usuario. "+
                    "No se reactivaron autoinicios anteriores automáticamente.");
                return;
            }
            boolean cluster=intent.getBooleanExtra("cluster_enabled",false);
            boolean saved=context.getSharedPreferences(
                ReceiverService.PREFS,Context.MODE_PRIVATE)
                .edit().putBoolean("launch_app_on_boot",false)
                .putString("boot_status",
                    "DriveLab Boot administra las pantallas configuradas; "+
                    "servicios OEM y registros siguen independientes").commit();
            if(cluster){
                boolean clusterSaved=T1ClusterWazeRouter.prefs(context).edit()
                    .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false).commit();
                saved=saved&&clusterSaved;
            }
            T1BootMosaicBridgeService.report(context,saved?
                "DriveLab Boot es el único autoinicio de mosaicos. "+
                (cluster?"El autoinicio separado de mapas del clúster "+
                    "se desactivó. ":"")+
                "Receiver mantiene viajes, cargas, controles, T1 Launcher "+
                "y avisos del tablero sin cambios.":
                "No se pudo guardar completamente la coordinación de arranque");
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
