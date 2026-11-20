package ar.com.rango5gps.drivelabauto.boot;
import java.util.List;
public final class BridgeClusterRecoveryPolicyTest {
    private static void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);}
    private static final String OEM="Stack id=7 bounds=[0,0][1920,720] displayId=1 userId=0\n"+
        " taskId=31: com.jetour.cluster/.Home userId=0\n";
    private static final String MAP="Stack id=99 bounds=[0,0][1920,720] displayId=1 userId=0\n"+
        " taskId=701: com.waze/.MainActivity bounds=[50,50][900,690] userId=0\n";
    public static void main(String[] args){
        List<BridgeClusterRecoveryPolicy.Task> good=
            BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(OEM+MAP);
        ok(good.size()==1&&good.get(0).taskId==701&&
            good.get(0).stackId==99,"Only Waze on display1");
        ok("am stack remove 701".equals(
            BridgeClusterRecoveryPolicy.removeCommand(good.get(0))),
            "Only validated map task id, no OEM stack");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(OEM).isEmpty(),
            "No OEM-only action");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
            MAP.replace("displayId=1","displayId=0")).isEmpty(),
            "Never remove center display app");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
            MAP.replace("com.waze/.MainActivity","com.jetour.cluster/.Home")).isEmpty(),
            "Never delete OEM");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
            MAP+" taskId=702: com.jetour.cluster/.App userId=0\n").isEmpty(),
            "Never touch Waze+OEM mixed stack");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
            MAP.replace("Stack id=99","Stack id=1")).isEmpty(),
            "Never remove primary system/root stack");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
            MAP+MAP).isEmpty(),"Reject duplicate root id/task id");
        ok(BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(
            "not an Android stack report").isEmpty(),"Ambiguous report fails closed");
        System.out.println("PASS manual display1 Waze-only recovery; never OEM/Display0");
    }
}
