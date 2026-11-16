package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static safety checks. Does NOT replace in-car OEM testing. */
public final class T1WheelPanelAndSpeedSourceTest {
    private static String source(String n)throws Exception{
        return Files.readString(Path.of(
          "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String p=source("T1WheelTarget.java");
        String w=source("T1WazeWheelService.java");
        String ui=source("T1GlobalToolbar.java");
        String settings=source("T1WheelSettingsActivity.java");
        String speech=source("T1VoiceRouteActivity.java");
        String meter=source("T1PremiumInstrumentView.java");
        String trip=source("T1TripTelemetry.java");
        String cockpit=source("T1PremiumCockpitActivity.java");
        require(p.contains("mapsAllowed(Context c)")&&
            p.contains("T1ClusterLayoutPolicy.active(c)")&&
            p.contains("com.google.android.apps.maps")&&
            p.contains("MODE_SPLIT.equals("),
            "Panel B can only target confirmed Maps split");
        require(ui.contains("wheelPanelButton=button(actions,")&&
            ui.indexOf("wheelPanelButton=button(actions,")<
                ui.indexOf("optimizer=button(actions,")&&
            ui.contains("refreshWheelPanel()")&&
            settings.contains("CONTROLAR PANEL A")&&
            settings.contains("CONTROLAR PANEL B")&&
            speech.contains("T1WheelTarget.select(this,true)"),
            "A/B selector must be prominent, persistent and optionally voice-driven");
        require(w.contains("getWindowsOnAllDisplays()")&&
            w.contains("private AccessibilityNodeInfo mapsRoot()")&&
            w.contains("handleMapsKey(root,forward,backward,confirm,cancel)")&&
            w.contains("MAPS.equals(String.valueOf(root.getPackageName()))")&&
            !w.contains("performGlobalAction(GLOBAL_ACTION_BACK)")&&
            !w.contains("injectInputEvent("),
            "No global back or blind input across the two cluster apps");
        require(meter.indexOf("\"VELOCIDAD · OEM\"")<
            meter.indexOf("\"COMBUSTIBLE UTILIZABLE\"")&&
            meter.contains("speed==null?\"SIN LECTURA\"")&&
            meter.contains("updateSpeed(Double value)")&&
            trip.contains("double speedOnly()")&&
            trip.contains("floatValue(b,2148)")&&
            trip.contains("intValue(b,1089)")&&
            cockpit.contains("gauge.updateSpeed(Double.isFinite(velocity)?velocity:null)"),
            "Digital speed from real OEM, first compact card, no fake zero");
        System.out.println("PASS panel A/B selection + guarded Maps + OEM speed");
    }
}
