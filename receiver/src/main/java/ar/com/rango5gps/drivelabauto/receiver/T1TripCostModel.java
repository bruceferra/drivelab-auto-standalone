package ar.com.rango5gps.drivelabauto.receiver;

/**
 * External electricity is ONLY energy attributable to a documented plug-in
 * charge. Positive NET HV draw is NOT proof of energy bought at home.
 * Both braking and engine-driven charging are internal, unbilled sources.
 * Do not invent a source split from the HV current sensor.
 */
final class T1TripCostModel {
    static final class Result {
        final double grossKwh,chargeKwh,netKwh,billableKwh,gasolineCost,
                     electricCost,totalCost,equivalentLiters,lPer100,kmPerLiter;
        Result(double gross,double charge,double net,double bill,double gas,
               double electric,double total,double equivalent,double l100,double kml){
            grossKwh=gross;chargeKwh=charge;netKwh=net;billableKwh=bill;
            gasolineCost=gas;electricCost=electric;totalCost=total;
            equivalentLiters=equivalent;lPer100=l100;kmPerLiter=kml;
        }
    }
    private T1TripCostModel(){}
    static Result fromNet(double km,double liters,double signedNet,
                          double fuelPrice,double kwhPrice,boolean priceKnown){
        return calculate(km,liters,Double.NaN,Double.NaN,signedNet,
            Double.NaN,fuelPrice,kwhPrice,priceKnown);
    }
    static Result fromGross(double km,double liters,double gross,
                           double charge,double fuelPrice,double kwhPrice,
                           boolean priceKnown){
        double net=finiteNonnegative(gross)&&finiteNonnegative(charge)?
            gross-charge:Double.NaN;
        return calculate(km,liters,gross,charge,net,
            Double.NaN,fuelPrice,kwhPrice,priceKnown);
    }
    /**
     * Only call when an external grid-charge record has been explicitly
     * assigned to this journey. Calling fromNet/fromGross alone NEVER bills
     * electricity regardless of net HV consumption or configured tariff.
     */
    static Result withDocumentedPlugKwh(double km,double liters,
                                       double gross,double charge,
                                       double plugKwh,double fuelPrice,
                                       double kwhPrice){
        double net=finiteNonnegative(gross)&&finiteNonnegative(charge)?
            gross-charge:Double.NaN;
        return calculate(km,liters,gross,charge,net,plugKwh,
            fuelPrice,kwhPrice,true);
    }
    private static boolean finiteNonnegative(double x){
        return Double.isFinite(x)&&x>=0;
    }
    private static Result calculate(double km,double liters,
                double gross,double charge,double net,double plugKwh,
                double fuelPrice,double kwhPrice,boolean priceKnown){
        boolean gas=finiteNonnegative(liters)&&
            Double.isFinite(fuelPrice)&&fuelPrice>0;
        // Only a documented, linked plug session is a paid energy source.
        // A positive pack depletion says nothing about provenance.
        double bill=finiteNonnegative(plugKwh)?plugKwh:Double.NaN;
        boolean electric=Double.isFinite(bill)&&priceKnown&&
            Double.isFinite(kwhPrice)&&kwhPrice>=0;
        double gasCost=gas?liters*fuelPrice:Double.NaN;
        double electricCost=electric?bill*kwhPrice:Double.NaN;
        double total=gas&&electric?gasCost+electricCost:Double.NaN;
        double equivalent=Double.isFinite(total)&&fuelPrice>0?
            total/fuelPrice:Double.NaN;
        double l100=Double.isFinite(equivalent)&&Double.isFinite(km)&&km>0?
            equivalent*100/km:Double.NaN;
        double kml=Double.isFinite(equivalent)&&equivalent>0&&
            Double.isFinite(km)&&km>0?km/equivalent:Double.NaN;
        return new Result(gross,charge,net,bill,gasCost,electricCost,total,
            equivalent,l100,kml);
    }
}
