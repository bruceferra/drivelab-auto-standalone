package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Static guard: auto-open the Receiver UI without taking T1Launcher HOME. */
public final class T1BootOpenSourceContractTest {
    private static void require(boolean condition,String description){
        if(!condition)throw new AssertionError(description);
    }
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    public static void main(String[] args)throws Exception{
        String manifest=Files.readString(Path.of("receiver/src/main/AndroidManifest.xml"));
        String activity=read("ReceiverActivity.java");
        String boot=read("BootReceiver.java");
        String service=read("ReceiverService.java");
        require(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
            "Boot broadcast permission must be declared");
        require(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"),
            "Optional background Activity launch overlay permission must be declared");
        require(manifest.contains("android:name=\".BootReceiver\"")&&
                manifest.contains("android.intent.action.BOOT_COMPLETED"),
            "Registered boot receiver is required");
        require(manifest.contains("android:name=\".ReceiverActivity\" android:exported=\"true\"")&&
                manifest.contains("android.intent.category.LAUNCHER"),
            "Receiver home UI launchable from MainthorCar");
        require(!manifest.contains("android.intent.category.HOME"),
            "Never replace T1Launcher/Jetour HOME activity");
        require(activity.contains("ACTIVAR APERTURA AUTOMÁTICA DRIVELAB")&&
                activity.contains("this::toggleOwnBoot")&&
                activity.contains(".putBoolean(\"auto_boot\",true)")&&
                activity.contains(".putBoolean(\"launch_app_on_boot\",true)")&&
                activity.contains(".putString(\"launch_package\",getPackageName())"),
            "One-tap explicit owner choice enables existing boot service and selects own APK");
        require(activity.contains("Settings.canDrawOverlays(this)")&&
                activity.contains("Settings.ACTION_MANAGE_OVERLAY_PERMISSION"),
            "Explain and request Android overlay permission; do not silently grant it");
        require(boot.contains("Intent.ACTION_BOOT_COMPLETED")&&
                boot.contains("context.startForegroundService(start)")&&
                boot.contains("getBoolean(\"auto_boot\",false)"),
            "Foreground service must be booted only when user opted in");
        require(service.contains("getBoolean(\"launch_app_on_boot\",false)")&&
                service.contains("getPackageName().equals(pkg)?")&&
                service.contains("new Intent(this,ReceiverActivity.class)")&&
                service.contains("launch.putExtra(\"drivelab_open_after_boot\",true)")&&
                service.contains("Intent.FLAG_ACTIVITY_NEW_TASK"),
            "Launch explicit ReceiverActivity rather than voice alias on real boot");
        require(activity.contains("getBooleanExtra(\"drivelab_open_after_boot\",false)")&&
                activity.contains("DriveLab abrió su pantalla tras el arranque de Android"),
            "Record actual activity onResume, not merely a successful startActivity call");
        System.out.println("PASS DriveLab optional cold-boot foreground UI, no HOME hijack");
    }
}
