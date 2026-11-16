package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1ClusterNoSilentFullscreenFallbackTest {
    static String src(String n)throws Exception {
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/"+n));
    }
    static void ck(boolean good,String why){if(!good)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        String policy=src("T1ClusterLayoutPolicy.java");
        String router=src("T1ClusterWazeRouter.java");
        String tb=src("T1GlobalToolbar.java");
        int a=tb.indexOf("private void toggleCluster()");
        int b=tb.indexOf("private void refreshMirrorAuto()",a);
        String toggle=tb.substring(a,b);
        ck(policy.contains("static boolean pendingUnverified(Context c)")&&
           policy.contains("!MODE_FULL.equals(mode(c))&&!active(c)")&&
           policy.contains("CONFIRMAR DISEÑO VISIBLE")&&
           policy.contains("No abriré Waze a pantalla completa"),
           "Do not automatically replace selected split with fullscreen");
        ck(toggle.contains("pendingLayout=T1ClusterLayoutPolicy.pendingUnverified(owner)")&&
           toggle.contains("result=T1ClusterLayoutPolicy.pendingMessage(owner)")&&
           toggle.contains("if(!launched&&!pendingLayout)")&&
           toggle.contains("T1ClusterLayoutPolicy.boot(owner)"),
           "CLUSTER ON must keep pending geometry available to confirm");
        int rt=router.indexOf("static boolean routeDestination(Context c,Intent waze)");
        int man=router.indexOf("static boolean openManualTest(Context c)",rt);
        ck(rt>0&&man>rt&&router.substring(rt,man).contains(
            "if(enabled(c)&&T1ClusterLayoutPolicy.pendingUnverified(c))")&&
           router.substring(rt,man).contains("return false;"),
           "Incoming routes must not overwrite split by Waze fullscreen");
        int boot=router.indexOf("static void onReceiverBoot(Context c)");
        ck(boot>0&&router.substring(boot).contains(
            "if(T1ClusterLayoutPolicy.pendingUnverified(c))")&&
            router.substring(boot).contains("return;")&&
            router.substring(boot).contains("T1ClusterLayoutPolicy.boot(c)"),
            "Delayed autoboot must not undo pending 50/50");
        ck(policy.contains("static String test(Context c)")&&
           policy.contains("static boolean enableAfterTest(Context c)"),
           "Parked ADB validation and confirmation stay enabled");
        System.out.println("PASS no silent FULL fallback over pending split");
    }
}
