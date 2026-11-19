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
    static void send(Context context){
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
