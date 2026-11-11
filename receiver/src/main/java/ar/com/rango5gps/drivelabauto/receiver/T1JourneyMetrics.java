package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/** Pure calculations: experimental OEM 500 m readings are NOT metered litres/kWh.
 * Energy net can be negative; never treat it as charged grid energy or money. */
final class T1JourneyMetrics {
    static final double SEGMENT_KM=0.5;
    static final class Checkpoint {
        final String tripId;
        final long time;
        final double km,rawFuel,rawElectric;
        final int fuelCount,electricCount;
        Checkpoint(String tripId,long time,double km,double rawFuel,
                   double rawElectric,int fuelCount,int electricCount) {
            this.tripId=tripId;this.time=time;this.km=km;
            this.rawFuel=rawFuel;this.rawElectric=rawElectric;
            this.fuelCount=fuelCount;this.electricCount=electricCount;
        }
    }
    static final class Result {
        final double km,rawFuel,rawElectric,fuelLitersCandidate,
                     electricNetKwhCandidate,fuelCostCandidate;
        final int fuelEvents,electricEvents,pairedEvents;
        final boolean fuelAvailable,electricAvailable,paired;
        Result(double km,double rawFuel,double rawElectric,int fuelEvents,
               int electricEvents,double fuelPrice) {
            this.km=km;this.rawFuel=rawFuel;this.rawElectric=rawElectric;
            this.fuelEvents=fuelEvents;this.electricEvents=electricEvents;
            pairedEvents=Math.min(fuelEvents,electricEvents);
            paired=fuelEvents==electricEvents&&fuelEvents>0;
            fuelAvailable=fuelEvents>0;
            electricAvailable=electricEvents>0;
            fuelLitersCandidate=fuelAvailable?rawFuel*SEGMENT_KM/100:Double.NaN;
            electricNetKwhCandidate=electricAvailable?
                rawElectric*SEGMENT_KM/100:Double.NaN;
            fuelCostCandidate=fuelAvailable?
                fuelLitersCandidate*Math.max(0,fuelPrice):Double.NaN;
        }
    }
    static Result between(Checkpoint start,Checkpoint end,double fuelPrice) {
        if(start==null||end==null||
                !start.tripId.equals(end.tripId)||
                !Double.isFinite(start.km)||!Double.isFinite(end.km)||
                end.km<start.km||end.time<start.time||
                end.fuelCount<start.fuelCount||
                end.electricCount<start.electricCount)
            throw new IllegalArgumentException("TRIP incoherente: no unir jornadas distintas");
        double a=end.rawFuel-start.rawFuel,b=end.rawElectric-start.rawElectric;
        if(!Double.isFinite(a)||!Double.isFinite(b))
            throw new IllegalArgumentException("Suma OEM inválida");
        return new Result(end.km-start.km,a,b,
            end.fuelCount-start.fuelCount,
            end.electricCount-start.electricCount,fuelPrice);
    }
    static String fmt(double n,int digits){
        return Double.isFinite(n)?String.format(Locale.forLanguageTag("es-AR"),
            "%."+digits+"f",n):"SIN DATO";
    }
    private T1JourneyMetrics(){}
}
