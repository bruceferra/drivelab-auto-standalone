package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Pure JVM policy: a GPS road category is NOT an applied optimized mode.
 * Green UI is permitted only after all three fresh road votes, all battery
 * conditions, and both OEM power (1043) and SOC/regen (1040/41/46) verification.
 * Fallback HEV/SMART when the battery condition fails is NOT the road profile.
 */
final class T1OptimizedAppliedRoadPolicy {
    enum Reason {
        APPLIED, GPS_UNKNOWN, VERIFYING_ROAD, SOC_CONDITION_NOT_MET,
        OEM_POWER_PENDING, OEM_SOC_REGEN_PENDING
    }
    static Reason evaluate(T1OptimizedModePolicy.Road road,
                           T1OptimizedModePolicy.Rule rule,double battery,
                           boolean threeRoadMatches,
                           boolean oem1043Confirmed,boolean socRegenConfirmed){
        if(road==null||road==T1OptimizedModePolicy.Road.UNKNOWN)
            return Reason.GPS_UNKNOWN;
        if(!threeRoadMatches)return Reason.VERIFYING_ROAD;
        if(rule==null||
           !T1OptimizedModePolicy.evaluate(road,rule,battery).conditionSatisfied)
            return Reason.SOC_CONDITION_NOT_MET;
        if(!oem1043Confirmed)return Reason.OEM_POWER_PENDING;
        if(!socRegenConfirmed)return Reason.OEM_SOC_REGEN_PENDING;
        return Reason.APPLIED;
    }
    static String label(Reason reason){
        if(reason==Reason.GPS_UNKNOWN)return "VÍA SIN IDENTIFICAR";
        if(reason==Reason.VERIFYING_ROAD)return "MODO · VERIFICANDO";
        if(reason==Reason.SOC_CONDITION_NOT_MET)return "SOC NO CUMPLE";
        if(reason==Reason.OEM_POWER_PENDING)return "MODO · OEM PENDIENTE";
        if(reason==Reason.OEM_SOC_REGEN_PENDING)return "SOC/REGEN PENDIENTE";
        return "MODO APLICADO";
    }
    private T1OptimizedAppliedRoadPolicy(){}
}
