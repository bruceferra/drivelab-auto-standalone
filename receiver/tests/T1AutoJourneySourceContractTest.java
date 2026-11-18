package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1AutoJourneySourceContractTest {
    static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            name));
    }
    static void yes(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }
    public static void main(String[] args)throws Exception{
        String o=src("T1AutoDriveOem.java");
        String service=src("T1AutoJourneyService.java");
        String db=src("T1AutoJourneyStore.java");
        String screen=src("T1AutoJourneyActivity.java");
        String boot=src("BootReceiver.java");
        String app=src("T1ImmersiveActivity.java");
        String cockpit=src("T1PremiumCockpitActivity.java");
        String manifest=Files.readString(Path.of(
            "receiver/src/main/AndroidManifest.xml"));
        yes(o.contains("i(1031)")&&o.contains("i(1033)")&&
            o.contains("i(1060)")&&o.contains("f(1062)")&&
            o.contains("f(1063)")&&o.contains("f(1064)")&&
            o.contains("f(2148)")&&!o.contains("transact(15"),
            "OEM ignition session must be read-only, sampling HV and gear");
        yes(service.contains("getBoolean(T1AutoJourneyStore.ENABLED,true)")&&
            service.contains("T1AutoDrivePolicy.start(")&&
            service.contains("T1AutoDrivePolicy.stop(")&&
            service.contains("openJourney()")&&
            service.contains("db.begin(")&&service.contains("db.append(")&&
            service.contains("db.end(")&&
            service.contains("now-previous.last>T1AutoJourneyStore.GAP_MS")&&
            service.contains("attachLocation()"),
            "Unattended boot-started session lifecycle and GPS capture");
        yes(db.contains("SQLiteOpenHelper")&&
            db.contains("t1_ignition_journeys.db")&&
            db.contains("CREATE TABLE sessions")&&
            db.contains("CREATE TABLE samples")&&
            db.contains("t_epoch_ms")&&
            db.contains("gps_lat")&&db.contains("gps_lon")&&
            db.contains("hv_out_cum")&&db.contains("hv_in_cum")&&
            db.contains("db.beginTransaction()")&&
            db.contains("db.setTransactionSuccessful()")&&
            db.contains("dt<=15000L"),
            "Durable raw points and atomic energy integration without gap fill");
        yes(screen.contains("DESDE ÚLTIMA CARGA")&&
            screen.contains("ESTE MES")&&
            screen.contains("HOY")&&
            screen.contains("EXPORTAR PUNTOS GPS")&&
            screen.contains("EXPORTAR SESIONES")&&
            screen.contains("chargeDb.latest(")&&
            screen.contains("db.since(chargedAt)")&&
            screen.contains("T1PlugEnergyLedger.estimate(")&&
            screen.contains("assignedPlugKwh")&&
            screen.contains("allocatedGridKwh"),
            "Charge-weighted nonphysical attribution, dated summaries and exports");
        yes(boot.contains("T1AutoJourneyService.startIfEnabled(context)")&&
            app.contains("T1AutoJourneyService.startIfEnabled(this)")&&
            cockpit.contains("VIAJES AUTO · ENCENDIDOS / ÚLTIMA CARGA")&&
            manifest.contains(".T1AutoJourneyActivity")&&
            manifest.contains(".T1AutoJourneyService"),
            "Available in cockpit, auto-enabled across boot, component declared");
        yes(!db.contains("com.t1launcher")&&
            !service.contains("com.t1launcher")&&
            !service.contains("T1JourneyStore.recordCharge")&&
            !db.contains("T1JourneyStore.recordCharge"),
            "No T1Launcher dependency or overwriting manual trip data");
        System.out.println("PASS OEM auto ignition trips: separate GPS raw flow and charge scopes");
    }
}
