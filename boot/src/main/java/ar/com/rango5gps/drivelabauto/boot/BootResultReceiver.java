package ar.com.rango5gps.drivelabauto.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Signature-permission protected result from its paired Receiver.
 * Provides a persistent diagnostic even when Boot's UI is in background.
 */
public final class BootResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!"ar.com.rango5gps.drivelabauto.boot.action.MOSAIC_RESULT"
                .equals(intent.getAction()))return;
        String result=intent.getStringExtra("result");
        if(result==null)return;
        BootConfig.prefs(context).edit().putString("last_status",
            result.substring(0,Math.min(6000,result.length())))
            .putLong("last_result",System.currentTimeMillis()).apply();
    }
}
