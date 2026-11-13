package ar.com.rango5gps.drivelabauto.receiver;

/** Regression: SOC 1/3,2/3,3/3 only on a NEW desired profile. */
public final class T1SocRegenTransitionGateTest {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        T1SocRegenTransitionGate g=new T1SocRegenTransitionGate();
        T1SocRegenWritePolicy.Targets city=
            new T1SocRegenWritePolicy.Targets(2,-1,2,false);
        T1SocRegenWritePolicy.Targets motorway=
            new T1SocRegenWritePolicy.Targets(4,20,3,false);
        T1SocRegenWritePolicy.Targets motorway30=
            new T1SocRegenWritePolicy.Targets(4,30,3,false);
        T1SocRegenTransitionGate.Decision d=g.examine(city,true,false);
        check(d.reason==T1SocRegenTransitionGate.Reason.WAIT_STABLE&&
              d.matches==1,"city new 1/3");
        d=g.examine(city,true,false);
        check(d.reason==T1SocRegenTransitionGate.Reason.WAIT_STABLE&&
              d.matches==2,"city new 2/3");
        d=g.examine(city,true,false);
        check(d.ready()&&d.matches==3,"city new 3/3");
        g.recordConfirmed(city);
        for(int i=0;i<50;i++){
            d=g.examine(city,true,false); // Raw OEM getter may be stale.
            check(d.reason==T1SocRegenTransitionGate.Reason.APPLIED,
                "same city must remain latched, not revalidate 1/3");
            check(!d.ready(),"never retry same profile in stable city");
        }
        d=g.examine(motorway,true,false);
        check(d.matches==1,"first real motorway transition 1/3");
        check(g.examine(motorway,true,false).matches==2,"motorway 2/3");
        check(g.examine(motorway,true,false).ready(),"motorway 3/3");
        g.recordConfirmed(motorway);
        for(int i=0;i<20;i++)
            check(!g.examine(motorway,true,false).ready(),
                "motorway 1041 raw getter cannot cause re-write");
        d=g.examine(motorway30,true,false);
        check(d.matches==1,"new configured SOC 30 1/3");
        check(g.examine(motorway30,true,false).matches==2,
            "SOC 30 2/3");
        check(g.examine(motorway30,true,false).ready(),"SOC 30 3/3");
        g.recordConfirmed(motorway30);
        check(!g.examine(motorway30,true,false).ready(),
            "new config locked after confirmation");
        check(g.examine(city,true,true).reason==
            T1SocRegenTransitionGate.Reason.OEM_MATCHES,
            "no write if the OEM already matches the newly requested profile");
        check(g.examine(city,true,false).reason==
            T1SocRegenTransitionGate.Reason.APPLIED,
            "OEM-matched mode is latched");
        T1SocRegenTransitionGate interrupted=new T1SocRegenTransitionGate();
        check(interrupted.examine(city,true,false).matches==1,
            "new decision 1/3");
        check(interrupted.examine(city,false,false).reason==
            T1SocRegenTransitionGate.Reason.UNAVAILABLE,
            "unsafe/unknown telemetry interrupts pending transition");
        check(interrupted.examine(city,true,false).matches==1,
            "transient unsafe condition must restart pending transition");
        check(!new T1SocRegenWritePolicy.Targets(4,56,2,false).valid(),
            "56 remains forbidden as a forced target");
        System.out.println("OK: stable city and motorway SOC are latched; "+
            "1/3 only on new configured transition, no repeated writes");
    }
}
