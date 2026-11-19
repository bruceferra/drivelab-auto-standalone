package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression checks for owner-confirmed multi-display Boot and safe insets. */
public final class T1BootDisplayClusterSourceContractTest {
    static String read(String s)throws Exception{
        return Files.readString(Path.of(s));
    }
    static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        String boot="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String receiver="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String ui=read(boot+"BootActivity.java");
        String config=read(boot+"BootConfig.java");
        String auto=read(boot+"BootAutoService.java");
        String service=read(receiver+"T1BootMosaicBridgeService.java");
        String bridge=read(receiver+"T1BootMosaicBridgeReceiver.java");
        String cluster=read(receiver+"T1BootClusterLayout.java");
        String immersive=read(receiver+"T1ImmersiveActivity.java");
        String bootBuild=read("boot/build.gradle");
        String receiverBuild=read("receiver/build.gradle");

        check(bootBuild.contains("versionCode 4")&&
            bootBuild.contains("versionName '0.1.3'")&&
            receiverBuild.contains("versionCode 198")&&
            receiverBuild.contains("versionName '0.3.97'"),
            "Both independently installed APKs need new signed versions");
        check(ui.contains("DISPLAY 0 · PANTALLA CENTRAL")&&
            ui.contains("DISPLAY 1 · CLÚSTER / MAPAS")&&
            ui.contains("clusterEnabled")&&
            ui.contains("clusterCount")&&
            ui.contains("clusterPercent")&&
            ui.contains("captionSafeDp")&&
            ui.contains("CONFIRMAR DISEÑO DEL CLÚSTER TRAS VERLO"),
            "Boot must configure both displays, caption and physical gate");
        check(config.contains("request.putExtra(\"cluster_enabled\",cluster)")&&
            config.contains("request.putExtra(\"cluster_confirmed\"")&&
            config.contains("request.putExtra(\"from_boot\",fromBoot)")&&
            config.contains("request.putExtra(\"caption_safe_dp\"")&&
            config.contains("receiverCode<198L")&&
            auto.contains("BootConfig.send(this,true)"),
            "Only compatible signed Receiver can run guarded ignition request");
        check(service.contains("T1CenterSavedFreeformMosaic.apply(")&&
            service.contains("T1BootClusterLayout.apply(")&&
            service.contains("new Thread(")&&
            service.contains("drivelab_freeform_ui_v1")&&
            service.contains("stopSelf(startId)")&&
            service.contains("getDisplay(Display.DEFAULT_DISPLAY)"),
            "Display 0 first, then optional background Display 1 through existing engine");
        check(immersive.contains("freeformCaptionDp()")&&
            immersive.contains("isInMultiWindowMode()")&&
            immersive.contains("onMultiWindowModeChanged(")&&
            immersive.contains("px(clearanceDp()+freeformCaptionDp())")&&
            immersive.contains("getSharedPreferences(\"drivelab_freeform_ui_v1\""),
            "Protect own buttons from system Freeform maximize caption");
        check(bridge.contains("ACTION_TAKEOVER")&&
            bridge.contains("T1ClusterWazeRouter.AUTO_BOOT,false")&&
            bridge.contains("\"launch_app_on_boot\",false")&&
            !bridge.contains("am force-stop")&&
            !bridge.contains("settings put "),
            "Boot owns one ignition sequence without modifying OEM state");
        check(cluster.contains("getDisplayId()!=1")&&
            cluster.contains("oem.speedKmh()")&&
            cluster.contains("kmh<=0.5")&&
            cluster.contains("fromBoot&&!visuallyConfirmed")&&
            cluster.contains("T1ClusterLayoutPolicy.configurePanels(")&&
            cluster.contains("T1ClusterLayoutPolicy.reopenSavedPanelsStopped(")&&
            cluster.contains("T1ClusterWazeRouter.launchApp(")&&
            cluster.contains("T1ClusterEdgeFade.width(c)")&&
            !cluster.contains("T1LocalAdbClient")&&
            !cluster.contains("force-stop")&&
            !cluster.contains("pm clear")&&
            !cluster.contains("settings put "),
            "Display 1 strictly guarded, no OEM layer changes or task killings");
        System.out.println("PASS: unified Boot Display0/1, 0-km/h, focus/layer cautions, caption and signature isolation");
    }
}
