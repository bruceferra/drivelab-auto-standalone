package ar.com.rango5gps.drivelabauto.receiver;

/** Pure width breakpoints for Receiver's independently resizable Cockpit.
 * Screen size is the ACTUAL Android window width, not the 1920px panel.
 */
final class T1CockpitResponsivePolicy {
    static final int WIDE=0,MEDIUM=1,NARROW=2,TINY=3;
    private T1CockpitResponsivePolicy(){}
    static int mode(int widthDp){
        if(widthDp>=1100)return WIDE;
        if(widthDp>=640)return MEDIUM;
        if(widthDp>=400)return NARROW;
        return TINY;
    }
    static float fontScale(int mode){
        if(mode==WIDE)return 1f;
        if(mode==MEDIUM)return .90f;
        if(mode==NARROW)return .82f;
        return .78f;
    }
    static int columns(int mode,int wide,int medium,int narrow){
        return mode==WIDE?wide:mode==MEDIUM?medium:
            mode==NARROW?narrow:1;
    }
}
