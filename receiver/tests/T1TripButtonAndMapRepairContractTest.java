package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source regression checks for the trip controls and manual map repair.
 * Run from repository root: javac -d /tmp/drivelab-tests receiver/tests/T1TripButtonAndMapRepairContractTest.java
 * && java -cp /tmp/drivelab-tests ar.com.rango5gps.drivelabauto.receiver.T1TripButtonAndMapRepairContractTest
 */
public final class T1TripButtonAndMapRepairContractTest {
    static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        String ui=read("T1PremiumCockpitActivity.java");
        String adb=read("T1LocalAdbClient.java");
        String reboot=read("T1ClusterManualReboot.java");
        check(ui.contains("tripActions(dayStartButton,dayPauseButton,dayEndButton,")&&
            ui.contains("tripActions(clientStartButton,clientPauseButton,clientEndButton,")&&
            ui.contains("tripActions(secondStartButton,secondPauseButton,secondEndButton,"),
            "All three trip scopes must use identical button policy");
        check(ui.contains("button.setEnabled(enabled)")&&
            ui.contains("button.setClickable(enabled)")&&
            ui.contains("button.setAlpha(enabled?1f:0.42f)")&&
            ui.contains("enabled?color:MUTED"),
            "Gray buttons must be actually disabled");
        check(ui.contains("T1TripActionPolicy.resolve(")&&
            ui.contains("tripAction(start,\"INICIAR\",TEAL,state.start)")&&
            ui.contains("paused?\"REANUDAR\":\"PAUSAR\"")&&
            ui.contains("paused?TEAL:GOLD")&&
            ui.contains("tripAction(end,\"TERMINAR\",RED,state.end)"),
            "Trip actions must have predictable green/yellow/red colors");
        check(ui.contains("if(T1JourneyStore.paused(this))askClient();else pauseClient()")&&
            ui.contains("if(T1JourneyStore2.paused(this))askSecond();else pauseSecond()"),
            "Paused trip must resume rather than start another trip");
        check(!adb.contains("!command.equals(\"pm clear")&&
            !adb.contains("!command.matches(\"pm clear"),
            "Android 11 must forbid all pm clear: --cache-only erases user data");
        check(!reboot.contains("T1LocalAdbClient.shell(")&&
            !reboot.contains("T1ClusterFreshStart.prepare(")&&
            reboot.contains("REPARAR MAPAS DESACTIVADO POR SEGURIDAD"),
            "Manual REBOOT must never erase accounts, offline maps or settings");
        System.out.println("Trip controls and manual map repair: OK");
    }
}
