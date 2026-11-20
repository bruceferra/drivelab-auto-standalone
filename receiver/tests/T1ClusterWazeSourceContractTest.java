package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static guard. Passing CI != physical cluster display access on the T1. */
public final class T1ClusterWazeSourceContractTest {
    private static void ok(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    private static String source(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    public static void main(String[] args)throws Exception{
        String manifest=Files.readString(
            Path.of("receiver/src/main/AndroidManifest.xml"));
        String router=source("T1ClusterWazeRouter.java");
        String setup=source("T1ClusterWazeSettingsActivity.java");
        String receiver=source("ReceiverService.java");
        String main=source("ReceiverActivity.java");
        String voice=source("T1VoiceRouteActivity.java");
        String share=source("ShareToClusterActivity.java");
        ok(manifest.contains(".T1ClusterWazeSettingsActivity")&&
            !manifest.contains("android.intent.category.HOME"),
            "No launcher HOME replacement; own settings only");
        ok(router.contains("ActivityOptions.makeBasic()")&&
            router.contains(".setLaunchDisplayId(d.getDisplayId())")&&
            router.contains("Display.DEFAULT_DISPLAY")&&
            router.contains("Display.FLAG_PRIVATE")&&
            router.contains("FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS")&&
            router.contains("Intent.FLAG_ACTIVITY_NEW_TASK"),
            "Explicit public secondary-display Waze launch");
        ok(router.contains("getBoolean(ENABLED,false)")&&
            router.contains("getBoolean(AUTO_BOOT,false)")&&
            router.contains("DISPLAY_NAME")&&
            router.contains("DISPLAY_WIDTH")&&
            router.contains("DISPLAY_HEIGHT")&&
            router.contains("if(only!=null)return null;")&&
            router.contains("if(only!=null)return null;"),
            "Boot-safe opt-in display identity and unambiguous fingerprint");
        ok(router.contains("WAZE.equals(intent.getPackage())")&&
            router.contains("No se abrió ventana de respaldo")&&
            router.contains("No overlay windows"),
            "Waze package only, no unexpected primary-screen fallback");
        ok(!router.contains("createVirtualDisplay(")&&
            !router.contains("WindowManager.addView(")&&
            !router.contains("transact(15")&&
            !router.contains("su -c")&&
            !router.contains("adb shell"),
            "No overlay/MediaProjection or OEM controls");
        String chooser=setup.substring(setup.indexOf("    private void chooseDisplay(){"),
            setup.indexOf("    private void testWaze(){"));
        ok(chooser.contains(".setItems(labels,") &&
            !chooser.contains(".setMessage(") &&
            setup.contains("Pantallas secundarias públicas detectadas: "),
            "Display selector must show real list; Android AlertDialog setMessage hides setItems");
        ok(setup.contains("T1ClusterWazeRouter.choose(")&&
            setup.contains("oem.speedKmh()")&&
            setup.contains("kmh>0.5")&&
            setup.contains("setPositiveButton(\"ACTIVAR\"")&&
            setup.contains("putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)")&&
            setup.contains("putBoolean(\"auto_boot\",true)"),
            "User chooses the display and permits boot; manual test verified speed 0");
        ok(manifest.contains("com.google.android.apps.maps")&&
            manifest.contains("android.intent.category.LAUNCHER")&&
            !manifest.contains("android.permission.QUERY_ALL_PACKAGES"),
            "App chooser must see Maps/launchable apps without broad package access");
        ok(router.contains("selectedAppPackage(Context c)")&&
            router.contains("getString(APP_PACKAGE,WAZE)")&&
            router.contains("isSelectablePackage(Context c,String packageName)")&&
            router.contains("getLaunchIntentForPackage(packageName)")&&
            router.contains("packageName.equals(\"com.t1launcher\")")&&
            router.contains("packageName.startsWith(\"com.autolink.\")")&&
            router.contains("putBoolean(AUTO_BOOT,false)")&&
            router.contains("launchApp(c,T1ClusterLayoutPolicy.singlePackage(c),")&&
            router.contains("openSelectedAppTest(Context c)"),
            "Only explicit user-selected public apps, with auto boot reset on change");
        ok(setup.contains("ELEGIR APLICACIÓN DEL CLÚSTER")&&
            setup.contains("PROBAR APLICACIÓN ELEGIDA · DETENIDO")&&
            setup.contains("getLaunchIntentForPackage")==false&&
            setup.contains("pm.queryIntentActivities(launcher,0)")&&
            setup.contains("runParkedTest(boolean selectedApp)")&&
            setup.contains("openSelectedAppTest(this)")&&
            setup.contains("openManualTest(this)")&&
            setup.contains("kmh>0.5"),
            "Manual app switch and Waze must both pass stopped-vehicle check");
        ok(router.contains("WAZE.equals(intent.getPackage())")&&
            router.contains("return launchOnDisplay(c,launch,manualTest,fromBoot,\"Waze\",true)")&&
            receiver.contains("routeDestination(this,launch)")&&
            voice.contains("routeDestination(this,waze)")&&
            share.contains("routeDestination(this,navigation)"),
            "Receiver and explicit shared routes retain their Waze destination contract");
        ok(receiver.contains("scheduleNativeClusterWaze()")&&
            receiver.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
            receiver.contains("T1ClusterWazeRouter.routeDestination(this,launch)")&&
            receiver.contains("cluster?new Intent(this,ReceiverActivity.class):launch"),
            "Receiver boot and Xiaomi destinations keep cluster route");
        // The Receiver home is now telemetry-only: window and cluster
        // controls are intentionally owned by the standalone Bridge app.
        // Keep existing voice/share routing in their own activities until
        // the separate Bridge routing is physically validated.
        ok(voice.contains("T1ClusterWazeRouter.routeDestination(this,waze)")&&
            share.contains("T1ClusterWazeRouter.routeDestination(this,navigation)")&&
            main.contains("T1BootInstaller.openBoot(this,this::report)")&&
            main.contains("DRIVELAB BRIDGE")&&
            !main.contains("T1ClusterWazeSettingsActivity.class")&&
            // Legacy direct-route diagnostic still exists only in the
            // technical menu. It is not a duplicate home/Bridge control.
            main.contains("T1ClusterWazeRouter.routeDestination(this,open)"),
            "Voice/share routes and technical test preserved; Receiver home delegates cluster controls to Bridge");
        System.out.println("PASS opt-in Waze public cluster without replacing T1Launcher");
    }
}
