package ar.com.rango5gps.drivelabauto.receiver;
public final class T1ZonePolicyTest {
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] a){
        check(T1ZonePolicy.parked(1,0.0),"park");
        check(!T1ZonePolicy.parked(4,0.0),"drive blocked");
        check(!T1ZonePolicy.parked(-1,0.0),"unknown gear blocked");
        check(!T1ZonePolicy.parked(1,5.0),"rolling park blocked");
        check(T1ZonePolicy.nearby(-34.5,-58.5,-34.5,-58.5,300,10),"same point");
        check(!T1ZonePolicy.nearby(-34.5,-58.5,-34.6,-58.6,300,10),"far");
        check(!T1ZonePolicy.nearby(-34.5,-58.5,-34.5,-58.5,300,100),"bad GPS blocked");
        check(!T1ZonePolicy.nearby(Double.NaN,-58.5,-34.5,-58.5,300,10),"invalid GPS");
        check("EV".equals(T1ZonePolicy.name(1)),"EV");
        check("HEV".equals(T1ZonePolicy.name(2)),"HEV");
        System.out.println("OK: GPS advisory distance and parking gate (no OEM writes)");
    }
}
