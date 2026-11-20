package ar.com.rango5gps.drivelabauto.receiver;
public final class T1AutoRangeMathTest {
    static void eq(double a,double b){
        if(Math.abs(a-b)>0.00000001)throw new AssertionError(a+" != "+b);
    }
    static void yes(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        T1AutoRangeMath.Cumulative a=new T1AutoRangeMath.Cumulative(
            1000,2,1.6,5,0.8);
        T1AutoRangeMath.Cumulative b=new T1AutoRangeMath.Cumulative(
            5000,2.04,1.63,5.02,0.82);
        T1AutoRangeMath.Delta full=T1AutoRangeMath.clip(a,b,0,6000);
        eq(full.distanceKm,0.04);eq(full.hvOutKwh,0.02);
        eq(full.hvInKwh,0.02);eq(full.net(),0);
        T1AutoRangeMath.Delta half=T1AutoRangeMath.clip(a,b,3000,7000);
        eq(half.distanceKm,0.02);eq(half.gpsKm,0.015);
        eq(half.hvOutKwh,0.01);eq(half.hvInKwh,0.01);
        yes(half.observedMs==2000);
        eq(T1AutoRangeMath.clip(a,b,5000,7000).distanceKm,0);
        eq(T1AutoRangeMath.clip(a,b,0,1000).distanceKm,0);
        eq(T1AutoRangeMath.clip(a,b,5000,4000).distanceKm,0);
        T1AutoRangeMath.Cumulative far=new T1AutoRangeMath.Cumulative(
            19000,5,3,8,2);
        eq(T1AutoRangeMath.clip(b,far,5000,20000).distanceKm,0);
        T1AutoRangeMath.Cumulative fuelA=
            new T1AutoRangeMath.Cumulative(
                1000,2,1.6,5,0.8,0.1,true,true);
        T1AutoRangeMath.Cumulative fuelB=
            new T1AutoRangeMath.Cumulative(
                5000,2.04,1.63,5.02,0.82,0.3,true,true);
        eq(T1AutoRangeMath.clip(fuelA,fuelB,3000,7000)
            .fuelCandidateL,0.1);
        yes(fuelB.hvValid&&fuelB.fuelValid);
        System.out.println("PASS exact user-selected minute interval with no unmeasured gap fill");
    }
}
