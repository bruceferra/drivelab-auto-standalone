package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Owner-established order: force-stop from T1Launcher Settings cleared OEM
 * Spotify overlay ONLY when Waze and Maps were still displayed on an ON
 * cluster. This contract verifies code order; it cannot verify Jetour ROM.
 */
public final class T1ClusterBootVisibleThenForceStopContractTest {
    private static String source(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String router=source("T1ClusterWazeRouter.java");
        String fresh=source("T1ClusterFreshStart.java");
        String bar=source("T1GlobalToolbar.java");
        String reboot=source("T1ClusterManualReboot.java");
        String service=source("ReceiverService.java");
        int boot=router.indexOf("static String onReceiverBoot(Context c)");
        int saved=router.indexOf("if(T1ClusterLayoutPolicy.savedDualSplit(c))",boot);
        int end=router.indexOf("if(T1ClusterLayoutPolicy.pendingUnverified(c))",saved);
        check(boot>=0&&saved>boot&&end>saved,"Boot split branch available");
        String part=router.substring(saved,end);
        int initial=part.indexOf("String initial=T1ClusterLayoutPolicy.reopenSavedPanelsStopped(c)");
        int stop=part.indexOf("T1ClusterFreshStart.prepare(c)");
        int second=part.indexOf("String opened=T1ClusterLayoutPolicy.reopenSavedPanelsStopped(c)");
        check(initial>=0&&stop>initial&&second>stop,
            "Cold boot MUST first open both apps, then force-stop with cluster ON, then reopen");
        int off=part.indexOf("putBoolean(ENABLED,false)");
        int taskClose=part.indexOf("T1ClusterAdbResize.closeClusterApps(c,");
        int on=part.indexOf("putBoolean(ENABLED,true)");
        check(part.contains("Thread.sleep(1600L)")&&
            part.contains("if(!autoBoot(c))")&&
            part.contains("if(!clean.changed)")&&
            part.contains("return initial;")&&
            off>stop&&taskClose>off&&on>taskClose&&second>on&&
            part.contains("Thread.sleep(T1ClusterManualReboot.OFF_VISIBLE_MS)")&&
            reboot.contains("static final long OFF_VISIBLE_MS=5000L")&&
            part.contains("closed.await(16L,java.util.concurrent.TimeUnit.SECONDS)")&&
            part.contains("BOOT_RELOAD_RESULT")&&
            !part.contains("speedKmh()")&&
            !part.contains("gear()"),
            "Boot must reproduce R force-stop, real OFF task close, pause, ON and saved split");
        check(fresh.contains("T1LocalAdbClient.shell(c,\"am force-stop \"+pkg)")&&
            fresh.contains("T1LocalAdbClient.shell(c,\"pidof \"+pkg)")&&
            reboot.contains("T1ClusterFreshStart.prepare(owner)")&&
            reboot.contains("reopenSavedPanelsStopped(owner)")&&
            reboot.contains("T1ClusterAdbResize.closeClusterApps(owner,")&&
            reboot.contains("performOn(owner,update,confirmed)"),
            "Scoped ADB stop and REBOOT still follow active-cluster method");
        int manualStart=bar.indexOf("private void toggleCluster()");
        int manualEnd=bar.indexOf("private void refreshMirrorAuto()",manualStart);
        check(manualStart>=0&&manualEnd>manualStart&&
            !bar.substring(manualStart,manualEnd).contains("T1ClusterFreshStart.prepare("),
            "CLUSTER ON normal never kills active navigation");
        check(service.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
            service.contains("T1LastRoute.restore(this)"),
            "Boot restores last route only after cluster sequence");
        System.out.println("PASS cold boot: maps visible -> force-stop -> OFF close -> wait -> ON -> restore");
    }
}
