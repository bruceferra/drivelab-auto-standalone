package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Conservative offline traffic-flow inference. Not live traffic data.
 * OSM is used ONLY to define road type and optional posted speed.
 * A short junction/traffic-light stop must not be called "congestion".
 * Slow speed must persist on the same OSM road category for 20-35 seconds.
 */
final class T1TrafficFlowPolicy {
    private T1TrafficFlowPolicy(){}
    static double referenceKmh(int road,int osmLimit){
        if(osmLimit>=20&&osmLimit<=150)return osmLimit;
        switch(road){
            case T1EcoTripMetrics.CITY:return 40;
            case T1EcoTripMetrics.ROAD:return 80;
            case T1EcoTripMetrics.MOTORWAY:return 110;
            default:return Double.NaN;
        }
    }
    static double thresholdKmh(int road,int osmLimit){
        double base=referenceKmh(road,osmLimit);
        if(!Double.isFinite(base))return Double.NaN;
        if(road==T1EcoTripMetrics.CITY)
            return Math.max(10,Math.min(22,base*.40));
        if(road==T1EcoTripMetrics.ROAD)
            return Math.max(20,Math.min(48,base*.45));
        if(road==T1EcoTripMetrics.MOTORWAY)
            return Math.max(32,Math.min(68,base*.55));
        return Double.NaN;
    }
    static int minSlowSeconds(int road){
        switch(road){
            case T1EcoTripMetrics.CITY:return 35;
            case T1EcoTripMetrics.ROAD:return 25;
            case T1EcoTripMetrics.MOTORWAY:return 20;
            default:return Integer.MAX_VALUE;
        }
    }
    static boolean slow(int road,int osmLimit,double observedKmh){
        double threshold=thresholdKmh(road,osmLimit);
        return Double.isFinite(threshold)&&
            Double.isFinite(observedKmh)&&observedKmh>=0&&
            observedKmh<threshold;
    }
}
