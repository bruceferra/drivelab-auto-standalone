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
        // Same user opt-in as Driver Uber's ContinuousTripBootReceiver.
        // Restart the EXISTING Trip/Customer after vehicle boot or APK update,
        // never create a new session and never close the user's passenger.
        // The foreground worker itself reconstructs its CSV/checkpoints.
        android.content.SharedPreferences trip=context.getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
        boolean desired=trip.getBoolean(T1TripRecorderService.DESIRED,false);
        // Migration: the old version had RUNNING but no persistent DESIRED.
        if(!trip.contains(T1TripRecorderService.DESIRED))
            desired=trip.getBoolean(T1TripRecorderService.RUNNING,false)||
                T1JourneyStore.active(context)||T1JourneyStore.pending(context);
        if(desired){
            try{
                Intent restore=new Intent(context,T1TripRecorderService.class)
                    .setAction(T1TripRecorderService.ACTION_RESTORE);
                if(Build.VERSION.SDK_INT>=26)
                    context.startForegroundService(restore);
                else context.startService(restore);
                trip.edit().putString(T1TripRecorderService.RESTORE_STATUS,
                    "Reanudación automática solicitada tras "+
                    (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)?
                        "actualización":"encendido del vehículo")).apply();
            }catch(RuntimeException error){
                trip.edit().putString(T1TripRecorderService.RESTORE_STATUS,
                    "Android bloqueó el reinicio automático: "+
                    error.getClass().getSimpleName()+
                    ". Reintentará al abrir el cockpit; NO se perdió el CSV.")
                    .apply();
            }
        }
        // Recirculation has independent opt-in. Mirror control is NOT
        // resumed across boot; only data recording is restarted here.
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
