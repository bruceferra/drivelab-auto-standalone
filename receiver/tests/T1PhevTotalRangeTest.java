package ar.com.rango5gps.drivelabauto.receiver;
public final class T1PhevTotalRangeTest {
    private static void ok(boolean b,String why){
        if(!b)throw new AssertionError(why);
    }
    private static void close(double got,double expected,String why){
        if(!Double.isFinite(got)||Math.abs(got-expected)>0.00001)
            throw new AssertionError(why+": "+got+" vs "+expected);
    }
    public static void main(String[] args){
        double consumption=67.0*100/950.0; // measured historical cycle
        T1PhevTotalRange.Estimate full=T1PhevTotalRange.estimate(
            67,100,consumption,100,false,Double.NaN);
        close(full.referenceFullKm,950,"950 km mixed cycle calibrated");
        close(full.remainingKm,950,"saturated full tank shows historic reference");
        close(full.evPotentialKm,100,"EV diagnostic separate");
        ok(full.remainingKm!=full.referenceFullKm+full.evPotentialKm,
            "NEVER add 100km EV to already-mixed 950km range");
        T1PhevTotalRange.Estimate at606=T1PhevTotalRange.estimate(
            67,60,consumption,100,true,606);
        close(at606.remainingKm,344,"606 km travelled leaves 344 on same cycle");
        T1PhevTotalRange.Estimate gauge=T1PhevTotalRange.estimate(
            28,50,consumption,100,false,Double.NaN);
        close(gauge.remainingKm,(28-5.3)/(67-5.3)*950,
            "CI186 gauge fraction normalized to real usable span");
        T1PhevTotalRange.Estimate combined=T1PhevTotalRange.estimate(
            29,50,consumption,100,true,606);
        close(combined.remainingKm,Math.min((29-5.3)/61.7*950,344),
            "conservative independent estimates never inflate");
        T1PhevTotalRange.Estimate cutoff=T1PhevTotalRange.estimate(
            5.3,50,consumption,100,false,Double.NaN);
        close(cutoff.remainingKm,0,"thermal cutoff no gasoline mixed distance");
        T1PhevTotalRange.Estimate stale=T1PhevTotalRange.estimate(
            Double.NaN,50,consumption,100,false,Double.NaN);
        ok(Double.isNaN(stale.remainingKm),
            "cannot invent range if both tank and anchor unknown");
        System.out.println("PASS PHEV cycle, no EV double count, 606/950 remaining, CI186 safeguards");
    }
}
