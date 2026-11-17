package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cluster settings must not hide the permanent upper controls. The
 * manual focus test must preserve its result even when route status changes.
 */
public final class T1ClusterFixedToolbarAndFocusResultContractTest {
    private static String source(String n)throws Exception {
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    private static void ck(boolean condition,String why) {
        if(!condition)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String layout=source("T1ClusterLayoutSettingsActivity.java");
        String cluster=source("T1ClusterWazeSettingsActivity.java");
        String recovery=source("T1ClusterForegroundRecovery.java");
        String immersive=source("T1ImmersiveActivity.java");
        ck(layout.contains("T1ClusterLayoutSettingsActivity extends T1ImmersiveActivity")&&
            cluster.contains("T1ClusterWazeSettingsActivity extends T1ImmersiveActivity"),
            "Cluster settings must inherit the fixed top toolbar");
        ck(immersive.contains("globalToolbar=new T1GlobalToolbar(this)")&&
            immersive.contains("shell.addView(content,"),
            "The fixed top toolbar must stay outside scrolling settings content");
        ck(recovery.contains("static final String RESULT=")&&
            recovery.contains(".putString(RESULT,answer)")&&
            recovery.contains(".putLong(RESULT_AT,System.currentTimeMillis()).commit()"),
            "Manual recovery diagnostics must persist independently of route status");
        ck(layout.contains("T1ClusterManualReboot.last(this)")&&
            layout.contains("ÚLTIMA RECARGA R")&&
            cluster.contains("T1ClusterManualReboot.last(this)")&&
            cluster.contains("ÚLTIMA RECARGA R"),
            "Read last R outcome from both regular cluster menus");
        ck(recovery.contains("am.moveTaskToFront(task.id,")&&
            !recovery.contains("am stack remove")&&
            !recovery.contains("am task resize"),
            "Diagnostics fix must not replace the existing manual task focus experiment");
        System.out.println("PASS cluster toolbar fixed and manual focus error persists");
    }
}
