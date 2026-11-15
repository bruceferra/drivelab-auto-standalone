import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy;
import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy.Gear;
import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy.Phase;
import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy.Action;

/** Pure FSM regressions: no vehicle signals or OEM writes. */
public final class MirrorParkingPolicyTest {
    private static int checks=0;
    static void eq(Object actual,Object expected){
        checks++;
        if(!actual.equals(expected))
            throw new AssertionError("Expected "+expected+" got "+actual);
    }
    static void yes(boolean actual){checks++;if(!actual)throw new AssertionError("failed");}
    static void invalid(Runnable r){
        checks++;
        try{r.run();throw new AssertionError("Expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }
    static MirrorParkingPolicy lowered(int left,int right){
        MirrorParkingPolicy p=new MirrorParkingPolicy(left,right);
        eq(p.observe(Gear.D,0,100,true),Action.NONE);
        eq(p.observe(Gear.R,0,200,true),Action.TILT_ON_R);
        p.completeTilt(left,right);
        eq(p.phase(),Phase.TILTED);
        return p;
    }
    static void pThirtySecondsOnly(){
        MirrorParkingPolicy p=lowered(2,3);
        eq(p.observe(Gear.P,0,1000,true),Action.NONE);
        eq(p.observe(Gear.P,0,30999,true),Action.NONE);
        eq(p.observe(Gear.P,0,31000,true),Action.NONE);
        eq(p.observe(Gear.P,0,31001,true),Action.RESTORE_AFTER_NON_R_SPEED);
        eq(p.phase(),Phase.RESTORING);
        p.completeRestore(2,3);
        eq(p.phase(),Phase.NORMAL);
        // No duplicate restoration while parked.
        eq(p.observe(Gear.P,0,33000,true),Action.NONE);
    }
    static void reverseParkingShufflesDoNotRestore(){
        MirrorParkingPolicy p=lowered(4,4);
        eq(p.observe(Gear.P,0,1000,true),Action.NONE);
        eq(p.observe(Gear.P,0,12000,true),Action.NONE);
        eq(p.observe(Gear.D,0,13000,true),Action.NONE);
        eq(p.observe(Gear.D,3,14000,true),Action.NONE);
        eq(p.observe(Gear.D,5,18000,true),Action.NONE);
        eq(p.observe(Gear.R,0,19000,true),Action.NONE);
        eq(p.observe(Gear.P,0,19500,true),Action.NONE);
        eq(p.observe(Gear.P,0,48000,true),Action.NONE);
        eq(p.observe(Gear.D,4.9,48100,true),Action.NONE);
        eq(p.observe(Gear.R,0,50000,true),Action.NONE);
        eq(p.phase(),Phase.TILTED);
        // A second shift into R never issues an additional tilt.
        eq(p.leftIssued(),4);
        eq(p.rightIssued(),4);
    }
    static void interruptedTiltCountsOnlyCompletedPulses(){
        MirrorParkingPolicy p=new MirrorParkingPolicy(20,20);
        eq(p.observe(Gear.P,0,10,true),Action.NONE);
        eq(p.observe(Gear.R,0,20,true),Action.TILT_ON_R);
        // Real T1: shifting back to P while the 20-pulse sequence was
        // active. Only 15 left and 12 right commands completed.
        p.completeTilt(15,12);
        eq(p.phase(),Phase.TILTED);
        eq(p.leftIssued(),15);
        eq(p.rightIssued(),12);
        eq(p.observe(Gear.P,0,1000,true),Action.NONE);
        eq(p.observe(Gear.P,0,31000,true),Action.NONE);
        eq(p.observe(Gear.P,0,31001,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
        p.completeRestore(15,12);
        eq(p.phase(),Phase.NORMAL);
        eq(p.leftIssued(),0);
        eq(p.rightIssued(),0);
        eq(p.observe(Gear.P,0,32000,true),Action.NONE);
    }
    static void interruptedReturnKeepsRemainingCounts(){
        MirrorParkingPolicy p=lowered(20,20);
        eq(p.observe(Gear.P,0,500,true),Action.NONE);
        eq(p.observe(Gear.P,0,30501,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
        // Original 20 down, but only 8 up before a gear change.
        p.completeRestore(8,6);
        eq(p.phase(),Phase.TILTED);
        eq(p.leftIssued(),12);
        eq(p.rightIssued(),14);
        // Next uninterrupted 30s in P restores the rest, NOT 20 again.
        eq(p.observe(Gear.P,0,32000,true),Action.NONE);
        eq(p.observe(Gear.P,0,62001,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
        p.completeRestore(12,14);
        eq(p.phase(),Phase.NORMAL);
        eq(p.leftIssued(),0);eq(p.rightIssued(),0);
    }
    static void noCompletedDownDoesNotCauseReturn(){
        MirrorParkingPolicy p=new MirrorParkingPolicy(20);
        eq(p.observe(Gear.P,0,1,true),Action.NONE);
        eq(p.observe(Gear.R,0,2,true),Action.TILT_ON_R);
        p.completeTilt(0,0);
        eq(p.phase(),Phase.NORMAL);
        eq(p.observe(Gear.P,0,99999,true),Action.NONE);
    }
    static void forwardDrivingOver5RestoresOnce(){
        MirrorParkingPolicy p=lowered(2,2);
        eq(p.observe(Gear.N,0,1000,true),Action.NONE);
        eq(p.observe(Gear.D,0,1100,true),Action.NONE);
        eq(p.observe(Gear.D,4.9,2000,true),Action.NONE);
        eq(p.observe(Gear.D,5.0,2100,true),Action.NONE);
        eq(p.observe(Gear.D,5.01,2200,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
        p.completeRestore(2,2);
        eq(p.phase(),Phase.NORMAL);
        eq(p.observe(Gear.D,12,3300,true),Action.NONE);
    }
    static void rollingParkDoesNotCount(){
        MirrorParkingPolicy p=lowered(2,2);
        eq(p.observe(Gear.P,2,1000,true),Action.NONE);
        eq(p.observe(Gear.P,0,32000,true),Action.NONE);
        eq(p.observe(Gear.P,0,61999,true),Action.NONE);
        eq(p.observe(Gear.P,0,62001,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
    }
    static void interruptionResetsParkCounter(){
        MirrorParkingPolicy p=lowered(2,2);
        eq(p.observe(Gear.P,0,1000,true),Action.NONE);
        eq(p.observe(Gear.P,0,29000,true),Action.NONE);
        eq(p.observe(Gear.N,0,29500,true),Action.NONE);
        eq(p.observe(Gear.P,0,30000,true),Action.NONE);
        eq(p.observe(Gear.P,0,60000,true),Action.NONE);
        eq(p.observe(Gear.P,0,60001,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
    }
    static void invalidStateNoMotion(){
        MirrorParkingPolicy p=lowered(2,2);
        eq(p.observe(Gear.P,Double.NaN,5000,true),Action.NONE);
        eq(p.observe(Gear.P,0,6000,true),Action.NONE);
        eq(p.observe(Gear.P,0,36000,true),Action.NONE);
        eq(p.observe(Gear.P,0,36001,true),
            Action.RESTORE_AFTER_NON_R_SPEED);
        MirrorParkingPolicy unexpected=new MirrorParkingPolicy(2);
        eq(unexpected.observe(Gear.R,0,1000,true),Action.NONE);
        invalid(()->new MirrorParkingPolicy(0));
        invalid(()->new MirrorParkingPolicy(21));
        yes(!MirrorParkingPolicy.completedManualRecovery(9,10,10,10));
        yes(MirrorParkingPolicy.completedManualRecovery(10,10,10,10));
    }
    public static void main(String[] args){
        pThirtySecondsOnly();
        reverseParkingShufflesDoNotRestore();
        interruptedTiltCountsOnlyCompletedPulses();
        interruptedReturnKeepsRemainingCounts();
        noCompletedDownDoesNotCauseReturn();
        forwardDrivingOver5RestoresOnce();
        rollingParkDoesNotCount();
        interruptionResetsParkCounter();
        invalidStateNoMotion();
        System.out.println("PASS "+checks+" assertions: R tilt once, P >30s stopped or D >5km/h restore, low-speed maneuvers ignored");
    }
}
