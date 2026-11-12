package ar.com.rango5gps.drivelabauto.receiver;

/** Pure-JVM regression: four independent rules, unknown fallback and SOC. */
public final class T1OptimizedModePolicyTest {
    private static void ok(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }
    public static void main(String[] args){
        usingRoadClassification();
        unknownIsConfigurable();
        conditionalSoc();
        forcedAndRegeneration();
        System.out.println("OK: four-road optimized profiles are read-only");
    }
    static void usingRoadClassification(){
        ok(T1OptimizedModePolicy.category("residential")==
            T1OptimizedModePolicy.Road.CITY,"residential city");
        ok(T1OptimizedModePolicy.category("primary")==
            T1OptimizedModePolicy.Road.ROAD,"primary road");
        ok(T1OptimizedModePolicy.category("trunk")==
            T1OptimizedModePolicy.Road.ROAD,"trunk is not necessarily motorway");
        ok(T1OptimizedModePolicy.category("motorway")==
            T1OptimizedModePolicy.Road.MOTORWAY,"motorway");
        ok(T1OptimizedModePolicy.category("footway")==
            T1OptimizedModePolicy.Road.UNKNOWN,"footway unknown");
        ok(T1OptimizedModePolicy.category(null)==
            T1OptimizedModePolicy.Road.UNKNOWN,"null unknown");
    }
    static void unknownIsConfigurable(){
        T1OptimizedModePolicy.Rule unknown=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV_MAX,
                T1OptimizedModePolicy.Strategy.SMART,30,
                false,25,T1OptimizedModePolicy.Regen.LOW);
        T1OptimizedModePolicy.Decision d=
            T1OptimizedModePolicy.evaluate(
                T1OptimizedModePolicy.Road.UNKNOWN,unknown,Double.NaN);
        ok(d.conditionSatisfied,"unknown with no threshold is user-configurable");
        ok(d.summary().contains("EV MAX"),"unknown selected EV MAX");
        ok(d.summary().contains("BAJA"),"regen low");
        ok(!d.summary().contains("comando"),"only suggestion");
    }
    static void conditionalSoc(){
        T1OptimizedModePolicy.Rule city=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.SMART,50,
                true,30,T1OptimizedModePolicy.Regen.MEDIUM);
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,Double.NaN)
                .conditionSatisfied,"missing SOC must not allow");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,30)
                .conditionSatisfied,"threshold requires strictly greater");
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,30.1)
                .conditionSatisfied,"SOC above 30 allowed");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,101)
                .conditionSatisfied,"invalid SOC");
    }
    static void forcedAndRegeneration(){
        T1OptimizedModePolicy.Rule highway=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.FORCED,50,
                false,0,T1OptimizedModePolicy.Regen.HIGH);
        T1OptimizedModePolicy.Decision d=
            T1OptimizedModePolicy.evaluate(
                T1OptimizedModePolicy.Road.MOTORWAY,highway,0);
        ok(d.conditionSatisfied,"forced 50 user profile allowed as suggestion");
        ok(d.summary().contains("SOC FORZADO 50"),"forced target kept");
        ok(d.summary().contains("ALTA"),"regen high");
        ok(T1OptimizedModePolicy.defaultRule(
            T1OptimizedModePolicy.Road.UNKNOWN).power==
                T1OptimizedModePolicy.Power.HEV,
            "unknown default is HEV SMART");
    }
}
