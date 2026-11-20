package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;
public final class BridgeWheelAccessibilityContractTest {
    static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    static void yes(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        String binder=src("BridgeWheelOemBinder.java");
        String service=src("BridgeWheelAccessibilityService.java");
        String activity=src("BootActivity.java");
        String manifest=Files.readString(Path.of("boot/src/main/AndroidManifest.xml"));
        yes(binder.contains("com.autolink.hardkeyservice.core.ALHardKeyService")&&
            binder.contains("IHardKeyService")&&
            binder.contains("IHardKeyCallback")&&
            binder.contains("transact(1)")&&binder.contains("transact(2)")&&
            binder.contains("code<1000||code>1003"),
            "Binding must be scoped to known T1 wheel keys, register/unregister cleanly");
        yes(service.contains("getWindowsOnAllDisplays()")&&
            service.contains("window.getDisplayId()!=selectedDisplay")&&
            service.contains("BridgeWheelPolicy.translate(")&&
            service.contains("case BridgeWheelPolicy.NEXT:")&&
            service.contains("case BridgeWheelPolicy.PREVIOUS:")&&
            service.contains("ACTION_ACCESSIBILITY_FOCUS")&&
            !service.contains("dispatchGesture(")&&
            !service.contains("GestureDescription")&&
            !service.contains("ZOOM_IN")&&
            service.contains("AccessibilityNodeInfo.ACTION_CLICK")&&
            service.contains("onKeyEvent(KeyEvent event)")&&
            service.contains("return false;")&&
            !service.contains("performGlobalAction(")&&
            !service.contains("am force-stop")&&
            !service.contains("pm clear")&&
            !service.contains("settings put")&&
            !service.contains("T1WazeWheelService")&&
            !service.contains("BootConfig.assertReceiver("),
            "Wheel service supports manually selected real window Display0/1 and no zoom/OEM/global controls");
        yes(activity.contains("BridgeWheelAccessibilityService")&&
            activity.contains("this::toggleWheel")&&
            activity.contains("setManuallyArmed(true)")&&
            activity.contains("setManuallyArmed(false)")&&
            service.contains("isManuallyArmed()&&prefs(this).getBoolean(ENABLED,false)")&&
            activity.contains("ACTION_ACCESSIBILITY_SETTINGS")&&
            manifest.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")&&
            manifest.contains("bridge_wheel_accessibility"),
            "Explicit owner accessibility enable and Bridge-only wheel UI");
        System.out.println("PASS Bridge wheel standalone and read-only OEM safety contracts");
    }
}
