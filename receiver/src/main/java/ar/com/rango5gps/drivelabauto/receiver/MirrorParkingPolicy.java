package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Standalone decision policy for the proposed reverse-mirror feature.
 *
 * NO vehicle access and NO actuation: it must not be connected to an OEM
 * writer until R/non-R mapping, speed units, directions and stop
 * behaviour are validated in the actual T1. Gear is supplied by a
 * validated OEM adapter and NEVER guessed from a raw gear number.
 *
 * Mirrors are relative to the driver's personally adjusted D pose.
 * One R maneuver tilts once; R/non-R shuffling at parking speed does nothing.
 * Restore requires VERIFIED non-R (D/N/P) and >10 km/h continuously
 * for 800 ms; N can move and must not be interpreted as zero speed.
 * No automatic homing against hard stops.
 */
public final class MirrorParkingPolicy {
    public enum Gear { P, R, N, D, UNKNOWN }
    public enum Phase { NORMAL, TILTING, TILTED, RESTORING, UNKNOWN_POSITION }
    public enum Action { NONE, TILT_ON_R, RESTORE_AFTER_NON_R_SPEED }
    public static final double RETURN_THRESHOLD_KMH=10.0;
    public static final long RETURN_DWELL_MS=800;
    public static final int MAX_PULSES_PER_SIDE=10;

    private final int requestedPulses;
    private Phase phase=Phase.NORMAL;
    private Gear previousGear=Gear.UNKNOWN;
    private long forwardSince=-1;
    private int leftIssued=0,rightIssued=0;

    public MirrorParkingPolicy(int steps){
        if(steps<1||steps>MAX_PULSES_PER_SIDE)
            throw new IllegalArgumentException("Pasos fuera del rango 1-10");
        requestedPulses=steps;
    }
    public Phase phase(){return phase;}
    public int requestedPulses(){return requestedPulses;}
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
            forwardSince=-1;
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
            if(gear!=Gear.R && gear!=Gear.UNKNOWN &&
                    speedKmh>RETURN_THRESHOLD_KMH){
                if(forwardSince<0)forwardSince=elapsedMs;
                if(elapsedMs-forwardSince>=RETURN_DWELL_MS){
                    phase=Phase.RESTORING;
                    forwardSince=-1;
                    return Action.RESTORE_AFTER_NON_R_SPEED;
                }
            }else forwardSince=-1;
        }
        return Action.NONE;
    }

    /** Record only completed OEM commands, not presumed physical distance. */
    public void completeTilt(int leftCommands,int rightCommands){
        if(phase!=Phase.TILTING)throw new IllegalStateException("No se inició inclinación");
        if(leftCommands<0||rightCommands<0||
                leftCommands>requestedPulses||rightCommands>requestedPulses)
            throw new IllegalArgumentException("Recuento inválido");
        leftIssued=leftCommands;rightIssued=rightCommands;
        phase=(leftCommands==requestedPulses && rightCommands==requestedPulses)?
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
    /** Any interrupted or uncontrolled movement invalidates reference. */
    public void invalidate(){
        phase=Phase.UNKNOWN_POSITION;
        forwardSince=-1;
    }
}
