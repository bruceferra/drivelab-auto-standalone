package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Observed OEM HVAC state 1004: 2 = exterior intake/recirculation OFF;
 * 1 = recirculation ON. These are observations, not motor commands.
 * The owner now requests automatic interior-air preference on app launch.
 * This pure policy ONLY supports 1004=1; OEM defog/visibility overrides
 * take precedence over repeated writes.
 */
public final class RecirculationPolicy {
    public static final int ID=1004;
    public static final int RECIRC_ON=1;
    public static final int RECIRC_OFF=2;
    private RecirculationPolicy(){}
    public static boolean valid(int state){
        return state==RECIRC_ON||state==RECIRC_OFF;
    }
    /** Returns whether a single 1004=1 write is necessary. */
    public static boolean needsActivation(int state){
        if(!valid(state))throw new IllegalArgumentException(
            "Lectura 1004 no reconocida: "+state);
        return state==RECIRC_OFF;
    }
    public static String describe(int state){
        switch(state){
            case RECIRC_ON:return "RECIRCULACIÓN ENCENDIDA (aire interior)";
            case RECIRC_OFF:return "RECIRCULACIÓN APAGADA (puede entrar aire exterior)";
            default:return "ESTADO 1004 DESCONOCIDO ("+state+")";
        }
    }
}
