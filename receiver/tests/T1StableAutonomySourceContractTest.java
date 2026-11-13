package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source contract tests for no 5-segment fuel denominator and 100 OEM events. */
public final class T1StableAutonomySourceContractTest {
    private static void ok(boolean b,String msg){
        if(!b)throw new AssertionError(msg);
    }
    public static void main(String[] ignored)throws Exception{
        Path root=Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver");
        String ui=Files.readString(root.resolve("T1PremiumCockpitActivity.java"));
        String stream=Files.readString(root.resolve("T1OemConsumptionCallbacks.java"));
        String cal=Files.readString(root.resolve("T1FuelCalibration.java"));
        ok(ui.contains("T1StableAutonomy.estimate("),
           "Cockpit must use stable model for headline autonomy");
        ok(ui.contains("T1Fuel500mWindow.decode("),
           "Cockpit must show recorded 100x500m evidence");
        ok(ui.contains("T1FuelCalibration.latestL100(this)"),
           "Latest pump evidence must be the default anchor");
        ok(ui.contains("stableFuelL100")&&ui.contains("stableFuelAt"),
           "Filtered L100 must persist after leaving Cockpit");
        ok(ui.contains("5 EVENTOS SON SOLO DIAGNÓSTICO"),
           "Short OEM callback average must not control headline");
        ok(stream.contains("history100.add(raw)")&&
           stream.contains("rawFuel100")&&
           stream.contains("restoreFuelHistory()"),
           "100 OEM segments are persisted and older trip logs backfilled");
        ok(stream.contains("trip_oem_")&&stream.contains("RandomAccessFile"),
           "Original historical raw fuel callbacks read without mutation");
        ok(!ui.contains("double selected=live.hasRollingCandidate?"),
           "Old 5 segment override must be removed");
        ok(!ui.contains("double selected=live.rolling50Candidate?"),
           "Only stable model chooses effective fuel consumption");
        ok(cal.contains("t1_refills_user.csv"),"Pump evidence not replaced");
        System.out.println("PASS stable fuel autonomy integration, historical OEM 500m ring and source gates");
    }
}
