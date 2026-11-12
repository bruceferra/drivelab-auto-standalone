package ar.com.rango5gps.drivelabauto.receiver;

/** Strict OEM gear/speed preflight for ONE manual 1043 mode request.
 * 1033=1 is P (zero only); 1033=4 is D (0..160 km/h).
 * Not a brake-pedal interlock and not authorization by itself: the
 * OEM powertrain service remains the final authority for mode acceptance.
 */
final class T1DriveModeGate {
    private T1DriveModeGate() {}
    static boolean allowed(int gear,double speed){
        if(!Double.isFinite(speed)||speed<0)return false;
        if(gear==1)return speed<=0.001;
        return gear==4&&speed<=160.0;
    }
    static String name(int gear){
        return gear==1?"P":gear==4?"D":"NO VALIDADA ("+gear+")";
    }
}
