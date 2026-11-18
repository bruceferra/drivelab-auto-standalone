package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Observed VehicleService 1041 READBACK conversion on the Argentine
 * Jetour T1. Sources: earlier T1Launcher signal photos plus owner's
 * 18-Nov-2026 paired ORIGINAL Jetour OEM menu A/B snapshots:
 *   SMART (stored OEM 70) -> 1040=2, 1041 raw72;
 *   FORZADO 70%           -> 1040=4, 1041 raw72;
 *   FORZADO 50%           -> 1040=4, 1041 raw56.
 *   FORZADO 60%           -> 1040=4, 1041 raw64 (owner confirmed).
 * Original verified older signal photos: raw30->20%, raw40->30%,
 * raw48->40%, raw56->50%. On this unit raw72->70% is now measured.
 * Neither the raw integer nor readFloat (observed 1) is the percentage.
 *
 * This is a READBACK conversion, NOT the setter encoding. 1041 setter
 * is sent the allowlisted percentage (70), NEVER the raw code (72).
 * A successful readback confirms the target only, not strategy/regen:
 * those are independently verified using OEM 1040/1046.
 * 60% raw64 was explicitly confirmed by the owner after the A/B capture.
 * This validates readback only; a complete DriveLab write is not yet proven.
 */
final class T1SocTargetCodec {
    private T1SocTargetCodec(){}

    static int observedPercentForRaw(int raw){
        switch(raw){
            case 30:return 20;
            case 40:return 30;
            case 48:return 40;
            case 56:return 50;
            case 64:return 60;
            case 72:return 70;
            default:return -1;
        }
    }

    static int observedRawForPercent(int percent){
        switch(percent){
            case 20:return 30;
            case 30:return 40;
            case 40:return 48;
            case 50:return 56;
            case 60:return 64;
            case 70:return 72;
            default:return -1;
        }
    }

    /**
     * Confirm only measured raw->% pairs. The floating 1041 getter
     * was observed as 1 and MUST NOT be treated as a percentage.
     * 60% and 70% now have owner-confirmed OEM pairings raw64/raw72.
     */
    static boolean confirmsRequested(int requested,int rawInt,double rawFloat){
        return T1SocRegenWritePolicy.accepted(1041,requested)&&
            observedPercentForRaw(rawInt)==requested;
    }

    static String explain(int rawInt,double rawFloat){
        int known=observedPercentForRaw(rawInt);
        return "1041 intRaw="+(rawInt==Integer.MIN_VALUE?"SIN LECTURA":
            Integer.toString(rawInt))+
            " -> "+(known>=0?known+"% observado en T1Launcher":
                "porcentaje SIN MAPEO confirmado")+
            "; floatRaw="+(Double.isFinite(rawFloat)?
                String.valueOf(rawFloat):"sin lectura")+
            " (getter flotante no calibrado)";
    }
}
