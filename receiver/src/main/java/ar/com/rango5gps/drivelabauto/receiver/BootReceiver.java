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
        if(context.getSharedPreferences(ReceiverService.PREFS,Context.MODE_PRIVATE)
                .getBoolean("auto_boot",false)){
            try{
                Intent start=new Intent(context,ReceiverService.class)
                    .setAction(ReceiverService.ACTION_START)
                    .putExtra(ReceiverService.FROM_BOOT,
                        Intent.ACTION_BOOT_COMPLETED.equals(action));
                if(Build.VERSION.SDK_INT>=26)context.startForegroundService(start);
                else context.startService(start);
            }catch(RuntimeException error){
                context.getSharedPreferences(ReceiverService.PREFS,Context.MODE_PRIVATE).edit()
                    .putString("boot_status","Android bloqueó el arranque: "+
                        error.getClass().getSimpleName()).apply();
            }
        }
        // Recirculation has independent explicit opt-in and survives reboot.
        // The mirror reference D cannot be trusted after a reboot; never
        // auto-resume mirror movement or trips from BOOT_COMPLETED.
        android.content.SharedPreferences hvac=context.getSharedPreferences(
            RecirculationGuardService.PREFS,Context.MODE_PRIVATE);
        if(RecirculationGuardService.MODE_INTERIOR.equals(hvac.getString(
                RecirculationGuardService.MODE,RecirculationGuardService.MODE_AUTO))&&
                !hvac.getBoolean(RecirculationGuardService.PAUSED,false)){
            try{
                Intent guard=new Intent(context,RecirculationGuardService.class)
                    .setAction(RecirculationGuardService.ACTION_INTERIOR);
                context.startForegroundService(guard);
            }catch(RuntimeException error){
                hvac.edit().putString(RecirculationGuardService.STATUS,
                    "No se pudo iniciar modo interior al encender: "+
                    error.getClass().getSimpleName()).apply();
            }
        }
    }
}
