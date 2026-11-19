package ar.com.rango5gps.drivelabauto.receiver;

/** Fail-closed filter for optional user-confirmed app stop. Never general
 * purpose "kill all processes": no OEM, HOME, navigation, car controllers,
 * Receiver, system or dynamically named arbitrary services.
 */
final class T1AppStopPolicy {
    private T1AppStopPolicy(){}
    static boolean allowPackage(String pkg){
        if(!T1CenterUserPackagePolicy.allowed(pkg))return false;
        String p=pkg.toLowerCase(java.util.Locale.ROOT);
        return !p.equals("com.waze")&&
            !p.equals("com.google.android.apps.maps")&&
            !p.startsWith("com.google.android.gms")&&
            !p.contains("launcher")&&
            !p.contains("mainthor")&&
            !p.contains("vehicle")&&
            !p.contains("carservice")&&
            !p.contains("cluster")&&
            !p.contains("adas")&&
            !p.contains("oem")&&
            !p.contains("systemui")&&
            !p.contains("bluetooth")&&
            !p.contains("telecom")&&
            !p.contains("telephony")&&
            !p.contains("emergency")&&
            !p.contains("mirrorautomation")&&
            !p.contains("recirculation");
    }
}
