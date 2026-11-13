package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Two independently configured conditional subprofiles per road.
 * Their battery conditions are evaluated against the ACTUAL OEM battery SOC,
 * never the forced SOC target 1041.
 *
 * If two enabled rules overlap they are ambiguous: refuse to save and
 * never act automatically. If no rule matches, keep current OEM powertrain,
 * SOC and regeneration unchanged (no implicit HEV fallback).
 */
final class T1OptimizedDualRulePolicy {
    static final int NONE=-1,PRIMARY=0,SECONDARY=1;

    static final class Choice {
        final int slot;
        final T1OptimizedModePolicy.Rule rule;
        final String reason;
        final boolean held;
        Choice(int slot,T1OptimizedModePolicy.Rule rule,String reason){
            this(slot,rule,reason,false);
        }
        Choice(int slot,T1OptimizedModePolicy.Rule rule,
               String reason,boolean held){
            this.slot=slot;this.rule=rule;this.reason=reason;this.held=held;
        }
        boolean matched(){return slot>=0&&rule!=null;}
        String label(){return matched()?"REGLA "+(slot==PRIMARY?"1":"2"):reason;}
        String title(T1OptimizedModePolicy.Road road){
            return T1OptimizedModePolicy.roadName(road)+" "+(slot==PRIMARY?"1":"2");
        }
    }

    static boolean matches(T1OptimizedModePolicy.Rule rule,double battery){
        if(rule==null||!rule.rangeValid())return false;
        return T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,rule,battery).conditionSatisfied;
    }

    static boolean overlaps(T1OptimizedModePolicy.Rule a,
                            T1OptimizedModePolicy.Rule b){
        if(a==null||b==null)return false;
        if(!a.rangeValid()||!b.rangeValid())return true;
        int aLow=a.minEnabled?a.minBattery:0;
        int aHigh=a.maxEnabled?a.maxBattery:100;
        int bLow=b.minEnabled?b.minBattery:0;
        int bHigh=b.maxEnabled?b.maxBattery:100;
        return Math.max(aLow,bLow)<=Math.min(aHigh,bHigh);
    }

    static Choice select(T1OptimizedModePolicy.Rule first,
                         T1OptimizedModePolicy.Rule second,
                         boolean secondEnabled,double actualBatterySoc){
        if(first==null||!first.rangeValid()||
            (secondEnabled&&(second==null||!second.rangeValid())))
            return new Choice(NONE,null,"REGLAS INVALIDAS");
        if(secondEnabled&&overlaps(first,second))
            return new Choice(NONE,null,"RANGOS SUPERPUESTOS");
        // On a two-rule configuration, never pick a battery-dependent
        // branch without an actual, valid battery reading.
        if(!Double.isFinite(actualBatterySoc)||actualBatterySoc<0||
           actualBatterySoc>100)
            return new Choice(NONE,null,"SOC SIN LECTURA");
        if(matches(first,actualBatterySoc))
            return new Choice(PRIMARY,first,"REGLA A");
        if(secondEnabled&&matches(second,actualBatterySoc))
            return new Choice(SECONDARY,second,"REGLA B");
        return new Choice(NONE,null,"NINGUNA CONDICION");
    }

    /**
     * Stateful hysteresis: both rules can act successively. Enter on an
     * actual threshold (A >=70, B <=30), but do not undo the selected
     * profile while SOC traverses the gap (69..31). A held rule is
     * read-only; the service MUST NOT send any OEM command during the gap.
     *
     * Only a previously fully OEM-confirmed rule may be held. A live rule
     * match always supersedes the held rule.
     */
    static Choice selectWithHold(T1OptimizedModePolicy.Rule first,
                                 T1OptimizedModePolicy.Rule second,
                                 boolean secondEnabled,double actualSoc,
                                 int confirmedSlot){
        Choice active=select(first,second,secondEnabled,actualSoc);
        if(active.matched()||!"NINGUNA CONDICION".equals(active.reason))
            return active;
        if(confirmedSlot==PRIMARY&&first!=null&&first.rangeValid())
            return new Choice(PRIMARY,first,"MANTENER REGLA 1",true);
        if(confirmedSlot==SECONDARY&&secondEnabled&&second!=null&&
           second.rangeValid())
            return new Choice(SECONDARY,second,"MANTENER REGLA 2",true);
        return active;
    }

    private T1OptimizedDualRulePolicy(){}
}
