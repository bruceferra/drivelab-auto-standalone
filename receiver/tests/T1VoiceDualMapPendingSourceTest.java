package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** OEM microphone must match Sender BOTH on approved Waze+Maps panels.
 * With one panel it follows the visible map. Physical display still needs T1. */
public final class T1VoiceDualMapPendingSourceTest {
    private static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            name));
    }
    private static void yes(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String voice=src("T1VoiceRouteActivity.java");
        String wheel=src("T1WazeWheelService.java");
        String targets=src("T1NavigationTargets.java");
        int start=voice.indexOf("private void navigate(String place){");
        int end=voice.indexOf("/** This is the ONLY",start);
        yes(start>=0&&end>start,"Navigation dispatcher exists");
        String navigation=voice.substring(start,end);
        int dual=navigation.indexOf("T1NavigationTargets.dualMapPanelsConfigured(this)");
        int dualSend=navigation.indexOf("BridgeRouteTarget.BOTH");
        int visible=navigation.indexOf("visibleNavigationOnCluster(this)");
        yes(dual>=0&&dualSend>dual&&visible>dualSend&&
            navigation.contains("T1NavigationTargets.send(")&&
            navigation.contains("T1LastRoute.remember(this,")&&
            navigation.contains("if(both.attempted)")&&
            navigation.contains("BridgeRouteTarget.MAPS")&&
            navigation.contains("routeDestination(this,waze)")&&
            navigation.contains("No se pudo identificar el mapa abierto")&&
            navigation.contains("if(pkg==null)"),
            "Voice must send BOTH when Waze+Maps panels configured, "+
            "and use visible map only for a single-panel layout");
        yes(wheel.contains("getWindowsOnAllDisplays()")&&
            wheel.contains("all.get(selected.getDisplayId())")&&
            wheel.contains("win.getDisplayId()!=selected.getDisplayId()")&&
            wheel.contains("if(maps&&!waze)return MAPS;")&&
            wheel.contains("if(waze&&!maps)return WAZE;")&&
            wheel.contains("if(focused!=null)return focused;")&&
            wheel.contains("T1WheelTarget.packageName(context)")&&
            wheel.contains("return null; // no arbitrary Waze fallback"),
            "Only visible windows of selected cluster drive voice map targeting");
        yes(targets.contains("BridgeRouteTarget.MAPS.equals(target)")&&
            targets.contains("T1ClusterWazeRouter.routeGoogleMaps(c,")&&
            targets.contains("BridgeRouteTarget.BOTH.equals(target)"),
            "Existing explicit Sender Maps/Both targets remain intact");
        System.out.println("PASS microphone sends BOTH via Sender dispatcher on dual Waze+Maps; single map follows visible panel");
    }
}
