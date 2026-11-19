package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Prevent accidental Waze/Maps process termination on ordinary CLUSTER ON,
 * Sender route or app screen navigation. Cold boot + manual REBOOT only.
 * Normal navigation is unrestricted; explicit destructive manual R requires P/0 twice.
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
        String boot=read("BootReceiver.java");
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
        yes(boot.contains("boolean clusterBoot=T1ClusterWazeRouter.autoBoot(context)")&&
            boot.contains("if(receiverBoot||clusterBoot)")&&
            boot.contains("BOOT_COMPLETED recibido")&&
            toolbar.contains("MAPAS · REPARACIÓN DESACTIVADA")&&
            toolbar.contains("this::confirmClusterReload"),
            "Cluster auto-start must not depend on unrelated Receiver auto_boot, and repair is discoverable");
        int bootMethod=router.indexOf("static String onReceiverBoot(Context c)");
        int dual=router.indexOf("if(T1ClusterLayoutPolicy.savedDualSplit(c))",bootMethod);
        int fallback=router.indexOf("if(T1ClusterLayoutPolicy.pendingUnverified(c))",dual);
        String bootPart=router.substring(dual,fallback);
        yes(!bootPart.contains("T1ClusterFreshStart.prepare(c)")&&
            !bootPart.contains("T1ClusterAdbResize.closeClusterApps(c,")&&
            bootPart.contains("reopenSavedPanelsStopped(c)")&&
            receiver.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
            receiver.contains("if(intent!=null&&intent.getBooleanExtra(FROM_BOOT,false))"),
            "Cold boot must NOT repeatedly close Waze/Maps after physical 0.3.68 regression");
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
        yes(reboot.contains("REPARAR MAPAS DESACTIVADO POR SEGURIDAD")&&
            !reboot.contains("T1ClusterFreshStart.prepare(owner)")&&
            !reboot.contains("T1LocalAdbClient.shell(")&&
            !reboot.contains("T1ClusterAdbResize.closeClusterApps(owner,")&&
            !reboot.contains("performOff("),
            "Android 11 destructive manual REBOOT must remain fully disabled");
        System.out.println("PASS: automatic map routing preserved; dangerous Android 11 REBOOT disabled");
    }
}
