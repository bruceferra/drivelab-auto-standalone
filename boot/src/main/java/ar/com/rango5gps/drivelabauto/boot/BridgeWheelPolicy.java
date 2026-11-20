package ar.com.rango5gps.drivelabauto.boot;

/**
 * Original, strictly bounded interop mapping of Jetour OEM hard keys.
 * T1Launcher T1N134 analysis: 1000/1001 scroll, 1002 BACK,
 * 1003 OK, action 100 press / 101 release.
 * No MODE/voice/ADAS/safety keys may ever become UI commands.
 */
final class BridgeWheelPolicy {
    static final int PRESS=100, RELEASE=101;
    static final int SCROLL_A=1000,SCROLL_B=1001,BACK=1002,OK=1003;
    static final int NONE=0,ZOOM_IN=1,ZOOM_OUT=2,CANCEL=3,CONFIRM=4;
    private BridgeWheelPolicy(){}
    static int translate(int key,int action,boolean reverse){
        if(action!=PRESS)return NONE;
        switch(key){
            case SCROLL_A:return reverse?ZOOM_OUT:ZOOM_IN;
            case SCROLL_B:return reverse?ZOOM_IN:ZOOM_OUT;
            case BACK:return CANCEL;
            case OK:return CONFIRM;
            default:return NONE;
        }
    }
    static String name(int action){
        switch(action){
            case ZOOM_IN:return "ACERCAR";
            case ZOOM_OUT:return "ALEJAR";
            case CANCEL:return "CANCELAR";
            case CONFIRM:return "ACEPTAR";
            default:return "IGNORAR";
        }
    }
}
