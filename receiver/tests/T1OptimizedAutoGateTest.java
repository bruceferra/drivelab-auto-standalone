package ar.com.rango5gps.drivelabauto.receiver;

/** Regression: auto-drive never acts on one map match, UNKNOWN, invalid
 * mode/speed/SOC, or rapid road oscillation. No OEM hardware used. */
public final class T1OptimizedAutoGateTest {
    static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    static T1OptimizedAutoGate.Decision read(T1OptimizedAutoGate g,
            T1OptimizedModePolicy.Road road,
            T1OptimizedModePolicy.Rule rule,double soc,int mode,
            long ms,boolean location){
        return g.examine(road,rule,soc,45,4,mode,ms,location,true);
    }
    public static void main(String[] args){
        final T1OptimizedModePolicy.Road city=
            T1OptimizedModePolicy.Road.CITY;
        final T1OptimizedModePolicy.Road highway=
            T1OptimizedModePolicy.Road.MOTORWAY;
        final T1OptimizedModePolicy.Road unknown=
            T1OptimizedModePolicy.Road.UNKNOWN;
        T1OptimizedModePolicy.Rule ev=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.EV,
            T1OptimizedModePolicy.Strategy.SMART,50,true,30,
            T1OptimizedModePolicy.Comparison.GREATER_OR_EQUAL,
            T1OptimizedModePolicy.Regen.HIGH);
        T1OptimizedModePolicy.Rule hev=new T1OptimizedModePolicy.Rule(
            T1OptimizedModePolicy.Power.HEV,
            T1OptimizedModePolicy.Strategy.FORCED,50,false,0,
            T1OptimizedModePolicy.Regen.MEDIUM);
        T1OptimizedAutoGate gate=new T1OptimizedAutoGate();
        check(!read(gate,city,ev,80,2,0,true).ready(),
            "first GPS city sample must not change OEM");
        check(!read(gate,city,ev,80,2,10000,true).ready(),
            "second road reading insufficient");
        T1OptimizedAutoGate.Decision cmd=
            read(gate,city,ev,80,2,20000,true);
        check(cmd.ready()&&cmd.target==1,"third city EV");
        gate.recordCommand(20000,1);
        check(!read(gate,city,ev,80,1,30000,true).ready(),
            "already EV, never write same target");
        check(!read(gate,highway,hev,80,1,40000,true).ready(),
            "highway candidate must stabilize");
        check(!read(gate,highway,hev,80,1,50000,true).ready(),
            "second highway candidate");
        T1OptimizedAutoGate.Decision soon=
            read(gate,highway,hev,80,1,60000,true);
        check(soon.reason==T1OptimizedAutoGate.Reason.COOLDOWN,
            "90-second command gap must win");
        cmd=read(gate,highway,hev,80,1,120000,true);
        check(cmd.ready()&&cmd.target==2,"confirmed HEV switch after cooldown");
        gate.recordCommand(120000,2);
        check(read(gate,unknown,hev,80,1,150000,true).reason==
            T1OptimizedAutoGate.Reason.UNKNOWN,
            "UNKNOWN cannot trigger fallback HEV");
        check(read(gate,city,ev,80,2,160000,false).reason==
            T1OptimizedAutoGate.Reason.NO_LOCATION,
            "lost GPS cannot trigger auto mode");
        check(!read(gate,city,ev,80,2,170000,true).ready(),
            "new stable sequence required after GPS loss");
        check(!read(gate,city,ev,80,2,180000,true).ready(),
            "second after GPS return");
        check(read(gate,city,ev,Double.NaN,2,220000,true).reason==
            T1OptimizedAutoGate.Reason.NO_BATTERY,
            "missing SOC must fail closed");
        T1OptimizedAutoGate low=new T1OptimizedAutoGate();
        read(low,city,ev,20,1,0,true);
        read(low,city,ev,20,1,10000,true);
        cmd=read(low,city,ev,20,1,20000,true);
        check(cmd.ready()&&cmd.target==2,
            "SOC below condition chooses HEV fallback only");
        T1OptimizedAutoGate invalid=new T1OptimizedAutoGate();
        invalid.examine(city,ev,80,170,4,2,0,true,true);
        invalid.examine(city,ev,80,170,4,2,10000,true,true);
        check(invalid.examine(city,ev,80,170,4,2,20000,true,true).reason==
            T1OptimizedAutoGate.Reason.NO_OEM,
            "unsafe speed cannot command");
        // Regression: after successful 1043 EV request the city was
        // already confirmed 3/3. A second OEM read MUST NOT start over
        // at 1/3, or delay a later manual/OEM correction in the same road.
        T1OptimizedAutoGate steady=new T1OptimizedAutoGate();
        read(steady,city,ev,80,2,0,true);
        read(steady,city,ev,80,2,10000,true);
        check(read(steady,city,ev,80,2,20000,true).ready(),
            "city first transition confirmed");
        steady.recordCommand(20000,1);
        check(read(steady,city,ev,80,1,30000,true).reason==
            T1OptimizedAutoGate.Reason.SAME_MODE,
            "city stable after write");
        check(read(steady,city,ev,80,2,130000,true).ready(),
            "same confirmed city must not revalidate three new GPS fixes");
        System.out.println("OK: automatic OEM 1043 gate, SOC conditions, stable city latch, anti-oscillation");
    }
}
