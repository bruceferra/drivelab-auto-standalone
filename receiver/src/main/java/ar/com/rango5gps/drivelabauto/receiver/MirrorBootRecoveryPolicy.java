package ar.com.rango5gps.drivelabauto.receiver;

/** Boot recovery only for confirmed, durable relative mirror pulses. */
final class MirrorBootRecoveryPolicy {
    private MirrorBootRecoveryPolicy(){}
    static boolean recoverable(int left,int right,boolean inFlight){
        return !inFlight && left>=0 && right>=0 &&
            left<=MirrorParkingPolicy.MAX_PULSES_PER_SIDE &&
            right<=MirrorParkingPolicy.MAX_PULSES_PER_SIDE && left+right>0;
    }
    static boolean parkedForRestore(int gear,double speedKmh){
        return gear==1 && Double.isFinite(speedKmh) &&
            speedKmh>=0 && speedKmh<=0.5;
    }
}
