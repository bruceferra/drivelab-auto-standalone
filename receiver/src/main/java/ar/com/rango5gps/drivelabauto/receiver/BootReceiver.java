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
        // Cluster auto-boot lives in a DIFFERENT preference file from
        // Receiver's legacy auto_boot. The old gate silently skipped the
        // entire Waze/Maps OFF(10 s)/ON sequence when ONLY the cluster
        // auto-start option was enabled.
        boolean receiverBoot=context.getSharedPreferences(
            ReceiverService.PREFS,Context.MODE_PRIVATE)
                .getBoolean("auto_boot",false);
        boolean clusterBoot=T1ClusterWazeRouter.autoBoot(context);
        // Ignore historical Nav boot flags, preserve stored preferences.
        if(T1NavSeparation.NAV_DELEGATED){receiverBoot=false;clusterBoot=false;}
        if(receiverBoot||clusterBoot){
            // Persist a real ignition broadcast receipt so the owner can
            // distinguish "never triggered" from a failed ADB step.
            if(clusterBoot&&Intent.ACTION_BOOT_COMPLETED.equals(action)){
                java.text.SimpleDateFormat time=new java.text.SimpleDateFormat(
                    "dd/MM HH:mm:ss",java.util.Locale.ROOT);
                T1ClusterWazeRouter.prefs(context).edit()
                    .putString(T1ClusterWazeRouter.BOOT_RELOAD_RESULT,
                        "BOOT_COMPLETED recibido "+time.format(
                            new java.util.Date())+
                        " · secuencia automática programada").commit();
            }
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
        // T1N134 verified key 1010, activated only after user opt-in.
        if(!T1NavSeparation.NAV_DELEGATED &&
           T1OemMicrophoneService.prefs(context).getBoolean(
                T1OemMicrophoneService.ENABLED,false)){
            try{
                context.startForegroundService(
                    new Intent(context,T1OemMicrophoneService.class)
                        .setAction(T1OemMicrophoneService.ACTION_START));
            }catch(RuntimeException error){
                T1OemMicrophoneService.status(context,
                    "Android bloqueó iniciar el micrófono OEM: "+
                    error.getClass().getSimpleName());
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
        if(desired && !trip.getBoolean(T1TripRecorderService.PAUSED,false)){
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
        // Charge monitor is INDEPENDENT of any manually started GPS trip.
        // Only restart if enabled from its own page. No camera, network or OEM writes.
        T1ChargeRecorderService.startIfEnabled(context);
        // Automatic ignition journey records are independent of manually
        // pressed Viaje 1/2/Total. OEM read-only; default enabled.
        T1AutoJourneyService.startIfEnabled(context);
        if(Intent.ACTION_BOOT_COMPLETED.equals(action))
            RecirculationGuardService.newIgnition(context);
        // Recirculation has independent opt-in and a dedicated foreground
        // service, even when DriveLab UI / Maps is closed. An OEM protective
        // PAUSED state is NOT cleared on reboot or package replacement.
        // Mirror control still requires safe known physical reference before
        // its intentional automatic movements.
        RecirculationGuardService.recoverLegacyReadError(context);
        android.content.SharedPreferences hvac=context.getSharedPreferences(
            RecirculationGuardService.PREFS,Context.MODE_PRIVATE);
        if(RecirculationGuardService.MODE_INTERIOR.equals(hvac.getString(
                RecirculationGuardService.MODE,RecirculationGuardService.MODE_AUTO))&&
                !hvac.getBoolean(RecirculationGuardService.PAUSED,false)){
            try{
                Intent guard=new Intent(context,RecirculationGuardService.class)
                    .setAction(RecirculationGuardService.ACTION_RESTORE);
                context.startForegroundService(guard);
            }catch(RuntimeException error){
                hvac.edit().putString(RecirculationGuardService.STATUS,
                    "No se pudo iniciar modo interior al encender: "+
                    error.getClass().getSimpleName()).apply();
            }
        }
    }
}
