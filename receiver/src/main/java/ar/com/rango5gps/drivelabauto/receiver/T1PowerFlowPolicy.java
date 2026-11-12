package ar.com.rango5gps.drivelabauto.receiver;

/** OEM-only indications: 1035 RPM and 1064 HV current.
 * Polarity is a candidate mapping (negative == charging) and must be
 * checked on the user's Jetour; this does not report grid kWh or recovery. */
final class T1PowerFlowPolicy {
    enum Engine { ON, OFF, UNKNOWN }
    enum Battery { CHARGING, DISCHARGING, REST, UNKNOWN }
    static Engine engine(double rpm) {
        if(!Double.isFinite(rpm)||rpm<0||rpm>12000)return Engine.UNKNOWN;
        return rpm>0?Engine.ON:Engine.OFF;
    }
    static Battery battery(double currentA) {
        if(!Double.isFinite(currentA)||Math.abs(currentA)>1500)
            return Battery.UNKNOWN;
        if(currentA < -0.75)return Battery.CHARGING;
        if(currentA > 0.75)return Battery.DISCHARGING;
        return Battery.REST;
    }
    static String engineLabel(Engine status) {
        if(status==Engine.ON)return "ENCENDIDO";
        if(status==Engine.OFF)return "APAGADO";
        return "SIN LECTURA";
    }
    static String batteryLabel(Battery state) {
        switch(state){
            case CHARGING:return "CARGANDO";
            case DISCHARGING:return "DESCARGANDO";
            case REST:return "EN REPOSO";
            default:return "SIN LECTURA";
        }
    }
    private T1PowerFlowPolicy(){}
}
