package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1ClusterOnRestoreSavedSplitTest {
    static void ck(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] a)throws Exception{
        String toolbar=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1GlobalToolbar.java"));
        int start=toolbar.indexOf("private void toggleCluster()");
        int end=toolbar.indexOf("private void refreshMirrorAuto()",start);
        String toggle=toolbar.substring(start,end);
        ck(!toggle.contains(".putBoolean(T1ClusterLayoutPolicy.ACTIVE,false)"),
            "Cluster OFF must retain user's previously confirmed split");
        ck(toggle.contains("T1ClusterLayoutPolicy.active(owner)")&&
            toggle.contains("T1ClusterLayoutPolicy.boot(owner)"),
            "Cluster ON must restore saved split");
        ck(toggle.contains("result.startsWith(\"APP SOLICITADA EN VENTANA:\")"),
            "Successful freeform launch must not revert cluster ON state");
        ck(toggle.contains("T1ClusterAdbResize.closeClusterApps(owner,"),
            "OFF must still close both map tasks");
        System.out.println("PASS cluster OFF/ON preserves approved split");
    }
}
