package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Debounces road-category changes before displaying a transient road banner.
 * Pure JVM logic: never invokes vehicle commands or changes driver settings.
 *
 * A first recognized road is announced so the user can test the offline map.
 * Subsequent changes need two consecutive samples, with unknown needing three
 * (to avoid blinking due to brief GPS loss/ambiguous parallel roads).
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
            1:current==T1OptimizedModePolicy.Road.UNKNOWN?3:2;
        if(votes<required)return null;
        stable=current;
        candidate=null;
        votes=0;
        return stable;
    }

    static String popupText(T1OptimizedModePolicy.Road road){
        switch(road){
            case CITY:return "ENTRANDO A CIUDAD";
            case ROAD:return "ENTRANDO A CARRETERA";
            case MOTORWAY:return "ENTRANDO A AUTOPISTA";
            default:return "ZONA DESCONOCIDA";
        }
    }
}
