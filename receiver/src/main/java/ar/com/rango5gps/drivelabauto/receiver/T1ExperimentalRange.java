package ar.com.rango5gps.drivelabauto.receiver;

/** Experiment for a two-energy-source PHEV. Uses observed fuel cutout 5.3L,
 * historic REAL tank-based 7.47L/100km and user-supplied nominal 100km EV.
 * NOT a guarantee, fuel rolling 50-km OEM average and electric kWh are NOT
 * multiplied together: avoids double counting electricity. */
final class T1ExperimentalRange {
    static final double FUEL_CUTOFF_L=5.3;
    static final double FUEL_SATURATED_L=60.5;
    static final double DEFAULT_L100=T1TripMath.lPer100();
    static final double DEFAULT_FULL_EV_KM=100.0;
    static final class Estimate {
        final double fuelKm,electricKm,totalKm;
        final boolean fuelOk,electricOk;
        Estimate(double gas,double ev){
            fuelKm=gas;electricKm=ev;
            fuelOk=Double.isFinite(gas);electricOk=Double.isFinite(ev);
            totalKm=fuelOk&&electricOk?gas+ev:Double.NaN;
        }
    }
    static Estimate estimate(double oemFuelLiters,double socPercent,
                             double l100,double fullEvKm){
        double fuel=Double.NaN,ev=Double.NaN;
        if(Double.isFinite(oemFuelLiters)&&oemFuelLiters>=0&&
           oemFuelLiters<FUEL_SATURATED_L&&Double.isFinite(l100)&&
           l100>=3&&l100<=20){
            fuel=Math.max(0,oemFuelLiters-FUEL_CUTOFF_L)*100/l100;
        }
        if(Double.isFinite(socPercent)&&socPercent>=0&&socPercent<=100&&
           Double.isFinite(fullEvKm)&&fullEvKm>=20&&fullEvKm<=180){
            ev=socPercent*fullEvKm/100;
        }
        return new Estimate(fuel,ev);
    }
    private T1ExperimentalRange(){}
}
