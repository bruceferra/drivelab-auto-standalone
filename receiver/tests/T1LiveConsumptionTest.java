package ar.com.rango5gps.drivelabauto.receiver;
public final class T1LiveConsumptionTest {
    private static void check(boolean yes,String msg){
        if(!yes)throw new AssertionError(msg);
    }
    private static void equalsTo(double x,double y,String msg){
        if(Math.abs(x-y)>0.005)
            throw new AssertionError(msg+": "+x+" expected "+y);
    }
    public static void main(String[] args){
        long now=1_800_000_000_000L;
        T1LiveConsumption.State highway=T1LiveConsumption.evaluate(
            5,6,5,now-5000,now,9,35,7.47);
        check(highway.rawFresh&&highway.hasRollingCandidate,
            "fresh 500m candidate");
        equalsTo(highway.candidateFuelKm,
            (35-5.3)*100/6d,"speed-dependent range");
        T1LiveConsumption.State slower=T1LiveConsumption.evaluate(
            4,4,5,now-10000,now,9,35,7.47);
        check(slower.candidateFuelKm>highway.candidateFuelKm,
            "slower 500m fuel rhythm increases range");
        T1LiveConsumption.State electric=T1LiveConsumption.evaluate(
            0,0,5,now-10000,now,8,35,7.47);
        check(!electric.hasRollingCandidate&&
            electric.candidateFuelKm>0&&
            Double.isFinite(electric.candidateFuelKm),
            "EV 0l/100 must not imply infinite gasoline range");
        T1LiveConsumption.State stale=T1LiveConsumption.evaluate(
            4,4,5,now-220000,now,8,35,7.47);
        check(!stale.rawFresh&&stale.rolling50Valid,
            "stale callback fallback 50km");
        T1LiveConsumption.State none=T1LiveConsumption.evaluate(
            Float.NaN,Float.NaN,0,0,now,Float.NaN,35,7.47);
        check(!none.rawFresh&&!none.rolling50Valid&&
            Double.isFinite(none.candidateFuelKm),"honest historical fallback");
        T1LiveConsumption.State full=T1LiveConsumption.evaluate(
            5,5,5,now-3000,now,7,67,7.47);
        check(Double.isNaN(full.candidateFuelKm),
            "fuel gauge saturated: do not claim exact range");
        System.out.println("OK: 500m dynamic range, freshness, EV fallback and saturation");
    }
}