package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Observed VehicleService 1041 READBACK conversion on the Argentine
 * Jetour T1. Source: four owner photos of the T1Launcher signal panel.
 *
 * 1041 readInt is NOT the user's requested percentage. The adjacent
 * T1Launcher display reports:
 *       intRaw 30 => 20%, 40 => 30%, 48 => 40%, 56 => 50%.
 * Neither the raw integer nor 1041 readFloat (observed 1) is a
 * reliable percentage by itself.
 *
 * This is a READ-ONLY conversion. It does NOT authorize sending the
 * encoded integer to VehicleService: setter 1041 arguments have not
 * yet been reverse-engineered or physically validated.
 *
 * Do not extrapolate 60%/70% encodings from the four observed points.
 */
final class T1SocTargetCodec {
    private T1SocTargetCodec(){}

    static int observedPercentForRaw(int raw){
        switch(raw){
            case 30:return 20;
            case 40:return 30;
            case 48:return 40;
            case 56:return 50;
            default:return -1;
        }
    }

    static int observedRawForPercent(int percent){
        switch(percent){
            case 20:return 30;
            case 30:return 40;
            case 40:return 48;
            case 50:return 56;
            default:return -1;
        }
    }

    /**
     * Confirm by a known raw->% pairing. The floating 1041 getter
     * was observed as 1 and MUST NOT be treated as a percentage or
     * as evidence of a successful write. 60/70 have no known mapping.
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
