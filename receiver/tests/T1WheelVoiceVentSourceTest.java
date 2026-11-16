package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1WheelVoiceVentSourceTest{
    static String s(String n)throws Exception{return Files.readString(Path.of(
        "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/"+n));}
    static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        String w=s("T1WazeWheelService.java"),u=s("T1WheelSettingsActivity.java"),
            voice=s("T1VoiceRouteActivity.java"),win=s("VehicleWindowGroup.java"),
            tb=s("T1GlobalToolbar.java"),qc=s("ReceiverQuickControls.java");
        ok(w.contains("windowDecoration(String label)")&&
            !w.contains("performGlobalAction(GLOBAL_ACTION_BACK)"),
            "No freeform chrome, no wrong-screen BACK");
        ok(u.contains("MODO RUEDITA ACTUAL")&&
            u.contains("ACTIVAR VOLANTE OEM")&&
            u.contains("DESACTIVAR VOLANTE OEM"),
            "Selection zoom and bridge status explicit");
        ok(voice.contains("T1NavigationTargets.send(")&&
            voice.contains("BridgeRouteTarget.BOTH")&&
            voice.contains("secondPackage(this)"),
            "Voice dispatches both apps when verified split is selected");
        ok(win.contains("awaitTarget(b,id,operation,STEP_TIMEOUT_MS)")&&
            !win.contains("Primero usá CIERRE TOTAL"),
            "Quarter state3 verifies each window from mixed positions");
        ok(tb.contains("MODO VENTILACIÓN ON")&&
            tb.contains("MODO VENTILACIÓN OFF")&&
            qc.contains("MODO VENTILACIÓN ON")&&
            qc.contains("MODO VENTILACIÓN OFF"),
            "Explicit ventilation mode labels");
        ok(tb.contains("AIRE INTERIOR · SOLICITADO")&&
            tb.contains("AIRE AUTO · PAUSA OEM"),
            "Green preference does not fake OEM confirmation");
        System.out.println("PASS explicit wheel controls, dual voice, ventilation and air status");
    }
}
