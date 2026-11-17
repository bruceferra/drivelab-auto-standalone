package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Only existing Waze/Maps tasks on Display 1 may be raised.
 * No steering commands, OEM task operations or geometry mutations. */
public final class T1ClusterForegroundRecoveryContractTest {
    private static String src(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    private static void check(boolean yes,String msg){
        if(!yes)throw new AssertionError(msg);
    }
    public static void main(String[] args)throws Exception{
        String foreground=src("T1ClusterForegroundRecovery.java");
        String toolbar=src("T1GlobalToolbar.java");
        String manifest=Files.readString(Path.of("receiver/src/main/AndroidManifest.xml"));
        check(manifest.contains("android.permission.REORDER_TASKS"),
            "Task foreground permission is required");
        check(toolbar.contains("button(actions,\"TOMAR CONTROL\"")&&
            toolbar.contains("T1ClusterForegroundRecovery.request(owner,message->"),
            "Single manual recovery control must be accessible next to CLUSTER");
        check(foreground.contains("T1ClusterLayoutPolicy.savedDualSplit(app)")&&
            foreground.contains("d.getDisplayId()!=1")&&
            foreground.contains("WAZE.equals(a)&&MAPS.equals(b)")&&
            foreground.contains("T1AdbTaskParser.unique(list,1,pkg)")&&
            foreground.contains("T1LocalAdbClient.shell(c,\"dumpsys activity activities\")"),
            "Identify both real navigation tasks on selected display before actions");
        check(foreground.contains("am.moveTaskToFront(task.id,")&&
            foreground.contains("ActivityManager.MOVE_TASK_NO_USER_ACTION")&&
            foreground.contains("if(!stopped(app))")&&
            !foreground.contains("moveTaskToBack")&&
            !foreground.contains("am task resize")&&
            !foreground.contains("am stack remove")&&
            !foreground.contains("am task focus")&&
            !foreground.contains("dispatchGesture")&&
            !foreground.contains("setLaunchBounds")&&
            !foreground.contains("T1WheelTarget.select("),
            "Never move/resize/close OEM tasks or remap/inject steering input");
        check(!foreground.contains("scheduleBoot")&&
            !foreground.contains("onReceiverBoot("),
            "Foreground recovery must remain explicit and manual until car validation");
        System.out.println("PASS: Display 1 foreground-only recovery, wheel untouched");
    }
}
