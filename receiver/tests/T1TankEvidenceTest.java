import ar.com.rango5gps.drivelabauto.receiver.T1TankEvidence;

/** Pure JVM regression: physical first-cut fuel vs OEM display, NEVER old launcher algorithm. */
public final class T1TankEvidenceTest {
    static void near(String name,double actual,double expected,double tolerance){
        if(Math.abs(actual-expected)>tolerance)
            throw new AssertionError(name+": actual="+actual+" expected="+expected);
    }
    static void invalid(Runnable operation){
        try{operation.run();throw new AssertionError("Expected invalid input rejection");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] ignored){
        near("second liters per 100",T1TankEvidence.SECOND.lPer100(),
            7.46674182638,0.00001);
        near("second km per liter",T1TankEvidence.SECOND.kmPerL(),
            13.3927223313,0.00001);
        near("previous approximate liters per 100",T1TankEvidence.PREVIOUS.lPer100(),
            7.03842105263,0.00001);
        near("OEM displayed delta",T1TankEvidence.oemRefillDelta(),61.7,0.000001);
        near("second pump minus OEM delta",
            T1TankEvidence.oemGapToPump(T1TankEvidence.SECOND),4.530,0.000001);
        near("first pump minus OEM delta",
            T1TankEvidence.oemGapToPump(T1TankEvidence.PREVIOUS),5.165,0.000001);
        near("difference of pump receipts",T1TankEvidence.PREVIOUS.litersPump-
            T1TankEvidence.SECOND.litersPump,0.635,0.000001);
        near("surtidor total rounding",T1TankEvidence.SECOND.litersPump*
            T1TankEvidence.REFILL_PRICE_PER_L,141003.67,0.001);
        near("DriveLab GPS vs Jetour",
            T1TankEvidence.DRIVE_LAB_GPS_KM-T1TankEvidence.SECOND.kmJetour,
            8.089,0.000001);
        near("third-party vs Jetour",
            T1TankEvidence.SECOND.kmJetour-T1TankEvidence.OTHER_APP_KM,
            20,0.000001);
        if(!T1TankEvidence.PREVIOUS.distanceApproximate||
            T1TankEvidence.SECOND.distanceApproximate)
            throw new AssertionError("historical distance accuracy labels incorrect");
        invalid(()->T1TankEvidence.litersPer100(1,0));
        invalid(()->T1TankEvidence.litersPer100(-1,100));
        invalid(()->T1TankEvidence.kmPerLiter(0,100));
        System.out.println("PASS 14 checks: two physical refuel cycles, no OEM delta as physical consumption");
    }
}
