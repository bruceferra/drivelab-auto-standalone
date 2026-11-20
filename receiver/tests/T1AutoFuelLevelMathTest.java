package ar.com.rango5gps.drivelabauto.receiver;
public final class T1AutoFuelLevelMathTest {
    static void near(double a,double b){if(Math.abs(a-b)>1e-7)
        throw new AssertionError(a+" != "+b);}
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        T1AutoFuelLevelMath.Delta d=
            T1AutoFuelLevelMath.step(Double.NaN,45.0,4000);
        near(d.low,45);near(d.consumedCandidate,0);
        d=T1AutoFuelLevelMath.step(d.low,44.9,4000);
        near(d.low,44.9);near(d.consumedCandidate,0.1);
        // Slosh up 0.1 L must NOT allow the same drop twice.
        d=T1AutoFuelLevelMath.step(d.low,45.0,4000);
        near(d.low,44.9);near(d.consumedCandidate,0);
        d=T1AutoFuelLevelMath.step(d.low,44.9,4000);
        near(d.low,44.9);near(d.consumedCandidate,0);
        // Refueling resets baseline; no negative consumption.
        d=T1AutoFuelLevelMath.step(22,40,4000);
        near(d.low,40);near(d.consumedCandidate,0);
        // Gap, sensor glitch or an invalid reading creates no fuel.
        d=T1AutoFuelLevelMath.step(40,39.2,4000);
        near(d.low,40);near(d.consumedCandidate,0);
        d=T1AutoFuelLevelMath.step(40,39.9,90000);
        near(d.low,40);near(d.consumedCandidate,0);
        d=T1AutoFuelLevelMath.step(40,Double.NaN,4000);
        near(d.low,40);near(d.consumedCandidate,0);
        check(!T1AutoFuelLevelMath.valid(-1));
        check(T1AutoFuelLevelMath.valid(67));
        check(!T1AutoFuelLevelMath.valid(80));
        System.out.println("PASS read-only CI186 lower-bound fuel candidate, no fabricated liters");
    }
}
