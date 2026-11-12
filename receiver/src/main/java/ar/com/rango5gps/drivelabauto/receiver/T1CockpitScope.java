package ar.com.rango5gps.drivelabauto.receiver;

/** Pure UI selection: never present the last stopped TRIP as a live journey.
 * A client's checkpoint deltas take priority over all-day totals. */
final class T1CockpitScope {
    enum Kind { IDLE, DAY, CLIENT, PREPARING, UNSYNCED }
    static final class Value {
        final Kind kind;
        final double km, fuelLiters, electricNetKwh, fuelCost;
        final int fuelEvents, electricEvents;
        final boolean fuelKnown, electricKnown;
        Value(Kind kind,double km,double fuelLiters,double electricNetKwh,
              double fuelCost,int fuelEvents,int electricEvents,
              boolean fuelKnown,boolean electricKnown) {
            this.kind=kind;
            this.km=km;
            this.fuelLiters=fuelLiters;
            this.electricNetKwh=electricNetKwh;
            this.fuelCost=fuelCost;
            this.fuelEvents=fuelEvents;
            this.electricEvents=electricEvents;
            this.fuelKnown=fuelKnown;
            this.electricKnown=electricKnown;
        }
    }
    private static Value unknown(Kind kind){
        return new Value(kind,0,Double.NaN,Double.NaN,Double.NaN,0,0,false,false);
    }
    static Value choose(boolean dayRecording,boolean hasClient,boolean preparing,
                        T1JourneyMetrics.Result client,double dayKm,
                        int dayFuelEvents,int dayElectricEvents,
                        double dayFuelRaw,double dayElectricRaw,double fuelPrice) {
        if(preparing)return unknown(Kind.PREPARING);
        if(hasClient){
            if(client==null)return unknown(Kind.UNSYNCED);
            return new Value(Kind.CLIENT,client.km,client.fuelLitersCandidate,
                client.electricNetKwhCandidate,client.fuelCostCandidate,
                client.fuelEvents,client.electricEvents,
                client.fuelAvailable,client.electricAvailable);
        }
        if(!dayRecording)return unknown(Kind.IDLE);
        if(!Double.isFinite(dayKm)||dayKm<0)return unknown(Kind.UNSYNCED);
        boolean f=dayFuelEvents>0,e=dayElectricEvents>0;
        if((f&&!Double.isFinite(dayFuelRaw))||
           (e&&!Double.isFinite(dayElectricRaw)))
            return unknown(Kind.UNSYNCED);
        double l=f?dayFuelRaw*0.005d:Double.NaN;
        return new Value(Kind.DAY,dayKm,l,e?dayElectricRaw*0.005d:Double.NaN,
            f?l*Math.max(0,fuelPrice):Double.NaN,
            dayFuelEvents,dayElectricEvents,f,e);
    }
    private T1CockpitScope(){}
}
