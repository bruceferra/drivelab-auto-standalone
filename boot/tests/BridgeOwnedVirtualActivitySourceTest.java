package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source contract: owner-UID-only Activity on private virtual display. */
public final class BridgeOwnedVirtualActivitySourceTest {
    private static final String DIR=
        "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
    private static void ok(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String host=Files.readString(Path.of(DIR+"BridgeClusterSurfaceProbeActivity.java"));
        String scene=Files.readString(Path.of(DIR+"BridgeVirtualSceneActivity.java"));
        String launch=Files.readString(Path.of(DIR+"BridgeVirtualTaskLauncher.java"));
        String ui=Files.readString(Path.of(DIR+"BootActivity.java"));
        String listener=Files.readString(Path.of(DIR+"BootLinkService.java"));
        String auto=Files.readString(Path.of(DIR+"BootAutoService.java"));
        String manifest=Files.readString(Path.of("boot/src/main/AndroidManifest.xml"));
        ok(host.contains("EXTRA_ACTIVITY_PROBE")&&
            host.contains("APP_MAX_MS=20000L")&&
            host.contains("if(activityProbe)")&&
            host.contains("BridgeVirtualTaskLauncher.launchOwn(this,d,sceneToken,publicProbe)")&&
            host.contains("publicProbe?PUBLIC_FLAGS:FLAGS")&&
            host.contains("DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC")&&
            host.contains("sceneVerified=true")&&
            host.contains("catch(SecurityException denied)")&&
            host.contains("showDeniedLaunchFallback(d,denied)")&&
            host.contains("PRESENTATION ANIMADA")&&
            host.contains("last_diagnostic")&&
            host.contains("static String lastDiagnostic(Context c)")&&
            host.contains("main.removeCallbacks(fallbackTicker)")&&
            host.contains("BridgeVirtualSceneActivity.finishSession(sceneToken)")&&
            host.contains("main.postDelayed(timeout,activityProbe?APP_MAX_MS:MAX_MS)")&&
            host.contains("virtual.release()")&&host.contains("surface.release()")&&
            host.contains("FLAG_NOT_TOUCHABLE")&&
            host.contains("bandHeight=Math.round(height*0.65f)"),
            "Only user initiated bounded noninteractive compositor");
        ok(launch.contains("new Intent(host,BridgeVirtualSceneActivity.class)")&&
            launch.contains("options.setLaunchDisplayId(id)")&&
            launch.contains("isActivityStartAllowedOnDisplay(host,id,own)")&&
            launch.contains("PRECHECK_DENIED_PRIVATE_DISPLAY")&&
            launch.contains("PRECHECK_DENIED_PUBLIC_DISPLAY")&&
            launch.contains("privateDisplay==expectPublic")&&
            launch.contains("id<=1")&&
            launch.contains("Display.FLAG_PRIVATE")&&
            launch.contains("FLAG_ACTIVITY_NEW_TASK")&&
            !launch.contains("com.waze")&&
            !launch.contains("com.google.android.apps.maps")&&
            !launch.contains("am stack")&&
            !launch.contains("force-stop")&&
            !launch.contains("CLEAR_TASK")&&
            !launch.contains("Runtime.getRuntime()"),
            "Private VirtualDisplay only own Activity, no maps or OEM");
        ok(scene.contains("Display actual=getDisplay()")&&
            scene.contains("actualId==expected")&&
            scene.contains("EXTRA_EXPECT_PUBLIC")&&
            scene.contains(")==expectedPublic")&&
            scene.contains("expected>1")&&
            scene.contains("Display.FLAG_PRIVATE")&&
            scene.contains("finishAndRemoveTask()")&&
            scene.contains("main.removeCallbacks(tick)")&&
            scene.contains("CONTADOR EN VIVO")&&
            !scene.contains("com.waze"),
            "Real Activity verifies display, animates and terminates");
        ok(ui.contains("this::askRealVirtualActivityProbe")&&
            ui.contains("this::askPublicVirtualActivityProbe")&&
            ui.contains("EXTRA_PUBLIC_PROBE,true")&&
            ui.contains("LAB · PROBAR APP REAL EN CAPA VIRTUAL · 20 S")&&
            ui.contains("BridgeClusterSurfaceProbeActivity.EXTRA_ACTIVITY_PROBE")&&
            !listener.contains("BridgeVirtualSceneActivity")&&
            !auto.contains("BridgeVirtualSceneActivity")&&
            manifest.contains("android:name=\".BridgeVirtualSceneActivity\"")&&
            manifest.contains("android:exported=\"false\"")&&
            manifest.contains("android:excludeFromRecents=\"true\"")&&
            !manifest.contains("android.intent.category.HOME"),
            "No boot, route, external or launcher entry point");
        System.out.println("PASS owned private VirtualDisplay Activity / cleanup");
    }
}
