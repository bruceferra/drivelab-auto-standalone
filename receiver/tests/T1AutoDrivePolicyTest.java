package ar.com.rango5gps.drivelabauto.receiver;
public final class T1AutoDrivePolicyTest {
    static void yes(boolean b){if(!b)throw new AssertionError();}
    static void near(double x,double y){if(Math.abs(x-y)>1e-9)
        throw new AssertionError(x+" != "+y);}
    public static void main(String[] args){
        yes(T1AutoDrivePolicy.start(1,0,1,0));
        yes(T1AutoDrivePolicy.start(-1,0,4,32));
        yes(!T1AutoDrivePolicy.start(1,1,1,0));
        yes(!T1AutoDrivePolicy.start(-1,-1,1,0));
        yes(!T1AutoDrivePolicy.start(1,-1,1,0));
        yes(T1AutoDrivePolicy.start(-1,-1,4,18));
        yes(T1AutoDrivePolicy.stop(0,0,1,0));
        yes(T1AutoDrivePolicy.stop(1,1,1,0));
        yes(!T1AutoDrivePolicy.stop(-1,0,1,0));
        double[] x=T1AutoDrivePolicy.integrate(320,20,36,4000);
        near(x[0],320*20*4/3600000d);near(x[1],0);
        near(x[2],36*4/3600d);
        x=T1AutoDrivePolicy.integrate(320,-20,36,4000);
        near(x[0],0);near(x[1],320*20*4/3600000d);
        yes(T1AutoDrivePolicy.integrate(320,20,36,90000)[0]==0);
        System.out.println("PASS auto OEM ignition, cable isolation, no gap energy");
    }
}
