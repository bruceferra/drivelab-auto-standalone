package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Read-only state latch for the SOC/regen half of an optimized road profile.
 *
 * The caller only enters this gate AFTER T1OptimizedAutoGate has already
 * received three consecutive trusted GPS road observations and OEM 1043
 * matches the selected propulsion mode. It is a BUG to demand a SECOND
 * 1/3,2/3,3/3 sequence for SOC/regen; that was observed on the real T1:
 * CITY 3/3 -> EV confirmed -> SOC/REGEN pending -> CITY 1/3,2/3,3/3.
 *
 * This gate checks whether the desired auxiliary profile differs from the
 * last verified one. New valid targets become READY on the very first call
 * after the verified road; the actual OEM 1040/1041/1046 command still uses
 * double preflight and independent readback via T1SocRegenPilot.
 */
final class T1SocRegenTransitionGate {
    enum Reason { READY, APPLIED, OEM_MATCHES, UNAVAILABLE }

    static final class Decision {
        final Reason reason;
        final String profile;
        Decision(Reason reason,String profile){
            this.reason=reason;
            this.profile=profile;
        }
        boolean ready(){return reason==Reason.READY;}
    }

    private String applied;

    static String key(T1SocRegenWritePolicy.Targets t){
        if(t==null||!t.valid())return null;
        return t.strategy+":"+(t.needsTarget()?t.forcedSoc:-1)+":"+t.regen;
    }

    /**
     * @param roadConfirmedAndEligible true only after 3 trusted road fixes,
     * OEM 1043 match and fresh OEM P/0 or D/valid speed. The parent service
     * must not call this gate for a new unconfirmed road.
     */
    synchronized Decision examine(T1SocRegenWritePolicy.Targets desired,
                     boolean roadConfirmedAndEligible,boolean oemMatches){
        String wanted=key(desired);
        if(!roadConfirmedAndEligible||wanted==null)
            return new Decision(Reason.UNAVAILABLE,wanted);
        // No revalidation/rewrite on an already confirmed profile even if
        // another GPS fix arrives or a readback is momentarily stale.
        if(wanted.equals(applied))
            return new Decision(Reason.APPLIED,wanted);
        if(oemMatches){
            // Nothing to write: current OEM matches the newly selected
            // road's strategy, target (if forced) and regeneration.
            applied=wanted;
            return new Decision(Reason.OEM_MATCHES,wanted);
        }
        // Road already passed 3/3. No second voting cycle.
        // This is NOT itself proof of OEM application; UI stays neutral
        // until T1SocRegenPilot's verified callback on a later cycle.
        return new Decision(Reason.READY,wanted);
    }

    synchronized void recordConfirmed(T1SocRegenWritePolicy.Targets done){
        String confirmed=key(done);
        if(confirmed==null)
            throw new IllegalArgumentException("Invalid SOC/regen target");
        applied=confirmed;
    }

    /** Disabling a road block invalidates prior verification. Re-enabling
     * still goes through the full T1OptimizedAutoGate 3-GPS process. */
    synchronized void reset(){applied=null;}

    synchronized String currentConfirmed(){return applied;}
}
