package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Auditable ACCOUNTING estimate since a known finished external charge.
 * HV battery draw after charge alone CANNOT physically identify the
 * provenance of each electron (grid vs braking vs engine vs old reserve).
 *
 * This is a budget model, not a measured attribution. Never treat meter
 * kWh as pack kWh; charging losses remain part of the actual grid bill.
 */
final class T1PlugEnergyLedger {
    private T1PlugEnergyLedger(){}
    static final class Result {
        final double fromPlugBatteryKwh,hvDischargeKwh,hvRechargeKwh,
            hvNetKwh,assignedPlugKwh,remainingPlugKwh,unattributedKwh,
            allocatedGridKwh,electricCost;
        final boolean gridMeterKnown,priceKnown;
        Result(double battery,double out,double in,double net,double plug,
               double remaining,double unassigned,double grid,double cost,
               boolean metered,boolean priced) {
            fromPlugBatteryKwh=battery;hvDischargeKwh=out;
            hvRechargeKwh=in;hvNetKwh=net;assignedPlugKwh=plug;
            remainingPlugKwh=remaining;unattributedKwh=unassigned;
            allocatedGridKwh=grid;electricCost=cost;
            gridMeterKnown=metered;priceKnown=priced;
        }
    }
    static Result estimate(double batteryFromPlugKwh,double gridMeterKwh,
                           double hvOut,double hvIn,double pricePerGridKwh){
        if(!Double.isFinite(batteryFromPlugKwh)||
           batteryFromPlugKwh<=0||
           !Double.isFinite(hvOut)||hvOut<0||
           !Double.isFinite(hvIn)||hvIn<0)
            return null;
        double net=hvOut-hvIn; // in includes regenerative + thermal charging
        double allocation=Math.max(0,Math.min(batteryFromPlugKwh,net));
        boolean metered=Double.isFinite(gridMeterKwh)&&gridMeterKwh>0;
        // Scale *cost*, not battery utilization, for wall-to-pack losses.
        double wallBudget=metered?gridMeterKwh:batteryFromPlugKwh;
        double allocatedGrid=allocation*(wallBudget/batteryFromPlugKwh);
        boolean priceKnown=Double.isFinite(pricePerGridKwh)&&
            pricePerGridKwh>=0;
        return new Result(batteryFromPlugKwh,hvOut,hvIn,net,allocation,
            Math.max(0,batteryFromPlugKwh-allocation),
            Math.max(0,net-batteryFromPlugKwh),
            allocatedGrid,priceKnown?allocatedGrid*pricePerGridKwh:
                Double.NaN,metered,priceKnown);
    }
}
