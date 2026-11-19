package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression after physical 0.3.68 failure: automatic boot MUST NOT
 * force-stop maps or cycle DriveLab OFF/ON. The optional explicit manual
 * repair remains separate; neither touches the disabled T1Launcher.
 */
public final class T1ClusterBootVisibleThenForceStopContractTest {
    private static String src(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    private static void check(boolean yes,String msg){
        if(!yes)throw new AssertionError(msg);
    }
    public static void main(String[] args)throws Exception{
        String router=src("T1ClusterWazeRouter.java");
        String service=src("ReceiverService.java");
        String toolbar=src("T1GlobalToolbar.java");
        String manual=src("T1ClusterManualReboot.java");
        int boot=router.indexOf("static String onReceiverBoot(Context c)");
        int saved=router.indexOf("if(T1ClusterLayoutPolicy.savedDualSplit(c))",boot);
        int end=router.indexOf("if(T1ClusterLayoutPolicy.pendingUnverified(c))",saved);
        check(boot>=0&&saved>boot&&end>saved,"Saved dual-pane boot available");
        String branch=router.substring(saved,end);
        check(branch.contains("T1ClusterLayoutPolicy.reopenSavedPanelsStopped(c)")&&
              branch.contains("ARRANQUE SIMPLE 0.3.69")&&
              branch.contains("BOOT_RELOAD_RESULT")&&
              !branch.contains("T1ClusterFreshStart.prepare(c)")&&
              !branch.contains("T1ClusterAdbResize.closeClusterApps(c,")&&
              !branch.contains("putBoolean(ENABLED,false)")&&
              !branch.contains("Thread.sleep(1600L)")&&
              !branch.contains("T1ClusterManualReboot.OFF_VISIBLE_MS"),
              "Cold boot must open maps ONCE without force-stop, stack close or OFF/ON");
        check(!service.contains("T1ClusterBootFocus.afterClusterBoot(")&&
              service.contains("T1WazeWheelService.requestClusterReady(this)")&&
              service.contains("T1LastRoute.restore(this)"),
              "Keep wheel and route, do not reorder boot tasks above Spotify");
        check(toolbar.contains("MAPAS · REPARACIÓN DESACTIVADA")&&
              manual.contains("REPARAR MAPAS DESACTIVADO POR SEGURIDAD")&&
              !manual.contains("T1ClusterFreshStart.prepare(owner)")&&
              !manual.contains("T1LocalAdbClient.shell("),
              "Both manual and automatic destructive map repair must be disabled");
        System.out.println("PASS no automatic destructive map restart or task focus");
    }
}
