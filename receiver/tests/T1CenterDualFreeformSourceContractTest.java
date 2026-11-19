package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

public final class T1CenterDualFreeformSourceContractTest {
    static void check(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    static String source(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    public static void main(String[] args)throws Exception {
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String probe=source(root+"T1CenterDualFreeformProbe.java");
        String ui=source(root+"T1CenterMosaicActivity.java");
        String bridge=source(root+"T1LocalAdbClient.java");
        String left=source(root+"T1CenterDualLeftActivity.java");
        String right=source(root+"T1CenterDualRightActivity.java");
        String common=source(root+"T1CenterDualPanelActivity.java");
        String manifest=source("receiver/src/main/AndroidManifest.xml");
        check(probe.contains("T1CenterDualLeftActivity")&&
            probe.contains("T1CenterDualRightActivity")&&
            bridge.contains("T1CenterDualLeftActivity")&&
            bridge.contains("T1CenterDualRightActivity"),
            "ADB must launch only two known app-owned temporary tasks");
        check(manifest.contains("${applicationId}.dualprobe.left")&&
            manifest.contains("${applicationId}.dualprobe.right")&&
            manifest.contains("android:excludeFromRecents=\"true\"")&&
            manifest.contains("android:resizeableActivity=\"true\""),
            "Separate affinities, temporary activities, no history");
        check(left.contains("extends T1CenterDualPanelActivity")&&
            right.contains("extends T1CenterDualPanelActivity")&&
            common.contains("finishAndRemoveTask()")&&
            common.contains("30000L")&&
            common.contains("isInMultiWindowMode()"),
            "Both app-owned panels measure own geometry and self-close");
        check(probe.contains("T1AdbTaskParser.exactCenter(")&&
            probe.contains("checkedTask(")&&
            probe.contains("own(")&&
            probe.contains("isFreeform(")&&
            probe.contains("twoColumns(")&&
            probe.contains("bothVisible")&&
            probe.contains("leftId!=rightId"),
            "Separate exact task IDs; require freeform, disjoint bounds and both visible");
        check(probe.contains("am task resize " )&&
            !probe.contains("am task resizeable ")&&
            !probe.contains("settings put")&&
            !probe.contains("am stack remove ")&&
            !probe.contains("com.autolink.")&&
            !probe.contains("am force-stop "),
            "No global settings or OEM/external task mutators");
        check(ui.contains("PROBAR DOS VENTANAS PROPIAS · DISPLAY 0")&&
            ui.contains("T1CenterDualFreeformProbe.run(this,result->")&&
            ui.contains("last_dual_freeform_probe"),
            "Explicit consent and saved user-visible result required");
        System.out.println("PASS safe 2-pane DriveLab-only Freeform test");
    }
}
