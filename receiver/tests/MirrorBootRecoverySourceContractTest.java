package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class MirrorBootRecoverySourceContractTest {
    static String read(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String svc=read("MirrorAutomationService.java");
        String toolbar=read("T1GlobalToolbar.java");
        ok(svc.contains("ACTION_BOOT_RECOVER=")&&
            svc.contains("MirrorBootRecoveryPolicy.recoverable(left,right,")&&
            svc.contains("MirrorBootRecoveryPolicy.parkedForRestore(")&&
            svc.contains("stable<10&&!stop")&&
            svc.contains("SystemClock.sleep(250L)")&&
            svc.contains("manual==3||manual==4")&&
            svc.contains("if(bootParkedOnly &&")&&
            svc.contains("PULSE_IN_FLIGHT,true).commit()")&&
            svc.contains("putInt(key,pending)")&&
            svc.contains("putBoolean(PULSE_IN_FLIGHT,false).commit()"),
            "Boot only compensates logged, released pulses in stable parked P");
        ok(svc.contains("if(unresolved(p))")&&
            svc.contains("if(p.getBoolean(SAFETY_HOLD,false)&&")&&
            svc.contains("startsWith(\"INCIERTA\")")&&
            svc.contains("if(manual==4){")&&
            svc.contains(".putBoolean(AUTO,true)")&&
            svc.contains("retorno al arrancar incompleto"),
            "Crash and uncertain cases keep warning; only complete restore rearms");
        ok(toolbar.contains("boolean unknown=(inFlight||(!manualSelected&&unresolved))&&!healthy;")&&
            toolbar.contains("ESPEJOS · RECUPERANDO EN P")&&
            toolbar.contains("ESPEJOS · REVISAR POSICIÓN")&&
            toolbar.contains("SUBIR ESPEJOS · MANUAL")&&
            toolbar.contains("T1JetourTheme.AMBER_BUTTON"),
            "No lost-orange state on reboot");
        System.out.println("PASS mirror ignition recovery source and orange status");
    }
}
