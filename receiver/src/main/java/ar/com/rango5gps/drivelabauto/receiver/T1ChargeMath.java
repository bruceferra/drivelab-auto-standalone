package ar.com.rango5gps.drivelabauto.receiver;

/** Pure read-only charge integration. 1060 must explicitly report cable
 * present. HV current sign remains provisional until on-car confirmation.
 * This is BATTERY-IN energy, not grid meter electricity. */
final class T1ChargeMath {
    static final int CABLE_PRESENT=1,CABLE_ABSENT=0;
    private T1ChargeMath(){}
    static boolean plugged(int raw){return raw==CABLE_PRESENT;}
    static boolean unplugged(int raw){return raw==CABLE_ABSENT;}
    static boolean validSoc(double soc){
        return Double.isFinite(soc)&&soc>=0&&soc<=100;
    }
    static double[] integrate(int cable,double volts,double amps,long dtMs){
        if(!plugged(cable)||!Double.isFinite(volts)||
           !Double.isFinite(amps)||volts<100||volts>1000||
           amps>=-0.5||amps< -1000||dtMs<=0||dtMs>12000)
            return new double[]{0,0};
        double ah=-amps*dtMs/3600000d;
        return new double[]{ah,ah*volts/1000d};
    }
    static boolean charging(int cable,double volts,double amps){
        return plugged(cable)&&Double.isFinite(volts)&&
            Double.isFinite(amps)&&volts>=100&&volts<=1000&&
            amps< -0.5&&amps>= -1000;
    }
}
