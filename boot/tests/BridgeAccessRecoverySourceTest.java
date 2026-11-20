package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source contract; real Jetour ADB host trust must be checked in the car. */
public final class BridgeAccessRecoverySourceTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    private static void ok(boolean pass,String s){
        if(!pass)throw new AssertionError(s);
    }
    public static void main(String[] args)throws Exception{
        String adb=read("BridgeLocalAdb.java");
        String recovery=read("BridgeWheelAccessRecovery.java");
        String service=read("BridgeWheelAccessibilityService.java");
        String home=read("BootActivity.java");
        ok(adb.contains("LOOPBACK=\"127.0.0.1\"")&&
           adb.contains("PERSISTENT_PORT=5556, MANUAL_PORT=5555")&&
           adb.contains("BridgeAdbRsaSpec.recoverPrivate(encoded)")&&
           adb.contains("Clave ADB de Bridge LAB todavía no autorizada")&&
           adb.contains("safeListing(")&&
           !adb.contains("am force-stop")&&
           !adb.contains("pm clear")&&
           !adb.contains("input keyevent")&&
           !adb.contains("su -c"),
           "Local bridge ADB must be authenticated and settings-only");
        ok(recovery.contains("BridgeAccessPolicy.addOwn(old)")&&
           recovery.contains("BridgeAccessPolicy.removeOwn(old)")&&
           recovery.contains("Settings.Secure.ACCESSIBILITY_ENABLED")&&
           recovery.contains("settings get secure enabled_accessibility_services")&&
           recovery.contains("checkedPut(app,old,updated)")&&
           recovery.contains("checkedPut(app,without,old)")&&
           recovery.contains("new AlertDialog.Builder(owner)")&&
           recovery.contains("AUTORIZO ADB LOCAL")&&
           recovery.contains("BridgeWheelAccessibilityService.isConnected()")&&
           recovery.contains("BridgeAccessPolicy.PACKAGE.equals(app.getPackageName())")&&
           !recovery.contains("onBoot("),
           "Manual-only owner consent, exact change+recovery and real service connection");
        ok(service.contains("private static volatile boolean connectedNow=false")&&
           service.contains("connectedNow=true")&&
           service.contains("connectedNow=false")&&
           home.contains("RECONECTAR ACCESIBILIDAD · ADB LOCAL · SIN TERMUX")&&
           home.contains("BridgeWheelAccessRecovery.describe(this)")&&
           !home.contains("BootConfig.assertReceiver"),
           "Bridge wheel permission, status and UI must require no Receiver");
        System.out.println("PASS owner-confirmed isolated LAB ADB access, safe rebind, no Receiver dependency");
    }
}
