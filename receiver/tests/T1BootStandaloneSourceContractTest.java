package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Historical Boot/Receiver contract migrated to independent Bridge.
 * Receiver's legacy mosaic component may still exist for old releases,
 * but Bridge must not import, invoke, or require it at runtime.
 */
public final class T1BootStandaloneSourceContractTest {
    private static String read(String name)throws Exception{return Files.readString(Path.of(name));}
    private static void ok(boolean pass,String what){if(!pass)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String root="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String settings=read("settings.gradle");
        String build=read("boot/build.gradle");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        String config=read(root+"BootConfig.java");
        String ui=read(root+"BootActivity.java");
        String windows=read(root+"BridgeStandaloneWindows.java");
        String auto=read(root+"BootAutoService.java");
        String ignition=read(root+"BootReceiver.java");
        String link=read(root+"BootLinkService.java");
        ok(settings.contains("':boot'")&&
            build.contains("applicationIdSuffix '.lab'")&&
            manifest.contains("android:name=\".BootActivity\"")&&
            manifest.contains("android.intent.category.LAUNCHER")&&
            !manifest.contains("android.intent.category.HOME")&&
            !manifest.contains("receiver.permission.BOOT_MOSAIC")&&
            !manifest.contains("BootResultReceiver"),
            "Bridge independently installed, no OEM HOME, no Receiver IPC");
        ok(config.contains("SharedPreferences prefs(")&&
            config.contains("getBoolean(\"enabled\",false)")&&
            !config.contains("assertReceiver")&&
            !config.contains("new ComponentName(")&&
            !config.contains("sendBroadcast(")&&
            !config.contains("T1BootMosaicBridgeReceiver")&&
            !ui.contains("BootConfig.assertReceiver")&&
            !ui.contains("BootConfig.sendCenterOnly")&&
            !ui.contains("BootConfig.sendClusterOnly")&&
            !ui.contains("legacyAdvanced"),
            "Configuration and UI must not delegate maps or windows to Receiver");
        ok(ui.contains("PROBAR DOS VENTANAS · SÓLO BRIDGE")&&
            ui.contains("LIBERAR CLÚSTER")&&
            ui.contains("VINCULAR POR QR")&&
            ui.contains("RECONECTAR ACCESIBILIDAD · ADB LOCAL")&&
            windows.contains("static Result center(")&&
            windows.contains("static Result tablero(")&&
            windows.contains("method.invoke(options,FREEFORM)")&&
            windows.contains("options.setLaunchDisplayId(displayId)")&&
            !windows.contains("T1BootMosaicBridgeService"),
            "Own two-screen launcher, QR and wheel, no Receiver call");
        ok(auto.contains("BootLinkService.START")&&
            auto.contains("BootConfig.enabled(this)")&&
            !auto.contains("startActivity(")&&
            ignition.contains("BootConfig.enabled(context)")&&
            ignition.contains("Intent.ACTION_BOOT_COMPLETED")&&
            link.contains("BridgeCrypto.NAV_PORT")&&
            link.contains("BridgeCrypto.PAIR_PORT")&&
            !link.contains("BootConfig.send(")&&
            !link.contains("am force-stop")&&
            !link.contains("pm clear"),
            "Opt-in ignition starts only own QR/LAN; never alters Jetour or maps");
        System.out.println("PASS standalone Bridge APK independent from Receiver, own windows and QR");
    }
}
