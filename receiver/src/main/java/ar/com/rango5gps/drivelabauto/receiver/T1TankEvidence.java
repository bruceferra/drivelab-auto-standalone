package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/**
 * Two photographed/reported refueling cycles of the Jetour T1 PHEV.
 * Immutable observed evidence, not real-time fuel-meter calibration.
 * Old T1Launcher consumption algorithm is NEVER used.
 */
public final class T1TankEvidence {
    private T1TankEvidence(){}

    public static final class Cycle {
        public final String label,cutoffDate,source;
        public final double litersPump,kmJetour;
        public final boolean distanceApproximate;
        public Cycle(String label,String cutoffDate,double litersPump,double kmJetour,
                     boolean distanceApproximate,String source){
            if(!Double.isFinite(litersPump)||litersPump<=0 ||
                    !Double.isFinite(kmJetour)||kmJetour<=0)
                throw new IllegalArgumentException("Surtidor y distancia positivos");
            this.label=label;this.cutoffDate=cutoffDate;this.litersPump=litersPump;
            this.kmJetour=kmJetour;this.distanceApproximate=distanceApproximate;
            this.source=source;
        }
        public double lPer100(){return litersPer100(litersPump,kmJetour);}
        public double kmPerL(){return kmPerLiter(litersPump,kmJetour);}
    }

    public static final Cycle PREVIOUS=new Cycle(
        "Primer ciclo","03/11/2026",66.865,950.0,true,
        "Surtidor primer corte y distancia ~950 km informada");
    public static final Cycle SECOND=new Cycle(
        "Segundo ciclo","10/11/2026",66.230,887.0,false,
        "Foto surtidor 66,230 L y TRIP Jetour 887 km");

    public static final double OEM_THERMAL_CUTOFF_L=5.3;
    public static final double OEM_FULL_DISPLAY_L=67.0;
    public static final double REFILL_PRICE_PER_L=2129.0;
    public static final double REFILL_TOTAL_ARS=141004.0;
    public static final double OTHER_APP_KM=867.0;
    public static final double DRIVE_LAB_GPS_KM=895.089;

    public static double litersPer100(double liters,double distanceKm){
        if(!Double.isFinite(liters)||!Double.isFinite(distanceKm)||
                liters<0||distanceKm<=0)throw new IllegalArgumentException("Entrada inválida");
        return 100.0*liters/distanceKm;
    }
    public static double kmPerLiter(double liters,double distanceKm){
        if(!Double.isFinite(liters)||!Double.isFinite(distanceKm)||
                liters<=0||distanceKm<0)throw new IllegalArgumentException("Entrada inválida");
        return distanceKm/liters;
    }
    public static double oemRefillDelta(){
        return OEM_FULL_DISPLAY_L-OEM_THERMAL_CUTOFF_L;
    }
    public static double oemGapToPump(Cycle c){
        return c.litersPump-oemRefillDelta();
    }
    public static String number(double value,int decimals){
        return String.format(Locale.forLanguageTag("es-AR"),"%."+decimals+"f",value);
    }
}
