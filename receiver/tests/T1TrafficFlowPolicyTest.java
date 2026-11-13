package ar.com.rango5gps.drivelabauto.receiver;

public final class T1TrafficFlowPolicyTest {
    private static void yes(boolean b,String reason){
        if(!b)throw new AssertionError(reason);
    }
    private static void near(double a,double b,String reason){
        if(Math.abs(a-b)>0.0001)
            throw new AssertionError(reason+" actual="+a+" expected="+b);
    }
    private static void samples(T1EcoTripMetrics m,int count,int road,
                                double speed,int limit,long base){
        for(int i=0;i<count;i++)
            m.add(base+i*5000L,.02,5,road,speed,0,limit);
    }
    public static void main(String[] args){
        // At the same 30 km/h city may be normal but motorway is slow.
        yes(!T1TrafficFlowPolicy.slow(T1EcoTripMetrics.CITY,40,30),
            "30 km/h normal city");
        yes(T1TrafficFlowPolicy.slow(T1EcoTripMetrics.MOTORWAY,110,30),
            "30 km/h slow motorway");
        yes(!T1TrafficFlowPolicy.slow(T1EcoTripMetrics.MOTORWAY,110,90),
            "90 km/h normal motorway");
        yes(T1TrafficFlowPolicy.slow(T1EcoTripMetrics.ROAD,80,30),
            "30 km/h slow carretera");
        yes(!T1TrafficFlowPolicy.slow(T1EcoTripMetrics.UNKNOWN,110,5),
            "cannot label traffic without OSM");
        near(T1TrafficFlowPolicy.referenceKmh(T1EcoTripMetrics.MOTORWAY,90),
            90,"posted speed used");

        T1EcoTripMetrics redLight=new T1EcoTripMetrics();
        samples(redLight,4,T1EcoTripMetrics.CITY,0,40,100000);
        near(redLight.congestedKm(T1EcoTripMetrics.CITY),0,
            "brief city red light must not be traffic");
        near(redLight.congestedSeconds(T1EcoTripMetrics.CITY),0,
            "brief city red light seconds should remain normal");

        T1EcoTripMetrics cityJam=new T1EcoTripMetrics();
        samples(cityJam,7,T1EcoTripMetrics.CITY,5,50,200000);
        near(cityJam.congestedKm(T1EcoTripMetrics.CITY),.14,
            "sustained 35s low speed retrospectively counted");
        near(cityJam.congestedSeconds(T1EcoTripMetrics.CITY),35,
            "city congestion time");

        T1EcoTripMetrics motorwayJam=new T1EcoTripMetrics();
        samples(motorwayJam,4,T1EcoTripMetrics.MOTORWAY,25,120,300000);
        near(motorwayJam.congestedKm(T1EcoTripMetrics.MOTORWAY),.08,
            "20s motorway slowdown counts as traffic");
        samples(motorwayJam,4,T1EcoTripMetrics.MOTORWAY,100,120,320000);
        near(motorwayJam.normalKm(T1EcoTripMetrics.MOTORWAY),.08,
            "recovery normal motorway");
        near(motorwayJam.congestionPercent(T1EcoTripMetrics.MOTORWAY),50,
            "traffic percent within whole trip");
        yes(motorwayJam.trafficMix().contains("congestión probable"),
            "explicitly labels inference, not verified traffic");

        T1EcoTripMetrics switchRoad=new T1EcoTripMetrics();
        samples(switchRoad,3,T1EcoTripMetrics.MOTORWAY,20,110,400000);
        samples(switchRoad,3,T1EcoTripMetrics.CITY,6,40,415000);
        near(switchRoad.congestedKm(T1EcoTripMetrics.MOTORWAY),0,
            "road change must reset 20s window");
        near(switchRoad.congestedKm(T1EcoTripMetrics.CITY),0,
            "road change must reset 35s window");
        T1EcoTripMetrics combined=new T1EcoTripMetrics();
        combined.combine(motorwayJam);
        near(combined.congestedKm(T1EcoTripMetrics.MOTORWAY),.08,
            "historical aggregation retains congestion");
        near(combined.normalKm(T1EcoTripMetrics.MOTORWAY),.08,
            "historical aggregation retains normal distance");
        System.out.println("OK: congestion normal vs probable on city, carretera, motorway; brief stop denied; OSM posted speed, time and distance.");
    }
}
