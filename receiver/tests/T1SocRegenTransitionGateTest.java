package ar.com.rango5gps.drivelabauto.receiver;

/** A single 3-GPS CITY confirmation covers both 1043 and SOC/regeneration.
 * Do not ask another 1/3,2/3,3/3 after EV is OEM-confirmed. */
public final class T1SocRegenTransitionGateTest {
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }

    public static void main(String[] args){
        T1OptimizedModePolicy.Road cityRoad=T1OptimizedModePolicy.Road.CITY;
        T1OptimizedModePolicy.Rule cityRule=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.EV,
                T1OptimizedModePolicy.Strategy.SMART,50,
                true,20,T1OptimizedModePolicy.Regen.LOW);
        T1OptimizedAutoGate road=new T1OptimizedAutoGate();
        T1SocRegenTransitionGate aux=new T1SocRegenTransitionGate();
        T1SocRegenWritePolicy.Targets city=
            T1SocRegenWritePolicy.desired(cityRule,30);
        check(city.valid()&&city.strategy==2&&city.regen==1,
            "city desired SMART + regen low");
        for(int i=1;i<=2;i++){
            T1OptimizedAutoGate.Decision x=road.examine(
                cityRoad,cityRule,30,0,1,2,i*10000,true,true);
            check(x.reason==T1OptimizedAutoGate.Reason.WAIT_STABLE,
                "initial road verification "+i+"/3");
            check(aux.examine(city,false,false).reason==
                T1SocRegenTransitionGate.Reason.UNAVAILABLE,
                "aux not eligible before GPS 3/3 and 1043 readback");
        }
        T1OptimizedAutoGate.Decision third=road.examine(
            cityRoad,cityRule,30,0,1,2,30000,true,true);
        check(third.ready()&&third.target==1,
            "third trusted GPS sample authorizes 1043 EV");
        road.recordCommand(30000,1);
        check(aux.examine(city,false,false).reason==
            T1SocRegenTransitionGate.Reason.UNAVAILABLE,
            "EV was requested but not OEM-confirmed yet: no aux");
        T1OptimizedAutoGate.Decision evConfirmed=road.examine(
            cityRoad,cityRule,30,0,1,1,40000,true,true);
        check(evConfirmed.reason==T1OptimizedAutoGate.Reason.SAME_MODE,
            "1043=EV verified, road still 3/3");
        T1SocRegenTransitionGate.Decision firstAux=
            aux.examine(city,true,false);
        check(firstAux.ready(),
            "after EV OEM confirmation, regen LOW must be ready IMMEDIATELY");
        check(firstAux.reason!=T1SocRegenTransitionGate.Reason.UNAVAILABLE,
            "no second 1/3 for regen");
        aux.recordConfirmed(city);
        for(int i=0;i<50;i++){
            check(aux.examine(city,true,false).reason==
                T1SocRegenTransitionGate.Reason.APPLIED,
                "confirmed CITY/regen never revalidate or rewrite in stable road");
        }

        // New road needs its OWN three GPS observations; no aux command
        // until the propulsion mode has been confirmed by 1043.
        T1OptimizedModePolicy.Road motorway=
            T1OptimizedModePolicy.Road.MOTORWAY;
        T1OptimizedModePolicy.Rule highwayRule=
            new T1OptimizedModePolicy.Rule(
                T1OptimizedModePolicy.Power.HEV,
                T1OptimizedModePolicy.Strategy.FORCED,20,
                false,0,T1OptimizedModePolicy.Regen.HIGH);
        T1SocRegenWritePolicy.Targets highway=
            T1SocRegenWritePolicy.desired(highwayRule,30);
        check(aux.examine(highway,false,false).reason==
            T1SocRegenTransitionGate.Reason.UNAVAILABLE,
            "new motorway not eligible before 3 GPS samples");
        check(!road.examine(motorway,highwayRule,30,0,1,1,
             130000,true,true).ready(),"motorway first GPS");
        check(!road.examine(motorway,highwayRule,30,0,1,1,
             140000,true,true).ready(),"motorway second GPS");
        check(road.examine(motorway,highwayRule,30,0,1,1,
             150000,true,true).ready(),"motorway third GPS");
        road.recordCommand(150000,2);
        check(road.examine(motorway,highwayRule,30,0,1,2,
             160000,true,true).reason==
             T1OptimizedAutoGate.Reason.SAME_MODE,
             "motorway 1043 HEV confirmed");
        check(aux.examine(highway,true,false).ready(),
             "new forced motorway SOC/regen ready without extra 3 samples");
        aux.recordConfirmed(highway);
        check(aux.examine(highway,true,false).reason==
             T1SocRegenTransitionGate.Reason.APPLIED,
             "confirmed motorway no repeated aux writes");
        T1SocRegenWritePolicy.Targets target30=
            new T1SocRegenWritePolicy.Targets(4,30,3,false);
        check(aux.examine(target30,true,false).ready(),
             "changing forced SOC from 20 to 30 requires no GPS revalidation");
        aux.recordConfirmed(target30);
        check(!aux.examine(target30,true,false).ready(),
             "new configuration latched after OEM confirmation");
        check(aux.examine(city,true,true).reason==
             T1SocRegenTransitionGate.Reason.OEM_MATCHES,
             "skip write if current OEM already matches city profile");
        check(!aux.examine(city,true,false).ready(),
             "no extra writes after OEM match");
        aux.reset();
        check(aux.currentConfirmed()==null,
             "disabled profile invalidates applied state");
        check(aux.examine(city,false,false).reason==
             T1SocRegenTransitionGate.Reason.UNAVAILABLE,
             "disabled road cannot apply regen");
        check(aux.examine(city,true,false).ready(),
             "re-enabled block requires road verification by caller, "+
             "then only one aux decision");
        check(!new T1SocRegenWritePolicy.Targets(4,56,2,false).valid(),
             "SOC 56 is a readback code, never an allowed forced target");
        System.out.println(
            "OK: one GPS 3/3 for EV and regen; no duplicate 1/2/3; "+
            "each new profile directly verified by OEM, no blind retries");
    }
}
