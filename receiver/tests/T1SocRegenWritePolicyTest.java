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
        check(menu.length==6,"six multiples of ten in dropdown");
        check(menu[0]==20&&menu[1]==30&&menu[2]==40&&
              menu[3]==50&&menu[4]==60&&menu[5]==70,
              "SOC forced dropdown contains only 20 30 40 50 60 70");
        for(int soc:menu)
            check(T1SocRegenWritePolicy.accepted(1041,soc),
                "every forced SOC menu choice is writable policy "+soc);
        for(int soc:new int[]{20,30,40,50,60,70}){
            boolean visible=false;
            for(int option:menu)if(soc==option)visible=true;
            check(visible,"supported OEM SOC is visible "+soc);
        }
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(48)==3,
            "legacy 48% maps to 50 only after explicit save");
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(56)==4,
            "legacy 56% maps to 60 only after explicit save");
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(59)==4,
            "legacy unsupported 59 maps to 60 on explicit save");
        check(T1SocRegenWritePolicy.nearestForcedSocPresetIndex(100)==5,
            "legacy unsupported 100 maps to max 70 on explicit save");
        menu[0]=999;
        check(T1SocRegenWritePolicy.accepted(1041,20),
            "UI mutation of clone must never change writer allowlist");
        for(int soc:new int[]{-1,0,19,21,37,48,56,64,72,80,100})
            check(!T1SocRegenWritePolicy.accepted(1041,soc),"invalid SOC "+soc);
        for(int r=1;r<=3;r++)
            check(T1SocRegenWritePolicy.accepted(1046,r),"regen "+r);
        check(!T1SocRegenWritePolicy.accepted(1046,0),"invalid regen");
        check(!T1SocRegenWritePolicy.accepted(1046,4),"invalid regen 4");
        check(!T1SocRegenWritePolicy.accepted(1043,2),"wrong channel");
        check(T1SocRegenWritePolicy.testAlternative(1040,2)==4,"mode test");
        check(T1SocRegenWritePolicy.testAlternative(1040,4)==2,"mode restore");
        check(T1SocRegenWritePolicy.testAlternative(1041,20)==-1,
            "no alternate target is allowed for experimental SOC");
        check(T1SocRegenWritePolicy.testAlternative(1041,56)==-1,
            "invalid 56 must never become a rollback target");
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
            T1OptimizedModePolicy.Strategy.FORCED,60,false,30,
            T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets desired=
            T1SocRegenWritePolicy.desired(forced,55);
        check(desired.valid(),"valid preset");
        check(desired.strategy==4&&desired.forcedSoc==60&&desired.regen==3,
            "full forced");
        check(T1SocRegenWritePolicy.desired(
            new T1OptimizedModePolicy.Rule(T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.FORCED,37,false,30,
                T1OptimizedModePolicy.Regen.LOW),50).valid()==false,
            "unverified SOC preset denied");
        T1OptimizedModePolicy.Rule conditional=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,60,true,50,
            T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets fallback=
            T1SocRegenWritePolicy.desired(conditional,20);
        check(fallback.valid()&&fallback.strategy==2&&fallback.regen==2
            &&!fallback.needsTarget(),"safe fallback");
        T1OptimizedModePolicy.Rule bounded=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,60,
            true,10,true,40,T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets inRange=
            T1SocRegenWritePolicy.desired(bounded,25);
        check(inRange.valid()&&inRange.strategy==4&&
            inRange.forcedSoc==60&&inRange.regen==3,
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
        check(T1SocRegenWritePolicy.mustDisarmForcedBeforeEv(1,4),
            "only EV 1043=1 needs SMART before a previous FORZADO 1040=4");
        check(!T1SocRegenWritePolicy.mustDisarmForcedBeforeEv(1,2),
            "EV already in SMART must not write 1040 again");
        check(!T1SocRegenWritePolicy.mustDisarmForcedBeforeEv(3,4),
            "EV MAX autonomously clears FORZADO; no pre-SMART write");
        check(!T1SocRegenWritePolicy.mustDisarmForcedBeforeEv(2,4),
            "HEV FORZADO must not be disabled to remain in HEV");
        check(T1SocRegenWritePolicy.electricPowerRequiresSmart(
            T1OptimizedModePolicy.Power.EV),"EV profile must stay SMART");
        check(T1SocRegenWritePolicy.electricPowerRequiresSmart(
            T1OptimizedModePolicy.Power.EV_MAX),"EV MAX profile must not restore FORZADO");
        check(!T1SocRegenWritePolicy.electricPowerRequiresSmart(
            T1OptimizedModePolicy.Power.HEV),"HEV allows FORZADO");
        T1OptimizedModePolicy.Rule oldEvForced=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.EV,
            T1OptimizedModePolicy.Strategy.FORCED,30,true,20,
            T1OptimizedModePolicy.Regen.LOW);
        T1SocRegenWritePolicy.Targets electric=
            T1SocRegenWritePolicy.desired(oldEvForced,25);
        check(electric.valid()&&electric.strategy==2&&
            !electric.needsTarget()&&electric.forcedSoc==-1&&
            electric.regen==1,
            "legacy EV FORZADO profile must normalize to SMART, no 1041 write");
        T1SocRegenWritePolicy.Targets tooLow=
            T1SocRegenWritePolicy.desired(oldEvForced,18);
        check(tooLow.fallback&&tooLow.strategy==2,
            "EV conditional min 20% must not request EV at battery 18%");
        T1OptimizedModePolicy.Rule oldEvMaxForced=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.EV_MAX,
            T1OptimizedModePolicy.Strategy.FORCED,50,false,0,
            T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets maxEffective=
            T1SocRegenWritePolicy.desired(oldEvMaxForced,35);
        check(maxEffective.valid()&&maxEffective.strategy==2&&
            !maxEffective.needsTarget()&&maxEffective.regen==3,
            "EV MAX OEM clears forced; optimizer must not turn FORZADO back on");
        check(T1OptimizedModePolicy.evaluate(
            T1OptimizedModePolicy.Road.CITY,oldEvForced,25).summary()
            .contains("SMART"),"EV preview must reflect effective SMART");
        System.out.println("OK: EV (not EV MAX) pre-SMART; EV and EV MAX never re-force; SOC condition honored");
    }
}