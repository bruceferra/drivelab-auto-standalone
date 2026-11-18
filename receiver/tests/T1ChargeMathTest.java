package ar.com.rango5gps.drivelabauto.receiver;
public final class T1ChargeMathTest {
    static void equal(double a,double b){if(Math.abs(a-b)>1e-9)
        throw new AssertionError(a+" != "+b);}
    static void yes(boolean x){if(!x)throw new AssertionError();}
    public static void main(String[] args){
        yes(T1ChargeMath.plugged(1));
        yes(!T1ChargeMath.plugged(2)&&!T1ChargeMath.plugged(-1));
        yes(T1ChargeMath.unplugged(0));
        double[] x=T1ChargeMath.integrate(1,320,-20,5000);
        equal(x[0],20*5/3600d);
        equal(x[1],320*20*5/3600000d);
        equal(T1ChargeMath.integrate(0,320,-20,5000)[1],0);
        equal(T1ChargeMath.integrate(1,320,-20,60000)[1],0);
        equal(T1ChargeMath.integrate(1,320,20,5000)[1],0);
        equal(T1ChargeMath.integrate(2,320,-20,5000)[1],0);
        yes(T1ChargeMath.validSoc(14)&&T1ChargeMath.validSoc(100));
        yes(!T1ChargeMath.validSoc(120));
        System.out.println("PASS charge only with explicit cable 1060 and HV-in current");
    }
}
