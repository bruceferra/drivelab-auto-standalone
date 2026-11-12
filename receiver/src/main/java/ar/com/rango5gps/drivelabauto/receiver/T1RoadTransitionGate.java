package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Debounces road-category changes before displaying a transient road banner.
 * Pure JVM logic: never invokes vehicle commands or changes driver settings.
 *
 * A first recognized road is announced so the user can test the offline map.
 * Subsequent recognized changes need two consecutive samples. UNKNOWN
 * requires at least 7 observations (~70s at 10s/call) so intermittent GPS
 * failures don't produce "Desconocido -> Ciudad" popup flicker.
 */
final class T1RoadTransitionGate {
    private T1OptimizedModePolicy.Road stable;
    private T1OptimizedModePolicy.Road candidate;
    private int votes;

    T1OptimizedModePolicy.Road observe(T1OptimizedModePolicy.Road current){
        if(current==null)current=T1OptimizedModePolicy.Road.UNKNOWN;
        if(current==stable){
            candidate=null;
            votes=0;
            return null;
        }
        if(current!=candidate){
            candidate=current;
            votes=1;
        }else{
            votes++;
        }
        // Immediately show first identified category; don't announce
        // UNKNOWN on first failed GPS read.
        int required=stable==null&&current!=T1OptimizedModePolicy.Road.UNKNOWN?
            1:current==T1OptimizedModePolicy.Road.UNKNOWN?7:2;
        if(votes<required)return null;
        stable=current;
        candidate=null;
        votes=0;
        return stable;
    }

    static String popupText(T1OptimizedModePolicy.Road road){
        switch(road){
            case CITY:return "CIUDAD";
            case ROAD:return "CARRETERA";
            case MOTORWAY:return "AUTOPISTA";
            default:return "DESCONOCIDO";
        }
    }
}
