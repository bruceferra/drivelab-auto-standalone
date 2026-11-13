package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only source contract: reversing mirrors must be discoverable and opt-in. */
public final class MirrorControlsVisibilitySourceContractTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver");
        String toolbar=Files.readString(root.resolve("T1GlobalToolbar.java"));
        String hub=Files.readString(root.resolve("ReceiverActivity.java"));
        String service=Files.readString(root.resolve("MirrorAutomationService.java"));
        check(toolbar.contains("REVERSA AUTO ✓") &&
              toolbar.contains("REVERSA MANUAL") &&
              toolbar.contains("CONFIG. ESPEJOS R"),
              "reverse tilt AUTO/MANUAL and configuration must be visible in fixed toolbar");
        check(toolbar.contains("MirrorAutomationService.ACTION_AUTO") &&
              toolbar.contains("MirrorAutomationService.ACTION_MANUAL") &&
              toolbar.contains("MirrorAutomationService.SERVICE_ACTIVE") &&
              toolbar.contains("MirrorAutomationService.LAST_POLL"),
              "reverse toggle must dispatch real service actions and display verified heartbeat");
        check(toolbar.contains("MirrorAutomationService.LAST_ERROR") &&
              toolbar.contains("MirrorAutomationService.LAST_GEAR") &&
              toolbar.contains("MirrorAutomationService.LAST_SPEED") &&
              toolbar.contains("MirrorAutomationService.LAST_TRANSITION"),
              "reverse configuration must expose service diagnostic evidence");
        check(toolbar.contains("MirrorAutomationService.LEFT,left.getValue()") &&
              toolbar.contains("MirrorAutomationService.RIGHT,right.getValue()") &&
              toolbar.contains("MirrorParkingPolicy.MAX_PULSES_PER_SIDE"),
              "left and right pulse settings must be independently configurable");
        int visible=hub.indexOf("CONTROLES OEM AVANZADOS · ESPEJOS R");
        int settings=hub.indexOf("button(body,connectionExpanded?");
        check(visible>=0 && settings>visible &&
              !hub.contains("button(settings,\"CONTROLES OEM AVANZADOS\""),
              "OEM advanced controls must be reachable without opening connection settings");
        check(service.contains("if(p.contains(AUTO)&&!p.getBoolean(AUTO,false))return;"),
              "manual mode must not auto-rearm after Receiver process restart");
        check(!toolbar.contains("oem.write(2068")&&!toolbar.contains("oem.write(2069"),
              "toolbar must never directly issue mirror actuator writes");
        System.out.println("PASS: visible reverse mirror controls, user mode persistence, heartbeat, diagnostics");
    }
}
