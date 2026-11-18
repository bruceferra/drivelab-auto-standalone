package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source-only Android11 regression: manual action NEVER claims to grant
 * Accessibility; rebind requires prior user authorization and local ADB. */
public final class T1WheelManualReconnectSourceTest {
    private static String source(String name)throws Exception {
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            name));
    }
    private static void must(boolean valid,String what){
        if(!valid)throw new AssertionError(what);
    }
    public static void main(String[] args)throws Exception {
        String recover=source("T1WheelAccessibilityRecovery.java");
        String settings=source("T1WheelSettingsActivity.java");
        String home=source("ReceiverActivity.java");
        String adb=source("T1LocalAdbClient.java");
        must(recover.contains("static String requestManual(Context context)")&&
            recover.contains("static String permissionState(Context context)")&&
            recover.contains("Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES")&&
            recover.contains("Settings.Secure.ACCESSIBILITY_ENABLED")&&
            recover.contains("T1WheelAccessListPolicy.target(component)")&&
            recover.contains("!state.startsWith(\"HABILITADA PERO DESCONECTADA\")")&&
            recover.contains("T1WheelAccessListPolicy.withoutOurService(current(app))")&&
            recover.contains("if(!RUNNING.compareAndSet(false,true))")&&
            recover.contains("restoreIfPending(app,p)")&&
            recover.contains("new Thread(()->{"),
            "One tap must require actual Android grant; protect rebind with journal and serialized worker");
        must(recover.contains("T1LocalAdbClient.shell(app,")&&
            recover.contains("T1WheelAccessListPolicy.command(without)")&&
            recover.contains("T1WheelAccessListPolicy.command(original)")&&
            recover.contains("reauthorizeWithOwnerApproval(Context context)")&&
            recover.contains("settings put secure accessibility_enabled 1")&&
            !recover.contains("settings put secure enabled_accessibility_services \"\""),
            "Only the exact previous component list can be temporarily unbound");
        must(settings.contains("RECONECTAR ACCESIBILIDAD · RUEDITA")&&
            settings.contains("T1WheelAccessibilityRecovery.requestManual(this)")&&
            settings.contains("T1WheelAccessibilityRecovery.permissionState(this)")&&
            settings.contains("openAccessibilitySettings()")&&
            settings.contains("Settings.ACTION_ACCESSIBILITY_SETTINGS"),
            "Wheel settings must offer explicit reconnect and Settings route");
        must(home.contains("RUEDITA · RECONECTAR ACCESIBILIDAD")&&
            home.contains("T1WheelAccessibilityRecovery.requestManual(this)")&&
            home.contains("Settings.ACTION_ACCESSIBILITY_SETTINGS"),
            "One-tap daily home recovery");
        must(adb.contains("private static final String LOOPBACK=\"127.0.0.1\"")&&
            adb.contains("settings put secure enabled_accessibility_services")&&
            !adb.contains("input keyevent 4")&&
            !adb.contains("Termux"),
            "Integrated ADB strictly loopback, no Termux, no input injection");
        System.out.println("PASS one-tap prior-authorized accessibility reconnect with consent and no Termux");
    }
}
