package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Compile-only and pure JVM source contract for owner's Nov 18 testing. */
public final class T1CockpitCompactAndClusterRecoveryContractTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String cockpit=read("T1PremiumCockpitActivity.java");
        String rebuilt=read("T1RebuiltMorning.java");
        String reload=read("T1ClusterManualReboot.java");
        int gauge=cockpit.indexOf("root.addView(gauge,meterParams)");
        int history=cockpit.indexOf("secondary.addView(reconstructedMorningStatus)");
        check(gauge>0&&history>gauge,
            "Historical energy paragraph must never precede live gauges");
        check(!cockpit.contains("root.addView(reconstructedMorningStatus)"),
            "No giant historical banner in primary cockpit");
        check(cockpit.contains("T1RebuiltMorning.isCarriedDay(this)"),
            "Only current linked original day can inherit 21.03 km");
        check(rebuilt.contains("static boolean isCarriedDay(Context c)")&&
              rebuilt.contains("!isToday(c)||!attached(c)")&&
              rebuilt.contains("T1TripRecorderService.DESIRED"),
              "After TERMINAR, a new TRIP must not inherit old opening balance");
        int parked=reload.indexOf("gear!=1||!Double.isFinite(speed)");
        int stop=reload.indexOf("T1ClusterFreshStart.prepare(owner)");
        check(parked>0&&stop>parked&&
            reload.contains("attempt<2")&&
            reload.contains("Thread.sleep(400L)"),
            "Never force-stop live Waze/Maps without two OEM P/0 readings");
        check(reload.contains("OFF_VISIBLE_MS=10000L")&&
            reload.contains("closeClusterApps")&&
            reload.contains("performOn(owner,update,false)"),
            "Manual recovery still means apps stop + OEM OFF wait >=10s + ON");
        System.out.println("PASS compact cockpit, new-day zero, safe manual cluster recovery");
    }
}
