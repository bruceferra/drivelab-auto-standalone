package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Standalone decision policy for the proposed reverse-mirror feature.
 *
 * Hardware-independent FSM for optional mirror parking automation. Raw gear
 * mapping is confirmed by driver (P1/R2/N3/D4). Speed units and pulse timing
 * still require verification with physical tests; OEM commands belong in a
 * separate bounded controller, not here.
 *
 * Mirrors are relative to the driver's personally adjusted D pose.
 * One R maneuver tilts once; R/non-R shuffling at parking speed does nothing.
 * Restore also works in P after 1200 ms fully stopped, not only D 800 ms.
 * Speed is checked for plausibility and PARK must be stopped; returning to normal view
 * must not wait for acceleration or braking. N never triggers restore.
 * No automatic homing against hard stops.
 */
public final class MirrorParkingPolicy {
    public enum Gear { P, R, N, D, UNKNOWN }
    public enum Phase { NORMAL, TILTING, TILTED, RESTORING, UNKNOWN_POSITION }
    public enum Action { NONE, TILT_ON_R, RESTORE_AFTER_NON_R_SPEED }
    // Legacy threshold is deliberately no longer used to gate D restoration.
    public static final double RETURN_THRESHOLD_KMH=10.0;
    public static final long RETURN_DWELL_MS=800;
    public static final long RETURN_P_DWELL_MS=1200;
    public static final int MAX_PULSES_PER_SIDE=20;

    private final int requestedLeft,requestedRight;
    private Phase phase=Phase.NORMAL;
    private Gear previousGear=Gear.UNKNOWN;
    private long restoreSince=-1;
    private Gear restoreGear=Gear.UNKNOWN;
    private int leftIssued=0,rightIssued=0;

    public MirrorParkingPolicy(int steps){this(steps,steps);}
    public MirrorParkingPolicy(int left,int right){
        if(left<1||left>MAX_PULSES_PER_SIDE||
                right<1||right>MAX_PULSES_PER_SIDE)
            throw new IllegalArgumentException("Pasos fuera del rango 1-20");
        requestedLeft=left;requestedRight=right;
    }
    public Phase phase(){return phase;}
    public int requestedPulses(){return requestedLeft;}
    public int requestedLeft(){return requestedLeft;}
    public int requestedRight(){return requestedRight;}
    public int leftIssued(){return leftIssued;}
    public int rightIssued(){return rightIssued;}

    /**
     * Observe interpreted gear and verified speed; caller must validate OEM
     * mapping and that the units really are km/h beforehand.
     * The first sample NEVER triggers motion, even if already in R.
     */
    public Action observe(Gear gear,double speedKmh,long elapsedMs,boolean armed){
        if(gear==null)gear=Gear.UNKNOWN;
        if(!armed || !Double.isFinite(speedKmh) || speedKmh<0 ||
                speedKmh>250 || elapsedMs<0){
            restoreSince=-1;restoreGear=Gear.UNKNOWN;
            previousGear=Gear.UNKNOWN; // rearm only after fresh, valid gear samples
            return Action.NONE;
        }
        Gear was=previousGear;
        previousGear=gear;
        if(phase==Phase.NORMAL){
            if(was!=Gear.UNKNOWN && was!=Gear.R && gear==Gear.R &&
                    speedKmh<=0.5){
                phase=Phase.TILTING;
                leftIssued=rightIssued=0;
                return Action.TILT_ON_R;
            }
            return Action.NONE;
        }
        if(phase==Phase.TILTED){
            // The vehicle can remain parked indefinitely after an R maneuver;
            // restoring only in D leaves mirrors tilted during long P parking.
            // Stable D is valid at any plausible speed; stable P only at 0 km/h.
            boolean canRestore=gear==Gear.D||
                (gear==Gear.P&&speedKmh<=0.5);
            if(canRestore){
                if(restoreGear!=gear||restoreSince<0){
                    restoreGear=gear;
                    restoreSince=elapsedMs;
                }
                long dwell=gear==Gear.P?RETURN_P_DWELL_MS:RETURN_DWELL_MS;
                if(elapsedMs-restoreSince>=dwell){
                    phase=Phase.RESTORING;
                    restoreSince=-1;restoreGear=Gear.UNKNOWN;
                    return Action.RESTORE_AFTER_NON_R_SPEED;
                }
            }else{
                restoreSince=-1;restoreGear=Gear.UNKNOWN;
            }
        }
        return Action.NONE;
    }

    /** Record only completed OEM commands, not presumed physical distance. */
    public void completeTilt(int leftCommands,int rightCommands){
        if(phase!=Phase.TILTING)throw new IllegalStateException("No se inició inclinación");
        if(leftCommands<0||rightCommands<0||
                leftCommands>requestedLeft||rightCommands>requestedRight)
            throw new IllegalArgumentException("Recuento inválido");
        leftIssued=leftCommands;rightIssued=rightCommands;
        phase=(leftCommands==requestedLeft && rightCommands==requestedRight)?
            Phase.TILTED:Phase.UNKNOWN_POSITION;
    }

    /**
     * We may return to NORMAL only if all previously issued commands were
     * countered; Binder success alone is not physical-position proof.
     */
    public void completeRestore(int leftCommands,int rightCommands){
        if(phase!=Phase.RESTORING)
            throw new IllegalStateException("No se inició retorno");
        phase=(leftCommands==leftIssued&&rightCommands==rightIssued)?
            Phase.NORMAL:Phase.UNKNOWN_POSITION;
        if(phase==Phase.NORMAL)leftIssued=rightIssued=0;
    }
    /**
     * After an explicit stationary manual RECUPERAR, resume AUTO only when
     * ALL planned release-confirmed commands for both mirrors completed.
     * This does not certify physical angle; the driver still owns the D pose.
     */
    public static boolean completedManualRecovery(int issuedLeft,int issuedRight,
                                                   int requestedLeft,int requestedRight){
        return requestedLeft>0 && requestedRight>0 &&
            issuedLeft==requestedLeft && issuedRight==requestedRight;
    }

    /** Any interrupted or uncontrolled movement invalidates reference. */
    public void invalidate(){
        phase=Phase.UNKNOWN_POSITION;
        restoreSince=-1;restoreGear=Gear.UNKNOWN;
    }
}
