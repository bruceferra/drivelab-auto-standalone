package ar.com.rango5gps.drivelabauto.receiver;

import android.content.*;
import android.os.Build;

/** Restarts only the expressly configured listener. No app UI on boot. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&
           !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        if(!context.getSharedPreferences(ReceiverService.PREFS,Context.MODE_PRIVATE)
            .getBoolean("auto_boot",false))return;
        try{
            Intent start=new Intent(context,ReceiverService.class)
                .setAction(ReceiverService.ACTION_START)
                .putExtra(ReceiverService.FROM_BOOT,Intent.ACTION_BOOT_COMPLETED.equals(action));
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(start);
            else context.startService(start);
        }catch(RuntimeException error){
            context.getSharedPreferences(ReceiverService.PREFS,Context.MODE_PRIVATE).edit()
                .putString("boot_status","Android bloqueó el arranque: "+
                    error.getClass().getSimpleName()).apply();
        }
    }
}
