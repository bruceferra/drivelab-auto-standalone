package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1AdbProbeDialogSourceContractTest {
    private static String file(String name)throws Exception {
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void check(boolean yes,String msg) {
        if(!yes)throw new AssertionError(msg);
    }
    public static void main(String[] args)throws Exception {
        String ui=file("T1ClusterLayoutSettingsActivity.java");
        String probe=file("T1ClusterAdbResize.java");
        check(ui.contains("adbProbeRunning=true;")&&
            ui.contains("dialog.show();")&&
            ui.contains("dialog.setMessage(message+")&&
            ui.contains("dialog.setTitle(connected?")&&
            ui.contains("ADB LOCAL NO CONECTADO")&&
            ui.contains("ADB LOCAL CONECTADO")&&
            ui.contains("Estado general Waze (NO es prueba ADB)")&&
            ui.contains("PROBAR CONEXIÓN ADB LOCAL")&&
            ui.contains("T1ClusterAdbResize.probe(this,(message,connected)->"),
            "ADB async probe must show explicit modal status and distinguish Waze");
        check(probe.contains("PROBANDO AHORA: consulta ADB iniciada")&&
            probe.contains(".putLong(VERIFIED_AT,0L).apply();")&&
            probe.contains("deliver(c,result,ok,done);"),
            "ADB probe must persist started and completed states");
        System.out.println("PASS ADB probe modal result and stale status regression");
    }
}
