package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * User-reversible immersive presentation for the Jetour T1 Android 11 screen.
 *
 * Immersive requests only affect standard Android status/navigation bars.
 * An OEM-owned persistent header ("Hola Jetour") or HVAC dock can remain:
 * we do NOT change permissions, Android settings, launcher packages, HOME
 * intent handlers, OEM services, or overlay windows.
 *
 * The cockpit, vehicle editor, tank dashboard and Receiver hub share this
 * preference; switching it OFF restores bars for recovery/accessibility.
 */
final class T1ImmersiveMode {
    private static final String PREFS="t1_immersive_screen";
    private static final String ENABLED="enabled";
    private static final int FLAGS=View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        |View.SYSTEM_UI_FLAG_FULLSCREEN
        |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    static boolean enabled(Context c){
        return c.getApplicationContext()
            .getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(ENABLED,true);
    }
    static void setEnabled(Activity a,boolean enabled){
        SharedPreferences p=a.getApplicationContext()
            .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        p.edit().putBoolean(ENABLED,enabled).apply();
        apply(a);
    }
    static void apply(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        Window window=a.getWindow();
        if(window==null)return;
        View decor=window.getDecorView();
        if(decor==null)return;
        if(enabled(a)){
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(FLAGS);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller=decor.getWindowInsetsController();
                if(controller!=null){
                    controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.statusBars()|
                        WindowInsets.Type.navigationBars());
                }
            }
        }else{
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(0);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
                window.setDecorFitsSystemWindows(true);
                WindowInsetsController controller=decor.getWindowInsetsController();
                if(controller!=null)controller.show(
                    WindowInsets.Type.statusBars()|
                        WindowInsets.Type.navigationBars());
            }
        }
    }
    private T1ImmersiveMode(){}
}
