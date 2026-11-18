package ar.com.rango5gps.drivelabauto.receiver;

/** Owner photographs, read-only Jetour 1041 readback mapping. */
public final class T1SocTargetCodecTest {
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    private static void close(double actual,double wanted,String why){
        check(Math.abs(actual-wanted)<0.001,why);
    }
    public static void main(String[] args){
        // raw72 was physically paired with FORZADO 70 in the OEM
        // Jetour menu; raw56 stayed paired with FORZADO 50.
        final int[] raw={30,40,48,56,64,72};
        final int[] selected={20,30,40,50,60,70};
        for(int i=0;i<raw.length;i++){
            int intRaw=raw[i],percent=selected[i];
            check(T1SocTargetCodec.observedPercentForRaw(intRaw)==percent,
                "1041 OEM raw should translate to displayed percent "+percent);
            check(T1SocTargetCodec.observedRawForPercent(percent)==intRaw,
                "observed reverse map for read-only comparison");
            check(T1SocTargetCodec.confirmsRequested(percent,intRaw,1.0),
                "raw "+intRaw+" confirms "+percent+"% despite floatRaw=1");
            close(T1SocModePolicy.socTarget(intRaw,1.0),percent,
                "Cockpit must show correct percent");
        }
        // Critical: old readInt==target compared 40 with 40% and falsely
        // confirmed while the real T1Launcher display says 30%.
        check(!T1SocTargetCodec.confirmsRequested(40,40,1.0),
            "raw 40 MUST NOT confirm target 40%; it's observed 30%");
        check(!T1SocTargetCodec.confirmsRequested(50,48,1.0),
            "raw48 is 40%, not requested50%");
        check(!T1SocTargetCodec.confirmsRequested(30,30,1.0),
            "raw30 is 20%, not requested30%");
        check(!T1SocTargetCodec.confirmsRequested(50,50,1.0),
            "raw50 has no proved 1041 target mapping");
        check(T1SocTargetCodec.confirmsRequested(60,64,1.0),
            "Owner confirmed FORZADO 60% on Jetour OEM menu with raw64");
        check(T1SocTargetCodec.confirmsRequested(70,72,1.0),
            "Owner OEM 70% selected with raw72; 70 is now confirmed");
        check(!T1SocTargetCodec.confirmsRequested(70,56,1.0),
            "Raw56 belongs to 50%, never falsely confirm requested 70");
        check(T1SocTargetCodec.observedRawForPercent(70)==72,
            "Optimizer target preflight must allow 70 and expect raw72");
        check(T1SocTargetCodec.observedRawForPercent(60)==64,
            "60% must now be allowed by target preflight");
        check(T1SocTargetCodec.confirmsRequested(50,56,1.0),
            "raw 56 MUST confirm requested 50%");
        check(Double.isNaN(T1SocModePolicy.socTarget(70,1.0)),
            "raw70 unrecognized; floatRaw=1 not a percentage");
        check(T1SocTargetCodec.explain(56,1).contains("50%"),
            "diagnostics must show decoded 50% beside raw56");
        System.out.println("PASS: photographed 1041 intRaw->percent, no erroneous floatRaw=1");
    }
}
