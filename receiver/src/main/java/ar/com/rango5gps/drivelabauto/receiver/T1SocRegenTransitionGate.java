package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Read-only state gate for the four-profile SOC/regen optimizer.
 * Three consecutive observations apply ONLY when the DESIRED profile
 * changes, not on every 10-second GPS read of the same road.
 * OEM writes are still handled and verified by T1SocRegenPilot.
 */
final class T1SocRegenTransitionGate {
    static final int REQUIRED_MATCHES=3;
    enum Reason { WAIT_STABLE, READY, APPLIED, OEM_MATCHES, UNAVAILABLE }
    static final class Decision {
        final Reason reason;
        final int matches;
        final String profile;
        Decision(Reason reason,int matches,String profile){
            this.reason=reason;this.matches=matches;this.profile=profile;
        }
        boolean ready(){return reason==Reason.READY;}
    }
    private String candidate, applied;
    private int votes;

    static String key(T1SocRegenWritePolicy.Targets t){
        if(t==null||!t.valid())return null;
        return t.strategy+":"+(t.needsTarget()?t.forcedSoc:-1)+":"+t.regen;
    }

    synchronized Decision examine(T1SocRegenWritePolicy.Targets desired,
                     boolean eligible,boolean oemMatches){
        String wanted=key(desired);
        if(!eligible||wanted==null){
            candidate=null;votes=0;
            return new Decision(Reason.UNAVAILABLE,0,wanted);
        }
        // The same confirmed OEM target never requires another 1/3 sequence,
        // even if other GPS fixes arrive or the integer 1041 getter is opaque.
        if(wanted.equals(applied))
            return new Decision(Reason.APPLIED,REQUIRED_MATCHES,wanted);
        if(oemMatches){
            applied=wanted;candidate=wanted;votes=REQUIRED_MATCHES;
            return new Decision(Reason.OEM_MATCHES,REQUIRED_MATCHES,wanted);
        }
        if(!wanted.equals(candidate)){
            candidate=wanted;votes=1;
        }else votes=Math.min(REQUIRED_MATCHES,votes+1);
        return new Decision(votes==REQUIRED_MATCHES?Reason.READY:
            Reason.WAIT_STABLE,votes,wanted);
    }

    synchronized void recordConfirmed(T1SocRegenWritePolicy.Targets done){
        String key=key(done);
        if(key==null)throw new IllegalArgumentException("Invalid target");
        applied=key;
        candidate=key;
        votes=REQUIRED_MATCHES;
    }

    synchronized String currentConfirmed(){return applied;}
}
