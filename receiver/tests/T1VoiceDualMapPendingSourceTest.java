package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Voice destination should reach BOTH Waze and Google Maps in saved
 * split panes after CLÚSTER OFF/ON recovery, even if ADB did not verify
 * split geometry. Source contract; in-car Maps navigation still unverified.
 */
public final class T1VoiceDualMapPendingSourceTest {
    private static String src(String filename)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            filename));
    }
    private static void yes(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String voice=src("T1VoiceRouteActivity.java");
        String targets=src("T1NavigationTargets.java");
        String layout=src("T1ClusterLayoutPolicy.java");
        String router=src("T1ClusterWazeRouter.java");
        int start=voice.indexOf("private void navigate(String place){");
        int end=voice.indexOf("private void ",start+16);
        String navigate=voice.substring(start,end>start?end:voice.length());
        yes(navigate.contains(
                "boolean both=T1NavigationTargets.dualMapPanelsConfigured(this);")&&
            navigate.contains("BridgeRouteTarget.BOTH")&&
            navigate.contains("T1NavigationTargets.send(")&&
            navigate.contains("T1LastRoute.remember(this,")&&
            !navigate.contains("T1ClusterLayoutPolicy.active(this)"),
            "OEM mic dual routing must not be gated on layout approval");
        yes(voice.contains("T1NavigationTargets.dualMapPanelsConfigured(this)?")&&
            voice.contains("Waze + Google Maps"),
            "Voice announcement reflects actual configured dual routing");
        int check=targets.indexOf("static boolean dualMapPanelsConfigured(Context c){");
        int finish=targets.indexOf("private static Intent intent(",check);
        yes(check>=0&&finish>check,"Dual map layout helper exists");
        String body=targets.substring(check,finish);
        yes(body.contains("T1ClusterWazeRouter.enabled(c)")&&
            body.contains("T1ClusterWazeRouter.selected(c)")&&
            body.contains("T1ClusterLayoutPolicy.MODE_SPLIT.equals(")&&
            body.contains("T1ClusterLayoutPolicy.panelAOn(c)")&&
            body.contains("T1ClusterLayoutPolicy.panelBOn(c)")&&
            body.contains("com.waze")&&
            body.contains("com.google.android.apps.maps")&&
            !body.contains("T1ClusterLayoutPolicy.active(c)")&&
            !body.contains("setMode")&&!body.contains("putBoolean("),
            "Only the real two selected map panels qualify, without changing geometry");
        yes(targets.contains("if(!dualMapPanelsConfigured(c))")&&
            targets.contains("T1ClusterLayoutPolicy.routeWazeIfActive(c,")&&
            targets.contains("T1ClusterWazeRouter.routeGoogleMaps(c,")&&
            targets.contains("T1GoogleMapsReplacement.arm(c)"),
            "Both target sends one destination to each map in freeform cluster");
        yes(layout.contains("static String reopenSavedPanelsStopped(Context c)")&&
            layout.contains("static boolean pendingUnverified(Context c)"),
            "Keep saved 60/40 recovery and separate verification state");
        yes(router.contains("if(T1ClusterLayoutPolicy.pendingUnverified(c))"),
            "Keep cold auto-boot safety guard");
        System.out.println("PASS: OEM microphone dual Waze+Maps routing while layout pending; no geometry changes");
    }
}
