package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Launcher/steering-key selectable voice shortcut.
 * Explicit one-touch launch opens the proven Android voice-command session.
 * The ordinary DriveLab voice settings remain accessible independently.
 * This does NOT intercept OEM steering keys or modify their current mapping.
 */
public final class T1VoiceShortcutActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        try{
            startActivity(new Intent(this,T1VoiceRouteActivity.class)
                .setAction(T1VoiceRouteActivity.ACTION_SHORTCUT_SYSTEM_VOICE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }catch(RuntimeException error){
            // Opening target voice UI is not required for driving controls.
        }
        finish();
    }
}
