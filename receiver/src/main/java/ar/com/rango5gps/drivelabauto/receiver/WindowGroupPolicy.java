package ar.com.rango5gps.drivelabauto.receiver;

/** Conservative group-window plan based on observed real Jetour T1 states. */
public final class WindowGroupPolicy {
    public static final int[] IDS={2004,2005,2006,2007};
    public static final String[] NAMES={"Delantera izquierda","Delantera derecha",
        "Trasera izquierda","Trasera derecha"};
    public static final int CLOSE=1;
    public static final int VENT_QUARTER=3;
    public static final int SKIP=0;
    public static final int SEND=1;
    public static final int NEEDS_CLOSING_FIRST=2;
    public static final int INVALID=3;
    public static boolean validId(int id){
        for(int known:IDS)if(known==id)return true;
        return false;
    }
    public static boolean validState(int state){return state>=0&&state<=4;}
    public static int decision(int operation,int state){
        if((operation!=CLOSE&&operation!=VENT_QUARTER)||!validState(state))
            return INVALID;
        if(operation==CLOSE)return state==1?SKIP:SEND;
        if(state==3)return SKIP; // already in the quarter reporting band.
        return SEND; // explicit ventilation request from ANY readable state 0..4.
    }
    /** Four-window all-or-nothing preflight, no unknown commands. */
    public static int[] plan(int operation,int[] states){
        if(states==null||states.length!=IDS.length)
            throw new IllegalArgumentException("Faltan cuatro lecturas OEM");
        int[] decisions=new int[IDS.length];
        for(int i=0;i<IDS.length;i++){
            int d=decision(operation,states[i]);
            if(d==INVALID)throw new IllegalArgumentException(
                NAMES[i]+": estado OEM inválido "+states[i]);
            // Open and partially open windows are valid ventilation sources.
            decisions[i]=d;
        }
        return decisions;
    }
    public static String name(int id){
        for(int i=0;i<IDS.length;i++)if(IDS[i]==id)return NAMES[i];
        throw new IllegalArgumentException("ID no permitido");
    }
    public static String operation(int value){
        if(value==CLOSE)return "CIERRE TOTAL";
        if(value==VENT_QUARTER)return "MODO VENTILACIÓN ON";
        throw new IllegalArgumentException("Operación no permitida");
    }
    private WindowGroupPolicy(){}
}
