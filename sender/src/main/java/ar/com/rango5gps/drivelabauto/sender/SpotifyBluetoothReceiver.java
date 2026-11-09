package ar.com.rango5gps.drivelabauto.sender;

import android.content.*;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Receives only OS Bluetooth connection changes; never starts an activity. */
public final class SpotifyBluetoothReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!SpotifyAutoPlay.enabled(context))return;
        PendingResult pending=goAsync();
        AtomicBoolean complete=new AtomicBoolean();
        Runnable finish=()->{
            if(complete.compareAndSet(false,true))pending.finish();
        };
        // A2DP proxy callbacks should be quick; never hold a receiver open indefinitely.
        new Handler(Looper.getMainLooper()).postDelayed(finish,6500);
        SpotifyAutoPlay.onBluetoothEvent(context.getApplicationContext(),intent,finish);
    }
}
