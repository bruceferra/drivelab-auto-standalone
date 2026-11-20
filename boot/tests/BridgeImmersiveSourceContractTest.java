package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BridgeImmersiveSourceContractTest {
    private static String src(String file)throws Exception{
        return Files.readString(Path.of("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",file));
    }
    private static void ok(boolean x,String what){if(!x)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String helper=src("BridgeImmersive.java");
        for(String activity:new String[]{"BootActivity.java","BridgeVirtualProbeActivity.java","BridgeLayersActivity.java"}){
            String s=src(activity);
            ok(s.contains("BridgeImmersive.apply(this)")&&s.contains("onWindowFocusChanged(boolean hasFocus)"),
                "Bridge must stay fullscreen only while its own window is active: "+activity);
        }
        ok(helper.contains("FLAG_FULLSCREEN")&&
           helper.contains("SYSTEM_UI_FLAG_IMMERSIVE_STICKY")&&
           helper.contains("SYSTEM_UI_FLAG_HIDE_NAVIGATION")&&
           helper.contains("WindowInsets.Type.statusBars()")&&
           helper.contains("WindowInsets.Type.navigationBars()")&&
           helper.contains("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE")&&
           !helper.contains("Settings.Secure")&&
           !helper.contains("Runtime.getRuntime()")&&
           !helper.contains("am force-stop")&&
           !helper.contains("pm clear")&&
           !helper.contains("com.autolink")&&
           !helper.contains("com.jetour"),
           "Only application Android insets may change; no OEM state or process mutation");
        System.out.println("PASS app-only immersive Bridge; no OEM layer control or ADB");
    }
}
