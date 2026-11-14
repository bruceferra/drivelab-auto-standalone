package ar.com.rango5gps.drivelabauto.sender;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * System A2DP-connected broadcast is exempt from implicit-broadcast limits.
 * Opt-in and the exact vehicle MAC are checked before any audio is created.
 * goAsync finishes within a bounded few seconds; no foreground service or
 * Spotify app is launched, and no sound plays for a different headset.
 */
public final class SenderAudioBootstrapReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||
                !BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(
                    intent.getAction()))return;
        if(intent.getIntExtra(BluetoothProfile.EXTRA_STATE,-1)!=
                BluetoothProfile.STATE_CONNECTED)return;
        if(!SenderAudioBootstrap.permission(context)||
                !SenderAudioBootstrap.configured(context))return;
        BluetoothDevice device;
        try{
            device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(!SenderAudioBootstrap.sameDevice(context,device))return;
        }catch(SecurityException error){
            SenderAudioBootstrap.report(context,"Sin permiso Bluetooth: "+
                error.getClass().getSimpleName());return;
        }
        final PendingResult pending=goAsync();
        Context app=context.getApplicationContext();
        new Thread(()->{
            try{
                SenderAudioBootstrap.prime(app,true);
            }finally{pending.finish();}
        },"DriveLab-T1-A2DP-Connect").start();
    }
}
