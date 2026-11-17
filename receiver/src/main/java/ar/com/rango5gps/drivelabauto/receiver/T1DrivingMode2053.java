package ar.com.rango5gps.drivelabauto.receiver;

/** Owner-verified driving style OEM 2053. Distinct from PHEV mode OEM 1043.
 * Display labels only, never send OEM writes. */
final class T1DrivingMode2053 {
    private T1DrivingMode2053(){}
    static String label(int raw){
        switch(raw){
            case 0:return "ECO";
            case 1:return "NORMAL";
            case 2:return "SPORT";
            case 3:return "SNOW";
            default:return "SIN DATO";
        }
    }
}
