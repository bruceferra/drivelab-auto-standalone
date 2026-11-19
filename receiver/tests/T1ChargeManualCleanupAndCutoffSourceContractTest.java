package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Users may delete only the manually imported test charge.
 * The new 19 Nov 08:00 journey report uses raw automatic samples and
 * explicitly never fabricates missing telemetry or resets live trips.
 */
public final class T1ChargeManualCleanupAndCutoffSourceContractTest {
    static void ck(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    static String file(String relative)throws Exception{
        return Files.readString(Path.of(relative));
    }
    public static void main(String[] args)throws Exception{
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String db=file(root+"T1ChargeStore.java");
        String ui=file(root+"T1ChargeHistoryActivity.java");
        String auto=file(root+"T1AutoJourneyActivity.java");
        String autoDb=file(root+"T1AutoJourneyStore.java");
        String trip=file(root+"T1TripRecorderService.java");
        ck(db.contains("synchronized boolean deleteManualImport(long id)")&&
            db.contains("db.beginTransaction()")&&
            db.contains("db.setTransactionSuccessful()")&&
            db.contains("db.endTransaction()")&&
            db.contains("is_open=0 AND source LIKE '%IMPORTACIÓN MANUAL%'"),
            "Only manually imported closed charges may be deleted by exact ID");
        ck(ui.contains("BORRAR SOLO ESTA CARGA MANUAL #")&&
            ui.contains("setNegativeButton(\"CANCELAR\",null)")&&
            ui.contains("db.deleteManualImport(row.id)")&&
            ui.contains("db.exportForCosts(this)")&&
            ui.contains("row.source.contains(\"IMPORTACIÓN MANUAL\")"),
            "Deletion must be manual, confirmed and update only its own charge CSV");
        ck(auto.contains("DESDE 19/11/2026 08:00 · SIN INVENTAR KM")&&
            auto.contains("dateFrom.setText(\"2026-11-19 08:00\")")&&
            auto.contains("generateRange()")&&
            autoDb.contains("synchronized RangeReport between(long from,long until)")&&
            autoDb.contains("km_oem_cum"),
            "Cutoff summary must derive from real automatic samples only");
        ck(trip.contains(".putLong(START_TIME,System.currentTimeMillis())"),
            "New live trip must use actual start time, not falsified retroactive 08:00");
        ck(!ui.contains("DELETE FROM sessions")&&
            !auto.contains("db.delete(\"sessions\""),
            "History view never deletes automatic charge or ignition data");
        System.out.println("PASS manual-only imported charge delete and observed 19 Nov 08:00 journey summary");
    }
}
