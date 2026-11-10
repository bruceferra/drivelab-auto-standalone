import ar.com.rango5gps.drivelabauto.receiver.T1TripMath;
public final class T1TripMathTest {
    static int n=0;
    static void near(double a,double b,double err){
        n++;if(!Double.isFinite(a)||Math.abs(a-b)>err)
            throw new AssertionError("Expected "+b+" got "+a);
    }
    static void yes(boolean v){n++;if(!v)throw new AssertionError("assertion failed");}
    public static void main(String[] ignored){
        near(T1TripMath.lPer100(),7.46674182638,0.00001);
        near(T1TripMath.initialRangeKm(),887,0.001);
        near(T1TripMath.gasolineProxyLiters(887),66.230,0.00001);
        near(T1TripMath.gasolineProxyLiters(100),7.46674182638,0.00001);
        near(T1TripMath.estimatedRangeKm(40,67.0),847,0.001);
        near(T1TripMath.estimatedRangeKm(40,5.3),0,0.001);
        near(T1TripMath.estimatedRangeKm(2000,30.0),0,0.001);
        yes(T1TripMath.floatUpperSaturated(60.5));
        yes(!T1TripMath.floatUpperSaturated(60.4));
        near(T1TripMath.cost(7.46674182638,2129),15896.89,0.1);
        near(T1TripMath.grossHvDischargeKwh(400,20,1000),8.0/3600,0.000001);
        near(T1TripMath.grossHvChargeKwh(400,-20,1000),8.0/3600,0.000001);
        near(T1TripMath.grossHvDischargeKwh(400,-20,1000),0,0.000001);
        near(T1TripMath.grossHvDischargeKwh(400,20,30000),0,0.000001);
        System.out.println("PASS "+n+" trip fuel/kWh proxy and saturation checks");
    }
}
