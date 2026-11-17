package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression for restoring the verified Nov 16 wheel routing from v0.3.27
 * WITHOUT rolling back the fixed v0.3.33 cluster 60/40 reopening.
 * Physical Jetour accessibility and controls require an in-car check.
 */
public final class T1WheelNov16RollbackSourceTest {
    static String src(String name)throws Exception{return Files.readString(
        Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));}
    static void yes(boolean b,String what){if(!b)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String wheel=src("T1WazeWheelService.java");
        String boot=src("ReceiverService.java");
        String cluster=src("T1GlobalToolbar.java");
        String layout=src("T1ClusterLayoutPolicy.java");
        String microphone=src("T1OemMicrophoneService.java");
        int start=wheel.indexOf("private void handleOemKey(int code,int action,int extra){");
        int end=wheel.indexOf("@Override public boolean onKeyEvent(KeyEvent event)",start);
        yes(start>=0&&end>start,"Nov16 OEM wheel handler remains");
        String handler=wheel.substring(start,end);
        yes(handler.contains("case 1000:mapped=KeyEvent.KEYCODE_DPAD_RIGHT;")&&
            handler.contains("case 1001:mapped=KeyEvent.KEYCODE_DPAD_LEFT;")&&
            handler.contains("case 1002:mapped=KeyEvent.KEYCODE_BACK;")&&
            handler.contains("case 1003:mapped=KeyEvent.KEYCODE_DPAD_CENTER;")&&
            handler.contains("onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,mapped));"),
            "Nov16 v0.3.27 physical wheel dispatch restored");
        yes(!handler.contains("T1WheelAdbFallback.request(")&&
            !handler.contains("T1WheelAdbFocusPolicy")&&
            !handler.contains("am task focus")&&
            !handler.contains("input dpad"),
            "No post-Nov16 ADB steering focus/DPAD fallback from OEM wheel");
        yes(!boot.contains("T1WheelAdbFallback.restoreFocusAfterBoot(this)")&&
            boot.contains("T1WazeWheelService.requestClusterReady(this)"),
            "No auto task-focus stealing Waze pane after ignition");
        yes(wheel.contains("T1WheelAccessibilityRecovery.request(app)")&&
            wheel.contains("static boolean isServiceConnected()")&&
            microphone.contains("T1WazeWheelService.dispatchFromSharedMicrophone("),
            "Keep corrected accessibility binding and same 1010 microphone callback");
        yes(cluster.contains("reopenSavedPanelsStopped(owner)")&&
            layout.contains("static String reopenSavedPanelsStopped(Context c)")&&
            layout.contains("number(c,SPLIT_PERCENT,50,30,70)"),
            "Keep v0.3.33 explicit manual 60/40 cluster reopening");
        System.out.println("PASS: Nov 16 wheel dispatch restored, v0.3.33 cluster split retained, no ADB focus");
    }
}
