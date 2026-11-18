package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1RebuiltMorningSourceContractTest {
    private static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void yes(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String morning=src("T1RebuiltMorning.java");
        String cockpit=src("T1PremiumCockpitActivity.java");
        String recorder=src("T1TripRecorderService.java");
        yes(morning.contains("KM=21.03,EV_KM=15.61,ICE_KM=5.42,LITERS=0.24")&&
            morning.contains("PACK_KWH=6.23,REGEN_KWH=1.59"),
            "Exact 18-Nov source totals");
        yes(morning.contains("T1TripDeletion.blockReason(c)")&&
            morning.contains("T1TripDeletion.deleteAll(c)")&&
            morning.contains("sql.delete(\"samples\",null,null)")&&
            morning.contains("sql.delete(\"sessions\",null,null)")&&
            morning.contains("startForegroundService")&&
            morning.contains("T1TripRecorderService.ACTION_START"),
            "Explicit local old-history cleanup and new real recorder");
        yes(morning.contains("bindNewTrip(")&&
            recorder.contains("T1RebuiltMorning.bindNewTrip(this,name)")&&
            morning.contains("TRIP_ID")&&morning.contains("totalKm("),
            "Carry tied to precisely one new recorder, not a fake CSV");
        yes(cockpit.contains("confirmReconstructedMorning")&&
            cockpit.contains("T1RebuiltMorning.isReconstructed(x.key())")&&
            cockpit.contains("T1RebuiltMorning.totalKm(this)")&&
            cockpit.contains("T1RebuiltMorning.summary(this)")&&
            cockpit.contains("selectedHistoryKey=\"1:\"+T1RebuiltMorning.ID"),
            "Cockpit closed partial and ongoing TOTAL shown independently");
        yes(morning.contains("ELECTRICIDAD PAGADA REAL: SIN DATO")&&
            morning.contains("ESCENARIO 6,23 kWh")&&
            morning.contains("PONDERADO ECONÓMICO")&&
            morning.contains("NO se descuenta dos veces"),
            "Weighted price is an explicit counterfactual, not fabricated grid bill");
        yes(!morning.contains("drop table")&&
            !morning.contains("getExternalStorageDirectory")&&
            !morning.contains("com.t1launcher."),
            "No legacy app, external file or drive ECU changes");
        System.out.println("PASS morning import and cost separation source contract");
    }
}
