package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Owner-reported physical outcome of an OTA-only Jetour display-layer trial.
 * No map, launcher, task, ADB, OEM or window calls are made.
 *
 * The PackageManager lastUpdateTime identifies the installed APK's actual
 * update time independently of whether MY_PACKAGE_REPLACED was delivered.
 * Each owner observation is tied to that time to avoid a stale result
 * being mistaken for an observation on a new build.
 */
final class BridgeOtaLayerTrial {
    private static final String PREF="bridge_ota_layer_trial_v1";
    private static final String RESULT="result",RECORDED_AT="recorded_at",
        OBSERVED_UPDATE="observed_update";
    private BridgeOtaLayerTrial(){}
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    private static PackageInfo packageInfo(Context c){
        try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0);}
        catch(PackageManager.NameNotFoundException e){return null;}
    }
    private static long updateAt(Context c){
        PackageInfo info=packageInfo(c);
        return info==null?0L:info.lastUpdateTime;
    }
    private static String stamp(long ms){
        if(ms<=0L)return "no disponible";
        return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM,
            new Locale("es","AR")).format(new Date(ms));
    }
    static void record(Context c,boolean mapVisible){
        long updated=updateAt(c);
        prefs(c).edit().putLong(OBSERVED_UPDATE,updated)
            .putString(RESULT,mapVisible?"MAPA VISIBLE":"JETOUR / BLUETOOTH ENCIMA")
            .putLong(RECORDED_AT,System.currentTimeMillis()).apply();
    }
    static String describe(Context c){
        PackageInfo info=packageInfo(c);
        SharedPreferences p=prefs(c);
        String version=info==null?"versión no disponible":info.versionName;
        long updated=updateAt(c);
        if(updated<=0L)return "Versión "+version+
            " · Android no informó la fecha de instalación.";
        String description="Versión "+version+
            " · INSTALADA/ACTUALIZADA: "+stamp(updated);
        if(p.getLong(OBSERVED_UPDATE,0L)!=updated)
            return description+" · SIN RESULTADO FÍSICO AÚN. "+
                "Bridge no intenta mover ni relanzar Maps.";
        return description+" · TU RESULTADO: "+
            p.getString(RESULT,"no registrado")+
            " · registrado "+stamp(p.getLong(RECORDED_AT,0L))+
            ". No se tocó ninguna ventana.";
    }
}
