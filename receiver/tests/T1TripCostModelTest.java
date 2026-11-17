package ar.com.rango5gps.drivelabauto.receiver;
public final class T1TripCostModelTest {
    private static void near(double a,double b){if(!Double.isFinite(a)||
        Math.abs(a-b)>0.000001)throw new AssertionError(a+" vs "+b);}
    private static void yes(boolean a){if(!a)throw new AssertionError();}
    public static void main(String[] args){
        // 76 km, 3.47L, gross 8kWh, internal charging 3kWh:
        // 5kWh net DOES NOT mean 5kWh came from a domestic plug!
        T1TripCostModel.Result unknown=T1TripCostModel.fromGross(
            76,3.47,8,3,2000,250,true);
        near(unknown.netKwh,5);
        near(unknown.gasolineCost,6940);
        yes(Double.isNaN(unknown.billableKwh));
        yes(Double.isNaN(unknown.electricCost));
        yes(Double.isNaN(unknown.totalCost));
        yes(Double.isNaN(unknown.equivalentLiters));
        // Even a valid tariff cannot prove origin; same for client net.
        T1TripCostModel.Result client=T1TripCostModel.fromNet(
            76,3.47,5,2000,250,true);
        yes(Double.isNaN(client.billableKwh));
        // Only source-confirmed, journey-attributed plug kWh may be costed.
        T1TripCostModel.Result paid=T1TripCostModel.withDocumentedPlugKwh(
            76,3.47,8,3,2,2000,250);
        near(paid.billableKwh,2);
        near(paid.gasolineCost,6940);
        near(paid.electricCost,500);
        near(paid.totalCost,7440);
        near(paid.equivalentLiters,3.72);
        near(paid.lPer100,3.72*100/76);
        near(paid.kmPerLiter,76/3.72);
        // Internally regenerated/engine-generated 5kWh is not billed.
        T1TripCostModel.Result internal=T1TripCostModel.withDocumentedPlugKwh(
            20,1,2,5,0,2000,250);
        near(internal.electricCost,0);
        near(internal.totalCost,2000);
        // No known plug allocation, not even a zero-price bill.
        yes(Double.isNaN(T1TripCostModel.fromNet(
            20,1,-1.5,2000,0,true).totalCost));
        System.out.println("PASS grid-origin only; HV net is NOT plug energy");
    }
}
