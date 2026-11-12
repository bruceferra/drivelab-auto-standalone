package ar.com.rango5gps.drivelabauto.receiver;

public final class T1ModeAndVoltageEvidenceTest {
    private static void yes(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }
    public static void main(String[] args){
        yes("HEV".equals(T1ManualHevPolicy.currentMode(2)),"1043=2 must read HEV");
        String already=T1ManualHevPolicy.preflightReason(1,0,2,150);
        yes(already.contains("YA ESTÁS EN HEV"),"photo-specific HEV condition");
        yes(already.contains("No se envió"),"no write attempted");
        yes(!T1ManualHevPolicy.canRequest(1,0,2),"no write if HEV already set");
        yes(T1ManualHevPolicy.canRequest(1,0,1),"manual EV->HEV possible only after user's confirmation");
        yes(T1ManualHevPolicy.canRequest(1,0,3),"EV MAX candidate");
        yes(!T1ManualHevPolicy.canRequest(1,0.2,1),"safety: not exactly zero speed");
        yes(!T1ManualHevPolicy.canRequest(4,0,1),"safety: in D");
        yes(T1ManualHevPolicy.preflightReason(1,0,1,25000)
            .contains("SIN TELEMETRÍA RECIENTE"),"stale sample");
        yes(T1ManualHevPolicy.preflightReason(1,0,1,150)
            .contains("LISTO PARA ENSAYO"),"ready state");
        yes(T1ElectricalEvidence.classifyVoltage(315)
            .contains("POSIBLE ALTA TENSIÓN"),"315 cannot be assigned to 12V");
        yes(T1ElectricalEvidence.classifyVoltage(0)
            .contains("SIN LECTURA"),"zero IBS reading not physical 0V");
        yes(T1ElectricalEvidence.classifyVoltage(12.7)
            .contains("compatible con sistema 12 V"),"candidate 12V");
        yes(T1ElectricalEvidence.report("RAW float=315.0")
            .contains("no es 12 V"),"explicit high-voltage warning");
        yes(T1ElectricalEvidence.report("RAW float=0.0")
            .contains("SIN LECTURA"),"invalid zero");
        System.out.println("OK: photographed 1043=2 mode, preflight/no-write and 315V not-12V diagnostics");
    }
}
