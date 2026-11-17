package ar.com.rango5gps.drivelabauto.receiver;
public final class T1TripCostModelTest {
    private static void near(double a,double b){if(!Double.isFinite(a)||
        Math.abs(a-b)>0.000001)throw new AssertionError(a+" vs "+b);}
    private static void yes(boolean a){if(!a)throw new AssertionError();}
    public static void main(String[] args){
        T1TripCostModel.Result r=T1TripCostModel.fromGross(
            76,3.47,8,3,2000,250,true);
        near(r.netKwh,5);near(r.billableKwh,5);
        near(r.gasolineCost,6940);near(r.electricCost,1250);
        near(r.totalCost,8190);near(r.equivalentLiters,4.095);
        near(r.lPer100,4.095*100/76);near(r.kmPerLiter,76/4.095);
        T1TripCostModel.Result incoming=T1TripCostModel.fromGross(
            20,1,2,5,2000,250,true);
        near(incoming.billableKwh,0);near(incoming.totalCost,2000);
        near(T1TripCostModel.fromNet(20,1,-1.5,2000,250,true).billableKwh,0);
        T1TripCostModel.Result unknown=T1TripCostModel.fromGross(
            20,1,5,Double.NaN,2000,250,true);
        yes(Double.isNaN(unknown.netKwh)&&Double.isNaN(unknown.totalCost));
        T1TripCostModel.Result missing=T1TripCostModel.fromNet(
            76,3.47,5,2000,0,false);
        yes(Double.isNaN(missing.totalCost)&&Double.isNaN(missing.equivalentLiters));
        T1TripCostModel.Result zero=T1TripCostModel.fromNet(0,0,0,2000,250,true);
        yes(Double.isNaN(zero.lPer100)&&Double.isNaN(zero.kmPerLiter));
        System.out.println("PASS HV net energy costs + money-equivalent liters");
    }
}
