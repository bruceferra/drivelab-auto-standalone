package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Classification ONLY: prevent T1Launcher EXT.BATT properties from being
 * falsely displayed as measurements of the 12-V auxiliary battery.
 * Raw OEM value scaling may be undocumented.
 */
final class T1ElectricalEvidence {
    static String classifyVoltage(double value) {
        if (!Double.isFinite(value) || value <= 0)
            return "SIN LECTURA VÁLIDA";
        if (value >= 180 && value <= 1000)
            return "POSIBLE ALTA TENSIÓN HV (no es 12 V)";
        if (value >= 10 && value <= 16)
            return "compatible con sistema 12 V; aún sin confirmar";
        return "DOMINIO / ESCALA NO VERIFICADOS";
    }
    static String report(String rawFloat) {
        if(rawFloat==null||!rawFloat.startsWith("RAW float="))
            return "SIN LECTURA VÁLIDA";
        try{
            double raw=Double.parseDouble(rawFloat.substring("RAW float=".length()));
            return rawFloat+" · "+classifyVoltage(raw);
        }catch(NumberFormatException ex){
            return "SIN LECTURA VÁLIDA";
        }
    }
    private T1ElectricalEvidence(){}
}
