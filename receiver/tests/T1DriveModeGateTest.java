package ar.com.rango5gps.drivelabauto.receiver;
public final class T1DriveModeGateTest {
    public static void main(String[] args){
        yes(1,0);yes(4,0);yes(4,0.1);yes(4,30);yes(4,120);yes(4,160);
        no(1,1);no(2,0);no(3,0);no(5,0);no(4,-1);no(4,160.01);
        no(4,Double.NaN);no(4,Double.POSITIVE_INFINITY);
        no(1,Double.NaN);no(1,-1);
        System.out.println("T1DriveModeGateTest OK — D moving allowed, P stationary only");
    }
    static void yes(int g,double s){if(!T1DriveModeGate.allowed(g,s))throw new AssertionError("expected yes "+g+"/"+s);}
    static void no(int g,double s){if(T1DriveModeGate.allowed(g,s))throw new AssertionError("expected no "+g+"/"+s);}
}
