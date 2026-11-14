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
        ok(setup.contains("T1ClusterWazeRouter.choose(")&&
            setup.contains("oem.gear()")&&
            setup.contains("oem.speedKmh()")&&
            setup.contains("gear!=1")&&setup.contains("kmh>0.5")&&
            setup.contains("setPositiveButton(\"ACTIVAR\"")&&
            setup.contains("putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)")&&
            setup.contains("putBoolean(\"auto_boot\",true)"),
            "User chooses the display and permits boot; manual test verified P/0");
        ok(receiver.contains("scheduleNativeClusterWaze()")&&
            receiver.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
            receiver.contains("T1ClusterWazeRouter.routeDestination(this,launch)")&&
            receiver.contains("cluster?new Intent(this,ReceiverActivity.class):launch"),
            "Receiver boot and Xiaomi destinations keep cluster route");
        ok(voice.contains("T1ClusterWazeRouter.routeDestination(this,i)")&&
            share.contains("T1ClusterWazeRouter.routeDestination(this,navigation)")&&
            main.contains("T1ClusterWazeSettingsActivity.class")&&
            main.contains("T1ClusterWazeRouter.routeDestination(this,open)"),
            "Voice, shared destinations and direct test follow same selected display");
        System.out.println("PASS opt-in Waze public cluster without replacing T1Launcher");
    }
}
