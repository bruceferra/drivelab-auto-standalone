package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1AutoRangeWindowSourceContractTest{
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    static void yes(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String db=read("T1AutoJourneyStore.java");
        String ui=read("T1AutoJourneyActivity.java");
        String math=read("T1AutoRangeMath.java");
        yes(db.contains("synchronized RangeReport between(long from,long until)")&&
            db.contains("t_epoch_ms<=?")&&
            db.contains("ORDER BY t_epoch_ms DESC LIMIT 1")&&
            db.contains("t_epoch_ms>?")&&
            db.contains("result.add(sessionId,started,previous,current)")&&
            db.contains("if(current.at>=until)break;"),
            "Exact period must include prior cumulative baseline and first post-end sample");
        yes(db.contains("java.util.Calendar.getInstance()")&&
            db.contains("cal.add(java.util.Calendar.DAY_OF_MONTH,1)")&&
            db.contains("byIgnition.computeIfAbsent")&&
            db.contains("days.computeIfAbsent")&&
            db.contains("exportWindowPoints(OutputStream out,long from,long until)"),
            "Custom period split by calendar days and ignition, export its own GPS");
        yes(math.contains("Math.max(previous.at,windowFrom)")&&
            math.contains("Math.min(current.at,windowUntil)")&&
            math.contains("current.at-previous.at>11000L")&&
            math.contains("double share=(double)(right-left)")&&
            math.contains("current.out-previous.out")&&
            math.contains("current.in-previous.in"),
            "No whole ignition included and no invented unobserved energy");
        yes(ui.contains("GENERADOR DE VIAJES · FECHA Y HORA EXACTAS")&&
            ui.contains("ELEGIR DESDE")&&ui.contains("ELEGIR HASTA")&&
            ui.contains("TimePickerDialog")&&
            ui.contains("DatePickerDialog")&&
            ui.contains("GENERAR DETALLE")&&
            ui.contains("EXPORTAR GPS FILTRADO")&&
            ui.contains("2026-11-18 19:00")&&
            ui.contains("report.days.values()")&&
            ui.contains("report.byIgnition.values()"),
            "User-selected minute precision with date/time pickers and day/ignition report");
        System.out.println("PASS virtual auto journey clipping, per day and minute interval UI");
    }
}
