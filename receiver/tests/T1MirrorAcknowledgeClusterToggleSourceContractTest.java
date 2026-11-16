package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Crash-safe status and physical-cluster task ownership assertions.
 * Static tests are not proof of rendering or OEM mirror angle.
 */
public final class T1MirrorAcknowledgeClusterToggleSourceContractTest {
    static String read(String file)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));
    }
    static void check(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
    }
    public static void main(String[] args)throws Exception{
        String mirror=read("MirrorAutomationService.java");
        String toolbar=read("T1GlobalToolbar.java");
        String router=read("T1ClusterWazeRouter.java");
        String resize=read("T1ClusterAdbResize.java");
        String adb=read("T1LocalAdbClient.java");
        int acknowledge=mirror.indexOf("if(ACTION_CONFIRM_NORMAL.equals(action))");
        int stop=mirror.indexOf("stop=true;",acknowledge);
        int join=mirror.indexOf("prior.join(4500L)",stop);
        int pCheck=mirror.indexOf("int gear=check.gear();",join);
        int normal=mirror.indexOf("NORMAL · referencia visual confirmada por conductor",pCheck);
        check(acknowledge>=0&&stop>acknowledge&&join>stop&&
            pCheck>join&&normal>pCheck,
            "Confirmation must stop prior monitor before verifying OEM P0 and clearing orange");
        check(mirror.contains("referenceConfirming=true;")&&
            mirror.contains("if(!referenceConfirming){")&&
            mirror.contains("stopSelf(startId)")&&
            mirror.contains(".putBoolean(SAFETY_HOLD,false)")&&
            mirror.contains(".putInt(PENDING_LEFT,0).putInt(PENDING_RIGHT,0)")&&
            mirror.contains(".remove(LAST_ERROR).commit()")&&
            mirror.contains("if(gear!=1||!Double.isFinite(speed)||"),
            "Require physical P0 user confirmation, durable reset and no worker race");
        check(toolbar.contains("mirrorRecoverButton=button(actions,")&&
            toolbar.contains("ESPEJOS · SIN PENDIENTES")&&
            toolbar.contains("needsRecovery=safetyHold||unknown||inFlight||")&&
            toolbar.contains("pendingLeft>0||pendingRight>0")&&
            toolbar.contains("T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI")&&
            !toolbar.contains("button(actions,\"RECUPERAR ESPEJOS\",T1JetourTheme.AMBER_BUTTON"),
            "Dedicated RECUPERAR button must stop glowing orange after confirmed zero-pulse NORMAL");
        int cluster=toolbar.indexOf("clusterButton=button(actions,");
        int optimized=toolbar.indexOf("optimizer=button(actions,");
        check(cluster>=0&&optimized>cluster&&
            toolbar.contains("CLÚSTER ON")&&
            toolbar.contains("CLÚSTER OFF")&&
            toolbar.contains("CLÚSTER OFF · SIN CIERRE")&&
            toolbar.contains("T1ClusterWazeRouter.launchSelectedApp(owner,true,false)")&&
            toolbar.contains("T1ClusterAdbResize.closeClusterApps(owner,")&&
            toolbar.contains(".putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)")&&
            toolbar.contains(".putBoolean(T1ClusterLayoutPolicy.ACTIVE,false)"),
            "Cluster must be first 2nd-row button and stop forwarding before closing apps");
        check(router.contains("CLOSE_PENDING=")&&
            resize.contains("static void closeClusterApps(Context c,Done done)")&&
            resize.contains("new String[]{WAZE,MAPS}")&&
            resize.contains("T1AdbTaskParser.unique(now,id,pkg)")&&
            resize.contains("\"am stack remove \"+task.id")&&
            resize.contains("if(!parked(app))")&&
            resize.contains("deliverClose(app,result,success,done)")&&
            resize.contains(".putLong(VERIFIED_AT,0L)")&&
            adb.contains("am stack remove [1-9]"),
            "Only Waze/Maps tasks on selected display may be closed with authorized P0 ADB");
        check(!toolbar.contains("Intent.CATEGORY_HOME")&&
            !resize.contains("am force-stop")&&
            !resize.contains("input keyevent")&&
            !mirror.contains("oem.write(2003"),
            "Do not replace OEM HOME or write mirror actuators during confirmation");
        System.out.println("PASS T1 orange mirror confirmation and reversible cluster task toggle");
    }
}