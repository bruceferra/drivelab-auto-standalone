package ar.com.rango5gps.drivelabauto.common;
import java.util.Arrays;
public final class BridgeRouteTargetTest {
    private static void ck(boolean v,String reason){
        if(!v)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        for(String base:Arrays.asList("coord","search")){
            ck(BridgeRouteTarget.sendKind(base,"waze").equals(base),
                "Legacy sender must continue using old Waze wire kind");
            for(String mode:Arrays.asList("maps","both","t1")){
                String kind=BridgeRouteTarget.sendKind(base,mode);
                ck(base.equals(BridgeRouteTarget.baseKind(kind)),
                    "Original coordinate/search type must survive routing");
                ck(mode.equals(BridgeRouteTarget.target(kind)),
                    "Selected app must survive encrypted wire");
            }
        }
        ck(BridgeRouteTarget.WAZE.equals(
            BridgeRouteTarget.normalize("unknown")),"Default Waze");
        ck(BridgeRouteTarget.baseKind("ping")==null&&
            BridgeRouteTarget.baseKind("coord_systemui")==null,
            "No unknown app or system command accepted");
        System.out.println("PASS route choice Waze/Google/Both/T1 existing-task and legacy wire");
    }
}
