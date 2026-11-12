package ar.com.rango5gps.drivelabauto.receiver;

/** Regression: GPS permission must never halve distances of any slot. */
public final class T1TripDistancePolicyTest {
    static void check(boolean x,String message){
        if(!x)throw new AssertionError(message);
    }
    static void near(double actual,double expected,double tolerance,String message){
        check(Math.abs(actual-expected)<=tolerance,
            message+" actual="+actual+" expected="+expected);
    }
    public static void main(String[] args){
        // 15 km OEM expected with 0.93279 calibration, even if every
        // second GPS interval under-reports or contains no update.
        double total=0,client1=0,client2=0;
        for(int i=0;i<900;i++){
            // 900 x 3 s at 21.45 km/h calibrated ~15 km.
            double candidate=i%2==0?0:0.020;
            T1TripDistancePolicy.Step step=T1TripDistancePolicy.integrate(
                21.45,3000,candidate,100);
            check(step.source==T1TripDistancePolicy.Source.OEM_SPEED_CAL,
                "OEM must remain primary with GPS permission");
            total+=step.km;
            client1+=step.km;
            client2+=step.km;
        }
        near(total,15,0.1,"OEM trip matches 15km, not GPS ~8km");
        near(client1,total,1e-8,"Viaje1 inherits same increments");
        near(client2,total,1e-8,"Viaje2 inherits same increments");
        T1TripDistancePolicy.Step stopped=T1TripDistancePolicy.integrate(
            0,3000,0.1,100);
        near(stopped.km,0,1e-12,"Stationary with GPS drift adds 0 km");
        T1TripDistancePolicy.Step fallback=T1TripDistancePolicy.integrate(
            Double.NaN,3000,0.035,1500);
        near(fallback.km,0.035,1e-12,"GPS fallback when OEM missing");
        check(fallback.source==T1TripDistancePolicy.Source.GPS_FALLBACK,
            "fallback labeled");
        near(T1TripDistancePolicy.integrate(-1,3000,0.2,100).km,0,1e-12,
            "reject GPS jump above physical plausible speed");
        near(T1TripDistancePolicy.integrate(Double.NaN,3000,0.005,100).km,0,
            1e-12,"reject low GPS jitter under 8m");
        near(T1TripDistancePolicy.integrate(Double.NaN,3000,0.035,11000).km,
            0,1e-12,"reject stale GPS fallback");
        near(T1TripDistancePolicy.integrate(80,12000,0.15,100).km,0,1e-12,
            "offline gap adds no imaginary distance");
        near(T1TripDistancePolicy.integrate(200,3000,0,Long.MAX_VALUE).km,
            200*3000/3600000.0*T1TripDistancePolicy.OEM_KM_FACTOR,
            1e-12,"OEM speed integration 200km/h");
        System.out.println("OK: robust OEM primary/GPS fallback; Viajes1/2/Total independent of GPS permission");
    }
}
