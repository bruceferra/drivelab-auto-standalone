package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Strict OEM write target allowlist for PHEV SOC strategy, target and regen.
 * Static read values are not proof of a writable contract. Individual writes
 * are unlocked only after a user-requested P/0 test and successful restoration.
 */
final class T1SocRegenWritePolicy {
    static final int STRATEGY=1040,TARGET=1041,REGEN=1046;
    static final int SMART=2,FORCED=4;
    static final int REGEN_LOW=1,REGEN_MEDIUM=2,REGEN_HIGH=3;
    private static final int[] SOC_PRESETS={20,30,40,48,50,56,60,64,70,72};

    static boolean accepted(int id,int value){
        if(id==STRATEGY)return value==SMART||value==FORCED;
        if(id==REGEN)return value>=REGEN_LOW&&value<=REGEN_HIGH;
        if(id==TARGET){
            for(int n:SOC_PRESETS)if(n==value)return true;
        }
        return false;
    }
    static int testAlternative(int id,int current){
        if(!accepted(id,current))return -1;
        if(id==STRATEGY)return current==SMART?FORCED:SMART;
        if(id==REGEN)return current==REGEN_LOW?REGEN_MEDIUM:REGEN_LOW;
        // Real OEM presets from the APK; never guess a percentage code.
        if(id==TARGET)return current==56?64:56;
        return -1;
    }
    static final class Targets{
        final int strategy,forcedSoc,regen;
        final boolean fallback;
        Targets(int s,int t,int r,boolean fallback){
            strategy=s;forcedSoc=t;regen=r;this.fallback=fallback;
        }
        boolean valid(){
            return accepted(STRATEGY,strategy)&&accepted(REGEN,regen)&&
                (strategy!=FORCED||accepted(TARGET,forcedSoc));
        }
        boolean needsTarget(){return strategy==FORCED;}
        String summary(){
            return (strategy==SMART?"SMART":"FORZADO "+forcedSoc+" %")+
                " · regeneración "+regen+(fallback?" · HEV respaldo":"");
        }
    }
    static Targets desired(T1OptimizedModePolicy.Rule rule,double battery){
        T1OptimizedModePolicy.Decision decision=
            T1OptimizedModePolicy.evaluate(T1OptimizedModePolicy.Road.CITY,
                rule,battery);
        if(!decision.conditionSatisfied)
            return new Targets(SMART,-1,REGEN_MEDIUM,true);
        int strategy=rule.strategy==T1OptimizedModePolicy.Strategy.FORCED?
            FORCED:SMART;
        int regen=rule.regeneration==T1OptimizedModePolicy.Regen.LOW?
            REGEN_LOW:rule.regeneration==T1OptimizedModePolicy.Regen.HIGH?
            REGEN_HIGH:REGEN_MEDIUM;
        return new Targets(strategy,rule.forcedSoc,regen,false);
    }
    static boolean parked(int gear,double speed){
        return gear==1&&Double.isFinite(speed)&&Math.abs(speed)<0.05;
    }
    /**
     * Regeneration is an in-driving OEM setting: unlike a one-shot parked
     * SOC calibration, the actual profile must follow a verified road change
     * in D. Gear 4 = D, verified 2148 finite 0..160 km/h. P is valid only
     * at zero. Never in R, N, unknown gear or absent speed telemetry.
     */
    static boolean regenDriveEligible(int gear,double speed){
        return Double.isFinite(speed)&&speed>=0&&speed<=160&&
            (gear==4||parked(gear,speed));
    }
    private T1SocRegenWritePolicy(){}
}