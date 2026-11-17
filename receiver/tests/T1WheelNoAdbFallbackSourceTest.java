package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** The retired ADB wheel fallback must be completely absent, not merely
 * inactive while its old error keeps appearing on screen.
 * Does not assert that Jetour will expose map Accessibility windows.
 */
public final class T1WheelNoAdbFallbackSourceTest {
    private static final Path SRC=Path.of(
        "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver");
    static String read(String name)throws Exception{
        return Files.readString(SRC.resolve(name));
    }
    static void yes(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        yes(!Files.exists(SRC.resolve("T1WheelAdbFallback.java")),
            "Retired wheel ADB fallback class must be deleted");
        yes(!Files.exists(SRC.resolve("T1WheelAdbFocusPolicy.java")),
            "Retired wheel task-focus policy must be deleted");
        String service=read("T1WazeWheelService.java");
        String boot=read("ReceiverService.java");
        String ui=read("T1WheelSettingsActivity.java");
        String adb=read("T1LocalAdbClient.java");
        String microphone=read("T1OemMicrophoneService.java");
        String cluster=read("T1GlobalToolbar.java");
        yes(!service.contains("T1WheelAdbFallback")&&
            !boot.contains("T1WheelAdbFallback")&&
            !ui.contains("T1WheelAdbFallback")&&
            !microphone.contains("T1WheelAdbFallback")&&
            !cluster.contains("T1WheelAdbFallback"),
            "Live code must never call the retired class");
        yes(!ui.contains("RESPALDO ADB DISPLAY")&&
            ui.contains("remove(\"adb_wheel_fallback_status\")")&&
            ui.contains("T1WheelAccessibilityRecovery.describe(this)"),
            "Remove the stale failed-task-focus warning, keep actual Accessibility status");
        yes(!adb.contains("am task focus [1-9][0-9]{0,6}")&&
            !adb.contains("input dpad -d [1-9] keyevent (21|22|23)")&&
            adb.contains("am stack resize") == false&&
            adb.contains("am task resize [1-9]"),
            "No focus or synthetic wheel key commands authorized; preserve cluster resize");
        yes(service.contains("onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,mapped));")&&
            boot.contains("T1WazeWheelService.requestClusterReady(this)")&&
            microphone.contains("T1WazeWheelService.dispatchFromSharedMicrophone("),
            "Preserve OEM codes, accessibility and microphone");
        yes(cluster.contains("reopenSavedPanelsStopped(owner)"),
            "Preserve saved split recovery without wheel ADB");
        System.out.println("PASS wheel fallback removed, no stale UI, map & mic preserved");
    }
}
