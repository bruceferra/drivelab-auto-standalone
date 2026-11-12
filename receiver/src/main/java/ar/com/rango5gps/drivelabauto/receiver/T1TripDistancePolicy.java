package ar.com.rango5gps.drivelabauto.receiver;

/** Incremental distance policy shared by Viaje Total, Viaje 1 and Viaje 2.
 *
 * Critical: the presence of GPS permission must NEVER disable OEM-speed
 * integration. GPS is used only for a bounded interval when OEM speed was
 * not readable. Never add both sources to the same interval.
 *
 * Calibration is provisional, based on a measured dashboard comparison:
 * raw integrated 24.55 km vs stock cluster 22.9 km.
 * Raw speed remains untouched in the trip CSV for later calibration.
 */
final class T1TripDistancePolicy {
    static final double OEM_KM_FACTOR=22.9d/24.55d;
    enum Source { OEM_SPEED_CAL, GPS_FALLBACK, STOPPED, NO_VALID_SOURCE, TIME_GAP }

    static final class Step {
        final double km;
        final Source source;
        Step(double distance,Source origin){
            km=distance;source=origin;
        }
        String sourceNote(){
            switch(source){
                case OEM_SPEED_CAL:return "OEM_SPEED_CAL_0.93279_PRELIMINAR";
                case GPS_FALLBACK:return "GPS_FALLBACK_OEM_MISSING";
                case STOPPED:return "OEM_SPEED_ZERO";
                case TIME_GAP:return "TIME_GAP_NO_DISTANCE";
                default:return "NO_VALID_SPEED_OR_GPS";
            }
        }
    }
    static Step integrate(double speedKmh,long sampleElapsedMs,
                          double freshGpsKm,long gpsFixAgeMs) {
        // No motion may be inferred over a service/process pause or long
        // data gap: that must remain an explicit unmeasured interval.
        if(sampleElapsedMs<=0||sampleElapsedMs>10000L)
            return new Step(0,Source.TIME_GAP);
        if(Double.isFinite(speedKmh)&&speedKmh>=0&&speedKmh<=220){
            if(speedKmh<0.1)return new Step(0,Source.STOPPED);
            return new Step(
                speedKmh*sampleElapsedMs/3600000.0*OEM_KM_FACTOR,
                Source.OEM_SPEED_CAL);
        }
        // GPS fallback only if OEM sample actually failed. The accepted
        // displacement is already filtered for accuracy and apparent speed.
        // Bound it to plausible travel in this measurement interval too.
        if(Double.isFinite(freshGpsKm)&&freshGpsKm>=0.008&&
           gpsFixAgeMs>=0&&gpsFixAgeMs<=10000L&&
           freshGpsKm<=sampleElapsedMs/1000.0*0.055){
            return new Step(freshGpsKm,Source.GPS_FALLBACK);
        }
        return new Step(0,Source.NO_VALID_SOURCE);
    }
    private T1TripDistancePolicy(){}
}
