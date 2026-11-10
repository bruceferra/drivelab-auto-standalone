import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy;
import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy.Gear;
import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy.Phase;
import ar.com.rango5gps.drivelabauto.receiver.MirrorParkingPolicy.Action;

/** No OEM writes. Regression tests for future R/D parking automation. */
public final class MirrorParkingPolicyTest {
    static int checks=0;
    static void eq(Object actual,Object expected){
        checks++;
        if(!actual.equals(expected))
            throw new AssertionError("Expected "+expected+" got "+actual);
    }
    static void yes(boolean b){checks++;if(!b)throw new AssertionError("condition failed");}
    static void invalid(Runnable r){
        checks++;
        try{r.run();throw new AssertionError("Expected exception");}
        catch(IllegalArgumentException ex){}
    }
    static void testParkingShuffle(){
        MirrorParkingPolicy m=new MirrorParkingPolicy(10);
        eq(m.observe(Gear.R,0,0,true),Action.NONE); // no startup movement
        eq(m.observe(Gear.D,0,200,true),Action.NONE);
        eq(m.observe(Gear.R,0,400,true),Action.TILT_ON_R);
        eq(m.phase(),Phase.TILTING);
        m.completeTilt(10,10);
        eq(m.phase(),Phase.TILTED);
        eq(m.observe(Gear.D,3,1600,true),Action.NONE);
        eq(m.observe(Gear.R,0,2100,true),Action.NONE);
        eq(m.observe(Gear.D,8,2700,true),Action.NONE);
        eq(m.observe(Gear.D,10,3200,true),Action.NONE);
        eq(m.observe(Gear.D,10.1,3400,true),Action.NONE);
        eq(m.observe(Gear.D,11.0,4100,true),Action.NONE);
        eq(m.observe(Gear.D,11.0,4300,true),Action.RESTORE_AFTER_NON_R_SPEED);
        eq(m.phase(),Phase.RESTORING);
        m.completeRestore(10,10);
        eq(m.phase(),Phase.NORMAL);
        eq(m.observe(Gear.D,14,6000,true),Action.NONE);
        eq(m.observe(Gear.R,0,7000,true),Action.TILT_ON_R);
    }
    static void testNoFalseTriggers(){
        MirrorParkingPolicy m=new MirrorParkingPolicy(4);
        eq(m.observe(Gear.P,0,0,true),Action.NONE);
        eq(m.observe(Gear.R,1,200,true),Action.NONE); // not stopped
        eq(m.observe(Gear.D,0,300,true),Action.NONE);
        eq(m.observe(Gear.R,0,400,false),Action.NONE); // unarmed
        eq(m.observe(Gear.R,0,450,true),Action.TILT_ON_R);
        m.completeTilt(4,4);
        eq(m.observe(Gear.R,16,1300,true),Action.NONE); // reversing fast must NOT restore
        eq(m.observe(Gear.N,18,1600,true),Action.NONE);
        eq(m.observe(Gear.D,11,2000,true),Action.NONE);
        eq(m.observe(Gear.D,8,2500,true),Action.NONE); // resets dwell
        eq(m.observe(Gear.D,11,2800,true),Action.NONE);
        eq(m.observe(Gear.D,12,3600,true),Action.RESTORE_AFTER_NON_R_SPEED);
    }
    static void testNeutralCoastAndUnknown(){
        MirrorParkingPolicy m=new MirrorParkingPolicy(3);
        eq(m.observe(Gear.D,0,0,true),Action.NONE);
        eq(m.observe(Gear.R,0,100,true),Action.TILT_ON_R);
        m.completeTilt(3,3);
        eq(m.observe(Gear.N,12,500,true),Action.NONE);
        eq(m.observe(Gear.N,12,1200,true),Action.NONE);
        eq(m.observe(Gear.N,12,1300,true),Action.RESTORE_AFTER_NON_R_SPEED);
        m.completeRestore(3,3);
        eq(m.phase(),Phase.NORMAL);

        MirrorParkingPolicy other=new MirrorParkingPolicy(2);
        eq(other.observe(Gear.D,0,0,true),Action.NONE);
        eq(other.observe(Gear.R,0,20,true),Action.TILT_ON_R);
        other.completeTilt(2,2);
        eq(other.observe(Gear.UNKNOWN,30,1000,true),Action.NONE);
        eq(other.observe(Gear.UNKNOWN,30,1900,true),Action.NONE);
        eq(other.observe(Gear.D,11,2000,true),Action.NONE);
        eq(other.observe(Gear.R,0,2500,true),Action.NONE);
        eq(other.observe(Gear.N,10.01,2800,true),Action.NONE);
        eq(other.observe(Gear.N,11,3700,true),Action.RESTORE_AFTER_NON_R_SPEED);
    }
    static void testInterruptedOperations(){
        MirrorParkingPolicy m=new MirrorParkingPolicy(5);
        eq(m.observe(Gear.D,0,0,true),Action.NONE);
        eq(m.observe(Gear.R,0,100,true),Action.TILT_ON_R);
        m.completeTilt(5,4);
        eq(m.phase(),Phase.UNKNOWN_POSITION);
        eq(m.observe(Gear.D,12,1400,true),Action.NONE);
        m.invalidate();
        eq(m.phase(),Phase.UNKNOWN_POSITION);
        invalid(()->new MirrorParkingPolicy(0));
        invalid(()->new MirrorParkingPolicy(11));
    }
    public static void main(String[] args){
        testParkingShuffle();
        testNoFalseTriggers();
        testNeutralCoastAndUnknown();
        testInterruptedOperations();
        System.out.println("PASS "+checks+" assertions: R single tilt, non-R >10 km/h debounce (D or N), no parking shuttle loop");
    }
}
