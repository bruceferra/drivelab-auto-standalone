package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression: standalone Bridge MUST NOT start DriveLab Auto on ignition,
 * must expose QR + signed updater on its own home, and must never claim
 * that the unvalidated Freeform/Jetour cluster stack is ready.
 * Source-only; Android runtime is validated in Gradle separately.
 */
public final class BridgeIndependentBootContractTest {
    private static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    private static void ok(boolean truth,String message){
        if(!truth)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String boot=src("BootAutoService.java");
        String receiver=src("BootReceiver.java");
        String ui=src("BootActivity.java");
        String link=src("BootLinkService.java");
        String qr=src("BootQr.java");
        String updater=src("BootUpdater.java");
        String config=src("BootConfig.java");
        String windows=src("BridgeStandaloneWindows.java");
        String manifest=Files.readString(Path.of(
            "boot/src/main/AndroidManifest.xml"));
        ok(boot.contains("BootLinkService.START")&&
            boot.contains("startForegroundService(")&&
            boot.contains("BootConfig.enabled(this)")&&
            !boot.contains("BootConfig.send(")&&
            !boot.contains("T1BootMosaicBridgeService")&&
            !boot.contains("ReceiverService"),
            "Boot ignition cannot delegate to the old Receiver UI, "+
            "cluster or mosaic service");
        ok(receiver.contains("BootConfig.enabled(context)")&&
            receiver.contains("Intent.ACTION_BOOT_COMPLETED")&&
            !receiver.contains("startActivity("),
            "Ignition is opt-in and opens NO arbitrary activity");
        ok(ui.contains("DRIVELAB BRIDGE")&&
            ui.contains("BootUpdater.check(")&&
            ui.contains("BootQr.draw(")&&
            ui.contains("BootLinkService.PAIR_QR")&&
            ui.contains("BootLinkService.STOP")&&
            ui.contains("PROBAR DOS VENTANAS · SÓLO BRIDGE")&&
            ui.contains("PROBAR MAPA TABLERO · ESTACIONADO")&&
            ui.contains("RECONECTAR ACCESIBILIDAD · ADB LOCAL · SIN TERMUX")&&
            !ui.contains("legacyAdvanced")&&
            !ui.contains("BootConfig.assertReceiver")&&
            !ui.contains("BootConfig.takeOver(this)")&&
            !ui.contains("BootConfig.send")&&
            !ui.contains("BootConfig.revealCenter"),
            "Bridge owns the setup screen and does not auto-open Cockpit");
        ok(link.contains("BridgeCrypto.NAV_PORT")&&
            link.contains("BridgeCrypto.PAIR_PORT")&&
            link.contains("BridgeCrypto.decrypt(")&&
            link.contains("BridgeRouteTarget.baseKind(")&&
            link.contains("if(intent==null&&!BootConfig.enabled(this))")&&
            link.contains("BootConfig.enabled(this)?START_STICKY:START_NOT_STICKY")&&
            qr.contains("QRCodeWriter")&&
            updater.contains("SHA-256"),
            "Independent Xiaomi AES/QR receiver and signed local updater");
        ok(config.contains("getBoolean(\"enabled\",false)")&&
            !config.contains("assertReceiver(")&&
            !config.contains("T1BootMosaicBridgeReceiver")&&
            !config.contains("sendBroadcast(")&&
            windows.contains("static Result center(")&&
            windows.contains("static Result tablero(")&&
            !manifest.contains("receiver.permission.BOOT_MOSAIC")&&
            !manifest.contains("BootResultReceiver")&&
            !manifest.contains("ar.com.rango5gps.drivelabauto.receiver\"")&&
            manifest.contains("android:label=\"${bridgeLabel}\"")&&
            manifest.contains("android:taskAffinity=\"${applicationId}.config\"")&&
            manifest.contains("android.permission.SYSTEM_ALERT_WINDOW")&&
            !manifest.contains("android.intent.category.HOME")&&
            !manifest.contains("WRITE_SECURE_SETTINGS")&&
            !boot.contains("pm clear")&&
            !link.contains("am force-stop"),
            "Bridge never becomes OEM HOME or clears third-party data");
        System.out.println("PASS independent DriveLab Bridge: "+
            "opt-in QR boot, native windows, wheel, updater and zero Receiver IPC");
    }
}
