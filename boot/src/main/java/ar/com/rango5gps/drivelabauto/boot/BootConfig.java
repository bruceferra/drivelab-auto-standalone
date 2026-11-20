package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.SharedPreferences;

/** DriveLab Bridge is its own installation and its own state owner.
 * No Receiver package, signature, service, broadcasts or data provider.
 * Preserve the old local preference filename so OTA never loses opt-in.
 */
final class BootConfig {
    static final String PREFS="drivelab_boot_mosaic_config_v1";
    private BootConfig(){}
    static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context){
        return prefs(context).getBoolean("enabled",false);
    }
}
