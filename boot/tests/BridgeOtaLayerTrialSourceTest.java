package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;

/** Update-only trial must neither relaunch Maps nor change OEM/window tasks. */
public final class BridgeOtaLayerTrialSourceTest {
    private static String src(String file)throws Exception{
        return Files.readString(Path.of(file));
    }
    private static void must(boolean ok,String msg){
        if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] args)throws Exception{
        String root="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String trial=src(root+"BridgeOtaLayerTrial.java");
        String ui=src(root+"BootActivity.java");
        String receiver=src(root+"BootReceiver.java");
        String link=src(root+"BootLinkService.java");
        String wheels=src(root+"BridgeWheelAccessibilityService.java");
        String mosaic=src(root+"BridgeStandaloneWindows.java");
        must(trial.contains("info.lastUpdateTime")&&
            trial.contains("OBSERVED_UPDATE")&&
            trial.contains("static void record(Context c,boolean mapVisible)")&&
            trial.contains("static String describe(Context c)")&&
            trial.contains("SIN RESULTADO FÍSICO AÚN")&&
            trial.contains("JETOUR / BLUETOOTH ENCIMA"),
            "User outcome must be attached to the actual installed APK timestamp");
        must(ui.contains("RESULTADO OTA · AHORA SE VE GOOGLE MAPS")&&
            ui.contains("RESULTADO OTA · SIGUE BLUETOOTH / JETOUR ENCIMA")&&
            ui.contains("BridgeOtaLayerTrial.describe(this)")&&
            ui.contains("BridgeOtaLayerTrial.record(this,true)")&&
            ui.contains("BridgeOtaLayerTrial.record(this,false)")&&
            ui.contains("MOSAICO · PRESET 67% MAPA / 33% SEGUNDA APP")&&
            ui.contains("putInt(BridgeStandaloneWindows.RATIO,67)")&&
            ui.contains("No crea todavía bordes redondeados"),
            "Manual update observation and honest optional 67/33 mosaic preset");
        for(String file:new String[]{trial,receiver}){
            must(!file.contains("startActivity(")&&
                !file.contains("setLaunchDisplayId(")&&
                !file.contains("pm clear")&&
                !file.contains("am force-stop")&&
                !file.contains("move-task")&&
                !file.contains("dispatchGesture(")&&
                !file.contains("WindowManager.addView("),
                "No automatic map, OEM, task or Accessibility control from OTA");
        }
        must(receiver.contains("Intent.ACTION_BOOT_COMPLETED")&&
            !receiver.contains("Intent.ACTION_MY_PACKAGE_REPLACED")&&
            mosaic.contains("static Result center(Activity activity)")&&
            link.contains("BridgeGoogleMapsReplacement.arm(this,displayId)")&&
            wheels.contains("BridgeGoogleMapsReplacement.startPending(this)"),
            "Existing confirmed navigation and manual mosaic must stay unchanged");
        System.out.println("PASS update-only nonintrusive layer A/B and mosaic preset; no OEM/map change");
    }
}
