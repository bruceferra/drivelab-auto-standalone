package ar.com.rango5gps.drivelabauto.sender;

import android.content.Context;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;

/** Persisted per-Sender choice: one tap in Uber always uses the same target. */
final class SenderRouteTarget {
    static final String KEY="nav_target";
    private SenderRouteTarget(){}
    static String selected(Context c){
        return BridgeRouteTarget.normalize(SenderClient.prefs(c).getString(
            KEY,BridgeRouteTarget.WAZE));
    }
    static void choose(Context c,String target){
        SenderClient.prefs(c).edit().putString(
            KEY,BridgeRouteTarget.normalize(target)).apply();
    }
    static String label(String mode){
        if(BridgeRouteTarget.MAPS.equals(mode))return "Google Maps";
        if(BridgeRouteTarget.BOTH.equals(mode))return "Waze + Google Maps";
        if(BridgeRouteTarget.T1_LAUNCHER.equals(mode))
            return "Google Maps dentro de T1Launcher (experimental)";
        return "Waze";
    }
}
