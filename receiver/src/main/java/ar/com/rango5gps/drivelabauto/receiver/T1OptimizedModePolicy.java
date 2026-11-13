package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Configuration-only road rule evaluator. This code contains no OEM setters
 * and must never trigger propulsion, SOC or regeneration commands.
 */
final class T1OptimizedModePolicy {
    enum Road { CITY, ROAD, MOTORWAY, UNKNOWN }
    enum Power { EV, HEV, EV_MAX }
    enum Strategy { SMART, FORCED }
    enum Regen { LOW, MEDIUM, HIGH }
    enum Comparison {
        GREATER_OR_EQUAL, LESS_OR_EQUAL;
        String symbol(){return this==GREATER_OR_EQUAL?"≥":"≤";}
        boolean matches(double current,int threshold){
            return this==GREATER_OR_EQUAL?current>=threshold:current<=threshold;
        }
    }

    static final class Rule {
        final Power power;
        final Strategy strategy;
        final int forcedSoc;
        // Legacy single-threshold fields are retained for earlier callers.
        final boolean thresholdEnabled;
        final int minBattery;
        final Comparison comparison;
        final Regen regeneration;
        final boolean minEnabled,maxEnabled;
        final int maxBattery;
        Rule(Power p, Strategy s, int target, boolean condition, int minimum, Regen r){
            this(p,s,target,condition,minimum,Comparison.GREATER_OR_EQUAL,r);
        }
        Rule(Power p, Strategy s, int target, boolean condition, int threshold,
             Comparison comparator, Regen r){
            this(p,s,target,
                condition&&comparator!=Comparison.LESS_OR_EQUAL,threshold,
                condition&&comparator==Comparison.LESS_OR_EQUAL,threshold,r);
        }
        /** Both battery limits can be enabled independently.
         * This is a filter on ACTUAL OEM SOC, not forced target 1041.
         * Invalid min>max is rejected by evaluate and the editor.
         */
        Rule(Power p,Strategy s,int target,
             boolean lowerEnabled,int lowerLimit,
             boolean upperEnabled,int upperLimit,Regen r){
            power=p==null?Power.HEV:p;
            strategy=s==null?Strategy.SMART:s;
            forcedSoc=clamp(target,0,100);
            minEnabled=lowerEnabled;
            maxEnabled=upperEnabled;
            minBattery=clamp(lowerLimit,0,100);
            maxBattery=clamp(upperLimit,0,100);
            thresholdEnabled=minEnabled||maxEnabled;
            comparison=minEnabled?
                Comparison.GREATER_OR_EQUAL:Comparison.LESS_OR_EQUAL;
            regeneration=r==null?Regen.MEDIUM:r;
        }
        boolean rangeValid(){
            return !minEnabled||!maxEnabled||minBattery<=maxBattery;
        }
        String rangeSummary(){
            if(!thresholdEnabled)return "sin condición SOC";
            StringBuilder result=new StringBuilder("batería");
            if(minEnabled)result.append(" ≥ ").append(minBattery).append(" %");
            if(maxEnabled)result.append(" ≤ ").append(maxBattery).append(" %");
            return result.toString();
        }
    }
    static final class Decision {
        final Road road;
        final Rule requested;
        final boolean conditionSatisfied;
        final boolean socValid;
        final String explanation;
        Decision(Road r,Rule cfg,boolean allowed,boolean valid,String why){
            road=r;requested=cfg;conditionSatisfied=allowed;
            socValid=valid;explanation=why;
        }
        String summary(){
            if(!conditionSatisfied)
                return "HEV · SMART (respaldo recomendado) · "+explanation;
            return requested.power.name().replace('_',' ')+
                " · "+(requested.strategy==Strategy.SMART?"SMART":
                    "SOC FORZADO "+requested.forcedSoc+" %")+
                " · regeneración "+regenerationName(requested.regeneration)+
                (requested.thresholdEnabled?
                    " · "+requested.rangeSummary():"")+
                " · SOLO SUGERENCIA";
        }
    }
    static Decision evaluate(Road road,Rule rule,double batteryPercent){
        if(rule==null)return new Decision(road,null,false,false,
            "No hay perfil configurado");
        if(rule.thresholdEnabled){
            if(!rule.rangeValid())
                return new Decision(road,rule,false,false,
                    "Condición SOC inválida: mínimo "+rule.minBattery+
                    " % superior al máximo "+rule.maxBattery+" %");
            if(!Double.isFinite(batteryPercent)||batteryPercent<0||
                batteryPercent>100)
                return new Decision(road,rule,false,false,
                    "SOC OEM desconocido; no se cumple condición");
            if((rule.minEnabled&&batteryPercent<rule.minBattery)||
               (rule.maxEnabled&&batteryPercent>rule.maxBattery))
                return new Decision(road,rule,false,true,
                    "SOC batería "+String.format(java.util.Locale.ROOT,"%.1f",
                        batteryPercent)+" % no cumple "+rule.rangeSummary());
        }
        return new Decision(road,rule,true,true,
            road==Road.UNKNOWN?"Vía desconocida: perfil de respaldo": "Vía identificada");
    }
    static Road category(String osmHighway){
        if(osmHighway==null)return Road.UNKNOWN;
        switch(osmHighway){
            case "motorway": case "motorway_link":
                return Road.MOTORWAY;
            case "trunk":case "trunk_link":case "primary":case "primary_link":
            case "secondary":case "secondary_link":
                return Road.ROAD;
            case "residential":case "living_street":case "service":
            case "tertiary":case "tertiary_link":case "unclassified":
                return Road.CITY;
            default:return Road.UNKNOWN;
        }
    }
    static String roadName(Road r){
        if(r==Road.CITY)return "CIUDAD";
        if(r==Road.ROAD)return "CARRETERA";
        if(r==Road.MOTORWAY)return "AUTOPISTA";
        return "DESCONOCIDO";
    }
    static String regenerationName(Regen r){
        return r==Regen.LOW?"BAJA":r==Regen.HIGH?"ALTA":"MEDIA";
    }
    private static int clamp(int x,int lo,int hi){
        return Math.max(lo,Math.min(hi,x));
    }
    static Rule defaultRule(Road r){
        if(r==Road.CITY)return new Rule(Power.EV,Strategy.SMART,50,false,30,Regen.MEDIUM);
        if(r==Road.MOTORWAY)return new Rule(Power.HEV,Strategy.SMART,50,false,30,Regen.MEDIUM);
        return new Rule(Power.HEV,Strategy.SMART,50,false,30,Regen.MEDIUM);
    }
    private T1OptimizedModePolicy(){}
}
