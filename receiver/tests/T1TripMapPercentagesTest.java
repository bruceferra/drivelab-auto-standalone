package ar.com.rango5gps.drivelabauto.receiver;

/** No Android dependency: exactly 100% across four road categories,
 * weighted by GPS METERS (not sample counts) and UNKNOWN in denominator.
 */
public final class T1TripMapPercentagesTest {
    private static int checks=0;
    private static void near(double value,double expected){
        checks++;
        if(Math.abs(value-expected)>1e-9)
            throw new AssertionError("expected "+expected+" got "+value);
    }
    private static void bad(Runnable action){
        checks++;
        try{action.run();throw new AssertionError("bad input accepted");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        T1TripMapPercentages stats=new T1TripMapPercentages();
        // Unequal sample distances: 5km total. UNKNOWN is not dropped.
        stats.add(1000,new int[]{0,0,0});
        stats.add(3000,new int[]{2,1,2});
        stats.add(1000,new int[]{3,3,3});
        near(stats.totalMeters,5000);
        for(int i=0;i<3;i++){
            double total=0;
            for(int j=0;j<4;j++)total+=stats.categoryMeters[i][j];
            near(total,5000);
        }
        near(stats.percent(stats.categoryMeters[0][0]),20.0);
        near(stats.percent(stats.categoryMeters[0][2]),60.0);
        near(stats.percent(stats.categoryMeters[0][3]),20.0);
        near(stats.percent(stats.categoryMeters[1][1]),60.0);
        near(stats.coverage(0),80.0);
        near(stats.coverage(1),80.0);
        near(stats.agreementMeters[0][1],1000.0);
        near(stats.sharedMeters[0][1],4000.0);
        near(stats.pairAgreement(0,1),25.0);
        near(stats.pairAgreement(0,2),100.0);
        near(stats.threeAgreeMeters,1000.0);
        near(stats.percent(stats.threeAgreeMeters),20.0);
        bad(()->stats.add(-1,new int[]{0,0,0}));
        bad(()->stats.add(100,new int[]{0,4,0}));
        bad(()->stats.add(100,new int[]{0,1}));
        System.out.println("PASS "+checks+" checks: GPS kilometer weighted city/route/motorway/unknown percentages and pairwise agreement");
    }
}
