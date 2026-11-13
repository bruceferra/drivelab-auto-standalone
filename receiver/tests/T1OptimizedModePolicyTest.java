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
        independentSocRange();
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
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,30)
                .conditionSatisfied,"default comparator is greater or equal");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,29.9)
                .conditionSatisfied,"below threshold rejected");
        T1OptimizedModePolicy.Rule low=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.SMART,50,true,30,
                T1OptimizedModePolicy.Comparison.LESS_OR_EQUAL,
                T1OptimizedModePolicy.Regen.LOW);
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,low,30)
                .conditionSatisfied,"less or equal includes threshold");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,low,30.1)
                .conditionSatisfied,"above less-than threshold rejected");
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,30.1)
                .conditionSatisfied,"SOC above 30 allowed");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,city,101)
                .conditionSatisfied,"invalid SOC");
    }
    static void independentSocRange(){
        T1OptimizedModePolicy.Rule bounded=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.FORCED,56,
                true,10,true,40,T1OptimizedModePolicy.Regen.LOW);
        for(double soc:new double[]{10,20,40})
            ok(T1OptimizedModePolicy.evaluate(
                T1OptimizedModePolicy.Road.CITY,bounded,soc)
                .conditionSatisfied,"range inclusive 10..40 "+soc);
        for(double soc:new double[]{0,9.9,40.1,80})
            ok(!T1OptimizedModePolicy.evaluate(
                T1OptimizedModePolicy.Road.CITY,bounded,soc)
                .conditionSatisfied,"outside 10..40 "+soc);
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,bounded,Double.NaN)
            .conditionSatisfied,"unknown OEM SOC must fail a ranged condition");
        ok(bounded.rangeSummary().contains("≥ 10 %")&&
            bounded.rangeSummary().contains("≤ 40 %"),"both bounds in summary");
        T1OptimizedModePolicy.Rule minOnly=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.SMART,50,
                true,10,false,40,T1OptimizedModePolicy.Regen.MEDIUM);
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.ROAD,minOnly,100)
            .conditionSatisfied,"single lower bound ignores disabled max");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.ROAD,minOnly,5)
            .conditionSatisfied,"single lower bound rejects low SOC");
        T1OptimizedModePolicy.Rule maxOnly=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.SMART,50,
                false,5,true,40,T1OptimizedModePolicy.Regen.HIGH);
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.MOTORWAY,maxOnly,5)
            .conditionSatisfied,"single upper bound ignores disabled min");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.MOTORWAY,maxOnly,40.1)
            .conditionSatisfied,"single upper bound rejects high SOC");
        T1OptimizedModePolicy.Rule none=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.SMART,50,
                false,10,false,40,T1OptimizedModePolicy.Regen.MEDIUM);
        ok(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.UNKNOWN,none,Double.NaN)
            .conditionSatisfied,"both OFF must not require known SOC");
        T1OptimizedModePolicy.Rule impossible=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.FORCED,56,
                true,70,true,30,T1OptimizedModePolicy.Regen.LOW);
        ok(!impossible.rangeValid(),"min above max invalid");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,impossible,50)
            .conditionSatisfied,"invalid range must fail closed");
        // Legacy single-compare constructor must migrate semantically.
        T1OptimizedModePolicy.Rule legacyMax=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.SMART,50,true,30,
                T1OptimizedModePolicy.Comparison.LESS_OR_EQUAL,
                T1OptimizedModePolicy.Regen.LOW);
        ok(legacyMax.maxEnabled&&!legacyMax.minEnabled,
            "legacy <= stays an upper-only bound");
        ok(legacyMax.maxBattery==30,"legacy max threshold retained");
        ok(!T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,legacyMax,31)
            .conditionSatisfied,"legacy <= rejects above 30");
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
