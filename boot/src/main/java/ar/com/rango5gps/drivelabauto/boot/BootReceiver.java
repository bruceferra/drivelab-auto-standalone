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
            !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        // A map that was visibly confirmed before ignition is NOT
        // guaranteed to exist on the next boot. Require new confirmation
        // before Sender may target Display 1 again.
        BridgeStandaloneWindows.prefs(context).edit()
            .putBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,false)
            .putBoolean(BridgeStandaloneWindows.TAB_ROUTE,false).apply();
        if(!BootConfig.enabled(context))return;
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
