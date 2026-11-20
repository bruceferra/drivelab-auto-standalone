package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Physical Display1 probe is a bounded, manually triggered full-width lower-band window.
 * A received frame is NOT proof of foreground Z or permission to hide OEM.
 */
public final class BridgeClusterSurfaceProbeSourceTest {
    private static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    private static void check(boolean valid,String why){
        if(!valid)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String test=src("BridgeClusterSurfaceProbeActivity.java");
        String ui=src("BootActivity.java");
        String listener=src("BootLinkService.java");
        String boot=src("BootReceiver.java");
        String manifest=Files.readString(
            Path.of("boot/src/main/AndroidManifest.xml"));
        check(test.contains("new Presentation(this,physical)")&&
            test.contains("DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY")&&
            test.contains("DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION")&&
            test.contains("new TextureView(cluster.getContext())")&&
            test.contains("new Surface(texture)")&&
            test.contains("displays.createVirtualDisplay(")&&
            test.contains("new Presentation(this,d)")&&
            test.contains("pattern.show()"),
            "Real Display1 Presentation must host separate, private virtual surface pipeline");
        check(test.contains("MAX_MS=12000L")&&
            test.contains("window.setLayout(width,height)")&&
            test.contains("host.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)")&&
            test.contains("width,bandHeight")&&
            test.contains("bandHeight=Math.round(height*0.65f)")&&
            test.contains("FLAG_NOT_FOCUSABLE")&&
            test.contains("FLAG_NOT_TOUCHABLE")&&
            test.contains("if((physical.getFlags()&Display.FLAG_PRIVATE)!=0)")&&
            test.contains("main.postDelayed(timeout,activityProbe?APP_MAX_MS:MAX_MS)")&&
            test.contains("main.removeCallbacks(timeout)")&&
            test.contains("@Override protected void onStop()")&&
            test.contains("@Override protected void onDestroy()")&&
            test.contains("virtual.release()")&&
            test.contains("surface.release()"),
            "Probe must span all lower Display1, be noninteractive, self-terminating and fail closed");
        check(test.contains("NO es Waze")||test.contains("NO ES WAZE"),
            "Test must clearly disclose it is not live map routing");
        check(!test.contains("am force-stop")&&
            !test.contains("pm clear")&&
            !test.contains("am stack")&&
            !test.contains("setLaunchDisplayId")&&
            !test.contains("FLAG_ACTIVITY_CLEAR_TASK")&&
            !test.contains("setType(")&&
            !test.contains("SYSTEM_ALERT_WINDOW")&&
            !test.contains("addView(" + "toOEM"),
            "Never alter Android task stacks, arbitrary overlays or OEM display services");
        check(ui.contains("COMPROBAR CAPA INFERIOR COMPLETA · DISPLAY 1 · 12 S")&&
            ui.contains("this::askRealClusterSurfaceProbe")&&
            ui.contains("startActivity(new Intent(this,")&&
            ui.contains("BridgeClusterSurfaceProbeActivity.class)")&&
            !listener.contains("BridgeClusterSurfaceProbeActivity")&&
            !boot.contains("BridgeClusterSurfaceProbeActivity"),
            "Only explicit parked user interaction; no ignition or route hook");
        check(manifest.contains("android:name=\".BridgeClusterSurfaceProbeActivity\"")&&
            manifest.contains("android:exported=\"false\""),
            "Probe Activity must not be externally launchable");
        System.out.println("PASS bounded physical Display1 test pattern, virtual surface, no OEM changes");
    }
}
