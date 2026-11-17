package ar.com.rango5gps.drivelabauto.receiver;

/** Economic what-if; HV charging can come from braking OR gasoline
 * generation. Do not bill recovered HV energy as purchased grid kWh. */
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
            fuelPrice,kwhPrice,priceKnown);
    }
    static Result fromGross(double km,double liters,double gross,
                           double charge,double fuelPrice,double kwhPrice,
                           boolean priceKnown){
        double net=finiteNonnegative(gross)&&finiteNonnegative(charge)?
            gross-charge:Double.NaN;
        return calculate(km,liters,gross,charge,net,
            fuelPrice,kwhPrice,priceKnown);
    }
    private static boolean finiteNonnegative(double x){
        return Double.isFinite(x)&&x>=0;
    }
    private static Result calculate(double km,double liters,
                double gross,double charge,double net,double fuelPrice,
                double kwhPrice,boolean priceKnown){
        boolean gas=finiteNonnegative(liters)&&
            Double.isFinite(fuelPrice)&&fuelPrice>0;
        boolean known=Double.isFinite(net)&&Math.abs(net)<10000;
        double bill=known?Math.max(0,net):Double.NaN;
        boolean electric=known&&priceKnown&&
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
