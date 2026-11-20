package ar.com.rango5gps.drivelabauto.receiver;

/** CI186 is remaining tank level, NOT fuel consumed. For a session we
 * track only plausible new low-water marks. The result is a *candidate*
 * fuel use from tank changes, not injector pulses or a calibrated meter.
 * Small upward slosh does not let the same 0.1L drop count twice.
 * Large refills reset the baseline, never create negative consumption.
 */
final class T1AutoFuelLevelMath {
    private T1AutoFuelLevelMath(){}
    static final class Delta{
        final double low,consumedCandidate;
        Delta(double low,double consumedCandidate){
            this.low=low;this.consumedCandidate=consumedCandidate;
        }
    }
    static boolean valid(double liters){
        return Double.isFinite(liters)&&liters>=0&&liters<=75;
    }
    static Delta step(double low,double current,long deltaMs){
        if(!valid(current))return new Delta(low,0);
        if(!valid(low))return new Delta(current,0);
        if(deltaMs<=0||deltaMs>11000)return new Delta(low,0);
        // A clear refuel requires starting a new low baseline. Read-only;
        // it does not assert how many actual liters were added.
        if(current>low+3.0)return new Delta(current,0);
        if(current>=low)return new Delta(low,0);
        double drop=low-current;
        // Discard big jumps, e.g., slosh, data glitches or lost samples.
        if(drop>0.35)return new Delta(low,0);
        return new Delta(current,drop);
    }
}
