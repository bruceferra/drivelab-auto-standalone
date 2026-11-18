package ar.com.rango5gps.drivelabauto.receiver;
public final class T1PlugEnergyLedgerTest {
    static void near(double x,double y){if(Math.abs(x-y)>1e-9)
        throw new AssertionError(x+" != "+y);}
    static void yes(boolean x){if(!x)throw new AssertionError();}
    public static void main(String[] args){
        T1PlugEnergyLedger.Result v=T1PlugEnergyLedger.estimate(
            25.6,28.0,6.0,2.0,200.0);
        near(v.hvNetKwh,4.0);
        near(v.assignedPlugKwh,4.0);
        near(v.remainingPlugKwh,21.6);
        near(v.allocatedGridKwh,4.375);
        near(v.electricCost,875.0);
        yes(v.gridMeterKnown);
        v=T1PlugEnergyLedger.estimate(25.6,Double.NaN,2.0,4.0,200.0);
        near(v.hvNetKwh,-2.0);near(v.assignedPlugKwh,0);
        near(v.electricCost,0);yes(!v.gridMeterKnown);
        v=T1PlugEnergyLedger.estimate(25.6,Double.NaN,40,0,200);
        near(v.assignedPlugKwh,25.6);
        near(v.unattributedKwh,14.4);
        near(v.electricCost,5120);
        yes(T1PlugEnergyLedger.estimate(Double.NaN,0,6,2,100)==null);
        yes(T1PlugEnergyLedger.estimate(25,0,Double.NaN,2,100)==null);
        v=T1PlugEnergyLedger.estimate(25,0,5,0,Double.NaN);
        yes(!v.priceKnown&&Double.isNaN(v.electricCost));
        System.out.println("PASS since last plug cost ledger never bills HV-in twice");
    }
}
