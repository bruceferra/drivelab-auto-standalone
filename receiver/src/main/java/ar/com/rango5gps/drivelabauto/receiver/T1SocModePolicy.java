package ar.com.rango5gps.drivelabauto.receiver;

/** Read-only OEM mappings verified on the Argentine T1.
 * SOC strategy 1040 and powertrain selection 1043 are different dimensions. */
final class T1SocModePolicy {
    static String name(int mode){
        if(mode==2)return "SMART";
        if(mode==4)return "FORZADO";
        if(mode==3)return "AUTO OEM (definido en APK, NO probado en esta T1)";
        return mode<0?"SIN LECTURA":"SIN MAPEO ("+mode+")";
    }
    static double socTarget(int rawInt,double rawFloat){
        if(rawInt>=0&&rawInt<=100)return rawInt;
        if(Double.isFinite(rawFloat)&&rawFloat>=0&&rawFloat<=100)return rawFloat;
        return Double.NaN;
    }
    private T1SocModePolicy(){}
}
