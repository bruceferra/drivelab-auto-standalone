package ar.com.rango5gps.drivelabauto.receiver;

/** Only read-only high-voltage-ready or genuine vehicle movement can start
 * an automatic ignition journey. Cable charging must never be driving. */
final class T1AutoDrivePolicy {
    static final long MAX_INTEGRATION_MS=10000L;
    static final long GAP_CLOSE_MS=90000L;
    private T1AutoDrivePolicy(){}
    static boolean start(int ready,int cable,int gear,double kmh){
        return cable!=1 && (ready==1 ||
            (gear!=1 && Double.isFinite(kmh) && kmh>1.5));
    }
    static boolean stop(int ready,int cable,int gear,double kmh){
        return cable==1 || (ready==0 && gear==1 &&
            Double.isFinite(kmh) && kmh>=0 && kmh<0.6);
    }
    static double[] integrate(double volts,double amps,double kmh,long dtMs){
        if(dtMs<=0||dtMs>MAX_INTEGRATION_MS||
            !Double.isFinite(volts)||volts<100||volts>1000||
            !Double.isFinite(amps)||Math.abs(amps)>1000)
            return new double[]{0,0,0};
        double kwh=volts*amps*dtMs/3600000000d;
        double km=Double.isFinite(kmh)&&kmh>=0&&kmh<220?
            kmh*dtMs/3600000d:0;
        return new double[]{Math.max(0,kwh),Math.max(0,-kwh),km};
    }
}
