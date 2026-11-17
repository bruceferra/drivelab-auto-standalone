package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: a hidden map Accessibility tree may use ONLY verified scoped DPAD.
 * Tests intentionally do not issue Android shell commands. */
public final class T1WheelAdbFallbackSourceContractTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
          "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void yes(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        String wheel=read("T1WazeWheelService.java");
        String backup=read("T1WheelAdbFallback.java");
        String policy=read("T1WheelAdbFocusPolicy.java");
        String adb=read("T1LocalAdbClient.java");
        String ui=read("T1WheelSettingsActivity.java");
        yes(wheel.contains("boolean handled=onKeyEvent(")&&
            wheel.contains("!handled&&activeRoot()==null")&&
            wheel.contains("T1WheelAdbFallback.request(this,code)"),
            "No fallback when Android Accessibility already works");
        yes(backup.contains("T1ClusterAdbResize.enabled(app)")&&
            backup.contains("T1ClusterWazeRouter.selected(app)")&&
            backup.contains("T1WheelTarget.selectedPanelEnabled(app)")&&
            backup.contains("T1WheelTarget.packageName(app)")&&
            backup.contains("T1AdbTaskParser.unique(tasks,id,pkg)")&&
            backup.contains("T1WheelAdbFocusPolicy.focusedOn(input,id,pkg)")&&
            backup.contains("T1LocalAdbClient.shell(app,")&&
            backup.contains("T1WheelAdbFocusPolicy.command(id,key)"),
            "Must verify actual selected task, package, display and input focus");
        String resize=read("T1ClusterAdbResize.java");
        yes(backup.contains("mayFocusMap(input,id,pkg)")&&
            !backup.contains("parkedForWheel")&&
            !backup.contains("speedKmh()")&&
            !backup.contains("gear()==1")&&
            backup.contains("focusCommand(map.id)")&&
            backup.contains("Thread.sleep(200L)")&&
            backup.contains("!T1WheelAdbFocusPolicy.focusedOn(input,id,pkg)")&&
            resize.contains("oem.gear()==1")&&
            resize.contains("oem.speedKmh()"),
            "Wheel focus must work while driving; geometry P/0 remains guarded");
        yes(adb.contains("am task focus [1-9][0-9]{0,6}")&&
            !backup.contains("am stack remove ")&&
            !backup.contains("am task resize "),
            "Shell focus change bounded to exact task ID; never alter split or remove maps");
        yes(policy.contains("hasFocus=true")&&
            policy.contains("canReceiveKeys=true")&&
            policy.contains("visible=true")&&
            policy.contains("displayId="),
            "Must prove actual per-display window focus before DPAD");
        yes(backup.contains("oemCode==1000")&&
            backup.contains("oemCode==1001")&&
            backup.contains("oemCode==1003")&&
            !backup.contains("key=4;")&&
            !backup.contains("tap ")&&
            !backup.contains("am task resize ")&&
            backup.contains("OK bloqueado; primero girá"),
            "Only deliberate wheel/OK, never BACK/OEM clicks/coordinates");
        yes(adb.contains("dumpsys input")&&
            adb.contains("input dpad -d [1-9] keyevent (21|22|23)")&&
            !adb.contains("input keyevent 4")&&
            ui.contains("RESPALDO ADB DISPLAY"),
            "Shell allowlist and visible diagnostic are required");
        System.out.println("PASS hidden map window verified ADB fallback safety contract");
    }
}
