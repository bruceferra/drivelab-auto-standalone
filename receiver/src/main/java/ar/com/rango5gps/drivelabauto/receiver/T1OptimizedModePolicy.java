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

    static final class Rule {
        final Power power;
        final Strategy strategy;
        final int forcedSoc;
        final boolean thresholdEnabled;
        final int minBattery;
        final Regen regeneration;
        Rule(Power p, Strategy s, int target, boolean condition, int minimum, Regen r){
            power=p==null?Power.HEV:p;
            strategy=s==null?Strategy.SMART:s;
            forcedSoc=clamp(target,0,100);
            thresholdEnabled=condition;
            minBattery=clamp(minimum,0,100);
            regeneration=r==null?Regen.MEDIUM:r;
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
                    " · batería > "+requested.minBattery+" %":"")+
                " · SOLO SUGERENCIA";
        }
    }
    static Decision evaluate(Road road,Rule rule,double batteryPercent){
        if(rule==null)return new Decision(road,null,false,false,
            "No hay perfil configurado");
        if(rule.thresholdEnabled){
            if(!Double.isFinite(batteryPercent)||batteryPercent<0||
                batteryPercent>100)
                return new Decision(road,rule,false,false,
                    "SOC OEM desconocido; no se cumple condición");
            // User wants greater than configured threshold, not >=.
            if(batteryPercent<=rule.minBattery)
                return new Decision(road,rule,false,true,
                    "SOC "+String.format(java.util.Locale.ROOT,"%.1f",batteryPercent)+
                    " % no supera "+rule.minBattery+" %");
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
