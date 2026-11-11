package ar.com.rango5gps.drivelabauto.receiver;

/** Location-based mode *recommendations*, no OEM write route.
 * GPS quality and OEM gear gates are checked by the Android caller. */
final class T1ZonePolicy {
    static final double DEFAULT_RADIUS_METERS=300;
    static double haversineMeters(double lat1,double lon1,double lat2,double lon2) {
        if(!Double.isFinite(lat1)||!Double.isFinite(lon1)||!Double.isFinite(lat2)||
           !Double.isFinite(lon2)||Math.abs(lat1)>90||Math.abs(lat2)>90||
           Math.abs(lon1)>180||Math.abs(lon2)>180)return Double.NaN;
        double a=Math.toRadians(lat2-lat1),b=Math.toRadians(lon2-lon1);
        double h=Math.pow(Math.sin(a/2),2)+
            Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
            Math.pow(Math.sin(b/2),2);
        return 6371000d*2*Math.asin(Math.min(1d,Math.sqrt(Math.max(0,h))));
    }
    static boolean nearby(double lat,double lon,double targetLat,double targetLon,
                          double radius,double accuracy){
        double dist=haversineMeters(lat,lon,targetLat,targetLon);
        return Double.isFinite(dist)&&Double.isFinite(accuracy)&&accuracy<=75d&&
            radius>=100&&radius<=2000&&dist<=radius;
    }
    static boolean parked(int gear,double speed){
        return gear==1&&Double.isFinite(speed)&&speed>=0&&speed<=0.5;
    }
    static String name(int mode){
        return mode==1?"EV":mode==2?"HEV":mode==3?"EV MAX":"SIN LECTURA";
    }
    private T1ZonePolicy(){}
}
