package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.os.Bundle;

/**
 * Immersive Android 11 activity for the T1's center display. This does not
 * replace the stock OEM launcher or intercept "Hola Jetour" overlays.
 * On-window-focus reapplication is needed after other activities/dialogs.
 */
public abstract class T1ImmersiveActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        T1ImmersiveMode.apply(this);
    }
    @Override protected void onResume(){
        super.onResume();
        T1ImmersiveMode.apply(this);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)T1ImmersiveMode.apply(this);
    }
}
