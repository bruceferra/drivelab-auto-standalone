package ar.com.rango5gps.drivelabauto.receiver;

public final class T1CenterWindowModePolicyTest {
    private static void ok(boolean pass,String text){
        if(!pass)throw new AssertionError(text);
    }
    public static void main(String[] args){
        String pro="ar.com.promanager.nativeapp";
        String uber="ar.com.rango5gps.uberbeta";
        String dump="Display #0 (activities from top to bottom):\n"+
            "* Task{988c023 #2799 visible=true type=standard "+
            "mode=fullscreen translucent=false A=10068:"+pro+
            " U=0 StackId=2799 sz=1}\n"+
            "    resizeMode=RESIZE_MODE_UNRESIZEABLE\n"+
            "* Task{a6ac9d1 #2798 visible=false type=standard "+
            "mode=fullscreen translucent=true A=10038:"+uber+
            " U=0 StackId=2798 sz=1}\n"+
            "    resizeMode=RESIZE_MODE_RESIZEABLE\n"+
            "Display #1 (activities from top to bottom):\n"+
            "* Task{some #2095 visible=true type=standard mode=freeform "+
            "A=10010:com.waze U=0 StackId=2095 sz=1}\n"+
            "    resizeMode=RESIZE_MODE_RESIZEABLE\n";
        ok(T1CenterWindowModePolicy.originalResizeMode(dump,2799,pro)==0,
            "Recover original ProManager resizeMode zero on Display 0");
        ok(T1CenterWindowModePolicy.originalResizeMode(dump,2798,uber)==2,
            "Recover original Uber resizeMode independently");
        ok(T1CenterWindowModePolicy.originalResizeMode(dump,2799,uber)==-1,
            "Never borrow a different task's resizeMode");
        ok(T1CenterWindowModePolicy.isFullscreen(dump,2799,pro),
            "User-provided Jetour display 0 task fullscreen");
        ok(!T1CenterWindowModePolicy.isFreeform(dump,2799,pro),
            "Reject false confirmation for fullscreen task");
        ok(T1CenterWindowModePolicy.isFreeform(dump,2095,"com.waze"),
            "Recognize freeform only for matching secondary task");
        ok(T1CenterWindowModePolicy.originalResizeMode(
            dump.replace("    resizeMode=RESIZE_MODE_UNRESIZEABLE\n",""),
            2799,pro)==-1,"No restore mode -> no mutation");
        ok(T1CenterWindowModePolicy.decodeMode("RESIZE_MODE_RESIZEABLE_AND_PIPABLE")==3,
            "Known reversible Android resize mode");
        ok(T1CenterWindowModePolicy.decodeMode("RESIZE_MODE_FORCE_RESIZEABLE")==-1,
            "Unsupported original mode must fail closed");
        ok(T1CenterWindowModePolicy.shellRejected("SecurityException: not allowed"),
            "Detect explicit ADB security refusal");
        ok(!T1CenterWindowModePolicy.shellRejected(""),
            "Empty ADB response is not success proof, but is not rejection");
        ok(T1CenterWindowModePolicy.isSystemPackage("com.autolink.bluetooth"),
            "OEM Bluetooth must never be resized");
        ok(!T1CenterWindowModePolicy.isSystemPackage(pro),
            "ProManager is an ordinary package");
        System.out.println("PASS reversible Display 0 mode probe policy and OEM guard");
    }
}
