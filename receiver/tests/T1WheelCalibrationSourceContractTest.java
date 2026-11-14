package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CI contract for the T1's wheel/Zoom overlay, without assuming that the
 * car's proprietary MCU/CAN signal becomes a regular Android KeyEvent.
 */
public final class T1WheelCalibrationSourceContractTest {
    private static void check(boolean b,String reason){
        if(!b)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        Path base=Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver");
        String service=Files.readString(base.resolve("T1WazeWheelService.java"));
        String ui=Files.readString(base.resolve("T1WheelSettingsActivity.java"));
        int learning=service.indexOf("String pending=p.getString(PENDING,\"\");",
            service.indexOf("@Override public boolean onKeyEvent"));
        int wazeCheck=service.indexOf("AccessibilityNodeInfo root=wazeRoot()",learning);
        check(learning>0&&wazeCheck>learning,
            "45s explicit learning MUST precede the Waze foreground-only filter");
        check(service.substring(learning,wazeCheck).contains(
            "return false; // OEM gets same key"),
            "learning must NEVER consume wheel key or hijack Zoom");
        check(service.contains("observeActivityKey(Context c,KeyEvent event)"),
            "foreground activity must have second read-only input path");
        check(service.contains("AccessibilityNodeInfo root=wazeRoot();")&&
              service.contains("if(root==null){")&&
              service.contains("getWindowsOnAllDisplays()"),
            "normal route control must stay restricted to Waze across displays");
        check(ui.contains("dispatchKeyEvent(KeyEvent event)")&&
              ui.contains("T1WazeWheelService.observeActivityKey(this,event);")&&
              ui.contains("return super.dispatchKeyEvent(event);"),
            "onscreen wheel learning must not consume OEM key events");
        check(ui.contains("report=text(body,")&&
              ui.indexOf("report=text(body,")<
              ui.indexOf("learning(body,\"RUEDITA / SIGUIENTE\""),
            "diagnostic codes must be visible above buttons, no scrolling to bottom");
        check(ui.contains("getPackageManager())==null")&&
              ui.contains("Usá WebADB."),
            "missing OEM Android accessibility settings must not crash the activity");
        check(ui.contains("protected int overlayClearBackgroundColor()")&&
              ui.contains("return T1JetourTheme.NIGHT;")&&
              ui.contains("scroll.setBackgroundColor(T1JetourTheme.NIGHT);")&&
              ui.contains("body.setBackgroundColor(T1JetourTheme.NIGHT);"),
            "wheel settings must never inherit white shell or scroll background "+
            "under white diagnostic labels");
        check(service.contains("LEARN_MS=45000L"),
            "wheel key collection must be explicit and time-limited");
        check(!service.contains("getevent -"),
            "diagnostic requires no shell/root privileges");
        System.out.println("PASS: T1 wheel calibration in DriveLab screen, "+
            "no Waze switching, no OEM override, 45s capture, safe defaults");
    }
}
