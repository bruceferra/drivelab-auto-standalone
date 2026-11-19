package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** DriveLab Boot must be a separate, signed app with a single-screen UI.
 * It may start only an exact existing receiver-side Freeform launcher,
 * never claim HOME, touch OEM/cluster or expose a shell to other apps.
 */
public final class T1BootStandaloneSourceContractTest {
    static String read(String file)throws Exception{
        return Files.readString(Path.of(file));
    }
    static void ck(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String receiver=read("receiver/src/main/AndroidManifest.xml");
        String boot=read("boot/src/main/AndroidManifest.xml");
        String settings=read("settings.gradle");
        String policy=read(root+"T1BootMosaicBridgeReceiver.java");
        String service=read(root+"T1BootMosaicBridgeService.java");
        String config=read(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootConfig.java");
        String ui=read(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootActivity.java");
        String auto=read(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootAutoService.java");
        String bootReceiver=read(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootReceiver.java");
        String geom=read(root+"T1CenterMosaicViewport.java");
        String engine=read(root+"T1CenterSavedFreeformMosaic.java");
        String oldService=read(root+"ReceiverService.java");
        ck(settings.contains("':boot'"),
            "Boot app must be a separate Gradle module and package");
        ck(boot.contains("android:name=\".BootActivity\"")&&
            boot.contains("android.intent.category.LAUNCHER")&&
            !boot.contains("android.intent.category.HOME")&&
            !boot.contains("android.permission.WRITE_SECURE_SETTINGS"),
            "Boot uses a single ordinary app icon, not T1 Launcher HOME");
        ck(receiver.contains("android:protectionLevel=\"signature\"")&&
            receiver.contains("android:name=\".T1BootMosaicBridgeReceiver\"")&&
            receiver.contains("android:permission=\"ar.com.rango5gps.drivelabauto.receiver.permission.BOOT_MOSAIC\"")&&
            policy.contains("PackageManager.SIGNATURE_MATCH"),
            "Receiver mosaic bridge must be signature permission and signature verified");
        ck(boot.contains("android:permission=\"ar.com.rango5gps.drivelabauto.receiver.permission.BOOT_MOSAIC\"")&&
            config.contains("checkSignatures(")&&
            config.contains("new ComponentName(RECEIVER,BRIDGE)"),
            "Boot can only talk to its signed Receiver over exact IPC");
        ck(ui.contains("Una aplicación (100 % de la zona útil)")&&
            ui.contains("Tres columnas")&&
            ui.contains("Dos columnas")&&
            ui.contains("slider.setMin(1);slider.setMax(99)")&&
            ui.contains("chooseApp(")&&
            ui.contains("MOSTRAR MOSAICO · RECUPERAR VENTANAS")&&
            ui.contains("PREPARAR / CAMBIAR DISTRIBUCIÓN")&&
            ui.contains("▶ PROBAR SOLO DISPLAY 1 · SIN SALIR DE BOOT")&&
            !ui.contains("T1MosaicShortcutActivity"),
            "Boot must expose 1/2/3 panels and never hand off its configuration to Receiver");
        ck(auto.contains("Thread.sleep(15000L)")&&
            auto.contains("BootConfig.enabled(this)")&&
            auto.contains("BootConfig.send(this,true)")&&
            bootReceiver.contains("Intent.ACTION_BOOT_COMPLETED")&&
            bootReceiver.contains("BootConfig.enabled(context)")&&
            config.contains("getBoolean(\"enabled\",false)"),
            "Ignition auto-start must be opt-in, one-shot and not open Boot UI");
        ck(config.contains("BOOT_MOSAIC_TAKEOVER")&&
            policy.contains("launch_app_on_boot\",false")&&
            oldService.contains("launch_app_on_boot")&&
            policy.contains("ReceiverService.PREFS"),
            "Boot takeover disables only legacy Receiver interface auto-launch");
        ck(service.contains("T1CenterSavedFreeformMosaic.apply(")&&
            service.contains("getDisplay(Display.DEFAULT_DISPLAY)")&&
            service.contains("T1CenterMosaicGeometry.layout(")&&
            service.contains("mode==4?1:mode==3?2:3")&&
            geom.contains("original.length<1")&&
            engine.contains("if(count<1||count>3"),
            "Standalone launcher must delegate proven 1/2/3 Freeform tiles on Display0");
        ck(!service.contains("am force-stop")&&
            !service.contains("settings put ")&&
            !service.contains("displayId=1")&&
            !ui.contains("setPreferredActivity(")&&
            !auto.contains("startActivity(")&&
            !policy.contains("startActivity("),
            "No OEM HOME/cluster takeover or killing apps in boot launcher");
        System.out.println("PASS independent Boot APK, one-shot opt-in and protected Receiver ADB bridge");
    }
}
