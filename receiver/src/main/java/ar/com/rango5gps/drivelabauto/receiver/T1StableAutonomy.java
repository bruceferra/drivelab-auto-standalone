package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Stable fuel-consumption model for a PHEV range *estimate*.
 * OEM 1085 is the vehicle's own 50 km rolling estimate. The raw 500 m
 * callbacks are NOT proven to be litres, and only corroborate 1085 when a
 * complete independent 100-event window agrees. Refuel measurements remain
 * the historical anchor, not the rejected T1Launcher fuel formula.
 *
 * Pure JVM: no Android, OEM writes, GPS history, or clock side effects.
 */
final class T1StableAutonomy {
    static final long MAX_PRIOR_AGE_MS=30L*24*60*60*1000;
    static final class Result {
        final double l100, targetL100, anchorL100, oem50L100, raw100L100;
        final int rawCount;
        final boolean oem50Used, rawCorroborated;
        final String source;
        Result(double consumption,double target,double anchor,double oem,
               double raw,int count,boolean oemUsed,boolean corroborated,String src){
            l100=consumption;targetL100=target;anchorL100=anchor;
            oem50L100=oem;raw100L100=raw;rawCount=count;
            oem50Used=oemUsed;rawCorroborated=corroborated;source=src;
        }
    }
    private T1StableAutonomy(){}
    static Result estimate(double anchorL100,double oem50L100,
                           double raw100Mean,int rawCount,
                           double priorL100,long priorAt,long now,
                           double speedKmH){
        // Accepted values are plausible ranges only, not a unit calibration.
        double anchor=valid(anchorL100,3.0,20.0)?anchorL100:
            T1TripMath.lPer100();
        boolean oem=valid(oem50L100,3.0,20.0);
        // A 50 km PHEV value can include long EV stretches, changing SOC and
        // charge strategy. Clamp its leverage relative to pump history.
        boolean usable=oem&&oem50L100>=anchor*0.60&&oem50L100<=anchor*1.50;
        boolean corroborated=usable&&rawCount==100&&
            valid(raw100Mean,0,35)&&Math.abs(raw100Mean-oem50L100)<=
            Math.max(0.7,oem50L100*0.15);
        double target=anchor;
        String source="SURTIDOR / BASE HISTÓRICA";
        if(usable){
            // A full historical 100×500m window is shown for audit, not
            // assigned independent fuel-meter precision.
            target=anchor*0.65+oem50L100*0.35;
            source=corroborated?
                "SURTIDOR + OEM 50 km · 100x500 m corroboran":
                "SURTIDOR + OEM 50 km · segmentos 500 m sin validar";
        }
        double output=anchor;
        boolean priorValid=valid(priorL100,3.0,20.0)&&priorAt>0&&
            now>=priorAt&&now-priorAt<=MAX_PRIOR_AGE_MS;
        if(priorValid){
            // No switch to a new source may cause a discontinuity. Time and
            // speed bound movement since the previous OEM poll (max 2 min).
            double seconds=Math.min(120,(now-priorAt)/1000.0);
            double speed=valid(speedKmH,0,180)?speedKmH:40.0;
            double km=speed*seconds/3600.0;
            double maxStep=0.018+0.14*km;
            output=Math.max(priorL100-maxStep,
                Math.min(priorL100+maxStep,target));
        }
        return new Result(output,target,anchor,oem50L100,raw100Mean,
            rawCount,usable,corroborated,source);
    }
    private static boolean valid(double x,double low,double high){
        return Double.isFinite(x)&&x>=low&&x<=high;
    }
}
