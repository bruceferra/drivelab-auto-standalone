package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ComponentName;

final class BootConfig {
    static final String PREFS="drivelab_boot_mosaic_config_v1";
    static final String RECEIVER="ar.com.rango5gps.drivelabauto.receiver";
    static final String ACTION="ar.com.rango5gps.drivelabauto.receiver.action.APPLY_BOOT_MOSAIC";
    static final String BRIDGE=
        "ar.com.rango5gps.drivelabauto.receiver.T1BootMosaicBridgeReceiver";
    static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context){
        return prefs(context).getBoolean("enabled",false);
    }
    /** One explicit, signature-protected broadcast. No generic ADB/shell,
     * no HOME or vehicle processes, and no actions targeting Display 1.
     */
    static void assertReceiver(Context context){
        if(context.getPackageManager().checkSignatures(
            context.getPackageName(),RECEIVER)!=
                android.content.pm.PackageManager.SIGNATURE_MATCH)
            throw new SecurityException(
                "Instalá Receiver 0.3.92 firmado para usar DriveLab Boot");
    }
    /** User explicitly elected to delegate boot UI opening to Boot app.
     * This only disables ReceiverService.launch_app_on_boot, not its FGS
     * or cluster. T1 Launcher HOME is never changed.
     */
    static void takeOver(Context context){
        assertReceiver(context);
        Intent request=new Intent(
            "ar.com.rango5gps.drivelabauto.receiver.action.BOOT_MOSAIC_TAKEOVER");
        request.setComponent(new ComponentName(RECEIVER,BRIDGE));
        context.sendBroadcast(request);
    }
    static void send(Context context){
        assertReceiver(context);
        SharedPreferences p=prefs(context);
        int mode=p.getInt("mode",3),percent=p.getInt("percent",50);
        if(mode<0||mode>4||percent<1||percent>99)
            throw new IllegalArgumentException("Mosaico inválido");
        int count=mode==4?1:mode==3?2:3;
        Intent request=new Intent(ACTION);
        request.setComponent(new ComponentName(RECEIVER,BRIDGE));
        request.putExtra("mode",mode);
        request.putExtra("percent",percent);
        for(int i=0;i<count;i++){
            String pkg=p.getString("pkg"+i,"");
            if(pkg==null||pkg.isEmpty())
                throw new IllegalStateException("Elegí la app del panel "+(i+1));
            request.putExtra("pkg"+i,pkg);
        }
        context.sendBroadcast(request);
        p.edit().putString("last_status",
            "Solicitud enviada al Receiver · esperando ADB y Freeform")
            .putLong("last_request",System.currentTimeMillis()).apply();
    }
}
