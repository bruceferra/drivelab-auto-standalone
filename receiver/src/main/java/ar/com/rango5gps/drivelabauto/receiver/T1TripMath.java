package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Transparent cost/range proxy based on photographed pump volumes, NOT on
 * the rejected T1Launcher consumption calculation. Gas km proxy uses the
 * recent two-tank evidence; HV battery discharge is not grid kWh billed.
 */
public final class T1TripMath {
    private T1TripMath(){}
    public static final double PUMP_LITERS=66.230;
    public static final double LAST_TANK_KM=887.0;
    public static final double CUTOFF_OEM=5.3;
    public static final double FULL_OEM=67.0;
    public static final double OHM_SATURATION_OEM=60.5;
    public static final double UPPER_SATURATION_OHM=56.0;

    public static double lPer100(){return 100.0*PUMP_LITERS/LAST_TANK_KM;}
    public static double initialRangeKm(){return LAST_TANK_KM;}

    public static double gasolineProxyLiters(double km){
        if(!Double.isFinite(km)||km<0)throw new IllegalArgumentException("km inválidos");
        return km*lPer100()/100.0;
    }
    public static double estimatedRangeKm(double kmSinceFull,Double oemLiter){
        if(!Double.isFinite(kmSinceFull)||kmSinceFull<0)
            throw new IllegalArgumentException("km inválidos");
        // The repeated 5.3-liter OEM thermal cutoff is an observed hard floor
        // for gasoline-only range. Do NOT claim physical tank empty.
        if(oemLiter!=null&&Double.isFinite(oemLiter) &&
                oemLiter<=CUTOFF_OEM)return 0;
        return Math.max(0,initialRangeKm()-kmSinceFull);
    }
    /**
     * SECONDARY sanity check from CI186 only. This linear proxy is explicitly
     * uncalibrated; 56-ohm top saturation makes it unusable above 60.5 OEM.
     */
    public static double rawGaugeRangeLowConfidence(double oemLiter){
        if(!Double.isFinite(oemLiter)||oemLiter<CUTOFF_OEM||
                oemLiter>=OHM_SATURATION_OEM)return Double.NaN;
        return Math.max(0,Math.min(1,
            (oemLiter-CUTOFF_OEM)/(FULL_OEM-CUTOFF_OEM)))*LAST_TANK_KM;
    }
    public static boolean floatUpperSaturated(Double oemLiter){
        return oemLiter!=null&&Double.isFinite(oemLiter)&&
            oemLiter>=OHM_SATURATION_OEM;
    }
    public static double cost(double quantity,double price){
        if(!Double.isFinite(quantity)||!Double.isFinite(price)||
            quantity<0||price<0)throw new IllegalArgumentException("Precio o cantidad inválido");
        return quantity*price;
    }
    /** Raw HV energy drawn from the pack: proxy only, not grid electricity. */
    public static double grossHvDischargeKwh(double voltage,double amps,long dtMillis){
        if(!Double.isFinite(voltage)||!Double.isFinite(amps)||
            voltage<100||voltage>1000||Math.abs(amps)>1000||
            dtMillis<=0||dtMillis>10000)return 0;
        return Math.max(0,voltage*amps)*dtMillis/3600000000.0;
    }
    /** Negative charging current observed on the T1; provisional direction. */
    public static double grossHvChargeKwh(double voltage,double amps,long dtMillis){
        if(!Double.isFinite(voltage)||!Double.isFinite(amps)||
            voltage<100||voltage>1000||Math.abs(amps)>1000||
            dtMillis<=0||dtMillis>10000)return 0;
        return Math.max(0,-voltage*amps)*dtMillis/3600000000.0;
    }
}
