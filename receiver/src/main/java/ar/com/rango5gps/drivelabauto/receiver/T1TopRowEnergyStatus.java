package ar.com.rango5gps.drivelabauto.receiver;

/** Read-only, human-readable OEM energy state for the fixed first-row toolbar.
 * Never use a saved request/profile as proof of actual 1040/1041/1046 state.
 */
final class T1TopRowEnergyStatus {
    static final int SMART=2,FORCED=4;
    private T1TopRowEnergyStatus(){}
    static String regeneration(int raw){
        switch(raw){
            case 1:return "REGEN · BAJA";
            case 2:return "REGEN · MEDIA";
            case 3:return "REGEN · ALTA";
            default:return "REGEN · SIN LECTURA";
        }
    }
    static String battery(int mode){
        switch(mode){
            case SMART:return "BATERÍA · SMART";
            case FORCED:return "BATERÍA · FORZADO";
            default:return "BATERÍA · SIN LECTURA";
        }
    }
    static String targetSoc(int mode,int raw){
        if(mode==SMART)return "SOC OBJ. · —"; // Not active in SMART
        if(mode!=FORCED)return "SOC OBJ. · S/L";
        int percent=T1SocTargetCodec.observedPercentForRaw(raw);
        return percent>=0?"SOC OBJ. · "+percent+"%":"SOC OBJ. · ?";
    }
    static String description(int mode,int raw){
        if(mode==SMART)return "Gestión de batería SMART confirmada en OEM 1040=2. "+
            "No hay objetivo SOC forzado activo.";
        if(mode!=FORCED)return "Estrategia de batería sin lectura OEM 1040 confiable.";
        int percent=T1SocTargetCodec.observedPercentForRaw(raw);
        return percent>=0?"OEM 1040=4 FORZADO; SOC objetivo "+percent+
            "% observado por getter 1041="+raw:
            "OEM 1040=4 FORZADO, pero porcentaje SOC 1041="+raw+
            " sin mapeo confirmado. Verificar menú original Jetour.";
    }
}
