package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

public final class T1CenterOwnFreeformSourceContractTest {
    private static String source(String file)throws Exception{
        return Files.readString(Path.of("receiver/src/main/java/ar/com/"+
            "rango5gps/drivelabauto/receiver",file));
    }
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String own=source("T1CenterOwnFreeformProbe.java");
        String activity=source("T1CenterFreeformTestActivity.java");
        String ui=source("T1CenterMosaicActivity.java");
        String bridge=source("T1LocalAdbClient.java");
        String manifest=Files.readString(Path.of(
            "receiver/src/main/AndroidManifest.xml"));
        String exact="am start --display 0 --windowingMode 5 -n ";
        check(own.contains(exact)&&bridge.contains(exact),
            "Only fixed freeform command targeting Display 0 authorized");
        check(own.contains("T1CenterWindowModePolicy.isFreeform(")&&
            own.contains("isInMultiWindowMode=true")&&
            own.contains("Display=0"),
            "Confirm source mode and physical display, not command output");
        check(own.contains("T1CenterFreeformTestActivity.KEY"),
            "Record own task measurements independently");
        check(activity.contains("getTaskId()")&&
            activity.contains("isInMultiWindowMode()")&&
            activity.contains("finishAndRemoveTask()")&&
            activity.contains("Display.DEFAULT_DISPLAY"),
            "Auto-close own task after measuring actual window state");
        check(manifest.contains("android:name=\".T1CenterFreeformTestActivity\"")&&
            manifest.contains("android:resizeableActivity=\"true\"")&&
            manifest.contains("android:excludeFromRecents=\"true\"")&&
            manifest.contains(".windowmodeprobe"),
            "Own isolated activity cannot replace Receiver or OEM launcher");
        check(ui.contains("PROBAR FREEFORM AISLADO · DISPLAY 0")&&
            ui.contains("T1CenterOwnFreeformProbe.run(this,result->"),
            "Expose a separate, opt-in freeform test");
        check(!own.contains("am task resizeable ")&&
            !own.contains("am task resize ")&&
            !own.contains("settings put")&&
            !own.contains("am stack remove "),
            "The isolated test must never mutate foreign task/window settings");
        System.out.println("PASS isolated self-closing Display 0 Freeform experiment");
    }
}
