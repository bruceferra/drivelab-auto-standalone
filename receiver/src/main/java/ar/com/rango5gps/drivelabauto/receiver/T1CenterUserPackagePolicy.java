package ar.com.rango5gps.drivelabauto.receiver;

/** Pure syntax/scope fence for center-screen launchable user applications.
 * Installation origin and FLAG_SYSTEM are separately checked via PackageManager.
 */
final class T1CenterUserPackagePolicy {
    private T1CenterUserPackagePolicy(){}
    static boolean allowed(String pkg){
        return pkg!=null&&pkg.length()<=180&&
            pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")&&
            !pkg.equals("ar.com.rango5gps.drivelabauto.receiver")&&
            !pkg.equals("android")&&!pkg.startsWith("android.")&&
            !pkg.startsWith("com.android.")&&
            !pkg.startsWith("com.autolink.")&&
            !pkg.startsWith("com.jetour.")&&
            !pkg.startsWith("com.chery.")&&
            !pkg.startsWith("com.qti.")&&
            !pkg.startsWith("com.qualcomm.");
    }
}
