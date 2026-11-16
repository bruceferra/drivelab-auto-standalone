package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1AdbProbePersistentSourceContractTest {
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
        check(ui.contains("COPIAR RESULTADO ADB")&&
              ui.contains("adbPersistentStatus.setText(label)")&&
              ui.contains("getSystemService(CLIPBOARD_SERVICE)")&&
              ui.contains("T1ClusterAdbResize.probe(this,(message,connected)->")&&
              !ui.contains("dialog.setMessage(message+")&&
              !ui.contains("dialog.show();")&&
              ui.contains("Estado general Waze (NO es prueba ADB)"),
              "ADB status must be durable, readable and copyable, never modal");
        check(probe.contains("AtomicBoolean finished")&&
              probe.contains("MAIN.postDelayed(")&&
              probe.contains("PROBE_LIMIT_MS=22000L")&&
              probe.contains("prueba interrumpida o proceso reiniciado")&&
              probe.contains("ADB SIN RESPUESTA: prueba excedió 22 segundos")&&
              probe.contains(".commit();"),
              "ADB probe needs bounded result even on interrupted session");
        System.out.println("PASS no-popup persistent ADB diagnostics");
    }
}
