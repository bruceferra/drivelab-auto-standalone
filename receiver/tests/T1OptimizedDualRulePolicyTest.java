package ar.com.rango5gps.drivelabauto.receiver;

/** Sequential motorway 1/2 activation with memory in the 31..69 SOC gap. */
public final class T1OptimizedDualRulePolicyTest {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static void isSlot(T1OptimizedDualRulePolicy.Choice c,int slot,
                               boolean hold,String why){
        check(c.slot==slot&&c.held==hold,why+
            " obtained "+c.reason+" slot="+c.slot+" hold="+c.held);
    }
    public static void main(String[] args){
        T1OptimizedModePolicy.Rule motorway1=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.SMART,50,
                true,70,false,100,T1OptimizedModePolicy.Regen.MEDIUM);
        T1OptimizedModePolicy.Rule motorway2=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.FORCED,40,
                false,5,true,30,T1OptimizedModePolicy.Regen.HIGH);
        check(!T1OptimizedDualRulePolicy.overlaps(motorway1,motorway2),
            "motorway >=70 EV and <=30 HEV never simultaneously match");
        isSlot(T1OptimizedDualRulePolicy.selectWithHold(
            motorway1,motorway2,true,50,-1),
            T1OptimizedDualRulePolicy.NONE,false,
            "no previously applied rule, middle SOC does NOT act");
        T1OptimizedDualRulePolicy.Choice entry=
            T1OptimizedDualRulePolicy.selectWithHold(
                motorway1,motorway2,true,80,-1);
        isSlot(entry,0,false,"SOC 80 enters motorway 1 EV SMART");
        check("AUTOPISTA 1".equals(entry.title(
            T1OptimizedModePolicy.Road.MOTORWAY)),"unique road 1 label");
        check(entry.rule.power==T1OptimizedModePolicy.Power.EV,
            "motorway 1 electric");
        // Simulate only AFTER actual OEM readback of EV+SMART+regen2:
        int lastFullyOemConfirmedSlot=entry.slot;
        for(double soc:new double[]{69,60,50,40,31,30.5}){
            T1OptimizedDualRulePolicy.Choice hold=
                T1OptimizedDualRulePolicy.selectWithHold(
                    motorway1,motorway2,true,soc,lastFullyOemConfirmedSlot);
            isSlot(hold,0,true,
                "EV must stay unchanged through SOC gap "+soc);
        }
        T1OptimizedDualRulePolicy.Choice trigger2=
            T1OptimizedDualRulePolicy.selectWithHold(
                motorway1,motorway2,true,30,lastFullyOemConfirmedSlot);
        isSlot(trigger2,1,false,"at SOC30 switch once to motorway2");
        check("AUTOPISTA 2".equals(trigger2.title(
            T1OptimizedModePolicy.Road.MOTORWAY)),
            "unique road 2 label");
        check(trigger2.rule.power==T1OptimizedModePolicy.Power.HEV&&
              trigger2.rule.strategy==T1OptimizedModePolicy.Strategy.FORCED&&
              trigger2.rule.forcedSoc==40,
              "motorway2 HEV FORZADO40 requested");
        // OEM confirmation completed; after SOC recovers to 40, maintain
        // motorway 2, NOT motorway 1 and NOT both.
        lastFullyOemConfirmedSlot=trigger2.slot;
        for(double soc:new double[]{32,39,50,69}){
            T1OptimizedDualRulePolicy.Choice hold=
                T1OptimizedDualRulePolicy.selectWithHold(
                    motorway1,motorway2,true,soc,lastFullyOemConfirmedSlot);
            isSlot(hold,1,true,
                "motorway2 remains applied until next rule trigger "+soc);
        }
        isSlot(T1OptimizedDualRulePolicy.selectWithHold(
            motorway1,motorway2,true,70,lastFullyOemConfirmedSlot),
            0,false,"SOC70 triggers motorway1 again, not concurrent");
        check(T1OptimizedDualRulePolicy.selectWithHold(
            motorway1,motorway2,false,20,1).slot==
            T1OptimizedDualRulePolicy.NONE,
            "disabled motorway2 cannot remain latched");
        check(T1OptimizedDualRulePolicy.selectWithHold(
            motorway1,motorway2,true,Double.NaN,0).slot==
            T1OptimizedDualRulePolicy.NONE,
            "missing SOC must not blindly choose remembered mode");

        // The editor must block mutually active overlapping conditions.
        T1OptimizedModePolicy.Rule clash=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.SMART,40,
                false,5,true,75,T1OptimizedModePolicy.Regen.HIGH);
        check(T1OptimizedDualRulePolicy.overlaps(motorway1,clash),
            "overlap at SOC70..75 must be rejected");
        check(T1OptimizedDualRulePolicy.select(
            motorway1,clash,true,72).slot==
            T1OptimizedDualRulePolicy.NONE,
            "runtime fail closed if overlapping rules somehow persisted");

        // Legacy single profile remains usable with second branch OFF.
        check(T1OptimizedDualRulePolicy.select(
            motorway1,motorway2,false,80).slot==0,
            "rule1 original profile is preserved by upgrade");
        check(T1OptimizedDualRulePolicy.select(
            motorway1,motorway2,false,30).slot==-1,
            "disabled rule2 should never silently force HEV");

        System.out.println("PASS: AUTOPISTA 1>=70 EV, hold 69..31, AUTOPISTA 2<=30 HEV FORZADO40; "+
            "one rule at a time, separate names, no overlap or fallback");
    }
}
