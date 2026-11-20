package ar.com.rango5gps.drivelabauto.receiver;

/** Exact time-window slicing of increments ALREADY measured and persisted.
 * Cumulative registers belong to the same ignition session. Do not compare
 * different sessions or invent HV / distance through missed samples. */
final class T1AutoRangeMath {
    private T1AutoRangeMath(){}
    static final class Delta{
        final double distanceKm,gpsKm,hvOutKwh,hvInKwh,fuelCandidateL;
        final long observedMs;
        Delta(double km,double gps,double out,double in,long ms){
            this(km,gps,out,in,0,ms);
        }
        Delta(double km,double gps,double out,double in,double fuel,long ms){
            distanceKm=km;gpsKm=gps;hvOutKwh=out;hvInKwh=in;
            fuelCandidateL=fuel;observedMs=ms;
        }
        double net(){return hvOutKwh-hvInKwh;}
        Delta plus(Delta other){return new Delta(
            distanceKm+other.distanceKm,gpsKm+other.gpsKm,
            hvOutKwh+other.hvOutKwh,hvInKwh+other.hvInKwh,
            fuelCandidateL+other.fuelCandidateL,
            observedMs+other.observedMs);}
    }
    static final Delta ZERO=new Delta(0,0,0,0,0);
    static final class Cumulative{
        final long at;
        final double km,gps,out,in,fuel;
        final boolean hvValid,fuelValid;
        Cumulative(long at,double km,double gps,double out,double in){
            this(at,km,gps,out,in,0,false,false);
        }
        Cumulative(long at,double km,double gps,double out,double in,
                   double fuel,boolean hvValid,boolean fuelValid){
            this.at=at;this.km=km;this.gps=gps;this.out=out;this.in=in;
            this.fuel=fuel;this.hvValid=hvValid;this.fuelValid=fuelValid;
        }
    }
    /**
     * Compute only the fraction overlapping [windowFrom, windowUntil).
     * Fractions split a measured 4s interval at arbitrary minute boundaries.
     * Incomplete/unrecorded intervals are excluded, never extrapolated.
     */
    static Delta clip(Cumulative previous,Cumulative current,
                       long windowFrom,long windowUntil){
        if(previous==null||current==null||windowUntil<=windowFrom||
           current.at<=previous.at)return ZERO;
        long left=Math.max(previous.at,windowFrom);
        long right=Math.min(current.at,windowUntil);
        if(right<=left)return ZERO;
        // A resumed session whose OEM timestamp jumps by >10 seconds has
        // an unobserved interval. Its cumulative difference is not reliable.
        if(current.at-previous.at>11000L)return ZERO;
        double share=(double)(right-left)/(current.at-previous.at);
        double km=Math.max(0,current.km-previous.km);
        double gps=Math.max(0,current.gps-previous.gps);
        double out=Math.max(0,current.out-previous.out);
        double in=Math.max(0,current.in-previous.in);
        double fuel=Math.max(0,current.fuel-previous.fuel);
        if(!Double.isFinite(km)||!Double.isFinite(gps)||
           !Double.isFinite(out)||!Double.isFinite(in)||
           !Double.isFinite(fuel))return ZERO;
        return new Delta(km*share,gps*share,out*share,in*share,
            fuel*share,right-left);
    }
}
