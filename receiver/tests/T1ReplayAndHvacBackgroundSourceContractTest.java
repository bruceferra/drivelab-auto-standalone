package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline/source QA for map replay service and HVAC 1004 fail-safe auto retry. */
public final class T1ReplayAndHvacBackgroundSourceContractTest {
    private static int checks;
    private static void ok(boolean yes,String why){
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static String src(String name)throws Exception{
        return Files.readString(Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    public static void main(String[] args)throws Exception{
        String activity=src("T1TripMapAnalysisActivity.java"),
               service=src("T1TripMapReplayService.java"),
               recirc=src("RecirculationGuardService.java"),
               toolbar=src("T1GlobalToolbar.java"),
               quick=src("ReceiverQuickControls.java"),
               boot=src("BootReceiver.java"),
               manifest=Files.readString(Path.of("receiver/src/main/AndroidManifest.xml")),
               waze=src("T1WazeWheelService.java"),
               xml=Files.readString(Path.of("receiver/src/main/res/xml/waze_wheel_service.xml"));
        ok(activity.contains("startForegroundService(request)")&&
            activity.contains("T1TripMapReplayService.snapshot(this)")&&
            activity.contains("void refreshReplay()")&&
            !activity.contains("new Thread(()->{\n            String result"),
            "Trip activity must delegate analysis to persistent worker");
        ok(service.contains("startForeground(NOTIFICATION_ID")&&
            service.contains("T1TripMapReplay.analyze(")&&
            service.contains("AtomicBoolean ACTIVE")&&
            service.contains("putString(RESULT,result)")&&
            service.contains("START_NOT_STICKY")&&
            service.contains("stopForeground(STOP_FOREGROUND_REMOVE)"),
            "Single foreground job with local persistent progress/result and no auto boot restart");
        ok(manifest.contains("android:name=\".T1TripMapReplayService\"")&&
            manifest.contains("android:foregroundServiceType=\"dataSync\""),
            "Declared independent user-started FGS");
        ok(recirc.contains("recoverLegacyReadError")&&
            recirc.contains("startsWith(")&&
            recirc.contains("PAUSADO: 4 errores OEM consecutivos.")&&
            !recirc.contains("if(failures>=4){\n                        pref().edit().putBoolean(PAUSED,true)")&&
            recirc.contains("Math.min(60000L,10000L*(failures-2))"),
            "No sticky pause after OEM read failures; capped retry backoff");
        ok(recirc.contains("recentWrites.size()>=3")&&
            recirc.contains("putBoolean(PAUSED,true)")&&
            recirc.contains("vehicle.enableCabinRecirculation()")&&
            recirc.contains("LAST_CONFIRMED"),
            "Keep pause after repeated OEM reversions; cannot claim successful recirc without verification");
        ok(boot.contains("RecirculationGuardService.recoverLegacyReadError(context)")&&
            toolbar.contains("verifiedAge<12000")&&
            toolbar.contains("LAST_CONFIRMED")&&
            toolbar.contains("AIRE AUTO · RECONECTANDO")&&
            quick.contains("recircConfirmed"),
            "Automatic restore on boot and green restricted to confirmed recent 1004=1");
        ok(waze.contains("getWindowsOnAllDisplays()")&&
            waze.contains("setDisplayId(chosen.displayId)")&&
            waze.contains("createDisplayContext(display)")&&
            waze.contains("T1WazePromptPolicy.dismiss")&&
            waze.contains("promptArmed")&&
            waze.contains("INSPECCIONAR")==false&&
            xml.contains("android:canPerformGestures=\"true\""),
            "Waze prompt only: on-display labels, user armed confirm, scoped gestures");
        ok(waze.contains("private void scanRequestedWindow()")&&
            waze.contains("SCAN_RESULT")&&
            waze.contains("lastScanAt=requested")&&
            src("T1WheelSettingsActivity.java").contains(
                "INSPECCIONAR CONTROLES DE WAZE"),
            "Diagnostics inspect Waze display without clicking");
        System.out.println("PASS "+checks+" background trip/HVAC/Waze safety source contracts");
    }
}
