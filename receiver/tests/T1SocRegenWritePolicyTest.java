package ar.com.rango5gps.drivelabauto.receiver;

public final class T1SocRegenWritePolicyTest {
    private static void check(boolean pass,String why){
        if(!pass)throw new AssertionError(why);
    }
    public static void main(String[] args){
        check(T1SocRegenWritePolicy.accepted(1040,2),"SMART 1040");
        check(T1SocRegenWritePolicy.accepted(1040,4),"FORCED 1040");
        check(!T1SocRegenWritePolicy.accepted(1040,3),"unverified AUTO");
        check(!T1SocRegenWritePolicy.accepted(1040,0),"invalid strategy");
        for(int soc:new int[]{20,30,40,48,50,56,60,64,70,72})
            check(T1SocRegenWritePolicy.accepted(1041,soc),"SOC "+soc);
        for(int soc:new int[]{-1,0,19,21,37,80,100})
            check(!T1SocRegenWritePolicy.accepted(1041,soc),"invalid SOC "+soc);
        for(int r=1;r<=3;r++)
            check(T1SocRegenWritePolicy.accepted(1046,r),"regen "+r);
        check(!T1SocRegenWritePolicy.accepted(1046,0),"invalid regen");
        check(!T1SocRegenWritePolicy.accepted(1046,4),"invalid regen 4");
        check(!T1SocRegenWritePolicy.accepted(1043,2),"wrong channel");
        check(T1SocRegenWritePolicy.testAlternative(1040,2)==4,"mode test");
        check(T1SocRegenWritePolicy.testAlternative(1040,4)==2,"mode restore");
        check(T1SocRegenWritePolicy.testAlternative(1041,56)==64,"SOC test");
        check(T1SocRegenWritePolicy.testAlternative(1041,64)==56,"SOC restore");
        check(T1SocRegenWritePolicy.testAlternative(1046,1)==2,"regen test");
        check(T1SocRegenWritePolicy.testAlternative(1046,3)==1,"regen test2");
        check(T1SocRegenWritePolicy.testAlternative(1040,-1)==-1,"sentinel deny");
        check(T1SocRegenWritePolicy.parked(1,0),"parked");
        check(!T1SocRegenWritePolicy.parked(4,0),"not parked D");
        check(!T1SocRegenWritePolicy.parked(1,1),"not parked moving");
        check(T1SocRegenWritePolicy.regenDriveEligible(1,0),"P/0 allowed");
        check(T1SocRegenWritePolicy.regenDriveEligible(4,83),"D/83 allowed");
        check(T1SocRegenWritePolicy.regenDriveEligible(4,0),"D/0 allowed");
        check(T1SocRegenWritePolicy.regenDriveEligible(4,160),"D/160 allowed");
        check(!T1SocRegenWritePolicy.regenDriveEligible(4,161),"speed too high");
        check(!T1SocRegenWritePolicy.regenDriveEligible(3,40),"R denied");
        check(!T1SocRegenWritePolicy.regenDriveEligible(2,40),"N denied");
        check(!T1SocRegenWritePolicy.regenDriveEligible(1,15),"P moving denied");
        check(!T1SocRegenWritePolicy.regenDriveEligible(4,Double.NaN),"bad speed");
        check(!T1SocRegenWritePolicy.regenDriveEligible(-1,0),"missing gear");
        T1OptimizedModePolicy.Rule forced=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,64,false,30,
            T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets desired=
            T1SocRegenWritePolicy.desired(forced,55);
        check(desired.valid(),"valid preset");
        check(desired.strategy==4&&desired.forcedSoc==64&&desired.regen==3,
            "full forced");
        check(T1SocRegenWritePolicy.desired(
            new T1OptimizedModePolicy.Rule(T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.FORCED,37,false,30,
                T1OptimizedModePolicy.Regen.LOW),50).valid()==false,
            "unverified SOC preset denied");
        T1OptimizedModePolicy.Rule conditional=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,56,true,50,
            T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets fallback=
            T1SocRegenWritePolicy.desired(conditional,20);
        check(fallback.valid()&&fallback.strategy==2&&fallback.regen==2
            &&!fallback.needsTarget(),"safe fallback");
        System.out.println("OK: SOC / regen policy (D movement permitted, P, presets, fallback)");
    }
}