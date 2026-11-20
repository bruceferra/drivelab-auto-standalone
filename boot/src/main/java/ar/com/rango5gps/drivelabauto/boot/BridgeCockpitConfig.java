package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.SharedPreferences;

/** Stable cockpit preferences, independent of navigation pairing and OEM state.
 * Manual app launch enters cockpit; ignition launch is a separate explicit opt-in.
 */
final class BridgeCockpitConfig {
    private static final String FILE="bridge_cockpit_main_v1";
    static final String BOOT_ENABLED="start_cockpit_on_ignition";
    static final String MODE="mosaic_mode";
    static final String LAST_STATUS="last_cockpit_result";
    static final String NATIVE="native_split";
    static final String FREEFORM="legacy_freeform";
    static final String IN_FLIGHT="mosaic_launch_in_flight";
    private BridgeCockpitConfig(){}
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    static boolean bootEnabled(Context c){
        return prefs(c).getBoolean(BOOT_ENABLED,false);
    }
    static String mode(Context c){
        String value=prefs(c).getString(MODE,NATIVE);
        return FREEFORM.equals(value)?FREEFORM:NATIVE;
    }
    static void status(Context c,String value){
        prefs(c).edit().putString(LAST_STATUS,value).apply();
    }
    static String status(Context c){
        return prefs(c).getString(LAST_STATUS,
            "Mosaico sin iniciar. Al abrir Bridge se intentará mostrar la "+
            "última combinación de dos aplicaciones guardada.");
    }
}
