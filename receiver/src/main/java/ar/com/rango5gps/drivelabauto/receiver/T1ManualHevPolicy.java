package ar.com.rango5gps.drivelabauto.receiver;

/** Narrowly scoped parked-only powertrain mode experiment. */
final class T1ManualHevPolicy {
    static final int POWER_MODE_ID=1043,HEV=2,EV=1,EV_MAX=3;
    static boolean canRequest(int gear,double speed,int powerMode){
        return T1ZonePolicy.parked(gear,speed)&&
            (powerMode==EV||powerMode==EV_MAX);
    }
    private T1ManualHevPolicy(){}
}
