package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fail-closed regression for owner's parked-only Waze foreground probe. */
public final class BridgeWazeForegroundProbeTest {
    private static void ok(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static String file(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/",name));
    }
    private static final String OEM=
        "Stack id=7 bounds=[0,0][1920,720] displayId=1 userId=0\n"+
        " taskId=31: com.jetour.cluster/.Home userId=0\n";
    private static final String WAZE=
        "Stack id=99 bounds=[0,0][1920,720] displayId=1 userId=0\n"+
        " taskId=701: com.waze/.MainActivity userId=0\n";
    private static final String MAPS=
        "Stack id=102 bounds=[0,0][1920,720] displayId=1 userId=0\n"+
        " taskId=810: com.google.android.apps.maps/.MapsActivity userId=0\n";
    public static void main(String[] args)throws Exception{
        BridgeClusterRecoveryPolicy.Task waze=
            BridgeWazeForegroundPolicy.uniqueWaze(OEM+WAZE+MAPS);
        ok(waze!=null&&waze.stackId==99&&waze.taskId==701&&
            "com.waze".equals(waze.pkg),"Only isolated Waze on Display1");
        ok("am stack move-task 701 99 true".equals(
            BridgeWazeForegroundPolicy.sameStackToTop(waze)),
            "Always move Waze to the top of the SAME stack");
        ok(BridgeWazeForegroundPolicy.same(waze,waze),
            "Verify exact task id, stack id and package");
        ok(!BridgeWazeForegroundPolicy.same(waze,
            new BridgeClusterRecoveryPolicy.Task(100,701,"com.waze")),
            "Reject stack changed after confirmation");
        ok(!BridgeWazeForegroundPolicy.same(waze,
            new BridgeClusterRecoveryPolicy.Task(99,703,"com.waze")),
            "Reject task changed after confirmation");
        ok(!BridgeWazeForegroundPolicy.same(waze,
            new BridgeClusterRecoveryPolicy.Task(99,701,"com.jetour.mode")),
            "Reject package switch");
        ok(BridgeWazeForegroundPolicy.uniqueWaze(OEM)==null,
            "Never act when only OEM present");
        ok(BridgeWazeForegroundPolicy.uniqueWaze(
            WAZE.replace("displayId=1","displayId=0"))==null,
            "Never touch Display0");
        ok(BridgeWazeForegroundPolicy.uniqueWaze(
            WAZE+" taskId=702: com.jetour.mode/.Media userId=0\n")==null,
            "Reject mixed Waze/OEM stack");
        ok(BridgeWazeForegroundPolicy.uniqueWaze(WAZE+WAZE)==null,
            "Reject duplicate Waze roots");
        ok(BridgeWazeForegroundPolicy.uniqueWaze(MAPS)==null,
            "Do not foreground Google Maps when owner requests Waze");
        ok(BridgeWazeForegroundPolicy.uniqueWaze(
            WAZE.replace("Stack id=99","Stack id=1"))==null,
            "Never act on protected low-numbered stack");
        boolean rejected=false;
        try{BridgeWazeForegroundPolicy.sameStackToTop(
            new BridgeClusterRecoveryPolicy.Task(5,701,"com.waze"));}
        catch(SecurityException expected){rejected=true;}
        ok(rejected,"Invalid root must fail closed");
        String ui=file("BootActivity.java");
        String probe=file("BridgeWazeForegroundProbe.java");
        String adb=file("BridgeLocalAdb.java");
        String receiver=file("BootLinkService.java");
        String ignition=file("BootReceiver.java");
        ok(ui.contains("PROBAR ADB · WAZE AL FRENTE · DISPLAY 1")&&
            ui.contains("BridgeWazeForegroundProbe.ask(this)"),
            "Only manual owner-clicked probe UI");
        ok(probe.contains("new AlertDialog.Builder(owner)")&&
            probe.contains("PROBAR WAZE AL FRENTE")&&
            probe.contains("BridgeWazeForegroundPolicy.uniqueWaze(report)")&&
            probe.contains("BridgeLocalAdb.bringVerifiedWazeToFront(")&&
            probe.contains("NO es prueba de píxeles")&&
            !probe.contains("startForegroundService(")&&
            !probe.contains("pm clear")&&
            !probe.contains("am force-stop"),
            "Owner confirms after reading IDs, no automatic execution or data loss");
        ok(adb.contains("bringVerifiedWazeToFront(Context context,")&&
            adb.contains("BridgeWazeForegroundPolicy.same(wanted,now)")&&
            adb.contains("shellInternal(context,command,true)")&&
            adb.contains("shellInternal(context,command,false)")&&
            adb.contains("verifiedWazeProbe&&")&&
            adb.contains("am stack move-task [1-9][0-9]{1,6} ")&&
            !receiver.contains("BridgeWazeForegroundProbe")&&
            !ignition.contains("BridgeWazeForegroundProbe"),
            "No generic shell access and no OTA/route/ignition task mutation");
        System.out.println("PASS: one-time owner-confirmed Waze-only same-stack ADB, never OEM");
    }
}
