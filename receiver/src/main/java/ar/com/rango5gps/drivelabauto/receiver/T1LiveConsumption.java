package ar.com.rango5gps.drivelabauto.receiver;

/** Experimental fuel consumption and pace-dependent fuel range.
 *
 * CI callback 135 (500m) raw unit is NOT yet independently calibrated.
 * 1085 from VehicleService is a candidate rolling last-50km L/100 value.
 * We expose the raw 500m reading and only derive a clearly labeled
 * candidate L/100 and range when value/freshness/quality gates hold.
 * EV-only 0.0 must never make remaining gasoline range infinite.
 */
final class T1LiveConsumption {
    static final long FRESH_MS=180000L;
    static final class State{
        final double last500Raw,rolling500Candidate,rolling50,
            candidateFuelKm;
        final boolean rawFresh,hasRollingCandidate,rolling50Valid;
        final String source;
        State(double last,double recent,double fifty,double projected,
            boolean fresh,boolean recentOk,boolean fiftyOk,String src){
            last500Raw=last;rolling500Candidate=recent;
            rolling50=fifty;candidateFuelKm=projected;
            rawFresh=fresh;hasRollingCandidate=recentOk;
            rolling50Valid=fiftyOk;source=src;
        }
    }
    private T1LiveConsumption(){}
    static State evaluate(double lastRaw,double recentRaw,int recentCount,
                          long lastAtMs,long nowMs,double oem50L100,
                          double fuelLitres,double userBaseL100){
        long age=nowMs-lastAtMs;
        boolean fresh=age>=0&&age<=FRESH_MS&&
            Double.isFinite(lastRaw)&&lastRaw>=0&&lastRaw<=35;
        boolean plausible50=Double.isFinite(oem50L100)&&
            oem50L100>=2.0&&oem50L100<=30.0;
        // Three 500m notifications are at least 1.5 km of the latest
        // route. In pure EV there can be zero gasoline demand: use a base,
        // not a zero divisor.
        boolean candidate=fresh&&recentCount>=3&&
            Double.isFinite(recentRaw)&&recentRaw>=2.5&&recentRaw<=25;
        double selected=candidate?recentRaw:
            plausible50?oem50L100:userBaseL100;
        String source=candidate?
            "últimos segmentos OEM 500 m · escala experimental":
            plausible50?"promedio OEM 50 km · menos sensible a cambios":
            "consumo histórico/manual · sin lectura dinámica fiable";
        double fuel=Double.NaN;
        if(Double.isFinite(fuelLitres)&&fuelLitres>=0&&
            fuelLitres<60.5&&Double.isFinite(selected)&&selected>=3&&
            selected<=30){
            fuel=Math.max(0,fuelLitres-T1ExperimentalRange.FUEL_CUTOFF_L)
                *100.0/selected;
        }
        return new State(lastRaw,recentRaw,oem50L100,fuel,
            fresh,candidate,plausible50,source);
    }
}
