package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pure JVM source contract: a physical 30-second MODE capture must stay visible. */
public final class T1ModeCaptureVisibleResultContractTest {
    private static String read(String filename)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            filename));
    }
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError(what);
    }
    public static void main(String[] args)throws Exception{
        String ui=read("T1WheelSettingsActivity.java");
        String probe=read("T1ModeKeyProbe.java");
        check(ui.contains("private TextView report,modeState,bridgeState,eventState,modeReport")&&
              ui.contains("refreshModeCapture();")&&
              ui.contains("modeDialogReport.setText(modeReportText())"),
              "30-second capture results must be updated independently of ticker report");
        check(ui.contains("this::startModeCapture")&&
              ui.contains("this::showModeResult")&&
              ui.contains("modeDialog.show();")&&
              ui.contains("setNegativeButton(\"CERRAR\"")&&
              ui.contains("setNeutralButton(\"COPIAR CÓDIGOS\""),
              "The MODE button must show a non-auto-closing dialog with copyable results");
        check(ui.contains("T1OemMicrophoneService.ENABLED")&&
              ui.contains("MODE · BINDER NO HABILITADO")&&
              ui.contains("gear==1&&Double.isFinite(speed)"),
              "Never fake a capture without OEM listener or P/zero speed");
        check(probe.contains("SystemClock.elapsedRealtime()+30000L")&&
              probe.contains("key=\"+code+\" action=\"+action+\" extra="),
              "Real key/action/extra capture retained for 30 seconds");
        System.out.println("PASS MODE 30-second result dialog and OEM capture prerequisites");
    }
}
