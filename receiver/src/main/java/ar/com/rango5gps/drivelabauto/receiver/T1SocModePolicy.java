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
        // T1Launcher observations: raw 56 means selected FORZADO 50%.
        // Unknown raw encodings (60/70% not photographed) stay unknown.
        // rawFloat=1 is not a valid percentage for this getter.
        int selected=T1SocTargetCodec.observedPercentForRaw(rawInt);
        return selected<0?Double.NaN:selected;
    }
    private T1SocModePolicy(){}
}
