package ar.com.rango5gps.drivelabauto.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Does not claim HOME. Ignition-start only after owner opt-in.
 * No boot UI launch, no OEM writes, no Receiver force-stop.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||
            !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||
            !BootConfig.enabled(context))return;
        try{
            context.startForegroundService(new Intent(context,
                BootAutoService.class));
        }catch(Exception ex){
            BootConfig.prefs(context).edit().putString("last_status",
                "Android bloqueó el autoinicio: "+
                    ex.getClass().getSimpleName()).apply();
        }
    }
}
