package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1ChargeAutoModuleSourceContractTest {
    private static String r(String name)throws Exception{
        return Files.readString(Path.of("receiver/src/main/java/ar/com/"+
            "rango5gps/drivelabauto/receiver",name));
    }
    private static void yes(boolean condition,String explanation){
        if(!condition)throw new AssertionError(explanation);
    }
    public static void main(String[] args)throws Exception{
        String service=r("T1ChargeRecorderService.java");
        String store=r("T1ChargeStore.java");
        String oem=r("T1ChargeOemReader.java");
        String screen=r("T1ChargeHistoryActivity.java");
        String maths=r("T1ChargeMath.java");
        String boot=r("BootReceiver.java");
        String cockpit=r("T1PremiumCockpitActivity.java");
        String manifest=Files.readString(Path.of(
            "receiver/src/main/AndroidManifest.xml"));
        yes(oem.contains("getInt(b,1060)")&&
            oem.contains("getFloatOrUnknown(b,1062)")&&
            oem.contains("getFloatOrUnknown(b,1063)")&&
            oem.contains("getFloatOrUnknown(b,1064)")&&
            !oem.contains("transact(15"),
            "No actuator writes or whole Vehicle diagnostic scan");
        yes(service.contains("CABLE_PRESENT")||
            service.contains("T1ChargeMath.plugged(raw)"),
            "Require explicit connected cable for every automatic session");
        yes(service.contains("cableConnected>=2")&&
            service.contains("cableRemoved>=2")&&
            service.contains("db.openSession()")&&
            service.contains("PLUG_POLL_MS=5000L")&&
            service.contains("IDLE_POLL_MS=15000L")&&
            service.contains("getBoolean(T1ChargeStore.ENABLED,false)")&&
            service.contains("db.exportForCosts(this)"),
            "Debounced opt-in low-frequency charge service and persisted DB");
        yes(service.contains("if(!open && cableConnected>=2)")&&
            !service.contains("cableConnected>=2 &&")&&
            service.contains("T1ChargeMath.charging(")&&
            service.contains("T1ChargeStore.fmt(data.amps,2)")&&
            service.contains("T1ChargeStore.fmt(data.volts,1)")&&
            store.contains("ENCHUFADO · flujo HV por confirmar"),
            "Stable cable opens session independently of signed HV current; "+
            "actual V/A remain visible without inventing energy");
        yes(service.contains("LAST_SAMPLE_AT")&&
            service.contains("LAST_VOLTS")&&
            service.contains("LAST_AMPS")&&
            service.contains("LAST_SAMPLE_OK")&&
            screen.contains("T1ChargeRecorderService.startIfEnabled(this)")&&
            screen.contains("refreshHandler.postDelayed(refreshLoop,5000L)")&&
            screen.contains("SEÑALES OEM · última muestra")&&
            screen.contains("T1ChargeStore.LAST_AMPS"),
            "Charge page must restart the enabled worker and show live OEM "+
            "signals even without a registered session");
        yes(oem.contains("getIntOrUnknown(b,1063)")&&
            oem.contains("getFloatOrUnknown(b,1090)")&&
            oem.contains("readAlcar1063()")&&
            service.contains("LAST_VOLTAGE_PROBE")&&
            service.contains("currentCharging(")&&
            screen.contains("LAST_VOLTAGE_PROBE")&&
            maths.contains("double kwh=Double.isFinite(volts)")&&
            maths.contains("double ah=-amps*dtMs/3600000d"),
            "Charge recorder must expose read-only voltage candidates; "+
            "integrate measured Ah without inventing battery kWh");
        yes(oem.contains("HAL_EXT_BATT_VOLT=559940910")&&
            oem.contains("HAL_EXT_BATT_CURRENT=559940911")&&
            oem.contains("HAL_EXT_BATT_POWER=557843808")&&
            oem.contains("HAL_SINGLE_CHARGE_ENERGY=559940899")&&
            oem.contains("alCarFloat(b,HAL_EXT_BATT_VOLT)")&&
            oem.contains("alCarFloat(b,HAL_EXT_BATT_CURRENT)")&&
            oem.contains("matchingCurrent")&&
            oem.contains("halVoltageValid")&&
            !oem.contains("transact(15")&&
            service.contains("data.voltageSource"),
            "Full VHAL IDs must be read-only and voltage can be used only "+
            "after independent HAL/vehicle current agreement");
        yes(store.contains("SQLiteOpenHelper")&&
            store.contains("t1_charge_sessions.db")&&
            store.contains("grid_charges_auto.csv")&&
            store.contains("meter_kwh")&&
            store.contains("battery_kwh")&&
            store.contains("T1LAUNCHER · IMPORTACIÓN MANUAL")&&
            store.contains("BATERIA_ESTIMADA_NO_MEDIDOR")&&
            store.contains("getFD().sync()"),
            "Independent SQLite, explicit grid meter vs battery and durable CSV");
        yes(maths.contains("currentCharging(cable,amps)")&&
            maths.contains("dtMs>12000")&&
            maths.contains("amps< -0.5")&&
            maths.contains("plugged(cable)"),
            "Only connected cable HV-in can accrue a charging session");
        yes(screen.contains("IMPORTAR 1.ª CARGA")&&
            screen.contains("EXPORTAR CSV")&&
            screen.contains("Intent.ACTION_CREATE_DOCUMENT")&&
            screen.contains("db.importLauncher("),
            "First charge import and SAF CSV export must be visible");
        yes(boot.contains("T1ChargeRecorderService.startIfEnabled(context)")&&
            cockpit.contains("CARGAS AUTOMÁTICAS · HISTORIAL")&&
            manifest.contains(".T1ChargeHistoryActivity")&&
            manifest.contains(".T1ChargeRecorderService"),
            "Boot and cockpit independent module entrypoint");
        yes(!oem.contains("com.t1launcher")&&
            !service.contains("com.t1launcher")&&
            !store.contains("com.t1launcher")&&
            !screen.contains("getDatabasePath(\"t1_viajes.db\")"),
            "No dependency on T1 Launcher process or private database");
        System.out.println("PASS independent Jetour charge monitor, first import and CSV");
    }
}
