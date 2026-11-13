package ar.com.rango5gps.drivelabauto.receiver;

/** Regression from vehicle: CITY min SOC 15, actual SOC 13 must NOT be green. */
public final class T1OptimizedAppliedRoadPolicyTest {
    static void check(boolean pass,String why){
        if(!pass)throw new AssertionError(why);
    }
    public static void main(String[] args){
        T1OptimizedModePolicy.Rule city=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.EV,
            T1OptimizedModePolicy.Strategy.SMART,50,
            true,15,false,100,T1OptimizedModePolicy.Regen.MEDIUM);
        T1OptimizedModePolicy.Road road=T1OptimizedModePolicy.Road.CITY;
        T1OptimizedAppliedRoadPolicy.Reason insufficient=
            T1OptimizedAppliedRoadPolicy.Reason.SOC_CONDITION_NOT_MET;
        check(T1OptimizedAppliedRoadPolicy.evaluate(road,city,13,true,true,true)
            ==insufficient,
            "CITY at 13% must NOT show green when profile requires >=15%");
        check("SOC NO CUMPLE".equals(T1OptimizedAppliedRoadPolicy.label(insufficient)),
            "low battery must show neutral SOC NO CUMPLE");
        check(T1OptimizedAppliedRoadPolicy.evaluate(road,city,15,false,true,true)
            ==T1OptimizedAppliedRoadPolicy.Reason.VERIFYING_ROAD,
            "only 1/3 or 2/3 GPS must NOT show applied city");
        check(T1OptimizedAppliedRoadPolicy.evaluate(road,city,15,true,false,true)
            ==T1OptimizedAppliedRoadPolicy.Reason.OEM_POWER_PENDING,
            "3/3 and SOC but OEM 1043 not confirmed must stay neutral");
        check(T1OptimizedAppliedRoadPolicy.evaluate(road,city,15,true,true,false)
            ==T1OptimizedAppliedRoadPolicy.Reason.OEM_SOC_REGEN_PENDING,
            "3/3 and 1043 but 1040/1046 not confirmed must stay neutral");
        check(T1OptimizedAppliedRoadPolicy.evaluate(road,city,15,true,true,true)
            ==T1OptimizedAppliedRoadPolicy.Reason.APPLIED,
            "CITY green only with 3/3 + battery + both OEM readbacks");
        check(T1OptimizedAppliedRoadPolicy.evaluate(road,city,Double.NaN,
                true,true,true)==insufficient,
            "missing battery must not count as profile applied");
        check(T1OptimizedAppliedRoadPolicy.evaluate(
            T1OptimizedModePolicy.Road.UNKNOWN,city,70,true,true,true)
            ==T1OptimizedAppliedRoadPolicy.Reason.GPS_UNKNOWN,
            "UNKNOWN is not automatically a confirmed road profile");
        T1OptimizedModePolicy.Rule maxRule=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,20,
            true,10,true,40,T1OptimizedModePolicy.Regen.HIGH);
        check(T1OptimizedAppliedRoadPolicy.evaluate(
            T1OptimizedModePolicy.Road.MOTORWAY,maxRule,50,
            true,true,true)==insufficient,
            "upper SOC condition also prevents green selection");
        System.out.println("OK: selected road only after 3 votes, SOC min/max and OEM power+SOC/regen");
    }
}
