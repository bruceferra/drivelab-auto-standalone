package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Bridge's only default UI owns both physical displays; no old Receiver menu. */
public final class T1BootStandaloneUiIsolationSourceTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
    private static void ck(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String root="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String ui=read(root+"BootActivity.java");
        String config=read(root+"BootConfig.java");
        String windows=read(root+"BridgeStandaloneWindows.java");
        String link=read(root+"BootLinkService.java");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        ck(ui.contains("DRIVELAB BRIDGE")&&
            ui.contains("PANTALLA CENTRAL · DISPLAY 0 · BRIDGE")&&
            ui.contains("PANTALLA DEL TABLERO · DISPLAY 1")&&
            ui.contains("PROBAR DOS VENTANAS · SÓLO BRIDGE")&&
            ui.contains("LIBERAR CLÚSTER")&&
            ui.contains("VINCULAR POR QR")&&
            ui.contains("BridgeWheelAccessRecovery.ask(this)")&&
            !ui.contains("BootConfig.send")&&
            !ui.contains("BootConfig.revealCenter")&&
            !ui.contains("MOSAICO_RECUPERADO_SOLICITADO:"),
            "Default Bridge UI owns QR/center/tablero/wheel independently");
        ck(windows.contains("BridgeWindowMath.center(")&&
            windows.contains("options.setLaunchDisplayId(1)")&&
            windows.contains("BridgeDualRoutePolicy.BOTH")&&
            !windows.contains("ReceiverService")&&
            link.contains("BridgeDualRoutePolicy.plan("),
            "Bridge independently routes selected map(s) on owner's chosen display");
        ck(manifest.contains("android:taskAffinity=\"${applicationId}.config\"")&&
            manifest.contains("android:launchMode=\"singleTask\"")&&
            manifest.contains("android:icon=\"@drawable/ic_drivelab_boot\"")&&
            !manifest.contains("receiver.permission.BOOT_MOSAIC")&&
            !config.contains("checkSignatures(")&&
            read("boot/src/main/res/drawable/ic_drivelab_boot.xml").contains("<vector"),
            "Bridge opens in independent task; no Receiver signature prerequisite");
        System.out.println("PASS Bridge standalone UI for both displays/QR/wheel without Receiver");
    }
}
