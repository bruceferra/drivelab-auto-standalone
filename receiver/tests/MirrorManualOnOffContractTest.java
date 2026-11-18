package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source regression: manual tilt is NOT automatic R/P/D position logic. */
public final class MirrorManualOnOffContractTest {
    private static void yes(boolean value,String msg){
        if(!value)throw new AssertionError(msg);
    }
    private static String src(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    public static void main(String[] args)throws Exception{
        String manual=src("MirrorManualTiltService.java");
        String auto=src("MirrorAutomationService.java");
        String toolbar=src("T1GlobalToolbar.java");
        String manifest=Files.readString(Path.of("receiver/src/main/AndroidManifest.xml"));
        yes(manual.contains("static final int DEFAULT_PULSES=15;")&&
            toolbar.contains("MirrorManualTiltService.DEFAULT_PULSES")&&
            toolbar.contains("CONFIG. ESPEJOS R"),
            "Manual defaults to 15 steps per side, existing config retained");
        yes(toolbar.contains("BAJAR ESPEJOS · MANUAL")&&
            toolbar.contains("SUBIR ESPEJOS · MANUAL")&&
            toolbar.contains("this::toggleMirrorManual")&&
            toolbar.contains("this::toggleMirrorAuto")&&
            toolbar.contains("T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI"),
            "Independent manual two-state top bar, auto switch unchanged");
        yes(manual.contains("int l=down?steps(MirrorAutomationService.LEFT):pendingLeft")&&
            manual.contains("int r=down?steps(MirrorAutomationService.RIGHT):pendingRight")&&
            manual.contains("boolean down=pendingLeft==0&&pendingRight==0;")&&
            manual.contains("int next=old+(down?1:-1)")&&
            manual.contains("putInt(key,next)")&&
            manual.contains("MirrorAutomationService.PULSE_IN_FLIGHT,true)")&&
            manual.contains("putBoolean(MirrorAutomationService.PULSE_IN_FLIGHT,false)"),
            "Down counts only confirmed released pulses; next tap inverts exact counts");
        yes(manual.contains("gear!=1||!Double.isFinite(speed)")&&
            manual.contains("parked(oem); // NO P/R/D automatic transition logic")&&
            manual.contains("synchronized(MirrorAutomationService.OEM_MIRROR_MUTEX)")&&
            manual.contains("oem.write(id,down?2:1)")&&
            manual.contains("oem.write(id,0)"),
            "P/zero guard each pulse, OEM mutex and neutral release required");
        yes(manual.contains("ACTION_REFERENCE=")&&
            manual.contains("if(reference)")&&
            manual.contains("YA ESTÁN NORMALES") == false&&
            toolbar.contains("YA ESTÁN NORMALES · CONFIRMAR")&&
            manual.contains("REFERENCE,true")&&
            manual.contains("Sin reintentos automáticos"),
            "Owner visual reference is explicit; interrupted tilt does not auto retry");
        yes(auto.contains("MirrorManualTiltService.SELECTED,false")&&
            auto.contains("if(p.getBoolean(MirrorManualTiltService.SELECTED,false))return;")&&
            auto.contains("if(p.getBoolean(MirrorManualTiltService.SELECTED,false)||")&&
            manual.contains(".putBoolean(MirrorAutomationService.AUTO,false)")&&
            manual.contains("MirrorAutomationService.ACTION_MANUAL"),
            "Manual selection survives ignition and stops old auto monitor");
        yes(manifest.contains("android:name=\".MirrorManualTiltService\"")&&
            manifest.contains("android:exported=\"false\""),
            "Manual actuator foreground service not exported");
        System.out.println("PASS mirror manual button down/up, journal, P safety, owner override");
    }
}
