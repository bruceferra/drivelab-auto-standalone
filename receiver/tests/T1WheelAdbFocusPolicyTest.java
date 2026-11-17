package ar.com.rango5gps.drivelabauto.receiver;

/** Pure Android 11 focused-window parser and key whitelist tests.
 * No actual keys are sent, no OEM APIs and no network in these tests. */
public final class T1WheelAdbFocusPolicyTest {
    static void yes(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static String win(String pkg,int display,boolean focused,boolean keys){
        return "  2: name='Window{bcd u0 "+pkg+"/.MainActivity}', "+
            "displayId="+display+", paused=false, hasFocus="+focused+
            ", hasWallpaper=false, visible=true, canReceiveKeys="+keys+
            ", type=0x00000001";
    }
    private static String dump(String... windows){
        return "INPUT MANAGER\nInput Dispatcher State:\n  DispatchEnabled: 1\n"+
            "  Windows:\n"+String.join("\n",windows)+"\n";
    }
    public static void main(String[] args){
        String w="com.waze",m="com.google.android.apps.maps";
        yes(T1WheelAdbFocusPolicy.focusedOn(dump(
                win("ar.com.rango5gps.drivelabauto.receiver",0,true,true),
                win(w,1,true,true),win(m,1,false,true)),1,w),
            "Waze focused in Display 1 despite Receiver on Display 0");
        yes(T1WheelAdbFocusPolicy.focusedOn(dump(
                win(w,1,false,true),win(m,1,true,true)),1,m),
            "Maps focused on same verified display");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(win(w,0,true,true)),1,w),
            "Never use center screen");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(win(w,1,false,true)),1,w),
            "A visible but unfocused Waze task cannot receive keys");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(win(w,1,true,false)),1,w),
            "Window must accept keys");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(
                win("com.autolink.instrument",1,true,true),
                win(w,1,false,true)),1,w),
            "OEM instruments cannot receive map commands");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(
                win(w,1,true,true),win("com.autolink.instrument",1,true,true)),1,w),
            "Fail closed on two simultaneous focused windows");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(win(w,1,true,true)),1,m),
            "One map must not receive another map's input");
        yes(!T1WheelAdbFocusPolicy.focusedOn(dump(win(w,1,true,true)),0,w),
            "Default display not allowed");
        yes(!T1WheelAdbFocusPolicy.focusedOn("Input Dispatcher State:\n  Windows:\n",
                1,w),"No focus evidence must fail closed");
        yes(T1WheelAdbFocusPolicy.command(1,22).equals(
            "input dpad -d 1 keyevent 22"),"Only explicit DPAD on secondary display");
        for(int[] x:new int[][]{{0,22},{1,4},{1,3},{1,66},{10,22}}){
            boolean rejected=false;
            try{T1WheelAdbFocusPolicy.command(x[0],x[1]);}
            catch(IllegalArgumentException expected){rejected=true;}
            yes(rejected,"Unknown/global/back key must be rejected");
        }
        System.out.println("PASS per-display map focus and strict ADB DPAD");
    }
}
