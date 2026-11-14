package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** T1 has no confirmed mirror-angle readback. Uncertain cannot disappear
 * because of an Android service restart, a ten-pulse write, or a random AUTO tap.
 * A deliberate driver visual acknowledgement is needed, checked in OEM P/0.
 */
public final class T1MirrorUncertainRecoverySourceContractTest {
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static String source(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    public static void main(String[] ignored)throws Exception{
        String svc=source("MirrorAutomationService.java");
        String ui=source("T1GlobalToolbar.java");
        String quick=source("ReceiverQuickControls.java");
        String policy=source("MirrorParkingPolicy.java");
        check(svc.contains("ACTION_CONFIRM_NORMAL")&&
            svc.contains("if(ACTION_CONFIRM_NORMAL.equals(action))"),
            "Must have a safe owner-driven recovery from uncertain state");
        int ack=svc.indexOf("if(ACTION_CONFIRM_NORMAL.equals(action))");
        int stop=svc.indexOf("if(ACTION_STOP.equals(action)",ack);
        check(ack>=0&&stop>ack, "ACK branch isolated before AUTO commands");
        String confirmation=svc.substring(ack,stop);
        check(confirmation.contains("check.gear()")&&
            confirmation.contains("check.speedKmh()")&&
            confirmation.contains("gear!=1||!Double.isFinite(speed)")&&
            confirmation.contains("speed>0.5"),
            "Only OEM-validated P and stopped can accept visual reference");
        check(!confirmation.contains("runPulses(")&&
            !confirmation.contains(".write(")&&
            !confirmation.contains(".putBoolean(AUTO,true)"),
            "Reference acknowledgement never actuates, guesses angle or auto-arms");
        check(svc.contains("if(physical.startsWith(\"INCIERTA\")||physical.startsWith(\"R · inclinados\"))")&&
            !svc.contains("INCIERTA · espejos posiblemente bajos tras reinicio"),
            "Keep prior tilted/uncertain pose through process restart");
        check(svc.contains("INCIERTA · comandos de subida enviados")&&
            !svc.contains("AUTO REACTIVADO · próximo R nuevo"),
            "Blind 10-UP does not automatically re-arm AUTO or claim a normal angle");
        check(ui.contains("ESPEJOS · REVISAR POSICIÓN")&&
            ui.contains("showMirrorRecovery()")&&
            ui.contains("MirrorAutomationService.ACTION_CONFIRM_NORMAL"),
            "Visible main header gives a real safe path out of uncertainty");
        check(ui.contains("mandos ORIGINALES")&&
            ui.contains("El modo AUTO seguirá apagado")&&
            ui.contains("REVERSA AUTO ✓"),
            "Driver confirmation explains OEM adjustment, leaves AUTO off, keeps green live-only");
        check(quick.contains("position.startsWith(\"INCIERTA\")")&&
            quick.contains("confirmá la referencia"),
            "Advanced mode must never claim AUTO rearmed while uncertain");
        check(svc.contains("if(!down && !(gear==4 || (gear==1 && speed<=0.5)))")&&
            !svc.contains("gear==4 && speed>5.0"),
            "D over 5 kmh is the initial trigger, not a per-pulse guard: slower D must finish restore");
        check(policy.contains("RETURN_P_DWELL_MS=30000")&&
            policy.contains("RETURN_THRESHOLD_KMH=5.0"),
            "Preserve user's P over 30 sec / D over 5 kmh only policy");
        System.out.println("PASS uncertain mirror recovery and no blind physical rearm");
    }
}
