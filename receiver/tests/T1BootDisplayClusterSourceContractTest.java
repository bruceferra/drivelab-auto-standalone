package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Migrated: Bridge must own both physical displays without Receiver.
 * Physical Jetour visibility and safety overlays still require parked QA.
 */
public final class T1BootDisplayClusterSourceContractTest {
    private static String read(String s)throws Exception{return Files.readString(Path.of(s));}
    private static void ok(boolean pass,String what){if(!pass)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String boot="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String ui=read(boot+"BootActivity.java");
        String config=read(boot+"BootConfig.java");
        String auto=read(boot+"BootAutoService.java");
        String windows=read(boot+"BridgeStandaloneWindows.java");
        String link=read(boot+"BootLinkService.java");
        String bootBuild=read("boot/build.gradle");
        String receiverBuild=read("receiver/build.gradle");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        ok(bootBuild.contains("applicationIdSuffix '.lab'")&&
            receiverBuild.contains("versionCode 198")&&
            receiverBuild.contains("versionName '0.3.97'"),
            "Bridge LAB and Receiver remain separately installed; Receiver untouched");
        ok(ui.contains("PANTALLA CENTRAL · DISPLAY 0 · BRIDGE")&&
            ui.contains("PANTALLA DEL TABLERO · DISPLAY 1")&&
            ui.contains("LIBERAR CLÚSTER")&&
            windows.contains("static Result center(")&&
            windows.contains("static Result tablero(")&&
            windows.contains("BridgeWindowMath.center(")&&
            windows.contains("Display 1 BLOQUEADO"),
            "Bridge owns manual center and instrument requests and user confirmation");
        ok(config.contains("getBoolean(\"enabled\",false)")&&
            !config.contains("receiverCode<")&&
            !config.contains("checkSignatures(")&&
            !manifest.contains("receiver.permission.BOOT_MOSAIC")&&
            !manifest.contains("BootResultReceiver")&&
            auto.contains("BootLinkService.START")&&
            !auto.contains("BootConfig.send(")&&
            link.contains("BridgeStandaloneWindows.routeOnTablero(this,pkg)"),
            "Receiver uninstall cannot invalidate Bridge's screen or Xiaomi paths");
        ok(windows.contains("Display.FLAG_PRIVATE")&&
            windows.contains("getState()==Display.STATE_OFF")&&
            windows.contains("BridgeClusterRecovery.disableNewCluster(activity)")&&
            !windows.contains("pm clear")&&
            !windows.contains("am force-stop"),
            "Fail closed on private/off displays or invalid map packages; no OEM changes");
        System.out.println("PASS Bridge owns both display requests without Receiver or OEM writes");
    }
}
