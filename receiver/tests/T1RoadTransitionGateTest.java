package ar.com.rango5gps.drivelabauto.receiver;

/** Pure JVM: announce only stable category transitions, no repetition. */
public final class T1RoadTransitionGateTest {
    private static final T1OptimizedModePolicy.Road CITY=T1OptimizedModePolicy.Road.CITY;
    private static final T1OptimizedModePolicy.Road MOTORWAY=T1OptimizedModePolicy.Road.MOTORWAY;
    private static final T1OptimizedModePolicy.Road ROAD=T1OptimizedModePolicy.Road.ROAD;
    private static final T1OptimizedModePolicy.Road UNKNOWN=T1OptimizedModePolicy.Road.UNKNOWN;
    private static void equal(Object actual,Object expected,String why){
        if(actual==null?expected!=null:!actual.equals(expected))
            throw new AssertionError(why+": actual="+actual+" expected="+expected);
    }
    public static void main(String[] args){
        T1RoadTransitionGate gate=new T1RoadTransitionGate();
        equal(gate.observe(CITY),CITY,"First known class announced");
        equal(gate.observe(CITY),null,"No repeat while staying in city");
        equal(gate.observe(MOTORWAY),null,"First motorway sample insufficient");
        equal(gate.observe(CITY),null,"GPS flicker cancelled");
        equal(gate.observe(MOTORWAY),null,"Motorway sample 1");
        equal(gate.observe(MOTORWAY),MOTORWAY,"Motorway sample 2");
        equal(gate.observe(MOTORWAY),null,"No repeated motorway popup");
        equal(gate.observe(UNKNOWN),null,"GPS loss 1 ignored");
        equal(gate.observe(UNKNOWN),null,"GPS loss 2 ignored");
        equal(gate.observe(MOTORWAY),null,"GPS recovered, clear candidate");
        equal(gate.observe(ROAD),null,"Road sample 1");
        equal(gate.observe(ROAD),ROAD,"Road sample 2");
        equal(gate.observe(UNKNOWN),null,"Unknown 1");
        equal(gate.observe(UNKNOWN),null,"Unknown 2");
        equal(gate.observe(UNKNOWN),UNKNOWN,"Unknown 3 stable");
        equal(gate.observe(UNKNOWN),null,"No repeated unknown");
        equal(T1RoadTransitionGate.popupText(CITY),"ENTRANDO A CIUDAD","City label");
        equal(T1RoadTransitionGate.popupText(ROAD),"ENTRANDO A CARRETERA","Road label");
        equal(T1RoadTransitionGate.popupText(MOTORWAY),"ENTRANDO A AUTOPISTA","Highway label");
        equal(T1RoadTransitionGate.popupText(UNKNOWN),"ZONA DESCONOCIDA","Unknown label");
        T1RoadTransitionGate fresh=new T1RoadTransitionGate();
        equal(fresh.observe(UNKNOWN),null,"Initial unknown not announced");
        equal(fresh.observe(UNKNOWN),null,"Initial unknown still pending");
        equal(fresh.observe(UNKNOWN),UNKNOWN,"Persistent initial unknown shown");
        System.out.println("OK: debounced large road popups only on transitions");
    }
}
