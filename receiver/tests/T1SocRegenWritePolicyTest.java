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
        int[] menu=T1SocRegenWritePolicy.forcedSocPresets();
        check(menu.length==10,"ten OEM values in dropdown");
        check(menu[0]==20&&menu[3]==48&&menu[5]==56&&
              menu[7]==64&&menu[9]==72,"OEM non-5-step values preserved");
        for(int soc:menu)
            check(T1SocRegenWritePolicy.accepted(1041,soc),
                "every forced SOC menu choice is writable policy "+soc);
        for(int soc:new int[]{20,30,40,48,50,56,60,64,70,72}){
            boolean visible=false;
            for(int option:menu)if(soc==option)visible=true;
            check(visible,"supported OEM SOC is visible "+soc);
        }
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(48)==3,
            "existing 48% stays 48%");
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(56)==5,
            "existing 56% stays 56%");
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(59)==6,
            "legacy unsupported 59 maps to 60 on explicit save");
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(100)==9,
            "legacy unsupported 100 maps to allowed 72 on explicit save");
        menu[0]=999;
        check(T1SocRegenWritePolicy.accepted(1041,20),
            "UI mutation of clone must never change writer allowlist");
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
        T1OptimizedModePolicy.Rule bounded=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,56,
            true,10,true,40,T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets inRange=
            T1SocRegenWritePolicy.desired(bounded,25);
        check(inRange.valid()&&inRange.strategy==4&&
            inRange.forcedSoc==56&&inRange.regen==3,
            "in-range SOC applies FORZADO and selected regeneration");
        T1SocRegenWritePolicy.Targets aboveRange=
            T1SocRegenWritePolicy.desired(bounded,45);
        check(aboveRange.valid()&&aboveRange.strategy==2&&
            aboveRange.regen==2&&aboveRange.fallback,
            "above SOC maximum must fall back to SMART");
        T1SocRegenWritePolicy.Targets belowRange=
            T1SocRegenWritePolicy.desired(bounded,5);
        check(belowRange.valid()&&belowRange.strategy==2&&
            belowRange.fallback,
            "below SOC minimum must fall back to SMART");
        T1SocRegenWritePolicy.Targets unknownSoc=
            T1SocRegenWritePolicy.desired(bounded,Double.NaN);
        check(unknownSoc.fallback&&unknownSoc.strategy==2,
            "unknown OEM SOC must not apply a conditioned FORZADO profile");
        System.out.println("OK: SOC / regen policy (D movement permitted, P, presets, fallback)");
    }
}