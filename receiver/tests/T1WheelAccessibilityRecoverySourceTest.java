package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source safety checks for recovery across Android 11 service unbinding.
 * Tests cannot guarantee actual Jetour ROM rebinding or map-wheel control.
 */
public final class T1WheelAccessibilityRecoverySourceTest {
    static String src(String file)throws Exception{return Files.readString(Path.of(
        "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));}
    static void yes(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String wheel=src("T1WazeWheelService.java");
        String mic=src("T1OemMicrophoneService.java");
        String recovery=src("T1WheelAccessibilityRecovery.java");
        String adb=src("T1LocalAdbClient.java");
        String ui=src("T1WheelSettingsActivity.java");
        yes(wheel.contains("static boolean isServiceConnected()")&&
            wheel.contains("if(active==null){")&&
            wheel.contains("T1WheelAccessibilityRecovery.request(app)")&&
            !wheel.contains("habilitá Accesibilidad")&&
            wheel.contains("onUnbind(android.content.Intent intent)")&&
            wheel.contains("if(connectedInstance==this)connectedInstance=null"),
            "Don't report a green preference as an Android-connected service");
        yes(mic.contains("!T1WazeWheelService.isServiceConnected()")&&
            mic.contains("T1WheelAccessibilityRecovery.request(")&&
            mic.contains("T1WazeWheelService.dispatchFromSharedMicrophone(")&&
            mic.contains("KEY_MIC=1010"),
            "Recover while keeping ONE physical 1010 OEM microphone callback");
        yes(recovery.contains("Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES")&&
            recovery.contains("Settings.Secure.ACCESSIBILITY_ENABLED")&&
            recovery.contains("T1WheelAccessListPolicy.withoutOurService(old)")&&
            recovery.contains("T1WheelAccessListPolicy.command(without)")&&
            recovery.contains("T1WheelAccessListPolicy.command(original)")&&
            recovery.contains("restoreIfPending(app,p)")&&
            recovery.contains("putString(PENDING,old)")&&
            recovery.contains("putString(EXPECTED,without)")&&
            recovery.contains("RETRY_MS=90000L")&&
            recovery.contains("AtomicBoolean RUNNING"),
            "Before any ADB rebind keep a journal, preserve other enabled apps and throttle");
        yes(!recovery.contains("enabled_accessibility_services \"\"")&&
            !recovery.contains("am force-stop")&&
            !recovery.contains("pm disable")&&
            !recovery.contains("am stack remove")&&
            !recovery.contains("input dpad")&&
            !recovery.contains("am task resize")&&
            !recovery.contains("startActivity("),
            "No blind screen touch, auto-enable unauthorized services or window modifications");
        yes(adb.contains("settings put secure enabled_accessibility_services")&&
            adb.contains("[A-Za-z0-9_.$/:]{1,1024}")&&
            !adb.contains("input keyevent 4"),
            "ADB allows only a bounded component-only accessibility list");
        yes(ui.contains("boolean connected=T1WazeWheelService.isServiceConnected()")&&
            ui.contains("ACCESIBILIDAD: ")&&
            ui.contains("RECUPERACIÓN ACCESIBILIDAD:")&&
            ui.contains("on&&link&&connected"),
            "UI exposes real bound service; no false green");
        System.out.println("PASS recovery of prior-approved wheel accessibility with OEM and UI guards");
    }
}
