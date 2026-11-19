package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Safety + launch contract for first two actual apps in center Freeform. */
public final class T1CenterExternalFreeformSourceContractTest {
    static void yes(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    static String source(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    public static void main(String[] args)throws Exception{
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String probe=source(root+"T1CenterExternalFreeformProbe.java"),
            bridge=source(root+"T1LocalAdbClient.java"),
            ui=source(root+"T1CenterMosaicActivity.java");
        yes(probe.contains("ar.com.promanager.nativeapp")&&
            probe.contains("ar.com.rango5gps.uberbeta")&&
            bridge.contains("ar\\\\.com\\\\.promanager\\\\.nativeapp/")&&
            bridge.contains("ar\\\\.com\\\\.rango5gps\\\\.uberbeta/"),
            "Only ProManager and Uber Beta launchers are shell-authorized");
        yes(probe.contains("getLaunchIntentForPackage(pkg)")&&
            probe.contains("FLAG_SYSTEM")&&
            probe.contains("FLAG_UPDATED_SYSTEM_APP"),
            "Only genuine installed non-system launcher components are allowed");
        yes(probe.contains("-f 0x18000000 -n ")&&
            probe.contains("--windowingMode 5")&&
            probe.contains("--display 0"),
            "Launch a new independently allocated task directly in freeform");
        yes(probe.contains("oldPro=ids(")&&
            probe.contains("oldUber=ids(")&&
            probe.contains("before.contains(t.id)")&&
            probe.contains("newTask(")&&
            probe.contains("checkedTask(")==false,
            "Must distinguish new ID from pre-existing fullscreen tasks");
        yes(probe.contains("exact(app,id,pkg)")&&
            probe.contains("isFreeform(")&&
            probe.contains("T1CenterFreeformGeometry.twoColumns(")&&
            probe.contains("concurrently")&&
            probe.contains("visible(app,left.id,LEFT_PKG)"),
            "Verify both app task mode, size and concurrent visibility");
        yes(probe.contains("shellApprovedAppTasks(")&&
            bridge.contains("T1AdbPackageTaskExcerpt"),
            "Stream exact package task excerpt rather than 96KB+ dump");
        yes(!probe.contains("am stack remove")&&
            !probe.contains("am force-stop")&&
            !probe.contains("am task resizeable")&&
            !probe.contains("settings put")&&
            !probe.contains("com.autolink.")&&
            !probe.contains("com.jetour."),
            "Never terminate older tasks or mutate OEM/global system");
        yes(ui.contains("PROBAR PROMANAGER + UBER BETA · FREEFORM DISPLAY 0")&&
            ui.contains("T1CenterExternalFreeformProbe.run(this,result->")&&
            ui.contains("last_external_freeform_probe"),
            "Explicit user confirmation and saved external-app result");
        System.out.println("PASS opt-in user-app-only Freeform 50/50 trial");
    }
}
