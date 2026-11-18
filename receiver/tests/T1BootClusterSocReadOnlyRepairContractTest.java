package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-only regression for boot trigger and non-writing SOC A/B getter. */
public final class T1BootClusterSocReadOnlyRepairContractTest {
    private static String source(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String boot=source("BootReceiver.java");
        String receiver=source("ReceiverService.java");
        String cluster=source("T1ClusterWazeRouter.java");
        String toolbar=source("T1GlobalToolbar.java");
        String mirror=source("MirrorAutomationOem.java");
        String optimized=source("T1OptimizedModeActivity.java");
        String pilot=source("T1SocRegenPilot.java");

        check(boot.contains("boolean clusterBoot=T1ClusterWazeRouter.autoBoot(context)")&&
              boot.contains("if(receiverBoot||clusterBoot)")&&
              boot.contains("BOOT_COMPLETED recibido")&&
              receiver.contains("T1ClusterWazeRouter.onReceiverBoot(this)")&&
              cluster.contains("T1ClusterFreshStart.prepare(c)")&&
              cluster.contains("T1ClusterManualReboot.OFF_VISIBLE_MS")&&
              cluster.contains("T1ClusterAdbResize.closeClusterApps(c,"),
              "Real boot must dispatch cluster close/off 10sec/on independent of receiver preference");
        check(toolbar.contains("REPARAR MAPAS")&&
              toolbar.contains("this::confirmClusterReload")&&
              toolbar.contains("T1ClusterManualReboot.run(owner,message->")&&
              !toolbar.contains("Button reload=button(header,\"R\""),
              "Owner must find one-tap manual full cluster repair in fixed top bar");
        check(mirror.contains("id!=1040 && id!=1041 && id!=1046")&&
              mirror.contains("if((id!=2068&&id!=2069)")&&
              optimized.contains("oem.readInt(1040)")&&
              optimized.contains("oem.readInt(1041)")&&
              optimized.contains("oem.readInt(1046)")&&
              !optimized.contains("gear!=1||!Double.isFinite(speed)")&&
              optimized.contains("SOC_OEM_CALIBRACION_B")&&
              !optimized.contains("oem.write("),
              "SOC A/B reads 1040/1041/1046 without a P/velocity or setter");
        check(pilot.contains("SOC_FORZADO_NO_AUTORIZADO_SIN_MAPEO")&&
              pilot.contains("observedRawForPercent(targets.forcedSoc)<0"),
              "60/70 target writes remain blocked until OEM mapping validated");
        System.out.println("PASS cluster true boot, manual repair visibility and SOC A/B read-only");
    }
}
