package ar.com.rango5gps.drivelabauto.receiver;

public final class T1StableAutonomyTest {
    private static void ok(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }
    private static void near(double got,double expected,double eps,String reason){
        ok(Double.isFinite(got)&&Math.abs(got-expected)<=eps,
            reason+": "+got+" vs "+expected);
    }
    public static void main(String[] args){
        long now=1800000000000L;
        T1StableAutonomy.Result baseline=T1StableAutonomy.estimate(
            7.47,Double.NaN,Double.NaN,0,Double.NaN,0,now,50);
        near(baseline.l100,7.47,0.000001,"cold start from pump, no bogus 500m");
        T1StableAutonomy.Result erratic=T1StableAutonomy.estimate(
            7.47,4.0,4.0,5,7.47,now-4000,now,35);
        near(erratic.targetL100,7.47,0.000001,
            "5 raw callbacks and outlying low OEM cannot change headline");
        near(erratic.l100,7.47,0.000001,"no huge EV-related swing");
        T1StableAutonomy.Result highway=T1StableAutonomy.estimate(
            7.47,6.7,6.9,100,7.47,now-4000,now,100);
        ok(highway.rawCorroborated&&highway.oem50Used,
            "100 matching 500m events corroborate but do not override OEM 50km");
        ok(Math.abs(highway.l100-7.47)<0.05,
            "400% speed change cannot double range in one block");
        T1StableAutonomy.Result city=T1StableAutonomy.estimate(
            7.47,12,11.8,100,7.47,now-4000,now,25);
        ok(city.targetL100>7.47&&city.l100<7.52,
            "urban trend gradual with explicit bound");
        T1StableAutonomy.Result noMatch=T1StableAutonomy.estimate(
            7.47,6.5,2.0,100,7.47,now-4000,now,80);
        ok(!noMatch.rawCorroborated,"raw units are NOT silently certified");
        T1StableAutonomy.Result fallback=T1StableAutonomy.estimate(
            8.2,Double.NaN,Double.NaN,0,8.3,now-4000,now,40);
        ok(fallback.targetL100==8.2&&fallback.l100>8.2,
            "missing OEM fallback has no discontinuity");
        T1StableAutonomy.Result old=T1StableAutonomy.estimate(
            7.47,9,9,100,12.0,now-40L*24*60*60*1000,now,70);
        near(old.l100,7.47,0.00001,
            "old persisted consumption cannot stay forever");
        T1StableAutonomy.Result moving=T1StableAutonomy.estimate(
            7.47,9,9,100,7.47,now-4000,now,100);
        double usable=30-5.3;
        double before=usable*100/7.47;
        double after=usable*100/moving.l100;
        ok(Math.abs(after-before)<5,"cannot jump hundreds of kilometres in a block");
        System.out.println("PASS stable range, 100x500m OEM corroboration and PHEV safeguards");
    }
}
