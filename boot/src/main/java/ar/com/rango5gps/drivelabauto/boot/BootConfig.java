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
        SharedPreferences p=prefs(context);
        // Preserve explicit owner ON/OFF. For previous LAB versions that
        // saved QR pairing but never configured auto-start, automatically
        // resume ONLY the network listener from their existing own key.
        if(p.contains("enabled"))return p.getBoolean("enabled",false);
        String saved=BootLinkService.prefs(context).getString("secret","");
        return saved!=null&&saved.matches("[A-Za-z0-9_-]{22}");
    }
}
