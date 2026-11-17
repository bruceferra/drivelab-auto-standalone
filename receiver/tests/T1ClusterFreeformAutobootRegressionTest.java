package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source regression: the user's physically working dual FREEFORM layout
 * is never discarded merely because vendor ADB hides task bounds.
 * Hardware drawing/steering is tested on the Jetour, not inferred here. */
public final class T1ClusterFreeformAutobootRegressionTest {
    private static String source(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            name));
    }
    private static void ck(boolean ok,String msg){
        if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] args)throws Exception{
        String policy=source("T1ClusterLayoutPolicy.java");
        String router=source("T1ClusterWazeRouter.java");
        String service=source("ReceiverService.java");
        String bar=source("T1GlobalToolbar.java");
        int saved=router.indexOf("if(T1ClusterLayoutPolicy.savedDualSplit(c)){");
        int pending=router.indexOf("if(T1ClusterLayoutPolicy.pendingUnverified(c))",saved);
        int reopen=router.indexOf("T1ClusterLayoutPolicy.reopenSavedPanelsStopped(c)",saved);
        ck(saved>=0&&reopen>saved&&pending>reopen,
            "Saved split must recover before ADB-pending cold-boot gate");
        ck(policy.contains("static boolean savedDualSplit(Context c)")&&
            policy.contains("MODE_SPLIT.equals(mode(c))&&panelAOn(c)&&panelBOn(c)"),
            "Only an explicitly selected two-panel freeform layout is restored");
        String boot=router.substring(saved,pending);
        ck(boot.contains("T1ClusterFreshStart.prepare(c)")&&
            !boot.contains("stoppedForSavedSplit(c)")&&
            boot.contains("Settings.canDrawOverlays(c)")&&
            !boot.contains("launchApp(")&&!boot.contains("T1ClusterAdbResize")&&
            !boot.contains("am task")&&!boot.contains("gear()"),
            "Real boot tries clean stop then reopens saved FREEFORM without a speed gate");
        int manual=bar.indexOf("private void toggleCluster()");
        int restore=bar.indexOf("if(T1ClusterLayoutPolicy.savedDualSplit(owner))",manual);
        int fallback=bar.indexOf("if(pendingLayout)",restore);
        ck(manual>=0&&restore>manual&&fallback>restore&&
            bar.substring(restore,fallback).contains(
                ".reopenSavedPanelsStopped(owner)"),
            "Manual ON must not choose an ADB-dependent path for saved split");
        int begin=service.indexOf("private void scheduleNativeClusterWaze()");
        int end=service.indexOf("private void scheduleBootApp()",begin);
        String start=service.substring(begin,end);
        ck(start.contains("new Thread(()->{")&&
            start.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
            start.contains("T1ClusterLayoutPolicy.savedDualSplit(this)")&&
            start.contains("T1WazeWheelService.requestClusterReady(this)")&&
            !start.contains("am task")&&!start.contains("setLaunchWindowingMode(3)"),
            "Boot runs OEM check off UI, restores route/wheel without native split");
        System.out.println("PASS saved FREEFORM 50/50 survives boot gates without ADB-native split");
    }
}
