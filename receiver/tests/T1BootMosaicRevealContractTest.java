package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Preserve legacy Receiver's own reveal safety without forcing Bridge
 * to call it or exposing a second, Receiver-dependent mosaic menu.
 */
public final class T1BootMosaicRevealContractTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
    private static void ok(boolean pass,String what){if(!pass)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String b="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String r="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String ui=read(b+"BootActivity.java");
        String config=read(b+"BootConfig.java");
        String engine=read(b+"BridgeStandaloneWindows.java");
        String old=read(r+"T1CenterBootReveal.java");
        ok(ui.contains("PROBAR DOS VENTANAS · SÓLO BRIDGE")&&
            ui.contains("BridgeStandaloneWindows.center(this)")&&
            !ui.contains("BootConfig.revealCenter(this)")&&
            !ui.contains("BootConfig.sendCenterOnly(this)")&&
            !ui.contains("MOSTRAR MOSAICO · RECUPERAR VENTANAS")&&
            !config.contains("ACTION_REVEAL")&&
            !config.contains("sendBroadcast("),
            "Bridge no longer depends on Receiver's mosaic recovery IPC");
        ok(engine.contains("Intent.FLAG_ACTIVITY_SINGLE_TOP")&&
            !engine.contains("FLAG_ACTIVITY_CLEAR_TASK")&&
            !engine.contains("force-stop")&&
            !engine.contains("pm clear"),
            "New Bridge launcher preserves maps and existing tasks");
        ok(old.contains("verifiedRememberedTask(")&&
            old.contains("actual.display!=0")&&
            old.contains("T1CenterMosaicViewport.disjoint(boxes)")&&
            old.contains("am.moveTaskToFront(")&&
            !old.contains("startActivity(")&&
            !old.contains("am force-stop")&&
            !old.contains("pm clear"),
            "Legacy Receiver reveal still safe in its own APK; not a dependency");
        System.out.println("PASS no Receiver reveal IPC in Bridge; maps and OEM protected");
    }
}
