package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** The owner's working Waze-left/Maps-right clúster must not be lost merely
 * because an ADB close returned "CIERRE NO CONFIRMADO" or a saved
 * split's measurement became pending. Does not simulate T1 hardware.
 */
public final class T1ClusterPendingRecoverySourceTest {
    static String src(String filename)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            filename));
    }
    static void yes(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        String bar=src("T1GlobalToolbar.java");
        String layout=src("T1ClusterLayoutPolicy.java");
        String adb=src("T1ClusterAdbResize.java");
        String setup=src("T1ClusterWazeSettingsActivity.java");
        String router=src("T1ClusterWazeRouter.java");
        int pending=bar.indexOf("boolean pendingLayout=T1ClusterLayoutPolicy.pendingUnverified(owner);");
        int reopen=bar.indexOf("reopenSavedPanelsStopped(owner)",pending);
        int fallback=bar.indexOf("if(pendingLayout)",reopen);
        yes(pending>=0&&reopen>pending&&fallback>reopen,
            "User's explicit CLÚSTER ON must recover split before pending layout block");
        String method=bar.substring(pending,fallback);
        yes(method.contains("MirrorAutomationOem")&&
            method.contains("gear==1")&&
            method.contains("speed<=0.5")&&
            method.contains("owner.runOnUiThread")&&
            method.contains("T1LastRoute.restore(owner)")&&
            method.contains("!both")&&
            method.contains("AUTO_BOOT,false"),
            "Saved split recovery is parked-only, fails safely and restores route");
        yes(!method.contains("T1ClusterAdbResize.closeClusterApps")&&
            !method.contains("T1ClusterAdbResize.apply")&&
            !method.contains("T1WheelAdbFallback")&&
            !method.contains("am task focus")&&
            !method.contains("am stack remove"),
            "Manual recovery must not perform unsafe ADB task focus, close or resize");
        int start=layout.indexOf("static String reopenSavedPanelsStopped(Context c){");
        int end=layout.indexOf("static String boot(Context c)",start);
        yes(start>=0&&end>start,"Recovered split launch helper present");
        String method2=layout.substring(start,end);
        yes(method2.contains("MODE_SPLIT.equals(mode(c))")&&
            method2.contains("panelAOn(c)")&&
            method2.contains("panelBOn(c)")&&
            method2.contains("start(c,display,a,geometry(c,display,inverted)")&&
            method2.contains("start(c,display,b,geometry(c,display,!inverted)")&&
            method2.contains("number(c,SPLIT_PERCENT,50,30,70)")&&
            !method2.contains("putBoolean(ACTIVE,true)")&&
            !method2.contains("launchSelectedApp")&&
            !method2.contains("am task"),
            "Keep both saved freeform panes and don't claim unverified approval");
        int close=adb.indexOf("private static void deliverClose(");
        yes(close>=0&&adb.substring(close).contains(
            "if(success)edit.putLong(VERIFIED_AT,0L)"),
            "Failed ADB close does not invalidate the previous verified layout");
        yes(adb.contains("if(enabled(c)==on)return;"),
            "Re-tapping already active ADB must not invalidate saved split");
        yes(!setup.contains("putBoolean(T1ClusterLayoutPolicy.ACTIVE,false)"),
            "Disabling navigation in settings must preserve approved panel geometry");
        yes(router.contains("if(T1ClusterLayoutPolicy.pendingUnverified(c))"),
            "Unverified geometry still must not auto-launch on cold boot");
        System.out.println("PASS: manual stopped pending split recovery, prior approval preserved on failed close, no ADB geometry edits");
    }
}
