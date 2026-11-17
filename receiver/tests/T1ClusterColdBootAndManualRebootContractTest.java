package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Prevent accidental Waze/Maps process termination on ordinary CLUSTER ON,
 * Sender route or app screen navigation. Cold boot + manual REBOOT only.
 * No gear/speed restrictions for navigation process management.
 */
public final class T1ClusterColdBootAndManualRebootContractTest {
    static String read(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    static void yes(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String clean=read("T1ClusterFreshStart.java");
        String adb=read("T1LocalAdbClient.java");
        String router=read("T1ClusterWazeRouter.java");
        String toolbar=read("T1GlobalToolbar.java");
        String settings=read("T1ClusterWazeSettingsActivity.java");
        String reboot=read("T1ClusterManualReboot.java");
        String receiver=read("ReceiverService.java");
        yes(adb.contains("am force-stop com.waze")&&
            adb.contains("am force-stop com.google.android.apps.maps")&&
            adb.contains("pidof com.waze")&&
            adb.contains("pidof com.google.android.apps.maps")&&
            !adb.contains("am force-stop com.autolink")&&
            !adb.contains("am force-stop ar.com.rango5gps.drivelabauto.receiver"),
            "Only 2 selected navigation packages may be force-stopped");
        yes(clean.contains("T1LocalAdbClient.shell(c,\"am force-stop \"+pkg)")&&
            clean.contains("T1LocalAdbClient.shell(c,\"pidof \"+pkg)")&&
            clean.contains("new String[]{WAZE,MAPS}")&&
            clean.contains("final boolean ready,changed")&&
            !clean.contains("speedKmh()")&&
            !clean.contains("gear()")&&
            !clean.contains("MirrorAutomationOem")&&
            !clean.contains("am stack remove")&&
            !clean.contains("am task resize"),
            "Navigation cleanup verifies process termination, no P/velocity gate");
        yes(router.contains("T1ClusterFreshStart.prepare(c)")&&
            !router.contains("stoppedForSavedSplit(c)")&&
            receiver.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
            receiver.contains("if(intent!=null&&intent.getBooleanExtra(FROM_BOOT,false))")&&
            receiver.contains("Navegación delegada al arranque limpio del clúster"),
            "Only true boot may clean before auto-opening map panes");
        int manual=toolbar.indexOf("private void toggleCluster()");
        int next=toolbar.indexOf("private void refreshMirrorAuto()",manual);
        yes(manual>0&&next>manual&&
            !toolbar.substring(manual,next).contains("T1ClusterFreshStart.prepare(")&&
            !toolbar.substring(manual,next).contains("am force-stop")&&
            !toolbar.substring(manual,next).contains("speedKmh()"),
            "Normal CLUSTER ON/OFF does not force-stop or require gear/speed");
        yes(settings.contains("if(showTechnical){")&&
            settings.contains("reboot.setText(\"REBOOT\")")&&
            settings.contains("reboot.setTextSize(11f)")&&
            settings.contains("T1ClusterManualReboot.run(this)")&&
            settings.contains("setNegativeButton(\"CANCELAR\",null)"),
            "REBOOT must stay small, hidden in technical submenu and confirmed");
        yes(reboot.contains("T1ClusterFreshStart.prepare(owner)")&&
            reboot.contains("reopenSavedPanelsStopped(owner)")&&
            reboot.contains("T1ClusterAdbResize.closeClusterApps(owner,")&&
            reboot.contains("putBoolean(T1ClusterWazeRouter.ENABLED,false)")&&
            reboot.contains("putBoolean(T1ClusterWazeRouter.ENABLED,true)")&&
            reboot.contains("T1LastRoute.restore(owner)")&&
            !reboot.contains("new Intent(owner,ReceiverActivity.class)")&&
            !reboot.contains("Intent.FLAG_ACTIVITY_CLEAR_TASK")&&
            router.contains("T1ClusterAdbResize.closeClusterApps(c,")&&
            router.contains("putBoolean(ENABLED,false)")&&
            router.contains("putBoolean(ENABLED,true)")&&
            reboot.contains("ReceiverService.ACTION_START")&&
            !reboot.contains("speedKmh()")&&
            !reboot.contains("oem.gear()")&&
            !reboot.contains("am force-stop ar.com.rango5gps.drivelabauto.receiver"),
            "Both manual and cold boot refresh maps; manual R must not blank DriveLab UI");
        System.out.println("PASS: boot and hidden REBOOT exclusively refresh maps, no speed/P gate");
    }
}
