package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression for real Jetour bug: Clients Today/Month reset but the two
 * history columns still show old trips after Delete All; deletion errors
 * previously appeared far above the buttons.
 */
public final class T1HistoryDeletionSourceContractTest {
    static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        Path base=Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver");
        String ui=Files.readString(base.resolve("T1PremiumCockpitActivity.java"));
        String deletion=Files.readString(base.resolve("T1TripDeletion.java"));
        check(ui.contains("historyDeleteStatus=text("),
            "deletion result must be visible beside the two red buttons");
        check(ui.contains("historyDeleteResult(done,msg);"),
            "explicit modal must show success or failure, not only distant feedback");
        check(ui.contains("T1TripDeletion.blockReason(this)"),
            "active/paused/pending recorder must have precise visible reason");
        check(ui.contains("historyDeleteRevision++")&&
              ui.contains("revision!=historyDeleteRevision"),
            "pre-deletion background snapshot cannot repaint deleted rows");
        check(ui.contains("historyPickList.removeAllViews();")&&
              ui.contains("historyMasterList.removeAllViews();"),
            "delete must clear stale UI columns immediately");
        check(ui.contains("cachedMasterHistory=master==null?")&&
              ui.contains("T1MasterTripHistory.closed("),
            "reconstruct master from disk instead of stale cache");
        check(ui.contains("updateHistory();")&&
              ui.contains("T1JourneyStore.history(this)")&&
              ui.contains("T1JourneyStore2.history(this)"),
            "both independent client CSVs must be reread after deletion");
        check(deletion.contains("BORRADO CONFIRMADO:")&&
              deletion.contains("El borrado NO quedó completo:")&&
              deletion.contains("historyHasId(history,id)"),
            "report success only after local CSV verification");
        check(deletion.contains("open.add(\"VIAJE 1\"")&&
              deletion.contains("open.add(\"VIAJE 2\""),
            "precise reasons for both independent client slots");
        check(deletion.contains("n.equals(\"client_trips.csv\")")&&
              deletion.contains("n.equals(\"client_trips_2.csv\")"),
            "delete all recognizes source CSVs for both trip slots");
        check(deletion.contains("n.equals(\"grid_charges.csv\")")==false,
            "delete must never include charging records");
        check(ui.contains("historyRepairBusy.get()")&&
              ui.contains("T1TripDeletion.busy()"),
            "delete and OEM history reconstruction must not race");
        System.out.println("PASS: delete all refreshes both client columns from actual CSVs, "+
            "stale background reads rejected, visible errors, local-only safeguards");
    }
}
