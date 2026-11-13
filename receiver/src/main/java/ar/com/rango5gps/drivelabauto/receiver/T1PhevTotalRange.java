package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Conservative remaining distance for a *mixed* PHEV tank cycle.
 *
 * Pump litres / total Jetour kilometres already captures how much driving
 * was electric in that historical cycle. EV range must NOT be added again
 * to the resulting mixed-cycle range: doing so double-counts past EV benefit.
 *
 * Only diagnostic data, never vehicle control. CI186 is uncalibrated and
 * saturates at the top. Distance from an explicit full-tank anchor is used
 * only when the driver has one; no hidden reset to a fabricated 0 km.
 */
final class T1PhevTotalRange {
    private static final double FULL_PUMP_REF_L=67.0;
    private static final double FULL_OEM=67.0, CUTOFF_OEM=5.3;
    private static final double HIGH_SATURATION=60.5;
    static final class Estimate {
        final double remainingKm,referenceFullKm,byGaugeKm,byTripKm,evPotentialKm;
        final boolean gaugeExactEnough, anchored, saturated;
        final String source;
        Estimate(double remain,double full,double gauge,double trip,double ev,
                 boolean gaugeValid,boolean hasTrip,boolean upper,String source){
            remainingKm=remain;referenceFullKm=full;
            byGaugeKm=gauge;byTripKm=trip;evPotentialKm=ev;
            gaugeExactEnough=gaugeValid;anchored=hasTrip;
            saturated=upper;this.source=source;
        }
    }
    private T1PhevTotalRange(){}
    static Estimate estimate(double ci186,double socPercent,double mixedL100,
                             double fullEvKm,boolean anchored,double sinceFullKm){
        if(!Double.isFinite(mixedL100)||mixedL100<3||mixedL100>20)
            return new Estimate(Double.NaN,Double.NaN,Double.NaN,Double.NaN,
                ev(socPercent,fullEvKm),false,false,false,"SIN CONSUMO VALIDADO");
        // Historical mixed km per litre from pump and distance in the
        // same cycle; not engine-only fuel efficiency.
        double full=FULL_PUMP_REF_L*100.0/mixedL100;
        boolean trip=anchored&&Double.isFinite(sinceFullKm)&&
            sinceFullKm>=0&&sinceFullKm<=3000;
        double byTrip=trip?Math.max(0,full-sinceFullKm):Double.NaN;
        boolean upper=Double.isFinite(ci186)&&
            ci186>=HIGH_SATURATION&&ci186<=70;
        boolean gauge=Double.isFinite(ci186)&&ci186>=0&&
            ci186<HIGH_SATURATION;
        double byGauge=gauge?
            Math.max(0,Math.min(1,(ci186-CUTOFF_OEM)/(FULL_OEM-CUTOFF_OEM)))*full:
            Double.NaN;
        double remaining=Double.NaN;
        String source="SIN REFERENCIA ACTUAL";
        if(trip&&gauge){
            // Keep the more conservative of two imperfect observations.
            remaining=Math.min(byTrip,byGauge);
            source="MIXTO · MENOR ENTRE KM DESDE LLENO Y NIVEL CI186";
        }else if(trip){
            remaining=byTrip;
            source="MIXTO · KM REALES DESDE TANQUE LLENO";
        }else if(gauge){
            remaining=byGauge;
            source="MIXTO · NIVEL CI186 PROVISIONAL";
        }else if(upper){
            // Strongly label full-cycle reference as an approximation,
            // not as litres physically measured at saturated upper gauge.
            remaining=full;
            source="MIXTO · REFERENCIA TANQUE ALTO (CI186 SATURADO)";
        }
        return new Estimate(remaining,full,byGauge,byTrip,
            ev(socPercent,fullEvKm),gauge,trip,upper,source);
    }
    private static double ev(double soc,double full){
        return Double.isFinite(soc)&&soc>=0&&soc<=100&&
            Double.isFinite(full)&&full>=20&&full<=180?
            soc*full/100:Double.NaN;
    }
}
