package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** App-window-only Android fullscreen, as requested for the T1 center.
 * NEVER uses global settings, OEM process changes or window overlays.
 * Android/Jetour may still draw independent HVAC/safety system layers.
 */
final class BridgeImmersive {
    private BridgeImmersive(){}
    static void apply(Activity activity){
        Window window=activity.getWindow();
        if(window==null)return;
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor=window.getDecorView();
        if(decor==null)return;
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
            View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        WindowInsetsController controller=decor.getWindowInsetsController();
        if(controller!=null){
            controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.statusBars()|
                WindowInsets.Type.navigationBars());
        }
    }
}
