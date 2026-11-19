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
        android.content.SharedPreferences.Editor edit=
            BootConfig.prefs(context).edit()
                .putString("last_status",
                    result.substring(0,Math.min(6000,result.length())))
                .putLong("last_result",System.currentTimeMillis());
        if(intent.getBooleanExtra("cluster_snapshot",false)){
            // Receiver is authoritative for Display 1. These are mirrored
            // READ-ONLY into Boot's draft: never use a 50/50 default to
            // overwrite the live two-map Receiver state.
            int count=intent.getIntExtra("cluster_count",-1);
            int ratio=intent.getIntExtra("cluster_percent",-1);
            String a=intent.getStringExtra("cluster_pkg0");
            String b=intent.getStringExtra("cluster_pkg1");
            if((count==1||count==2)&&ratio>=30&&ratio<=70&&
               a!=null&&!a.isEmpty()&&b!=null&&!b.isEmpty()){
                edit.putInt("live_cluster_count",count)
                    .putInt("live_cluster_percent",ratio)
                    .putString("live_cluster_pkg0",a)
                    .putString("live_cluster_pkg1",b)
                    .putBoolean("live_cluster_enabled",
                        intent.getBooleanExtra("cluster_enabled",false))
                    .putInt("live_cluster_display_id",
                        intent.getIntExtra("cluster_display_id",-1))
                    .putLong("live_cluster_read_at",System.currentTimeMillis());
            }
        }
        edit.apply();
    }
}
