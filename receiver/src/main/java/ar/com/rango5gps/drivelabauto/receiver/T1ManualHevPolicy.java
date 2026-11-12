package ar.com.rango5gps.drivelabauto.receiver;

/** Narrowly scoped parked-only powertrain mode experiment. */
final class T1ManualHevPolicy {
    static final int POWER_MODE_ID=1043,HEV=2,EV=1,EV_MAX=3;
    static boolean canRequest(int gear,double speed,int powerMode){
        // More restrictive than generic UI parked() <= 0.5 km/h:
        // a propulsion-mode write requires a fresh *exact* zero speed.
        return gear==1&&Double.isFinite(speed)&&speed>=0&&speed<=0.001&&
            (powerMode==EV||powerMode==EV_MAX);
    }
    /**
     * Why the 1043=2 screenshot did NOT result in a command.
     * The experiment only switches FROM EV or EV MAX TO HEV.
     * No unverified HEV->EV setter is introduced.
     */
    static String preflightReason(int gear,double speed,int powerMode,long sampleAgeMs){
        if(sampleAgeMs<0||sampleAgeMs>10000)
            return "SIN TELEMETRÍA RECIENTE: no se envió comando. "+
                "Esperá una muestra OEM nueva.";
        if(gear!=1)
            return "NO SE ENVIÓ: se requiere P (1033=1); actual 1033="+gear+".";
        if(!Double.isFinite(speed)||Math.abs(speed)>0.001)
            return "NO SE ENVIÓ: velocidad OEM debe ser exactamente "+
                "0 km/h; lectura="+speed+".";
        if(powerMode==HEV)
            return "YA ESTÁS EN HEV (1043=2). No se envió nada porque "+
                "el botón solo convierte EV/EV MAX a HEV. "+
                "Para ensayar la escritura, seleccioná EV (1043=1) "+
                "o EV MAX (1043=3) desde el menú original Jetour, "+
                "siempre detenido en P, y volvé a confirmar.";
        if(powerMode==EV||powerMode==EV_MAX)
            return "LISTO PARA ENSAYO EV→HEV: P confirmada, "+
                "0 km/h y modo "+(powerMode==EV?"EV":"EV MAX")+
                ". Escritura 1043=2 comprobada en P en esta unidad; "+
                "no está validada en movimiento.";
        return "NO SE ENVIÓ: 1043="+powerMode+
            " no es un modo de partida EV/EV MAX verificado.";
    }
    static String currentMode(int raw){
        if(raw==HEV)return "HEV";
        if(raw==EV)return "EV";
        if(raw==EV_MAX)return "EV MAX";
        return "DESCONOCIDO ("+raw+")";
    }
    private T1ManualHevPolicy(){}
}
