package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Source-only safety contract for T1N145-inspired VirtualDisplay exploration.
 * Does NOT mean that Jetour firmware displays a Waze/Maps task.
 */
public final class BridgeVirtualProbeContractTest {
    private static void ok(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    public static void main(String[] args)throws Exception{
        String activity=read("boot/src/main/java/ar/com/rango5gps/"
            +"drivelabauto/boot/BridgeVirtualProbeActivity.java");
        String home=read("boot/src/main/java/ar/com/rango5gps/"
            +"drivelabauto/boot/BootActivity.java");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        ok(activity.contains("DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY")&&
            activity.contains("DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION")&&
            activity.contains("manager.createVirtualDisplay(")&&
            activity.contains("new Surface(texture)")&&
            activity.contains("new Presentation(this,target)")&&
            activity.contains("presentation.show()")&&
            activity.contains("onSurfaceTextureUpdated(")&&
            activity.contains("new TextureView(this)"),
            "Private virtual screen and own visual content must be real, not a mock ACK");
        ok(activity.contains("MAX_SESSION_MS=120000L")&&
            activity.contains("main.postDelayed(expiry,MAX_SESSION_MS)")&&
            activity.contains("presentation.dismiss()")&&
            activity.contains("virtualDisplay.release()")&&
            activity.contains("sink.release()")&&
            activity.contains("protected void onStop()")&&
            activity.contains("onSurfaceTextureDestroyed(")&&
            activity.contains("stopProbe("),
            "Preview must time out and release all resources on exit/failure");
        ok(activity.contains("Display.DEFAULT_DISPLAY")&&
            activity.contains("Display[] existing=manager.getDisplays()")&&
            activity.contains("Display.FLAG_PRIVATE")&&
            activity.contains("previous.getDisplayId()==target.getDisplayId()")&&
            !activity.contains("startActivity(")&&
            !activity.contains("getLaunchIntentForPackage(")&&
            !activity.contains("setLaunchDisplayId(")&&
            !activity.contains("AccessibilityService")&&
            !activity.contains("WindowManager.addView")&&
            !activity.contains("pm clear")&&
            !activity.contains("force-stop")&&
            !activity.contains("settings put")&&
            !activity.contains("adb shell"),
            "No third-party tasks, physical Display 1, OEM overlays or destructive commands");
        ok(home.contains("this::askBridgeVirtualProbe")&&
            home.contains("BridgeVirtualProbeActivity.class")&&
            home.contains("Sólo con la Jetour detenida")&&
            home.contains("setNegativeButton(\"CANCELAR\",null)")&&
            manifest.contains("android:name=\".BridgeVirtualProbeActivity\"")&&
            manifest.contains("android:exported=\"false\"")&&
            !manifest.contains("android.permission.CAPTURE_VIDEO_OUTPUT")&&
            !manifest.contains("android.permission.WRITE_SECURE_SETTINGS"),
            "Owner-initiated isolated lab feature must need no privileged Android permissions");
        System.out.println("PASS isolated Bridge VirtualDisplay preview, manual & bounded, without map/OEM task mutation");
    }
}
