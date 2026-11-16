package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** OFF without ADB is a routing change, not proof Waze vanished. */
public final class T1ClusterOffAutobootSourceContractTest {
    static String src(String file)throws Exception {
        return Files.readString(Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));
    }
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        String toolbar=src("T1GlobalToolbar.java");
        String adb=src("T1ClusterAdbResize.java");
        String layout=src("T1ClusterLayoutPolicy.java");
        int begin=toolbar.indexOf("private void toggleCluster()");
        int end=toolbar.indexOf("private void refreshMirrorAuto()",begin);
        check(begin>=0&&end>begin,"Cluster toggle exists");
        String toggle=toolbar.substring(begin,end);
        check(toggle.contains("if(T1ClusterWazeRouter.enabled(owner)){")&&
            // An OFF/ON decision must not be coupled to CLOSE_PENDING.
            // But the delayed last-route replay MUST check still ON.
            !toggle.contains("if(T1ClusterWazeRouter.enabled(owner)||")&&
            toggle.contains("if(!T1ClusterWazeRouter.enabled(owner)||")&&
            toggle.contains(".putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,false)")&&
            toggle.contains(".putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)"),
            "Pending OFF cannot prevent turning ON and restoring autoboot");
        check(toolbar.contains("CLÚSTER OFF · SIN CIERRE")&&
            toggle.contains("Waze puede seguir")&&
            toggle.contains("if(!T1ClusterAdbResize.enabled(owner))")&&
            toggle.contains("if(T1ClusterWazeRouter.enabled(owner))return;"),
            "OFF cannot claim to close without ADB or undo a newer ON");
        int enable=adb.indexOf("static void enable(Context c,boolean on)");
        int probe=adb.indexOf("static void probe(Context c,Done done)",enable);
        check(enable>=0&&probe>enable&&
            !adb.substring(enable,probe).contains("AUTO_BOOT,false"),
            "ADB enable/disable must not disable the proven Waze autoboot");
        int set=layout.indexOf("static void setMode(Context c,String desired)");
        int selected=layout.indexOf("static String secondPackage(Context c)",set);
        check(set>=0&&selected>set&&
            !layout.substring(set,selected).contains("AUTO_BOOT,false"),
            "Experimental geometry edits must not disable proven Waze autoboot");
        int closeStart=adb.indexOf("static void closeClusterApps(Context c,Done done)");
        int closeEnd=adb.indexOf("/** Closing tasks INVALIDATES",closeStart);
        check(closeStart>0&&closeEnd>closeStart,"Cluster OFF method boundaries");
        String off=adb.substring(closeStart,closeEnd);
        check(off.contains("if(T1ClusterWazeRouter.enabled(app))")&&
            off.contains("Cierre cancelado: CLÚSTER volvió a ON")&&
            !off.contains("parked(app)")&&
            !off.contains("gear()")&&
            !off.contains("speedKmh()")&&
            off.contains("T1AdbTaskParser.unique(now,id,pkg)")&&
            off.contains("am stack remove "),
            "Cluster OFF must restore OEM in D without P or speed restriction");
        check(adb.contains("if(!parked(app))")&&
            adb.contains("BLOQUEADO: solo redimensionar con P")&&
            adb.contains("BLOQUEADO: restaurar pantalla solo detenido en P"),
            "Resize and split layout MUST remain P-only");
        System.out.println("PASS CLUSTER OFF reversible with no ADB; Waze autoboot protected");
    }
}
