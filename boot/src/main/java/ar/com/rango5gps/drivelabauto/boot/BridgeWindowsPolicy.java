package ar.com.rango5gps.drivelabauto.boot;

import java.util.Locale;

/** Pure policy shared by Bridge UI and window launcher.
 * Neither DriveLab Auto nor any OEM / launcher / car service is a valid panel.
 */
final class BridgeWindowsPolicy {
    static final String MAPS="com.google.android.apps.maps",
        WAZE="com.waze",SPOTIFY="com.spotify.music",
        AUTO="ar.com.rango5gps.drivelabauto.receiver",
        BRIDGE="ar.com.rango5gps.drivelabauto.boot";
    private BridgeWindowsPolicy(){}
    static boolean allowed(String pkg){
        if(pkg==null||!pkg.matches(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
            return false;
        String p=pkg.toLowerCase(Locale.ROOT);
        return !p.startsWith("ar.com.rango5gps.drivelabauto.")&&
            !p.startsWith("android.")&&
            !p.startsWith("com.android.")&&
            !p.startsWith("com.autolink.")&&
            !p.startsWith("com.jetour.")&&
            !p.startsWith("com.chery.")&&
            !p.startsWith("com.t1.")&&
            !p.startsWith("com.mainthor.")&&
            !p.startsWith("com.rango5gps.")&&
            !p.contains("systemui")&&
            !p.contains("launcher")&&
            !p.contains("cluster")&&
            !p.contains("vehicle");
    }
    static boolean validCenter(String first,String second,int percent){
        return allowed(first)&&allowed(second)&&
            !first.equals(second)&&percent>=40&&percent<=80;
    }
    static boolean validMap(String pkg){
        return MAPS.equals(pkg)||WAZE.equals(pkg);
    }
}
