package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verified user environment: T1Launcher cluster remains OFF; only
 * DriveLab-owned display tasks, Spotify overlay and non-destructive
 * boot are in scope. Source contract, not an OEM display-layer proof.
 */
public final class T1ClusterOwnDisplayReadOnlyRecoveryContractTest{
    static String src(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String router=src("T1ClusterWazeRouter.java");
        String service=src("ReceiverService.java");
        String adb=src("T1LocalAdbClient.java");
        String snap=src("T1ClusterTaskSnapshot.java");
        String settings=src("T1ClusterWazeSettingsActivity.java");
        String bar=src("T1GlobalToolbar.java");
        int method=router.indexOf("static String onReceiverBoot(Context c)");
        int start=router.indexOf("if(T1ClusterLayoutPolicy.savedDualSplit(c))",method);
        int end=router.indexOf("if(T1ClusterLayoutPolicy.pendingUnverified(c))",start);
        check(start>method&&end>start,"Saved DriveLab two-panel boot path exists");
        String auto=router.substring(start,end);
        check(auto.contains("reopenSavedPanelsStopped(c)")&&
            auto.contains("ARRANQUE SIMPLE 0.3.69")&&
            !auto.contains("T1ClusterFreshStart.prepare(c)")&&
            !auto.contains("T1ClusterAdbResize.closeClusterApps(c,")&&
            !auto.contains("putBoolean(ENABLED,false)")&&
            !auto.contains("Thread.sleep(1600L)"),
            "Automatic boot MUST NOT repeat the failing destructive 0.3.68 sequence");
        check(!service.contains("T1ClusterBootFocus.afterClusterBoot(this")&&
            service.contains("T1WazeWheelService.requestClusterReady(this)")&&
            service.contains("T1LastRoute.restore(this)"),
            "Preserve route and steering while disabling automatic task reorder");
        check(adb.contains("!command.equals(\"dumpsys window windows\")")&&
            snap.contains("T1LocalAdbClient.shell(c,\"am stack list\")")&&
            snap.contains("T1LocalAdbClient.shell(c,\"dumpsys activity activities\")")&&
            snap.contains("T1LocalAdbClient.shell(c,\"dumpsys window windows\")")&&
            snap.contains("T1AdbTaskParser.scan(raw)")&&
            snap.contains("T1Launcher: no se consulta ni se modifica.")&&
            !snap.contains("T1LocalAdbClient.shell(c,\"am force-stop")&&
            !snap.contains("T1LocalAdbClient.shell(c,\"am stack remove")&&
            !snap.contains("T1LocalAdbClient.shell(c,\"am task resize"),
            "Task snapshot is strictly read-only: Waze/Maps displays and sanitized window hints");
        check(settings.contains("DIAGNÓSTICO DISPLAY 1 / MAPAS · SOLO LEER")&&
            settings.contains("this::showClusterTaskSnapshot")&&
            settings.contains("dialog.show()")&&
            settings.contains("COPIAR INFORME")&&
            settings.contains("copy.setOnClickListener")&&
            bar.contains("REPARAR MAPAS"),
            "UI makes the persistent read-only diagnostic accessible and copyable");
        System.out.println("PASS DriveLab-only simple boot, no automatic destructive actions, ADB snapshot");
    }
}
